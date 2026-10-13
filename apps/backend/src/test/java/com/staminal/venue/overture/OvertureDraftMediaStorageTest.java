package com.staminal.venue.overture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.media.UploadStorageProperties;

class OvertureDraftMediaStorageTest {
    private static final String KEY = "drafts/42/08df8947-1544-475d-9d81-40984bbdf110.jpg";
    private static final byte[] JPEG = {(byte)255,(byte)216,(byte)255,(byte)217};
    private static final String PRIVATE_ACL = """
            <AccessControlPolicy xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
              <Owner><ID>owner</ID></Owner><AccessControlList><Grant>
                <Grantee xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="CanonicalUser"><ID>owner</ID></Grantee>
                <Permission>FULL_CONTROL</Permission></Grant></AccessControlList>
            </AccessControlPolicy>
            """;
    private static final String NO_POLICY = "<Error><Code>NoSuchBucketPolicy</Code></Error>";
    private static final String MINIO_ACL = "<AccessControlPolicy><Owner><ID></ID><DisplayName></DisplayName></Owner><AccessControlList><Grant><Grantee xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"CanonicalUser\"><Type>CanonicalUser</Type></Grantee><Permission>FULL_CONTROL</Permission></Grant></AccessControlList></AccessControlPolicy>";
    private final Queue<Fixture> responses = new ArrayDeque<>();
    private final List<HttpRequest> requests = new ArrayList<>();
    private HttpClient client;
    private OvertureDraftMediaStorage storage;
    private static final Instant NOW = Instant.parse("2013-05-24T00:00:00Z");

