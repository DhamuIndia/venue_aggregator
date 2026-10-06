package com.staminal.venue.overture;

import java.io.IOException;
import java.util.Set;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public final class OverturePublicationRequest {
    private OverturePublicationRequest() { }
    @JsonDeserialize(using = PublishDeserializer.class)
    public record Publish(long expectedPublicationVersion, long expectedReviewVersion, long expectedMediaVersion, String reason) { }
    @JsonDeserialize(using = UnpublishDeserializer.class)
    public record Unpublish(long expectedPublicationVersion, String reason) { }
    private abstract static class Strict<T> extends JsonDeserializer<T> {
        abstract Set<String> fields();
        abstract T convert(JsonNode node);
        @Override public T getNullValue(DeserializationContext context) throws JsonMappingException {
            throw JsonMappingException.from(context.getParser(), "Invalid venue publication request");
        }
        @Override public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            try {
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                JsonNode root = parser.getCodec().readTree(parser);
                if (root == null || !root.isObject() || root.size() != fields().size()) invalid();
                root.fieldNames().forEachRemaining(key -> { if (!fields().contains(key)) invalid(); });
                T result = convert(root);
                if (parser.nextToken() != null) invalid();
                return result;
            } catch (IllegalArgumentException exception) { throw JsonMappingException.from(parser, "Invalid venue publication request"); }
        }
    }
    public static final class PublishDeserializer extends Strict<Publish> {
        Set<String> fields() { return Set.of("expectedPublicationVersion", "expectedReviewVersion", "expectedMediaVersion", "reason"); }
        Publish convert(JsonNode node) { return new Publish(version(node, "expectedPublicationVersion"), version(node, "expectedReviewVersion"),
                version(node, "expectedMediaVersion"), reason(node)); }
    }
    public static final class UnpublishDeserializer extends Strict<Unpublish> {
        Set<String> fields() { return Set.of("expectedPublicationVersion", "reason"); }
        Unpublish convert(JsonNode node) { return new Unpublish(version(node, "expectedPublicationVersion"), reason(node)); }
    }
    private static long version(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) invalid();
        return value.longValue();
    }
    private static String reason(JsonNode node) {
        JsonNode value = node.get("reason");
        if (value == null || !value.isTextual()) invalid();
        return value.textValue();
    }
    private static void invalid() { throw new IllegalArgumentException(); }
}
