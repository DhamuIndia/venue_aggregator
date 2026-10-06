package com.staminal.venue.overture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.staminal.venue.media.UploadStorageProperties;

/** Backend-only SigV4 access. This class neither creates buckets nor changes their policies. */
@Component
public class OvertureDraftMediaStorage {
    private static final int MAX_CONTROL_BYTES = 131_072;
    private static final byte[] EMPTY = new byte[0];
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]");
    private static final Pattern KEY = Pattern.compile("drafts/([1-9][0-9]{0,18})/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final OvertureDraftMediaProperties properties;
    private final UploadStorageProperties shared;
    private final String endpoint;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Clock clock;
    private final Duration timeout;

    @Autowired
    public OvertureDraftMediaStorage(OvertureDraftMediaProperties properties, UploadStorageProperties shared,
            @Value("${app.storage.s3.endpoint:http://localhost:9000}") String endpoint, ObjectMapper mapper) {
        this(properties, shared, endpoint, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), Clock.systemUTC(), Duration.ofSeconds(15));
    }

    OvertureDraftMediaStorage(OvertureDraftMediaProperties properties, UploadStorageProperties shared, String endpoint,
            ObjectMapper mapper, HttpClient client, Clock clock, Duration timeout) {
        this.properties = properties; this.shared = shared; this.endpoint = endpoint;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.client = client; this.clock = clock; this.timeout = timeout;
    }

    public void ensurePrivate() {
        checkPrivate();
    }

    private boolean checkPrivate() {
        validateConfiguration();
        Response policy = exchange("GET", null, "policy=", EMPTY, Map.of(), MAX_CONTROL_BYTES);
        if (policy.status() == 404) {
            Document error = xml(policy.body());
            if (!"Error".equals(error.getDocumentElement().getLocalName()) || !"NoSuchBucketPolicy".equals(singleText(error.getDocumentElement(), "Code")))
                throw unavailable();
        } else {
            requireStatus(policy, 200);
            verifyPolicy(policy.body());
        }
        Response acl = exchange("GET", null, "acl=", EMPTY, Map.of(), MAX_CONTROL_BYTES);
        requireStatus(acl, 200);
        boolean syntheticMinio = verifyAcl(acl.body(), acl.headers());
        if (syntheticMinio) denyAnonymous(null);
        return syntheticMinio;
    }

    public void put(String key, byte[] bytes, String contentType) {
        validateKey(key);
        if (!"image/jpeg".equals(contentType) || bytes == null || bytes.length < 3
                || bytes.length > OvertureDraftImageProcessor.MAX_OUTPUT_BYTES || (bytes[0] & 255) != 255
                || (bytes[1] & 255) != 216 || (bytes[2] & 255) != 255) throw invalid();
        boolean syntheticMinio = checkPrivate();
        Response response = exchange("PUT", key, null, bytes.clone(),
                Map.of("content-type", "image/jpeg", "cache-control", "no-store", "x-amz-acl", "private"), MAX_CONTROL_BYTES);
        requireStatus(response, 200);
        if (syntheticMinio) {
            try { denyAnonymous(key); }
            catch (RuntimeException exception) {
                // Do not leave a newly written object behind when its privacy proof fails.
                try { delete(key); } catch (RuntimeException ignored) { /* The transactional service also records compensation failure. */ }
                throw exception;
            }
        }
    }

    public byte[] read(String key, int maxBytes) {
        validateKey(key);
        if (maxBytes < 1 || maxBytes > OvertureDraftImageProcessor.MAX_OUTPUT_BYTES) throw invalid();
        boolean syntheticMinio = checkPrivate();
        // Bucket ACLs do not override individual public object ACLs on S3.
        Response acl = exchange("GET", key, "acl=", EMPTY, Map.of(), MAX_CONTROL_BYTES);
        if (acl.status() == 404) throw missing();
        requireStatus(acl, 200);
        boolean syntheticObjectAcl = verifyAcl(acl.body(), acl.headers());
        if (syntheticMinio || syntheticObjectAcl) denyAnonymous(key);
        Response object = exchange("GET", key, null, EMPTY, Map.of(), maxBytes);
        if (object.status() == 404) throw missing();
        requireStatus(object, 200);
        String mime = object.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        byte[] bytes = object.body();
        if (!"image/jpeg".equals(mime) || bytes.length < 3 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216 || (bytes[2] & 255) != 255)
            throw unavailable();
        return bytes;
    }

    /** Rollback cleanup can delete an object even if a privacy check has just failed. */
    public void delete(String key) {
        validateKey(key); validateConfiguration();
        Response response = exchange("DELETE", key, null, EMPTY, Map.of(), MAX_CONTROL_BYTES);
        if (response.status() != 204 && response.status() != 200 && response.status() != 404) throw unavailable();
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private draft photo storage is disabled");
        String bucket = properties.getBucket();
        if (!BUCKET.matcher(bucket).matches() || shared.getBucket() == null || shared.getBucket().isBlank()
                || bucket.equals(shared.getBucket().strip())
                || shared.getRegion() == null || !shared.getRegion().matches("[a-z0-9-]{1,64}")
                || shared.getAccessKey() == null || !shared.getAccessKey().matches("[A-Za-z0-9_-]{1,256}")
                || shared.getSecretKey() == null || shared.getSecretKey().isBlank()
                || shared.getSecretKey().chars().anyMatch(Character::isISOControl)
                || client.followRedirects() != HttpClient.Redirect.NEVER) throw unavailable();
        endpointUri();
    }
    private URI endpointUri() {
        try {
            URI uri = URI.create(endpoint);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535
                    || !shared.isPathStyleAccess() && uri.getHost().contains(":")) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException exception) { throw unavailable(); }
    }
    private static void validateKey(String key) {
        if (key == null) throw invalid();
        var match = KEY.matcher(key);
        if (!match.matches()) throw invalid();
        try { Long.parseLong(match.group(1)); } catch (NumberFormatException exception) { throw invalid(); }
    }
    private URI objectUri(String key, String query) {
        URI base = endpointUri();
        String host = shared.isPathStyleAccess() ? base.getHost() : properties.getBucket() + "." + base.getHost();
        String path = (shared.isPathStyleAccess() ? "/" + properties.getBucket() : "") + "/" + (key == null ? "" : key);
        try { return new URI(base.getScheme(), null, host, base.getPort(), path, query, null); }
        catch (Exception exception) { throw unavailable(); }
    }
    private record Response(int status, java.net.http.HttpHeaders headers, byte[] body) { }

    private Response exchange(String method, String key, String query, byte[] body, Map<String, String> headers, int limit) {
        HttpRequest request = signedRequest(method, objectUri(key, query), body, headers, shared.getAccessKey(), shared.getSecretKey(),
                shared.getRegion(), Instant.now(clock), timeout);
        return execute(request, limit);
    }

    private void denyAnonymous(String key) {
        URI uri = objectUri(key, key == null ? "list-type=2&max-keys=0" : null);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .GET().build();
        Response response = execute(request, MAX_CONTROL_BYTES);
        if (response.status() != 401 && response.status() != 403) throw unavailable();
    }

    private Response execute(HttpRequest request, int limit) {
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            future = client.sendAsync(request, info -> {
                // HEAD reports a representation length even though its response has no body.
                long declared = request.method().equals("HEAD") ? -1 : info.headers().firstValueAsLong("Content-Length").orElse(-1);
                return new BoundedBodySubscriber(limit, declared);
            });
            HttpResponse<byte[]> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.body() == null || response.body().length > limit) throw unavailable();
            return new Response(response.statusCode(), response.headers(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); if (future != null) future.cancel(true); throw unavailable();
        } catch (Exception exception) {
            if (future != null) future.cancel(true); throw unavailable();
        }
    }

    // AWS canonical request/header algorithm: full payload hash, no presigned/public URLs.
    static HttpRequest signedRequest(String method, URI uri, byte[] body, Map<String, String> extra,
            String accessKey, String secretKey, String region, Instant now, Duration timeout) {
        String date = DATE.format(now), timestamp = AMZ_DATE.format(now), payloadHash = sha256(body);
        int port = uri.getPort();
        String host = uri.getHost() + (port == -1 || port == 80 && uri.getScheme().equals("http") || port == 443 && uri.getScheme().equals("https") ? "" : ":" + port);
        TreeMap<String, String> headers = new TreeMap<>(extra);
        headers.put("host", host); headers.put("x-amz-content-sha256", payloadHash); headers.put("x-amz-date", timestamp);
        StringBuilder canonicalHeaders = new StringBuilder();
        headers.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value.strip().replaceAll("\\s+", " ")).append('\n'));
        String signedHeaders = String.join(";", headers.keySet());
        String canonical = method + "\n" + uri.getRawPath() + "\n" + (uri.getRawQuery() == null ? "" : uri.getRawQuery())
                + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String scope = date + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + timestamp + "\n" + scope + "\n" + sha256(canonical.getBytes(StandardCharsets.UTF_8));
        byte[] signing = hmac(hmac(hmac(hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date), region), "s3"), "aws4_request");
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ",SignedHeaders=" + signedHeaders
                + ",Signature=" + HexFormat.of().formatHex(hmac(signing, toSign));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", authorization);
        headers.forEach((name, value) -> { if (!name.equals("host")) builder.header(name, value); });
        return builder.method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }
    private static byte[] hmac(byte[] key, String value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw unavailable(); }
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw unavailable(); }
    }

    private void verifyPolicy(byte[] bytes) {
        try {
            JsonNode policy = mapper.readTree(bytes);
            if (policy == null || !policy.isObject()) throw unavailable();
            JsonNode statements = policy.get("Statement");
            if (statements == null || !(statements.isArray() || statements.isObject()) || statements.isArray() && statements.isEmpty()) throw unavailable();
            Iterable<JsonNode> items = statements.isArray() ? statements : List.of(statements);
            for (JsonNode statement : items) {
                if (!statement.isObject() || !statement.path("Effect").isTextual()) throw unavailable();
                String effect = statement.path("Effect").textValue();
                if (!effect.equals("Allow") && !effect.equals("Deny")) throw unavailable();
                if (effect.equals("Allow")) {
                    if (statement.has("NotPrincipal") || !statement.has("Principal") || !privatePrincipal(statement.get("Principal"))) throw unavailable();
                    if (!statement.has("Action") && !statement.has("NotAction") || !statement.has("Resource") && !statement.has("NotResource")) throw unavailable();
                }
            }
        } catch (Exception exception) { throw unavailable(); }
    }
    private static boolean privatePrincipal(JsonNode principal) {
        if (principal.isTextual()) return !principal.textValue().isBlank() && !principal.textValue().contains("*")
                && !principal.textValue().contains("?") && !principal.textValue().contains("${");
        if (principal.isArray()) {
            if (principal.isEmpty()) return false;
            for (JsonNode item : principal) if (!privatePrincipal(item)) return false;
            return true;
        }
        if (!principal.isObject() || principal.isEmpty()) return false;
        var fields = principal.fields();
        while (fields.hasNext()) {
            var item = fields.next();
            if (!List.of("AWS", "Service", "CanonicalUser", "Federated").contains(item.getKey()) || !privatePrincipal(item.getValue())) return false;
        }
        return true;
    }
    private static Document xml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException error) throws SAXParseException { throw error; }
                public void error(SAXParseException error) throws SAXParseException { throw error; }
                public void fatalError(SAXParseException error) throws SAXParseException { throw error; }
            });
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception exception) { throw unavailable(); }
    }
    private static boolean verifyAcl(byte[] bytes, java.net.http.HttpHeaders headers) {
        Document document = xml(bytes); Element root = document.getDocumentElement();
        if (!"AccessControlPolicy".equals(root.getLocalName())) throw unavailable();
        NodeList owners = root.getElementsByTagNameNS("*", "Owner"), lists = root.getElementsByTagNameNS("*", "AccessControlList");
        if (owners.getLength() != 1 || lists.getLength() != 1) throw unavailable();
        if (singleText((Element) owners.item(0), "ID").isBlank()) {
            // MinIO does not implement ACL ownership. Its exact synthetic response is
            // Modern MinIO omits Server. The exact synthetic shape is not itself a
            // privacy proof: callers must also prove unsigned bucket/object GET denial.
            String server = headers.firstValue("Server").orElse("");
            if (!server.isEmpty() && !server.equalsIgnoreCase("MinIO") || !syntheticMinioAcl(root)) throw unavailable();
            return true;
        }
        NodeList grants = ((Element) lists.item(0)).getElementsByTagNameNS("*", "Grant");
        if (grants.getLength() == 0 || grants.getLength() > 100) throw unavailable();
        for (int i = 0; i < grants.getLength(); i++) {
            Element grant = (Element) grants.item(i);
            NodeList grantees = grant.getElementsByTagNameNS("*", "Grantee");
            if (grantees.getLength() != 1) throw unavailable();
            Element grantee = (Element) grantees.item(0);
            // A strict canonical-user ACL deliberately excludes all predefined group grants.
            String type = grantee.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
            if (!(type.equals("CanonicalUser") || type.equals("Canonical User")) || singleText(grantee, "ID").isBlank()
                    || grantee.getElementsByTagNameNS("*", "URI").getLength() != 0) throw unavailable();
            String permission = singleText(grant, "Permission");
            if (!List.of("READ", "WRITE", "READ_ACP", "WRITE_ACP", "FULL_CONTROL").contains(permission)) throw unavailable();
        }
        return false;
    }
    private static boolean syntheticMinioAcl(Element root) {
        if (!childNames(root).equals(List.of("Owner", "AccessControlList")) || root.getElementsByTagNameNS("*", "URI").getLength() != 0) return false;
        Element owner = child(root, "Owner"), acl = child(root, "AccessControlList");
        if (!childNames(owner).equals(List.of("ID", "DisplayName")) || !singleText(owner, "DisplayName").isBlank()
                || !flat(child(owner, "ID")) || !flat(child(owner, "DisplayName"))
                || !childNames(acl).equals(List.of("Grant"))) return false;
        Element grant = child(acl, "Grant");
        if (!childNames(grant).equals(List.of("Grantee", "Permission")) || !flat(child(grant, "Permission"))
                || !singleText(grant, "Permission").equals("FULL_CONTROL")) return false;
        Element grantee = child(grant, "Grantee");
        return childNames(grantee).equals(List.of("Type")) && flat(child(grantee, "Type"))
                && singleText(grantee, "Type").equals("CanonicalUser")
                && grantee.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type").equals("CanonicalUser");
    }
    private static List<String> childNames(Element parent) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) names.add(element.getLocalName());
            else if (node.getNodeType() == Node.TEXT_NODE && !node.getTextContent().isBlank()) throw unavailable();
        }
        return names;
    }
    private static boolean flat(Element element) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) if (node instanceof Element) return false;
        return true;
    }
    private static Element child(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element && name.equals(element.getLocalName())) return element;
        throw unavailable();
    }
    private static String singleText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() != 1 || nodes.item(0).getNodeType() != Node.ELEMENT_NODE) throw unavailable();
        return nodes.item(0).getTextContent().strip();
    }
    private static void requireStatus(Response response, int expected) { if (response.status() != expected) throw unavailable(); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private draft photo storage is unavailable or its privacy could not be verified"); }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid private draft photo object"); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft photo bytes not found"); }

    /** Backpressure and bounded allocation apply before a response body is buffered. */
    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final long declared;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBodySubscriber(int limit, long declared) { this.limit = limit; this.declared = declared; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) { subscription.cancel(); return; }
            this.subscription = subscription;
            if (declared > limit || declared < -1) fail(); else subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) { fail(); return; }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        private void fail() { if (subscription != null) subscription.cancel(); body.completeExceptionally(new IOException("Storage response exceeded its limit")); }
        @Override public void onError(Throwable error) { body.completeExceptionally(new IOException("Storage response failed")); }
        @Override public void onComplete() {
            if (declared >= 0 && declared != bytes.size()) fail(); else body.complete(bytes.toByteArray());
        }
    }
}
