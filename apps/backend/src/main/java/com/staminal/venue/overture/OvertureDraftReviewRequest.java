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
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;

public final class OvertureDraftReviewRequest {
    private OvertureDraftReviewRequest() { }
    @JsonDeserialize(using = UpdateDeserializer.class)
    public record Update(long expectedVersion, Facts facts, List<String> verifiedFields, ReviewStatus reviewStatus,
            String reviewNotes, DuplicateDecision duplicateDecision, String duplicateNotes, List<Long> reviewedDuplicateHallIds) { }

    /** Strict field and scalar types for this authority-sensitive, full-replacement endpoint only. */
    public static final class UpdateDeserializer extends JsonDeserializer<Update> {
        @Override public Update deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            try {
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                JsonNode root = parser.getCodec().readTree(parser);
                keys(root, Set.of("expectedVersion", "facts", "verifiedFields", "reviewStatus", "reviewNotes", "duplicateDecision", "duplicateNotes", "reviewedDuplicateHallIds"));
                JsonNode f = root.get("facts"), a = f == null ? null : f.get("amenities");
                keys(f, Set.of("name", "address", "city", "area", "postcode", "latitude", "longitude", "phone", "website", "operatingStatus", "capacity", "description", "amenities"));
                keys(a, Set.of("ac", "carParking", "bikeParking", "dining", "generator", "lift", "bridalRoom", "cateringKitchen"));
                Facts facts = new Facts(string(f.get("name")), string(f.get("address")), string(f.get("city")),
                        string(f.get("area")), string(f.get("postcode")), number(f.get("latitude")), number(f.get("longitude")),
                        string(f.get("phone")), string(f.get("website")), string(f.get("operatingStatus")), integer(f.get("capacity")),
                        string(f.get("description")), new Amenities(bool(a.get("ac")), bool(a.get("carParking")), bool(a.get("bikeParking")),
                        bool(a.get("dining")), bool(a.get("generator")), bool(a.get("lift")), bool(a.get("bridalRoom")), bool(a.get("cateringKitchen"))));
                long version = positiveLong(root.get("expectedVersion"), true);
                List<String> verified = strings(root.get("verifiedFields"), 20);
                List<Long> duplicateIds = ids(root.get("reviewedDuplicateHallIds"));
                // This DTO is a complete HTTP body, not a nested patch: a second JSON value is ambiguous.
                if (parser.nextToken() != null) invalid();
                return new Update(version, facts, verified, ReviewStatus.valueOf(requiredString(root.get("reviewStatus"))),
                        string(root.get("reviewNotes")), DuplicateDecision.valueOf(requiredString(root.get("duplicateDecision"))),
                        string(root.get("duplicateNotes")), duplicateIds);
            } catch (IllegalArgumentException exception) {
                throw JsonMappingException.from(parser, "Invalid draft review request");
            }
        }
        private static void keys(JsonNode node, Set<String> expected) {
            if (node == null || !node.isObject() || node.size() != expected.size()) invalid();
            node.fieldNames().forEachRemaining(key -> { if (!expected.contains(key)) invalid(); });
        }
        private static void invalid() { throw new IllegalArgumentException(); }
        private static String string(JsonNode node) {
            if (node == null) { invalid(); return null; }
            if (node.isNull()) return null;
            if (!node.isTextual()) invalid();
            return node.textValue();
        }
        private static String requiredString(JsonNode node) { String value = string(node); if (value == null) invalid(); return value; }
        private static Double number(JsonNode node) {
            if (node == null) { invalid(); return null; }
            if (node.isNull()) return null;
            if (!node.isNumber() || !Double.isFinite(node.doubleValue())) invalid();
            return node.doubleValue();
        }
        private static Integer integer(JsonNode node) {
            if (node == null) { invalid(); return null; }
            if (node.isNull()) return null;
            if (!node.isIntegralNumber() || !node.canConvertToInt()) invalid();
            return node.intValue();
        }
        private static Boolean bool(JsonNode node) {
            if (node == null) { invalid(); return null; }
            if (node.isNull()) return null;
            if (!node.isBoolean()) invalid();
            return node.booleanValue();
        }
        private static long positiveLong(JsonNode node, boolean zero) {
            if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < (zero ? 0 : 1)) invalid();
            return node.longValue();
        }
        private static List<String> strings(JsonNode node, int max) {
            if (node == null || !node.isArray() || node.size() > max) invalid();
            List<String> result = new ArrayList<>(); Set<String> unique = new HashSet<>();
            for (JsonNode item : node) { String value = requiredString(item); if (!unique.add(value)) invalid(); result.add(value); }
            return List.copyOf(result);
        }
        private static List<Long> ids(JsonNode node) {
            if (node == null || !node.isArray() || node.size() > 100) invalid();
            List<Long> result = new ArrayList<>(); Set<Long> unique = new HashSet<>();
            for (JsonNode item : node) { long value = positiveLong(item, false); if (!unique.add(value)) invalid(); result.add(value); }
            return result.stream().sorted().toList();
        }
    }
}
