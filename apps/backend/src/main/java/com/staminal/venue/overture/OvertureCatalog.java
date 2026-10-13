package com.staminal.venue.overture;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.overture.OvertureResponse.CatalogVenue;
import com.staminal.venue.overture.OvertureResponse.Source;

/** A bounded, validated snapshot loaded once at startup; web requests never fetch provider data. */
@Component
public class OvertureCatalog {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_RECORDS = 500;
    static final Set<String> CATEGORIES = Set.of("event_venue", "exhibition_and_trade_fair_venue");
    private static final Map<String, String> LICENSES = Map.of(
            "meta", "CDLA-Permissive-2.0", "microsoft", "CDLA-Permissive-2.0",
            "pinmeto", "CDLA-Permissive-2.0", "krick", "CDLA-Permissive-2.0",
            "renderseo", "CDLA-Permissive-2.0", "dac", "CDLA-Permissive-2.0",
            "brightquery", "CDLA-Permissive-2.0", "foursquare", "Apache-2.0",
            "alltheplaces", "CC0-1.0", "overture", "CDLA-Permissive-2.0");
    private final Snapshot snapshot;

    public OvertureCatalog(OvertureOnboardingProperties properties, ObjectMapper mapper) {
        Snapshot loaded = null;
        if (properties.isEnabled() && !properties.getCatalogPath().isBlank()) {
            try (InputStream input = Files.newInputStream(Path.of(properties.getCatalogPath()))) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                loaded = parse(bytes, mapper);
            } catch (Exception ignored) {
                // Fail closed. File paths and parser/provider payloads are never exposed through the API.
            }
        }
        this.snapshot = loaded;
    }

    public Snapshot snapshot() { return snapshot; }
    public record Snapshot(String version, String release, String city, Instant generatedAt,
            List<Double> bbox, List<CatalogVenue> venues, Map<String, CatalogVenue> byId) { }

    static Snapshot parse(byte[] bytes, ObjectMapper mapper) throws Exception {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
        ObjectMapper strict = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonNode root = strict.readTree(bytes);
        fields(root, Set.of("schemaVersion", "provider", "release", "generatedAt", "region", "venues"));
        if (!root.path("schemaVersion").isIntegralNumber() || !root.path("schemaVersion").canConvertToInt()
                || root.path("schemaVersion").intValue() != 1
                || !"OVERTURE_PLACES".equals(text(root, "provider", 30, true))) throw invalid();
        String release = text(root, "release", 32, true);
        if (!release.matches("\\d{4}-\\d{2}-\\d{2}\\.\\d{1,2}")) throw invalid();
        LocalDate.parse(release.substring(0, 10));
        Instant generatedAt = Instant.parse(text(root, "generatedAt", 40, true));
        JsonNode region = root.get("region");
        fields(region, Set.of("city", "bbox"));
        String city = text(region, "city", 120, true);
        JsonNode box = region.get("bbox");
        if (box == null || !box.isArray() || box.size() != 4) throw invalid();
        List<Double> bbox = new ArrayList<>();
        for (JsonNode value : box) {
            if (!value.isNumber() || !Double.isFinite(value.doubleValue())) throw invalid();
            bbox.add(value.doubleValue());
        }
        if (bbox.get(0) < -180 || bbox.get(2) > 180 || bbox.get(1) < -90 || bbox.get(3) > 90
                || bbox.get(0) >= bbox.get(2) || bbox.get(1) >= bbox.get(3)) throw invalid();
        JsonNode records = root.get("venues");
        if (records == null || !records.isArray() || records.size() > MAX_RECORDS) throw invalid();
        List<CatalogVenue> venues = new ArrayList<>();
        Map<String, CatalogVenue> byId = new LinkedHashMap<>();
        for (JsonNode record : records) {
            CatalogVenue venue = venue(record, bbox);
            if (byId.putIfAbsent(venue.id(), venue) != null) throw invalid();
            venues.add(venue);
        }
        venues.sort(java.util.Comparator.comparing(CatalogVenue::id));
        String version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new Snapshot(version, release, city, generatedAt, List.copyOf(bbox), List.copyOf(venues), Map.copyOf(byId));
    }

    private static CatalogVenue venue(JsonNode record, List<Double> bbox) {
        fields(record, Set.of("id", "name", "category", "address", "city", "area", "postcode", "latitude",
                "longitude", "phone", "website", "confidence", "operatingStatus", "sources"));
        String id = text(record, "id", 36, true);
        if (!validUuid(id)) throw invalid();
        String category = text(record, "category", 50, true);
        if (!CATEGORIES.contains(category)) throw invalid();
        double latitude = number(record, "latitude"), longitude = number(record, "longitude");
        if (latitude < bbox.get(1) || latitude > bbox.get(3) || longitude < bbox.get(0) || longitude > bbox.get(2)) throw invalid();
        Double confidence = null;
        JsonNode value = record.get("confidence");
        if (value != null && !value.isNull()) {
            confidence = number(record, "confidence");
            if (confidence < 0 || confidence > 1) throw invalid();
        }
        String status = text(record, "operatingStatus", 40, false);
        String website = text(record, "website", 2048, false);
        if (website != null && !safeWebsite(website)) throw invalid();
        JsonNode provenance = record.get("sources");
        if (provenance == null || !provenance.isArray() || provenance.isEmpty() || provenance.size() > 20) throw invalid();
        List<Source> sources = new ArrayList<>();
        Set<Source> distinct = new HashSet<>();
        for (JsonNode source : provenance) {
            fields(source, Set.of("dataset", "license", "recordId"));
            String dataset = text(source, "dataset", 80, true);
            String license = text(source, "license", 80, true);
            String recordId = text(source, "recordId", 512, false);
            if (!license.equals(LICENSES.get(dataset))) throw invalid();
            Source item = new Source(dataset, license, recordId);
            if (!distinct.add(item)) throw invalid();
            sources.add(item);
        }
        return new CatalogVenue(id, text(record, "name", 180, true), category,
                text(record, "address", 120, false), text(record, "city", 120, false), text(record, "area", 120, false),
                text(record, "postcode", 16, false), latitude, longitude, text(record, "phone", 20, false),
                website, confidence, status, List.copyOf(sources));
    }

    static boolean validUuid(String id) {
        if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) return false;
        return UUID.fromString(id).toString().equals(id);
    }

    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid();
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw invalid(); });
    }

    private static String text(JsonNode node, String field, int max, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) { if (required) throw invalid(); return null; }
        if (!value.isTextual()) throw invalid();
        String raw = value.textValue();
        if (raw.codePoints().anyMatch(c ->
                Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) throw invalid();
        String text = raw.strip();
        if (text.isEmpty() || text.length() > max) throw invalid();
        return text;
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) throw invalid();
        return value.doubleValue();
    }

    private static boolean safeWebsite(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid Overture catalog"); }
}
