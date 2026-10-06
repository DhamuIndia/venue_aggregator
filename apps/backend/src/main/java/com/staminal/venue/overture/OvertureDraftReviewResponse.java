package com.staminal.venue.overture;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.staminal.venue.halls.Entity.Halls;

public final class OvertureDraftReviewResponse {
    private OvertureDraftReviewResponse() { }
    public enum ReviewStatus { UNREVIEWED, IN_REVIEW, VERIFIED, DUPLICATE }
    public enum DuplicateDecision { NOT_REVIEWED, DISTINCT, CONFIRMED_DUPLICATE }
    public record Amenities(Boolean ac, Boolean carParking, Boolean bikeParking, Boolean dining,
            Boolean generator, Boolean lift, Boolean bridalRoom, Boolean cateringKitchen) { }
    public record Facts(String name, String address, String city, String area, String postcode,
            Double latitude, Double longitude, String phone, String website, String operatingStatus,
            Integer capacity, String description, Amenities amenities) {
        public static Facts from(Halls hall, String website, String operatingStatus) {
            return new Facts(hall.getName(), hall.getAddressLine(), hall.getCity(), hall.getArea(), hall.getPincode(),
                    hall.getLatitude(), hall.getLongitude(), hall.getContactNumber(), website,
                    operatingStatus == null ? "unknown" : operatingStatus, hall.getCapacityMax(), hall.getDescription(),
                    new Amenities(hall.getAcAvailable(), hall.getCarParking(), hall.getBikeParking(), hall.getDiningAvailable(),
                            hall.getGeneratorAvailable(), hall.getLiftAvailable(), hall.getBridalRoomAvailable(), hall.getCateringKitchenAvailable()));
        }
        public Map<String, Object> fields() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("name", name); fields.put("address", address); fields.put("city", city); fields.put("area", area);
            fields.put("postcode", postcode); fields.put("latitude", latitude); fields.put("longitude", longitude);
            fields.put("phone", phone); fields.put("website", website); fields.put("operatingStatus", operatingStatus);
            fields.put("capacity", capacity); fields.put("description", description);
            fields.put("amenities.ac", amenities.ac()); fields.put("amenities.carParking", amenities.carParking());
            fields.put("amenities.bikeParking", amenities.bikeParking()); fields.put("amenities.dining", amenities.dining());
            fields.put("amenities.generator", amenities.generator()); fields.put("amenities.lift", amenities.lift());
            fields.put("amenities.bridalRoom", amenities.bridalRoom()); fields.put("amenities.cateringKitchen", amenities.cateringKitchen());
            return fields;
        }
    }
    public record Verification(long adminId, String adminName, Instant verifiedAt, String evidence) { }
    public record Reviewer(long adminId, String adminName) { }
    public record Duplicate(long hallId, String name, String city, String area, String status,
            String listingOrigin, Double distanceMeters) { }
    public record Detail(long hallId, String sourceId, String status, long reviewVersion, ReviewStatus reviewStatus,
            Facts facts, Facts sourceFacts, List<OvertureResponse.Source> sources, String release, Instant importedAt,
            Map<String, String> fieldOrigins, Map<String, Verification> verifications, List<String> missingFields,
            List<String> unverifiedFields, List<Duplicate> duplicates, DuplicateDecision duplicateDecision,
            String duplicateNotes, List<Long> reviewedDuplicateHallIds, String reviewNotes, Reviewer lastReviewedBy,
            Instant lastReviewedAt, boolean duplicateAssessmentLimited) {
        public Detail(long hallId, String sourceId, String status, long reviewVersion, ReviewStatus reviewStatus,
                Facts facts, Facts sourceFacts, List<OvertureResponse.Source> sources, String release, Instant importedAt,
                Map<String, String> fieldOrigins, Map<String, Verification> verifications, List<String> missingFields,
                List<String> unverifiedFields, List<Duplicate> duplicates, DuplicateDecision duplicateDecision,
                String duplicateNotes, List<Long> reviewedDuplicateHallIds, String reviewNotes, Reviewer lastReviewedBy,
                Instant lastReviewedAt) {
            this(hallId,sourceId,status,reviewVersion,reviewStatus,facts,sourceFacts,sources,release,importedAt,fieldOrigins,
                    verifications,missingFields,unverifiedFields,duplicates,duplicateDecision,duplicateNotes,
                    reviewedDuplicateHallIds,reviewNotes,lastReviewedBy,lastReviewedAt,false);
        }
    }
}
