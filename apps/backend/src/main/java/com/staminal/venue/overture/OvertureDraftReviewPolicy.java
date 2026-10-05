package com.staminal.venue.overture;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;

final class OvertureDraftReviewPolicy {
    static final List<String> REQUIRED = List.of("name", "address", "city", "area", "phone", "latitude", "longitude", "operatingStatus", "capacity");
    private static final Set<String> OPERATING_STATUSES = Set.of("unknown", "open", "temporarily_closed", "permanently_closed");
    private OvertureDraftReviewPolicy() { }

    static Facts normalize(Facts input) {
        if (input == null || input.amenities() == null) throw bad("Complete venue facts are required");
        String name = text(input.name(), 180, false);
        if (name == null) throw bad("Venue name is required");
        String operatingStatus = text(input.operatingStatus(), 40, false);
        if (operatingStatus == null) operatingStatus = "unknown";
        if (!OPERATING_STATUSES.contains(operatingStatus)) throw bad("Invalid operating status");
        if ((input.latitude() == null) != (input.longitude() == null)) throw bad("Latitude and longitude must be provided together");
        if (input.latitude() != null && (!Double.isFinite(input.latitude()) || input.latitude() < -90 || input.latitude() > 90
                || !Double.isFinite(input.longitude()) || input.longitude() < -180 || input.longitude() > 180)) throw bad("Invalid location coordinates");
        if (input.capacity() != null && (input.capacity() < 1 || input.capacity() > 100000)) throw bad("Capacity must be between 1 and 100000");
        String phone = text(input.phone(), 20, false);
        if (phone != null && (!phone.matches("\\+?[0-9() -]+") || phone.replaceAll("[^0-9]", "").length() < 6))
            throw bad("Phone must contain a valid business number with at least six digits");
        String website = text(input.website(), 2048, false);
        if (website != null) {
            try {
                URI uri = URI.create(website);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null
                        || uri.getPort() < -1 || uri.getPort() > 65535) throw new IllegalArgumentException();
                // Encoded control characters and credentials are disallowed as well as literal forms.
                String decoded = java.net.URLDecoder.decode(website, java.nio.charset.StandardCharsets.UTF_8);
                if (hasControls(decoded, false)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) { throw bad("Website must be a valid HTTP or HTTPS URL without credentials"); }
        }
        return new Facts(name, text(input.address(), 120, false), text(input.city(), 120, false), text(input.area(), 120, false),
                text(input.postcode(), 16, false), input.latitude(), input.longitude(), phone, website,
                operatingStatus, input.capacity(), text(input.description(), 4000, true), input.amenities());
    }

    static String text(String input, int limit, boolean multiline) {
        if (input == null) return null;
        if (input.length() > limit || hasControls(input, multiline)) throw bad("A text field contains invalid characters or exceeds its limit");
        String value = input.strip();
        return value.isBlank() ? null : value;
    }
    private static boolean hasControls(String value, boolean multiline) {
        return value.codePoints().anyMatch(c -> (Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\r' || c == '\t')))
                || Character.getType(c) == Character.FORMAT);
    }

    static boolean missing(String key, Object value) {
        return value == null || value instanceof String text && (text.isBlank() || "operatingStatus".equals(key) && "unknown".equals(text));
    }
    static boolean identityChanged(Facts before, Facts after) {
        return !Objects.equals(OvertureOnboardingStore.fold(before.name()), OvertureOnboardingStore.fold(after.name()))
                || !Objects.equals(OvertureOnboardingStore.fold(before.city()), OvertureOnboardingStore.fold(after.city()))
                || !Objects.equals(before.latitude(), after.latitude()) || !Objects.equals(before.longitude(), after.longitude());
    }
    static Map<String, Verification> verifications(Facts before, Facts after, Map<String, Verification> existing,
            List<String> selected, String evidence, long adminId, String adminName, Instant now) {
        if (selected == null || selected.size() > after.fields().size() || selected.stream().distinct().count() != selected.size()) throw bad("Invalid verified fields");
        Map<String, Object> values = after.fields(), old = before.fields();
        Map<String, Verification> result = new LinkedHashMap<>();
        for (String key : selected) {
            if (!values.containsKey(key) || missing(key, values.get(key))) throw bad("Only supplied facts may be verified");
            Verification retained = existing.get(key);
            if (retained != null && Objects.equals(old.get(key), values.get(key))) result.put(key, retained);
            else {
                if (evidence == null) throw bad("Review notes describing the evidence are required for new or changed verifications");
                result.put(key, new Verification(adminId, adminName, now, evidence));
            }
        }
        return Map.copyOf(result);
    }

    static void requireDecision(ReviewStatus status, DuplicateDecision decision, String notes, List<Long> acknowledged,
            List<Duplicate> duplicates, boolean changedIdentity) {
        if (status == null || decision == null || acknowledged == null || acknowledged.size() > 100
                || acknowledged.stream().anyMatch(id -> id == null || id <= 0)
                || acknowledged.stream().distinct().count() != acknowledged.size()) throw bad("Invalid review decision");
        List<Long> current = duplicates.stream().map(Duplicate::hallId).sorted().toList();
        if (changedIdentity && !duplicates.isEmpty() && status == ReviewStatus.VERIFIED)
            throw bad("Save changed identity as in review, then assess the new duplicate matches");
        if (decision != DuplicateDecision.NOT_REVIEWED) {
            if (notes == null) throw bad("A duplicate decision requires notes");
            if (!acknowledged.stream().sorted().toList().equals(current))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate matches have changed; reload the latest draft before saving");
        } else if (!acknowledged.isEmpty()) throw bad("An unresolved assessment cannot acknowledge duplicate matches");
        if (decision == DuplicateDecision.CONFIRMED_DUPLICATE && (duplicates.isEmpty() || status != ReviewStatus.DUPLICATE))
            throw bad("A confirmed duplicate must have a current match and duplicate review status");
        if (status == ReviewStatus.DUPLICATE && decision != DuplicateDecision.CONFIRMED_DUPLICATE)
            throw bad("Duplicate review status requires a confirmed duplicate assessment");
        if (status == ReviewStatus.VERIFIED && !duplicates.isEmpty() && decision != DuplicateDecision.DISTINCT)
            throw bad("Resolve possible duplicate matches before completing verification");
    }
    static void requireVerified(Facts facts, Map<String, Verification> verifications, ReviewStatus status) {
        if (status != ReviewStatus.VERIFIED) return;
        List<String> unmet = new ArrayList<>();
        Map<String, Object> fields = facts.fields();
        for (String required : REQUIRED) if (missing(required, fields.get(required)) || !verifications.containsKey(required)) unmet.add(required);
        if (!"open".equals(facts.operatingStatus())) throw bad("Only a venue verified as open can complete factual review");
        if (!unmet.isEmpty()) throw bad("Required facts must be supplied and verified: " + String.join(", ", unmet));
    }
    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
