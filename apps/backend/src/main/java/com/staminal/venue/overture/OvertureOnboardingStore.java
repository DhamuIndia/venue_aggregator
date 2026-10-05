package com.staminal.venue.overture;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.HallListingOrigin;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.overture.OvertureCatalog.Snapshot;
import com.staminal.venue.overture.OvertureResponse.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OvertureOnboardingStore {
    // One transaction-scoped lock for imports, independent of any provider/source ID.
    static final long IMPORT_LOCK = 723803070411L;
    static final double MIN_CONFIDENCE = 0.8;
    private final JdbcTemplate jdbc;
    private final HallRepository halls;
    private final ObjectMapper mapper;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<PreviewItem> preview(List<CatalogVenue> venues) {
        List<Halls> existing = halls.findAll();
        return venues.stream().map(venue -> assess(venue, existing)).toList();
    }

    @Transactional
    public ImportResult importDrafts(Snapshot snapshot, List<CatalogVenue> venues, Actor actor) {
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, IMPORT_LOCK);
        List<Halls> existing = new ArrayList<>(halls.findAll());
        List<ImportItem> items = new ArrayList<>();
        int created = 0;
        for (CatalogVenue venue : venues) {
            PreviewItem assessed = assess(venue, existing);
            if (assessed.outcome() != Outcome.READY) {
                items.add(new ImportItem(venue.id(), venue.name(), assessed.outcome(), assessed.hallId(), assessed.issues()));
                continue;
            }
            Halls hall = draft(venue);
            hall = halls.saveAndFlush(hall);
            jdbc.update("""
                    insert into venue_overture_imports
                    (source_id, hall_id, catalog_version, release, category, source_website, source_operating_status, sources, created_by_admin)
                    values (?::uuid, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    """, venue.id(), hall.getId(), snapshot.version(), snapshot.release(), venue.category(),
                    venue.website(), venue.operatingStatus(), serialize(venue.sources()), actor.admin().getId());
            audit.record(new AuditCommand(actor.userId(), actor.role(), AuditAction.OVERTURE_VENUE_DRAFT_CREATED,
                    "HALL", String.valueOf(hall.getId()), "Application venue draft created", null,
                    Map.of("status", "DRAFT", "listingOrigin", "APPLICATION"),
                    Map.of("sourceId", venue.id(), "catalogVersion", snapshot.version(), "release", snapshot.release())));
            existing.add(hall);
            items.add(new ImportItem(venue.id(), venue.name(), Outcome.CREATED, hall.getId(), assessed.issues()));
            created++;
        }
        return new ImportResult(created, items.size() - created, List.copyOf(items));
    }

    @Transactional(readOnly = true)
    public Page<Draft> drafts(int page, int size) {
        Long total = jdbc.queryForObject("""
                select count(*) from venue_overture_imports i join halls h on h.id=i.hall_id
                where h.listing_origin='APPLICATION' and h.status='DRAFT'
                """, Long.class);
        List<Draft> content = jdbc.query("""
                select h.id, i.source_id::text, h.name, h.city, h.area, h.address_line, i.category, i.release,
                       i.imported_at, i.sources::text, h.contact_number, i.source_website,
                       h.cover_image_url, h.capacity_max, h.full_day_amount, i.source_operating_status
                from venue_overture_imports i join halls h on h.id=i.hall_id
                where h.listing_origin='APPLICATION' and h.status='DRAFT'
                order by i.imported_at desc, i.id desc limit ? offset ?
                """, (row, index) -> new Draft(row.getLong("id"), row.getString("source_id"), row.getString("name"),
                row.getString("city"), row.getString("area"), row.getString("address_line"), row.getString("category"),
                row.getString("release"), row.getTimestamp("imported_at").toInstant(), sources(row.getString("sources")),
                missingFields(row.getString("source_operating_status"), row.getString("city"), row.getString("area"), row.getString("address_line"),
                        row.getString("contact_number"), row.getString("source_website"), row.getString("cover_image_url"),
                        row.getObject("capacity_max"), row.getObject("full_day_amount")), "DRAFT"), size, (long) page * size);
        long count = total == null ? 0 : total;
        return new Page<>(content, page, size, count, (int) ((count + size - 1) / size));
    }

    private PreviewItem assess(CatalogVenue venue, List<Halls> existing) {
        List<Long> imported = jdbc.query("select hall_id from venue_overture_imports where source_id=?::uuid",
                (row, index) -> row.getLong(1), venue.id());
        if (!imported.isEmpty()) return new PreviewItem(venue, Outcome.ALREADY_IMPORTED, imported.getFirst(), List.of(), List.of());
        List<String> issues = new ArrayList<>();
        if ("temporarily_closed".equals(venue.operatingStatus()) || "permanently_closed".equals(venue.operatingStatus())) {
            issues.add("The source marks this venue as closed");
        } else if (venue.operatingStatus() != null && !"open".equals(venue.operatingStatus())
                && !"unknown".equals(venue.operatingStatus())) {
            issues.add("The source operating status is not recognized");
        }
        if (venue.confidence() == null || venue.confidence() < MIN_CONFIDENCE) issues.add("Source confidence must be at least 0.8");
        if (!issues.isEmpty()) return new PreviewItem(venue, Outcome.INCOMPLETE, null, List.of(), List.copyOf(issues));
        List<Long> duplicates = existing.stream().filter(hall -> possibleDuplicate(venue, hall)).map(Halls::getId).sorted().toList();
        if (!duplicates.isEmpty()) return new PreviewItem(venue, Outcome.POSSIBLE_DUPLICATE, null, duplicates,
                List.of("An existing venue may match; review it before creating another listing"));
        List<String> missing = missingFields(venue.operatingStatus(), venue.city(), venue.area(), venue.address(), venue.phone(), venue.website(), null, null, null);
        return new PreviewItem(venue, Outcome.READY, null, List.of(),
                missing.isEmpty() ? List.of() : List.of("Missing fields: " + String.join(", ", missing)));
    }

    static Halls draft(CatalogVenue venue) {
        Halls hall = new Halls();
        hall.setListingOrigin(HallListingOrigin.APPLICATION);
        hall.setStatus(HallStatus.DRAFT);
        hall.setName(venue.name());
        hall.setAddressLine(venue.address());
        hall.setCity(venue.city());
        hall.setArea(venue.area());
        hall.setPincode(venue.postcode());
        hall.setLatitude(venue.latitude());
        hall.setLongitude(venue.longitude());
        hall.setContactNumber(venue.phone());
        hall.setHallType(venue.category());
        LocalDateTime now = LocalDateTime.now();
        hall.setCreatedAt(now);
        hall.setUpdatedAt(now);
        return hall;
    }

    static boolean possibleDuplicate(CatalogVenue venue, Halls hall) {
        boolean sameNameCity = fold(venue.name()).equals(fold(hall.getName()))
                && venue.city() != null && hall.getCity() != null && fold(venue.city()).equals(fold(hall.getCity()));
        boolean nearby = hall.getLatitude() != null && hall.getLongitude() != null
                && Double.isFinite(hall.getLatitude()) && Double.isFinite(hall.getLongitude())
                && distanceMeters(venue.latitude(), venue.longitude(), hall.getLatitude(), hall.getLongitude()) <= 75;
        return sameNameCity || nearby;
    }

    static String fold(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).strip()
                .replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }

    static List<String> missingFields(String operatingStatus, String city, String area, String address, String phone, String website,
            String photos, Object capacity, Object pricing) {
        List<String> missing = new ArrayList<>();
        if (!"open".equals(operatingStatus)) missing.add("operatingStatus");
        if (city == null || city.isBlank()) missing.add("city");
        if (area == null || area.isBlank()) missing.add("area");
        if (address == null || address.isBlank()) missing.add("address");
        if (phone == null || phone.isBlank()) missing.add("phone");
        if (website == null || website.isBlank()) missing.add("website");
        if (photos == null || photos.isBlank()) missing.add("photos");
        if (capacity == null) missing.add("capacity");
        if (pricing == null) missing.add("pricing");
        return List.copyOf(missing);
    }

    private String serialize(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Could not save source provenance"); }
    }
    private List<Source> sources(String value) {
        try { return List.copyOf(mapper.readValue(value, new TypeReference<List<Source>>() { })); }
        catch (Exception e) { throw new IllegalStateException("Stored source provenance is invalid"); }
    }
}