    @BeforeEach void setup() {
        client = mock(HttpClient.class); when(client.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        when(client.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0); requests.add(request);
            Fixture fixture = responses.remove();
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            HttpResponse.ResponseInfo info = mock(HttpResponse.ResponseInfo.class);
            HttpHeaders headers = HttpHeaders.of(fixture.headers(), (name, value) -> true);
            when(info.statusCode()).thenReturn(fixture.status()); when(info.headers()).thenReturn(headers);
            var subscriber = handler.apply(info); subscriber.onSubscribe(mock(Flow.Subscription.class));
            subscriber.onNext(List.of(ByteBuffer.wrap(fixture.body()))); subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().thenApply(bytes -> {
                @SuppressWarnings("unchecked") HttpResponse<byte[]> response = mock(HttpResponse.class);
                when(response.statusCode()).thenReturn(fixture.status()); when(response.headers()).thenReturn(headers); when(response.body()).thenReturn(bytes); return response;
            });
        });
        storage = storage(true, "venue-draft-media-private", "http://internal.test:9000", Duration.ofSeconds(15));
    }

    @Test void matchesTheOfficialAwsSigV4HeaderExample() {
        HttpRequest request = OvertureDraftMediaStorage.signedRequest("GET", URI.create("https://examplebucket.s3.amazonaws.com/test.txt"), new byte[0],
                Map.of("range", "bytes=0-9"), "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1", NOW, Duration.ofSeconds(15));
        assertThat(request.headers().firstValue("Authorization").orElseThrow()).isEqualTo(
                "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
    }

    @Test void putUsesInternalEndpointAndExplicitPrivateAclAfterBothPrivacyChecks() {
        privateBucket(); add(200, ""); storage.put(KEY, JPEG, "image/jpeg");
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).uri().getRawQuery()).isEqualTo("policy="); assertThat(requests.get(1).uri().getRawQuery()).isEqualTo("acl=");
        HttpRequest put = requests.get(2);
        assertThat(put.uri().toString()).isEqualTo("http://internal.test:9000/venue-draft-media-private/" + KEY);
        assertThat(put.method()).isEqualTo("PUT"); assertThat(put.headers().firstValue("x-amz-acl")).contains("private");
        assertThat(put.headers().firstValue("Cache-Control")).contains("no-store"); assertThat(put.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(put.headers().firstValue("Authorization").orElseThrow()).contains("x-amz-acl", "x-amz-content-sha256");
        assertThat(put.uri().toString()).doesNotContain("public.test", "secret");
    }

    @ParameterizedTest @ValueSource(strings = {"\"*\"", "{\"AWS\":\"*\"}", "{\"AWS\":[\"arn:aws:iam::123456789012:root\",\"*\"]}",
            "{\"AWS\":\"arn:aws:iam::123456789012:user/${aws:username}\"}", "{\"AWS\":\"arn:aws:iam::123456789012:user/a?\"}", "null", "{}"})
    void rejectsEveryPublicOrUnprovableAllowPrincipal(String principal) {
        add(200, "{\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":" + principal + ",\"Action\":\"s3:GetObject\",\"Resource\":\"*\",\"Condition\":{\"StringEquals\":{\"aws:SourceVpce\":\"vpce-fixed\"}}}]}");
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE); assertThat(requests).hasSize(1);
    }

    @Test void rejectsNotPrincipalAllowButAcceptsFixedNamedOrDenyPolicies() {
        add(200, "{\"Statement\":{\"Effect\":\"Allow\",\"NotPrincipal\":{\"AWS\":\"arn:aws:iam::123456789012:root\"},\"Action\":\"*\",\"Resource\":\"*\"}}");
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        add(200, "{\"Statement\":[{\"Effect\":\"Deny\",\"Principal\":\"*\",\"Action\":\"*\",\"Resource\":\"*\"},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"arn:aws:iam::123456789012:root\"},\"Action\":\"s3:GetObject\",\"Resource\":\"*\"}]}");
        add(200, PRIVATE_ACL); storage.ensurePrivate();
    }

    @ParameterizedTest @ValueSource(strings = {"{\"Statement\":[]}", "{\"Statement\":{\"Effect\":\"Unrecognized\"}}", "[]",
            "{\"Statement\":{\"Effect\":\"Deny\"}} {}", "{\"Statement\":{\"Effect\":\"Deny\",\"Effect\":\"Allow\"}}"})
    void rejectsAmbiguousMalformedAndTrailingPolicyData(String policy) {
        add(200, policy); rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ParameterizedTest @ValueSource(strings = {"<Error><Code>NoSuchBucket</Code></Error>", "<Error><Code>AccessDenied</Code></Error>",
            "<!DOCTYPE Error [<!ENTITY value SYSTEM 'file:///etc/passwd'>]><Error><Code>&value;</Code></Error>", "<Error/>", "not xml"})
    void onlyNoSuchBucketPolicyIsAnAcceptable404(String body) {
        add(404, body); rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ParameterizedTest @ValueSource(strings = {"AllUsers", "AuthenticatedUsers", "LogDelivery"})
    void rejectsPublicAndUnknownGroupAclGrants(String group) {
        add(404, NO_POLICY); add(200, groupAcl(group));
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void rejectsMalformedOrEntityExpandingAcls() {
        add(404, NO_POLICY); add(200, "<!DOCTYPE AccessControlPolicy [<!ENTITY value SYSTEM 'file:///etc/passwd'>]><AccessControlPolicy><Owner><ID>&value;</ID></Owner></AccessControlPolicy>");
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        add(404, NO_POLICY); add(200, "<AccessControlPolicy><Owner><ID>owner</ID></Owner><AccessControlList/></AccessControlPolicy>");
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void readChecksIndividualObjectAclAndReturnsOnlyBoundedJpegBytes() {
        privateBucket(); add(200, PRIVATE_ACL); responses.add(new Fixture(200, Map.of("Content-Type", List.of("image/jpeg")), JPEG));
        assertThat(storage.read(KEY, 4)).isEqualTo(JPEG); assertThat(requests).hasSize(4);
        assertThat(requests.get(2).uri().getRawPath()).endsWith(KEY); assertThat(requests.get(2).uri().getRawQuery()).isEqualTo("acl=");
        assertThat(requests.get(3).uri().getRawQuery()).isNull();
    }

    @Test void aPublicObjectAclStopsReadBeforeFetchingItsImage() {
        privateBucket(); add(200, groupAcl("AllUsers"));
        rejected(() -> storage.read(KEY, 4), HttpStatus.SERVICE_UNAVAILABLE); assertThat(requests).hasSize(3);
    }

    @Test void readRejectsWrongMimeAndReportsMissingObjectWithoutStorageDetails() {
        privateBucket(); add(200, PRIVATE_ACL); responses.add(new Fixture(200, Map.of("Content-Type", List.of("image/svg+xml")), JPEG));
        rejected(() -> storage.read(KEY, 4), HttpStatus.SERVICE_UNAVAILABLE);
        privateBucket(); add(404, "<Error><Code>NoSuchKey</Code></Error>");
        rejected(() -> storage.read(KEY, 4), HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest @ValueSource(ints = {301,302,403,500})
    void redirectsAndErrorResponsesNeverEstablishPrivacy(int status) {
        add(status, "upstream secret diagnostic");
        assertThatThrownBy(storage::ensurePrivate).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(error.getReason()).doesNotContain("secret", "internal.test", "public.test");
        }); assertThat(requests).hasSize(1);
    }

    @Test void boundsControlAndObjectBodiesBeforeBufferingAndRejectsTruncation() {
        responses.add(new Fixture(200, Map.of(), new byte[131073])); rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        responses.add(new Fixture(200, Map.of("Content-Length", List.of("131073")), new byte[0])); rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        privateBucket(); add(200, PRIVATE_ACL); responses.add(new Fixture(200, Map.of("Content-Type", List.of("image/jpeg")), new byte[5]));
        rejected(() -> storage.read(KEY, 4), HttpStatus.SERVICE_UNAVAILABLE);
        privateBucket(); add(200, PRIVATE_ACL); responses.add(new Fixture(200, Map.of("Content-Type", List.of("image/jpeg"), "Content-Length", List.of("4")), new byte[3]));
        rejected(() -> storage.read(KEY, 4), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void wallClockTimeoutCancelsTheOutstandingExchange() {
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        org.mockito.Mockito.doReturn(pending).when(client).sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        storage = storage(true, "venue-draft-media-private", "http://internal.test:9000", Duration.ofMillis(20));
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE); assertThat(pending.isCancelled()).isTrue();
    }

    @Test void rejectsDisabledPublicBucketAndUnsafeEndpointBeforeNetwork() {
        rejected(storage(false, "venue-draft-media-private", "http://internal.test:9000", Duration.ofSeconds(15))::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        rejected(storage(true, "venue-media", "http://internal.test:9000", Duration.ofSeconds(15))::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        rejected(storage(true, "venue-draft-media-private", "http://user:secret@internal.test:9000", Duration.ofSeconds(15))::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        rejected(storage(true, "venue-draft-media-private", "http://internal.test:9000/?redirect=x", Duration.ofSeconds(15))::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"../x.jpg", "drafts/0/08df8947-1544-475d-9d81-40984bbdf110.jpg", "drafts/42/UUID.jpg",
            "drafts/42/08df8947-1544-475d-9d81-40984bbdf110.jpg?acl=", "drafts/9223372036854775808/08df8947-1544-475d-9d81-40984bbdf110.jpg"})
    void rejectsNoncanonicalObjectKeysBeforeNetwork(String key) {
        rejected(() -> storage.put(key, JPEG, "image/jpeg"), HttpStatus.BAD_REQUEST);
        rejected(() -> storage.read(key, 4), HttpStatus.BAD_REQUEST);
        rejected(() -> storage.delete(key), HttpStatus.BAD_REQUEST); assertThat(requests).isEmpty();
    }

    @Test void rollbackDeletionDoesNotDependOnPrivacyVerification() {
        add(204, ""); storage.delete(KEY); assertThat(requests).hasSize(1); assertThat(requests.getFirst().method()).isEqualTo("DELETE");
    }

    @Test void recognizesExactSyntheticMinioAclWithOrWithoutServerHeaderAndAnonymousDenial() {
        minioBucket(403); storage.ensurePrivate();
        assertThat(requests).hasSize(3); HttpRequest probe = requests.get(2);
        assertThat(probe.method()).isEqualTo("GET"); assertThat(probe.uri().getRawQuery()).isEqualTo("list-type=2&max-keys=0");
        assertThat(probe.headers().firstValue("Authorization")).isEmpty();
        add(404, NO_POLICY); add(200, MINIO_ACL);
        add(403, ""); storage.ensurePrivate();
        add(404, NO_POLICY); responses.add(new Fixture(200, Map.of("Server", List.of("Unknown")), MINIO_ACL.getBytes(StandardCharsets.UTF_8)));
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ParameterizedTest @ValueSource(ints = {200,301,404,500})
    void syntheticMinioAclDoesNotEstablishPrivacyIfUnsignedAccessIsNotDenied(int status) {
        minioBucket(status); rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void minioReadAlsoProvesAnonymousObjectDenialBeforeReturningSignedBytes() {
        minioBucket(403); minioAcl(); add(403, "");
        responses.add(new Fixture(200, Map.of("Content-Type", List.of("image/jpeg")), JPEG));
        assertThat(storage.read(KEY, 4)).isEqualTo(JPEG);
        HttpRequest objectProbe = requests.get(4); assertThat(objectProbe.method()).isEqualTo("GET");
        assertThat(objectProbe.headers().firstValue("Authorization")).isEmpty();
        minioBucket(403); minioAcl(); add(200, "");
        rejected(() -> storage.read(KEY, 4), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void minioPutProvesPrivateObjectAndCompensatesIfAnonymousGetSucceeds() {
        minioBucket(403); add(200, ""); add(403, ""); storage.put(KEY, JPEG, "image/jpeg");
        assertThat(requests.get(4).method()).isEqualTo("GET"); assertThat(requests.get(4).headers().firstValue("Authorization")).isEmpty();
        minioBucket(403); add(200, ""); add(200, ""); add(204, "");
        rejected(() -> storage.put(KEY, JPEG, "image/jpeg"), HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(requests.getLast().method()).isEqualTo("DELETE");
    }

    @ParameterizedTest @ValueSource(strings = {"Group", "AllUsers", "unexpected"})
    void minioCompatibilityCannotAcceptAdditionalOrGroupAclData(String change) {
        String acl = change.equals("Group") ? MINIO_ACL.replace("xsi:type=\"CanonicalUser\"", "xsi:type=\"Group\"")
                : change.equals("AllUsers") ? MINIO_ACL.replace("<Type>CanonicalUser</Type>", "<Type>CanonicalUser</Type><URI>http://acs.amazonaws.com/groups/global/AllUsers</URI>")
                : MINIO_ACL.replace("</AccessControlList>", "<Grant><Permission>READ</Permission></Grant></AccessControlList>");
        add(404, NO_POLICY); responses.add(new Fixture(200, Map.of("Server", List.of("MinIO")), acl.getBytes(StandardCharsets.UTF_8)));
        rejected(storage::ensurePrivate, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void boundedSubscriberCancelsWithoutGrowingBeyondLimit() {
        var subscriber = new OvertureDraftMediaStorage.BoundedBodySubscriber(3, -1);
        Flow.Subscription subscription = mock(Flow.Subscription.class); subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[4])));
        assertThat(subscriber.getBody().toCompletableFuture().isCompletedExceptionally()).isTrue();
        org.mockito.Mockito.verify(subscription).cancel();
    }

    private OvertureDraftMediaStorage storage(boolean enabled, String bucket, String endpoint, Duration timeout) {
        UploadStorageProperties shared = new UploadStorageProperties("https://public.test", "us-east-1", "venue-media", "test-access", "test-secret", true, "https://public.test/venue-media", 10485760);
        return new OvertureDraftMediaStorage(new OvertureDraftMediaProperties(enabled, bucket), shared, endpoint, new ObjectMapper(), client, Clock.fixed(NOW, ZoneOffset.UTC), timeout);
    }
    private void privateBucket() { add(404, NO_POLICY); add(200, PRIVATE_ACL); }
    private void minioBucket(int unsignedStatus) { add(404, NO_POLICY); minioAcl(); add(unsignedStatus, ""); }
    private void minioAcl() { responses.add(new Fixture(200, Map.of("Server", List.of("MinIO")), MINIO_ACL.getBytes(StandardCharsets.UTF_8))); }
    private void add(int status, String body) { responses.add(new Fixture(status, Map.of(), body.getBytes(StandardCharsets.UTF_8))); }
    private record Fixture(int status, Map<String,List<String>> headers, byte[] body) { }
    private static String groupAcl(String group) {
        String uri = group.equals("LogDelivery") ? "http://acs.amazonaws.com/groups/s3/LogDelivery" : "http://acs.amazonaws.com/groups/global/" + group;
        return PRIVATE_ACL.replace("xsi:type=\"CanonicalUser\"><ID>owner</ID>", "xsi:type=\"Group\"><URI>" + uri + "</URI>");
    }
    private static void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, HttpStatus status) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
