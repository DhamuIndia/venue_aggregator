package com.staminal.venue.overture;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.overture.OverturePhotoCreditResponse.LicenseCode;

/** VenueMart acceptance rules, not an automated legal determination of ownership or licensing. */
public final class OverturePhotoCreditPolicy {
    public static final String PROCESSING_NOTICE = "VenueMart normalized this image to JPEG and may have resized it.";
    private static final Set<String> BY_NAMES = Set.of("CC BY 4.0", "CC-BY-4.0", "CREATIVE COMMONS ATTRIBUTION 4.0 INTERNATIONAL");
    private static final Set<String> CC0_NAMES = Set.of("CC0 1.0", "CC0-1.0", "CC0 1.0 UNIVERSAL", "CREATIVE COMMONS ZERO 1.0 UNIVERSAL");
    private static final Set<String> LOCAL_SUFFIXES = Set.of("localhost", "local", "internal", "home", "lan", "corp", "invalid", "test", "example", "onion", "arpa");
    private static final Pattern PERCENT_RUN = Pattern.compile("(?:%[0-9a-fA-F]{2})+");
    private OverturePhotoCreditPolicy() { }

    public static OverturePhotoCreditRequest.Save validate(OverturePhotoCreditRequest.Save request) {
        if (request == null || request.expectedVersion() < 0 || request.expectedCreditVersion() < 0 || request.licenseCode() == null)
            throw bad("Invalid photo credit versions or license");
        return new OverturePhotoCreditRequest.Save(request.expectedVersion(), request.expectedCreditVersion(),
                text(request.title(), 200, true, false, "Photo title"), text(request.creator(), 300, true, false, "Creator"),
                publicUrl(request.creatorUrl(), false), publicUrl(request.sourceUrl(), true), request.licenseCode(),
                text(request.changesNotice(), 1000, true, true, "Changes notice"), text(request.requiredNotices(), 2000, false, true, "Required notices"));
    }
    public static String reason(String value) {
        String reason = text(value, 4000, true, true, "Credit review reason");
        if (reason.length() < 10) throw bad("Credit review reason must contain 10 to 4000 characters");
        return reason;
    }
    public static void requireLicenseMatch(String uploadedName, LicenseCode code) {
        String normalized = uploadedName == null ? "" : uploadedName.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (code == null || !(code == LicenseCode.CC_BY_4_0 ? BY_NAMES : CC0_NAMES).contains(normalized))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credit license must match supported immutable upload evidence; re-upload with correct license evidence if needed");
    }
    public static String licenseLabel(String code) {
        return switch (LicenseCode.valueOf(code)) { case CC_BY_4_0 -> "CC BY 4.0"; case CC0_1_0 -> "CC0 1.0"; };
    }
    public static String licenseUrl(String code) {
        return switch (LicenseCode.valueOf(code)) {
            case CC_BY_4_0 -> "https://creativecommons.org/licenses/by/4.0/";
            case CC0_1_0 -> "https://creativecommons.org/publicdomain/zero/1.0/";
        };
    }
    public static String publicUrl(String value, boolean required) {
        String text = text(value, 2048, required, false, "Public source or creator URL");
        if (text == null) return null;
        try {
            if (text.codePoints().anyMatch(character -> Character.isWhitespace(character) || Character.getType(character) == Character.FORMAT)
                    || text.indexOf('\\') >= 0 || text.indexOf('@') >= 0) throw new IllegalArgumentException();
            URI uri = URI.create(text);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || !host.contains(".") || host.length() > 253
                    || !host.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
                    || !host.matches(".*[a-z].*") || host.matches("(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+))+")
                    || LOCAL_SUFFIXES.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix)))
                throw new IllegalArgumentException();
            for (String label : host.split("\\.")) if (label.length() > 63) throw new IllegalArgumentException();
            String decoded = text;
            for (int pass = 0; pass < 16; pass++) {
                String next = decodePercentRuns(decoded);
                if (next.codePoints().anyMatch(character -> Character.isISOControl(character) || Character.getType(character) == Character.FORMAT)
                        || next.indexOf('\\') >= 0 || next.indexOf('@') >= 0) throw new IllegalArgumentException();
                if (next.equals(decoded)) break;
                decoded = next;
            }
            if (PERCENT_RUN.matcher(decoded).find()) throw new IllegalArgumentException();
            String lower = decoded.toLowerCase(Locale.ROOT);
            if (host.contains("googleusercontent") || host.contains("ggpht") || host.contains("gstatic")
                    || host.startsWith("photos.google.") || host.startsWith("maps.google.") || host.equals("maps.app.goo.gl")
                    || host.equals("goo.gl") || lower.matches("(?s).*https://(?:[^/]+\\.)?google\\.[^/]+/(?:maps|photos)(?:[/#?].*|$)"))
                throw new IllegalArgumentException();
            return text;
        } catch (IllegalArgumentException exception) {
            throw bad("Credit links must be safe public HTTPS source or creator URLs without credentials, query strings or fragments");
        }
    }
    private static String text(String value, int max, boolean required, boolean multiline, String label) {
        if (value == null || value.isBlank()) {
            if (required) throw bad(label + " is required");
            return null;
        }
        String normalized = (multiline ? value.replace("\r\n", "\n").replace('\r', '\n') : value).trim();
        if (normalized.length() > max || normalized.codePoints().anyMatch(character -> Character.getType(character) == Character.FORMAT
                || Character.isISOControl(character) && !(multiline && (character == '\n' || character == '\t'))))
            throw bad(label + " is too long or contains control characters");
        return normalized;
    }
    private static String decodePercentRuns(String value) {
        var matcher = PERCENT_RUN.matcher(value);
        StringBuilder decoded = new StringBuilder();
        while (matcher.find()) {
            String encoded = matcher.group();
            byte[] bytes = new byte[encoded.length() / 3];
            for (int index = 0; index < bytes.length; index++)
                bytes[index] = (byte) Integer.parseInt(encoded.substring(index * 3 + 1, index * 3 + 3), 16);
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException exception) { throw new IllegalArgumentException("Invalid URL encoding"); }
            matcher.appendReplacement(decoded, java.util.regex.Matcher.quoteReplacement(text));
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
