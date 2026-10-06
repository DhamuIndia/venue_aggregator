package com.staminal.venue.overture;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.HallListingOrigin;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.overture.OverturePublicationResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OverturePublicationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OvertureDraftReviewService reviews;
    private final OvertureDraftMediaStorage storage;
    private final VenueDiscoveryAccess access;
    private final AuditService audit;
    private final OvertureOnboardingProperties onboarding;
    private final OvertureDraftMediaProperties media;
    private final OverturePublicationProperties properties;

    public static boolean isApplication(Halls hall) { return hall != null && hall.getListingOrigin() == HallListingOrigin.APPLICATION; }

    @Transactional(readOnly = true)
    public boolean isPublic(Halls hall) {
        if (hall == null || hall.getStatus() != HallStatus.APPROVED) return false;
        if (!isApplication(hall)) return true;
        if (!enabled() || hall.getOwnerUserId() != null || hall.getOwnerName() != null) return false;
        return !publicRows(hall.getId(), null).isEmpty();
    }

    /** The caller's enquiry transaction keeps this lock until the request and routing snapshot commit. */
    @Transactional
    public long lockPublishedForEnquiry(long hallId) {
        lockHall(hallId);
        if (!enabled()) throw notFound();
        List<Published> rows = publicRows(hallId, null);
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst().version();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OvertureResponse.Page<Summary> list(int page, int size, Authentication authentication) {
        access.requireAdmin(authentication);
        int safePage=Math.max(0,page), safeSize=Math.max(1,Math.min(100,size));
        long total=jdbc.queryForObject("""
                select count(*) from halls h join venue_overture_imports i on i.hall_id=h.id
                join venue_overture_draft_reviews r on r.hall_id=h.id
                where h.listing_origin='APPLICATION' and h.owner_user_id is null and h.owner_name is null
                    and h.status in ('DRAFT','APPROVED')
                """, Long.class);
        List<Summary> content=jdbc.query("""
                select h.id,h.name,h.city,h.area,h.status,coalesce(p.publication_state,'UNPUBLISHED') publication_state,
                    coalesce(p.publication_version,0) publication_version,r.review_version,coalesce(m.media_version,0) media_version
                from halls h join venue_overture_imports i on i.hall_id=h.id join venue_overture_draft_reviews r on r.hall_id=h.id
                left join venue_overture_media_state m on m.hall_id=h.id left join venue_overture_publications p on p.hall_id=h.id
                where h.listing_origin='APPLICATION' and h.owner_user_id is null and h.owner_name is null
                    and h.status in ('DRAFT','APPROVED') order by i.imported_at desc,h.id desc limit ? offset ?
                """, (row,index)->summary(row),safeSize,(long)safePage*safeSize);
        return new OvertureResponse.Page<>(content,safePage,safeSize,total,(int)((total+safeSize-1)/safeSize));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail detail(long hallId, Authentication authentication) {
        access.requireAdmin(authentication);
        requireHall(hallId);
        return detail(hallId);
    }

    @Transactional
    public Detail publish(long hallId, OverturePublicationRequest.Publish request, Authentication authentication) {
        var actor=access.requireAdmin(authentication);
        if (!enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Application venue publication is disabled");
        if(request==null || request.expectedPublicationVersion()<0 || request.expectedReviewVersion()<0 || request.expectedMediaVersion()<0)
            throw bad("Invalid publication versions");
        String reason=OvertureDraftMediaService.evidence(request.reason(),"Publication reason");
        // Same first lock as factual review/import; hall then review/media serialize all draft changes.
        jdbc.queryForObject("select pg_advisory_xact_lock(?)",Object.class,OvertureOnboardingStore.IMPORT_LOCK);
        lockHall(hallId); lockWorkflow(hallId);
        Publication publication=publication(hallId,true);
        Detail before=detail(hallId);
        if(publication.version()!=request.expectedPublicationVersion() || before.reviewVersion()!=request.expectedReviewVersion()
                || before.mediaVersion()!=request.expectedMediaVersion()) throw conflict("This venue changed; reload before publishing");
        if(!before.ready()) throw conflict("Venue is not ready for publication: "+String.join("; ",before.blockers()));
        storage.ensurePrivate();
        // Verify every selected byte before exposing it. This is bounded to 20 canonical photos.
        for(long photoId:before.approvedPhotoIds()) verifyContent(storedPhoto(hallId,photoId));
        long version=publication.version()+1;
        Instant now=Instant.now();
        if(jdbc.update("""
                update venue_overture_publications set publication_version=?,publication_state='PUBLISHED',
                    published_review_version=?,published_media_version=?,cover_media_id=?,changed_by=?,admin_name=?,changed_at=?,reason=?
                where hall_id=? and publication_version=? and publication_state='UNPUBLISHED'
                """,version,before.reviewVersion(),before.mediaVersion(),before.coverMediaId(),actor.admin().getId(),actor.admin().getFullName(),
                java.sql.Timestamp.from(now),reason,hallId,publication.version())!=1) throw conflict("Publication has changed; reload the venue");
        jdbc.update("delete from venue_overture_publication_photos where hall_id=?",hallId);
        for(int index=0;index<before.approvedPhotoIds().size();index++) jdbc.update("""
                insert into venue_overture_publication_photos(hall_id,photo_id,publication_version,sort_order) values (?,?,?,?)
                """,hallId,before.approvedPhotoIds().get(index),version,index);
        jdbc.update("update halls set status='APPROVED',approved_by=?,approved_at=?,updated_at=? where id=? and status='DRAFT'",
                actor.admin().getId(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now),hallId);
        history(hallId,version,State.PUBLISHED,before,actor,now,reason);
        audit.record(new AuditCommand(actor.userId(),actor.role(),AuditAction.OVERTURE_VENUE_PUBLISHED,"HALL",String.valueOf(hallId),
                "Application venue published",Map.of("state","UNPUBLISHED"),Map.of("state","PUBLISHED","publicationVersion",version),
                Map.of("reviewVersion",before.reviewVersion(),"mediaVersion",before.mediaVersion(),"photoIds",before.approvedPhotoIds())));
        return detail(hallId);
    }

    @Transactional
    public Detail unpublish(long hallId, OverturePublicationRequest.Unpublish request, Authentication authentication) {
        var actor=access.requireAdmin(authentication);
        // Withdrawal remains available when the publication kill switch is off.
        if(request==null || request.expectedPublicationVersion()<0) throw bad("Invalid publication version");
        String reason=OvertureDraftMediaService.evidence(request.reason(),"Withdrawal reason");
        jdbc.queryForObject("select pg_advisory_xact_lock(?)",Object.class,OvertureOnboardingStore.IMPORT_LOCK);
        lockHall(hallId); lockWorkflow(hallId);
        Publication publication=publication(hallId,true);
        if(publication.version()!=request.expectedPublicationVersion()) throw conflict("Publication has changed; reload the venue");
        if(publication.state()!=State.PUBLISHED) throw conflict("This venue is already unpublished");
        Detail before=detail(hallId);
        long version=publication.version()+1; Instant now=Instant.now();
        if(jdbc.update("""
                update venue_overture_publications set publication_version=?,publication_state='UNPUBLISHED',
                    changed_by=?,admin_name=?,changed_at=?,reason=? where hall_id=? and publication_version=? and publication_state='PUBLISHED'
                """,version,actor.admin().getId(),actor.admin().getFullName(),java.sql.Timestamp.from(now),reason,hallId,publication.version())!=1)
            throw conflict("Publication has changed; reload the venue");
        jdbc.update("update halls set status='DRAFT',approved_by=null,approved_at=null,updated_at=? where id=?",
                java.sql.Timestamp.from(now),hallId);
        history(hallId,version,State.UNPUBLISHED,before,actor,now,reason);
        audit.record(new AuditCommand(actor.userId(),actor.role(),AuditAction.OVERTURE_VENUE_UNPUBLISHED,"HALL",String.valueOf(hallId),
                "Application venue withdrawn",Map.of("state","PUBLISHED"),Map.of("state","UNPUBLISHED","publicationVersion",version),null));
        return detail(hallId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PublicListing publicListing(long hallId) {
        if(!enabled()) throw notFound();
        List<Published> rows=publicRows(hallId,null);
        if(rows.isEmpty()) throw notFound();
        Published published=rows.getFirst();
        List<Long> ids=jdbc.query("""
                select photo_id from venue_overture_publication_photos where hall_id=? and publication_version=?
                order by (photo_id=? ) desc,sort_order,photo_id
                """,(row,index)->row.getLong(1),hallId,published.version(),published.cover());
        Set<String> verifiedFields=readJson(published.verifications(),new TypeReference<Map<String,OvertureDraftReviewResponse.Verification>>(){}).keySet();
        return new PublicListing(published.version(),ids.stream().map(id->photoUrl(hallId,id,published.version())).toList(),
                readJson(published.sources(),new TypeReference<List<OvertureResponse.Source>>(){}),published.release(),Set.copyOf(verifiedFields));
    }

    @Transactional
    public byte[] publicPhoto(long hallId,long photoId,long publicationVersion) {
        if(!enabled() || photoId<=0 || publicationVersion<=0) throw notFound();
        // Share lock prevents withdrawal while this request is reading bytes; the next request is denied.
        List<Long> locked=jdbc.query("select id from halls where id=? and listing_origin='APPLICATION' and status='APPROVED' for share",
                (row,index)->row.getLong(1),hallId);
        if(locked.isEmpty() || publicRows(hallId,publicationVersion).isEmpty()) throw notFound();
        List<Long> selected=jdbc.query("select photo_id from venue_overture_publication_photos where hall_id=? and photo_id=? and publication_version=?",
                (row,index)->row.getLong(1),hallId,photoId,publicationVersion);
        if(selected.isEmpty()) throw notFound();
        return verifyContent(storedPhoto(hallId,photoId));
    }

    private Detail detail(long hallId) {
        Publication publication=publication(hallId,false);
        var assessment=reviews.publicationAssessment(hallId);
        Media state=media(hallId);
        List<Long> approved=jdbc.query("select id from venue_overture_draft_photos where hall_id=? and status='APPROVED' order by sort_order,id limit 21",
                (row,index)->row.getLong(1),hallId);
        boolean needsPublicCredit=!jdbc.query("select source_kind from venue_overture_draft_photos where hall_id=? and status='APPROVED' and source_kind='LICENSED_IMAGE' limit 1",
                (row,index)->row.getString(1),hallId).isEmpty();
        List<String> blockers=blockers(assessment,state,approved,publication,needsPublicCredit);
        List<History> history=jdbc.query("""
                select * from venue_overture_publication_history where hall_id=? order by publication_version desc limit 100
                """,(row,index)->new History(row.getLong("publication_version"),State.valueOf(row.getString("publication_state")),
                    row.getLong("changed_by"),row.getString("admin_name"),row.getTimestamp("changed_at").toInstant(),row.getString("reason"),
                    row.getLong("review_version"),row.getLong("media_version")),hallId);
        return new Detail(hallId,assessment.facts().name(),assessment.facts().city(),assessment.facts().area(),assessment.status(),publication.state(),
                publication.version(),assessment.reviewVersion(),state.version(),blockers.isEmpty(),List.copyOf(blockers),state.cover(),List.copyOf(approved),
                assessment.sources(),assessment.release(),List.copyOf(history));
    }
    private List<String> blockers(OvertureDraftReviewResponse.Detail facts,Media state,List<Long> photos,Publication publication,boolean needsPublicCredit) {
        List<String> blockers=new ArrayList<>();
        if(!onboarding.isEnabled()) blockers.add("Overture onboarding is disabled");
        if(!media.isEnabled()) blockers.add("Private venue photos are disabled");
        if(!properties.isEnabled()) blockers.add("Application venue publication is disabled");
        if(publication.state()==State.PUBLISHED || !"DRAFT".equals(facts.status())) blockers.add("Unpublish this venue before editing or republishing");
        if(facts.reviewStatus()!=OvertureDraftReviewResponse.ReviewStatus.VERIFIED) blockers.add("Complete a current verified factual review and duplicate assessment");
        if(facts.duplicateAssessmentLimited()) blockers.add("Too many duplicate candidates to verify safely; withdrawal remains available");
        try { OvertureDraftReviewPolicy.requireVerified(facts.facts(),facts.verifications(),OvertureDraftReviewResponse.ReviewStatus.VERIFIED); }
        catch(ResponseStatusException exception) { blockers.add(exception.getReason()); }
        if(!facts.duplicates().isEmpty() && (facts.duplicateDecision()!=OvertureDraftReviewResponse.DuplicateDecision.DISTINCT
                || !facts.reviewedDuplicateHallIds().stream().sorted().toList().equals(facts.duplicates().stream().map(OvertureDraftReviewResponse.Duplicate::hallId).sorted().toList())))
            blockers.add("Resolve the complete current duplicate match set as distinct");
        if(photos.isEmpty()) blockers.add("Approve at least one permitted venue photo");
        if(photos.size()>20) blockers.add("No more than 20 approved photos may be published");
        if(needsPublicCredit) blockers.add("Licensed images need a public credit workflow; archive them before publishing this venue");
        if(state.cover()==null || !photos.contains(state.cover())) blockers.add("Select an approved cover photo");
        return blockers;
    }
    private void lockWorkflow(long hallId) {
        jdbc.query("select hall_id from venue_overture_draft_reviews where hall_id=? for update",(row,index)->row.getLong(1),hallId);
        List<Long> mediaRows=jdbc.query("select hall_id from venue_overture_media_state where hall_id=? for update",(row,index)->row.getLong(1),hallId);
        if(mediaRows.isEmpty()) jdbc.update("insert into venue_overture_media_state(hall_id) values (?)",hallId);
        jdbc.update("insert into venue_overture_publications(hall_id) values (?) on conflict do nothing",hallId);
    }
    private void lockHall(long hallId) {
        List<Long> rows=jdbc.query(HALL_SQL+" for update of h",(row,index)->row.getLong(1),hallId);
        if(rows.isEmpty()) throw notFound();
    }
    private void requireHall(long hallId) {
        if(hallId<=0 || jdbc.query(HALL_SQL,(row,index)->row.getLong(1),hallId).isEmpty()) throw notFound();
    }
    private static final String HALL_SQL="""
            select h.id from halls h join venue_overture_imports i on i.hall_id=h.id join venue_overture_draft_reviews r on r.hall_id=h.id
            where h.id=? and h.listing_origin='APPLICATION' and h.status in ('DRAFT','APPROVED')
                and h.owner_user_id is null and h.owner_name is null
            """;
    private Publication publication(long hallId,boolean lock) {
        List<Publication> rows=jdbc.query("select publication_version,publication_state from venue_overture_publications where hall_id=?"+(lock?" for update":""),
                (row,index)->new Publication(row.getLong("publication_version"),State.valueOf(row.getString("publication_state"))),hallId);
        return rows.isEmpty()?new Publication(0,State.UNPUBLISHED):rows.getFirst();
    }
    private Media media(long hallId) {
        List<Media> rows=jdbc.query("select media_version,cover_media_id from venue_overture_media_state where hall_id=?",
                (row,index)->new Media(row.getLong("media_version"),row.getObject("cover_media_id",Long.class)),hallId);
        return rows.isEmpty()?new Media(0,null):rows.getFirst();
    }
    private List<Published> publicRows(long hallId,Long version) {
        return jdbc.query("""
                select p.publication_version,p.cover_media_id,i.sources::text,i.release,r.verifications::text
                from venue_overture_publications p join halls h on h.id=p.hall_id join venue_overture_imports i on i.hall_id=h.id
                join venue_overture_draft_reviews r on r.hall_id=h.id join venue_overture_media_state m on m.hall_id=h.id
                where h.id=? and h.listing_origin='APPLICATION' and h.owner_user_id is null and h.owner_name is null
                    and h.status='APPROVED' and p.publication_state='PUBLISHED' and p.published_review_version=r.review_version
                    and r.review_status='VERIFIED' and r.effective_operating_status='open' and p.published_media_version=m.media_version
                    and p.cover_media_id=m.cover_media_id and (?::bigint is null or p.publication_version=?)
                    and exists(select 1 from venue_overture_publication_history ph where ph.hall_id=h.id and ph.publication_version=p.publication_version
                        and ph.publication_state='PUBLISHED' and ph.review_version=r.review_version and ph.media_version=m.media_version and ph.cover_media_id=p.cover_media_id)
                    and exists(select 1 from venue_overture_publication_photos pp join venue_overture_draft_photos dp on dp.hall_id=pp.hall_id and dp.id=pp.photo_id
                        where pp.hall_id=h.id and pp.publication_version=p.publication_version and pp.photo_id=p.cover_media_id and dp.status='APPROVED')
                    and not exists(select 1 from venue_overture_publication_photos pp join venue_overture_draft_photos dp on dp.hall_id=pp.hall_id and dp.id=pp.photo_id
                        where pp.hall_id=h.id and pp.publication_version=p.publication_version and (dp.status<>'APPROVED' or dp.source_kind='LICENSED_IMAGE'))
                    and (select count(*) from venue_overture_publication_photos pp where pp.hall_id=h.id and pp.publication_version=p.publication_version)
                        =(select count(*) from venue_overture_draft_photos dp where dp.hall_id=h.id and dp.status='APPROVED')
                    and not exists(select 1 from venue_overture_publication_photos pp join venue_overture_draft_photos dp on dp.hall_id=pp.hall_id and dp.id=pp.photo_id
                        where pp.hall_id=h.id and pp.publication_version=p.publication_version and pp.sort_order<>(select count(*) from venue_overture_draft_photos previous
                            where previous.hall_id=h.id and previous.status='APPROVED' and (previous.sort_order,previous.id)<(dp.sort_order,dp.id)))
                """,(row,index)->new Published(row.getLong("publication_version"),row.getLong("cover_media_id"),row.getString("sources"),row.getString("release"),row.getString("verifications")),hallId,version,version);
    }
    private Stored storedPhoto(long hallId,long photoId) {
        List<Stored> photos=jdbc.query("select storage_key,size_bytes,sha256 from venue_overture_draft_photos where hall_id=? and id=? and status='APPROVED'",
                (row,index)->new Stored(row.getString("storage_key"),row.getInt("size_bytes"),row.getString("sha256")),hallId,photoId);
        if(photos.isEmpty()) throw notFound();
        return photos.getFirst();
    }
    private byte[] verifyContent(Stored stored) {
        if(stored.size()<1 || stored.size()>OvertureDraftImageProcessor.MAX_OUTPUT_BYTES) throw unavailable();
        byte[] bytes=storage.read(stored.key(),stored.size());
        try {
            if(bytes==null || bytes.length!=stored.size() || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(stored.sha256())) throw unavailable();
        } catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
        return bytes;
    }
    private void history(long hallId,long version,State state,Detail before,VenueDiscoveryAccess.Actor actor,Instant at,String reason) {
        jdbc.update("""
                insert into venue_overture_publication_history(hall_id,publication_version,publication_state,review_version,media_version,
                    cover_media_id,photo_ids,changed_by,admin_name,changed_at,reason) values (?,?,?,?,?,?,?::jsonb,?,?,?,?)
                """,hallId,version,state.name(),before.reviewVersion(),before.mediaVersion(),before.coverMediaId(),json(before.approvedPhotoIds()),
                actor.admin().getId(),actor.admin().getFullName(),java.sql.Timestamp.from(at),reason);
    }
    private static Summary summary(ResultSet row) throws SQLException { return new Summary(row.getLong("id"),row.getString("name"),row.getString("city"),
            row.getString("area"),row.getString("status"),State.valueOf(row.getString("publication_state")),row.getLong("publication_version"),row.getLong("review_version"),row.getLong("media_version")); }
    private boolean enabled() { return onboarding.isEnabled() && media.isEnabled() && properties.isEnabled(); }
    private static String photoUrl(long hall,long photo,long version) { return "/api/v1/halls/"+hall+"/application-photos/"+photo+"?publicationVersion="+version; }
    private String json(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Publication snapshot is invalid");} }
    private <T>T readJson(String value,TypeReference<T> type) {try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Stored publication metadata is invalid");} }
    private record Publication(long version,State state) { }
    private record Media(long version,Long cover) { }
    private record Published(long version,long cover,String sources,String release,String verifications) { }
    private record Stored(String key,int size,String sha256) { }
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Application venue not found");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Published photo is unavailable");}
}
