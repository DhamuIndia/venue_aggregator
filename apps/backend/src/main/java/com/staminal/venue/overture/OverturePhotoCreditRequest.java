package com.staminal.venue.overture;

import java.io.IOException;
import java.util.Set;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.staminal.venue.overture.OverturePhotoCreditResponse.LicenseCode;
import com.staminal.venue.overture.OverturePhotoCreditResponse.Status;

public final class OverturePhotoCreditRequest {
    private OverturePhotoCreditRequest() { }
    @JsonDeserialize(using = SaveDeserializer.class)
    public record Save(long expectedVersion, long expectedCreditVersion, String title, String creator,
            String creatorUrl, String sourceUrl, LicenseCode licenseCode, String changesNotice, String requiredNotices) { }
    @JsonDeserialize(using = ReviewDeserializer.class)
    public record Review(long expectedVersion, long expectedCreditVersion, Status status, String reason,
            boolean rightsConfirmed, boolean attributionConfirmed) { }

    private abstract static class Strict<T> extends JsonDeserializer<T> {
        abstract Set<String> fields();
        abstract T convert(JsonNode node);
        @Override public T getNullValue(DeserializationContext context) throws JsonMappingException {
            throw JsonMappingException.from(context.getParser(), "Photo credit request must be an object");
        }
        @Override public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            try {
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                JsonNode node = parser.getCodec().readTree(parser);
                if (node == null || !node.isObject() || node.size() != fields().size()) invalid();
                node.fieldNames().forEachRemaining(field -> { if (!fields().contains(field)) invalid(); });
                T result = convert(node);
                if (parser.nextToken() != null) invalid();
                return result;
            } catch (IllegalArgumentException exception) { throw JsonMappingException.from(parser, "Invalid photo credit request"); }
        }
    }
    public static final class SaveDeserializer extends Strict<Save> {
        Set<String> fields() { return Set.of("expectedVersion", "expectedCreditVersion", "title", "creator", "creatorUrl", "sourceUrl", "licenseCode", "changesNotice", "requiredNotices"); }
        Save convert(JsonNode node) { return new Save(version(node, "expectedVersion"), version(node, "expectedCreditVersion"),
                text(node, "title", true), text(node, "creator", true), text(node, "creatorUrl", false), text(node, "sourceUrl", true),
                LicenseCode.valueOf(text(node, "licenseCode", true)), text(node, "changesNotice", true), text(node, "requiredNotices", false)); }
    }
    public static final class ReviewDeserializer extends Strict<Review> {
        Set<String> fields() { return Set.of("expectedVersion", "expectedCreditVersion", "status", "reason", "rightsConfirmed", "attributionConfirmed"); }
        Review convert(JsonNode node) {
            Status status = Status.valueOf(text(node, "status", true));
            if (status != Status.APPROVED && status != Status.REJECTED) invalid();
            boolean rights = bool(node, "rightsConfirmed"), attribution = bool(node, "attributionConfirmed");
            if (status == Status.APPROVED && (!rights || !attribution)) invalid();
            return new Review(version(node, "expectedVersion"), version(node, "expectedCreditVersion"), status,
                    text(node, "reason", true), rights, attribution);
        }
    }
    private static long version(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) invalid();
        return value.longValue();
    }
    private static String text(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || required && !value.isTextual() || !required && !value.isNull() && !value.isTextual()) invalid();
        return value.isNull() ? null : value.textValue();
    }
    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field); if (value == null || !value.isBoolean()) invalid(); return value.booleanValue();
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid photo credit request"); }
}
