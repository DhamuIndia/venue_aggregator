package com.staminal.venue.overture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

public final class OvertureDraftMediaRequest {
    private OvertureDraftMediaRequest() { }
    @JsonDeserialize(using = UploadDeserializer.class)
    public record Upload(long expectedVersion, String caption, SourceKind sourceKind, String sourceReference,
            RightsBasis rightsBasis, String licenseName, String permissionEvidence, boolean rightsConfirmed) { }
    @JsonDeserialize(using = ReviewDeserializer.class)
    public record Review(long expectedVersion, PhotoStatus status, String reason) { }
    @JsonDeserialize(using = ArrangementDeserializer.class)
    public record Arrangement(long expectedVersion, Long coverMediaId, List<Long> orderedMediaIds) { }
    @JsonDeserialize(using = ArchiveDeserializer.class)
    public record Archive(long expectedVersion, String reason) { }

    private abstract static class Strict<T> extends JsonDeserializer<T> {
        abstract T convert(JsonNode node);
        abstract Set<String> fields();
        @Override public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            try {
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                JsonNode root = parser.getCodec().readTree(parser);
                if (root == null || !root.isObject() || root.size() != fields().size()) invalid();
                root.fieldNames().forEachRemaining(key -> { if (!fields().contains(key)) invalid(); });
                T result = convert(root);
                if (parser.nextToken() != null) invalid();
                return result;
            } catch (IllegalArgumentException exception) { throw JsonMappingException.from(parser, "Invalid private photo request"); }
        }
    }
    public static final class UploadDeserializer extends Strict<Upload> {
        Set<String> fields() { return Set.of("expectedVersion", "caption", "sourceKind", "sourceReference", "rightsBasis", "licenseName", "permissionEvidence", "rightsConfirmed"); }
        Upload convert(JsonNode node) {
            JsonNode confirmed = node.get("rightsConfirmed");
            if (!confirmed.isBoolean() || !confirmed.booleanValue()) invalid();
            return new Upload(version(node), text(node.get("caption")), SourceKind.valueOf(required(node.get("sourceKind"))),
                    text(node.get("sourceReference")), RightsBasis.valueOf(required(node.get("rightsBasis"))),
                    text(node.get("licenseName")), required(node.get("permissionEvidence")), true);
        }
    }
    public static final class ReviewDeserializer extends Strict<Review> {
        Set<String> fields() { return Set.of("expectedVersion", "status", "reason"); }
        Review convert(JsonNode node) {
            PhotoStatus status = PhotoStatus.valueOf(required(node.get("status")));
            if (status != PhotoStatus.APPROVED && status != PhotoStatus.REJECTED) invalid();
            return new Review(version(node), status, required(node.get("reason")));
        }
    }
    public static final class ArrangementDeserializer extends Strict<Arrangement> {
        Set<String> fields() { return Set.of("expectedVersion", "coverMediaId", "orderedMediaIds"); }
        Arrangement convert(JsonNode node) {
            JsonNode cover = node.get("coverMediaId"), order = node.get("orderedMediaIds");
            if (!order.isArray() || order.size() > 20) invalid();
            List<Long> ids = new ArrayList<>(); Set<Long> unique = new HashSet<>();
            for (JsonNode item : order) { long id = positive(item, false); if (!unique.add(id)) invalid(); ids.add(id); }
            return new Arrangement(version(node), cover.isNull() ? null : positive(cover, false), List.copyOf(ids));
        }
    }
    public static final class ArchiveDeserializer extends Strict<Archive> {
        Set<String> fields() { return Set.of("expectedVersion", "reason"); }
        Archive convert(JsonNode node) { return new Archive(version(node), required(node.get("reason"))); }
    }
    private static long version(JsonNode node) { return positive(node.get("expectedVersion"), true); }
    private static long positive(JsonNode node, boolean zero) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < (zero ? 0 : 1)) invalid();
        return node.longValue();
    }
    private static String text(JsonNode node) {
        if (node == null) { invalid(); return null; }
        if (node.isNull()) return null;
        if (!node.isTextual()) invalid();
        return node.textValue();
    }
    private static String required(JsonNode node) { String value = text(node); if (value == null) invalid(); return value; }
    private static void invalid() { throw new IllegalArgumentException(); }
}
