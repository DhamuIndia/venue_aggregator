package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.discovery.places.VenuePlacesClient;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.media.UploadStorageProperties;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;
import com.staminal.venue.overture.OverturePhotoCreditResponse.LicenseCode;
import com.staminal.venue.overture.OverturePhotoCreditResponse.Status;

/** Synthetic upgrade/workflow checks: only explicitly disposable local database and private bucket. */
@EnabledIfEnvironmentVariable(named="OVERTURE_PHOTO_CREDIT_TEST_DB",
        matches="jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4d_test_[a-zA-Z0-9_]+")
@SpringBootTest(properties={
        "app.overture-onboarding.enabled=true", "app.overture-onboarding.catalog-path=",
        "app.overture-onboarding.media.enabled=true", "app.overture-onboarding.publication.enabled=true",
        "app.overture-onboarding.publication.licensed-photos-enabled=true",
        "app.storage.s3.endpoint=http://localhost:9000", "app.storage.s3.presign-endpoint=http://localhost:9000",
        "app.storage.s3.bucket=venue-media", "app.storage.s3.access-key=venue_minio",
        "app.storage.s3.secret-key=venue_minio_password", "app.storage.s3.region=us-east-1",
        "app.storage.s3.path-style-access=true", "app.features.venue-discovery-enabled=false",
        "app.venue-discovery.live-api-enabled=false", "app.notifications.whatsapp.sending-enabled=false",
        "app.notifications.whatsapp.webhook-enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OverturePhotoCreditWorkflowIntegrationTest {
    private static long adminId, adminRecordId, ownerId, ownerHallId, legacyHallId, byHallId, cc0HallId, unknownHallId;
    private static long byPhotoId, cc0PhotoId, unknownPhotoId, legacyPhotoId;
    private static byte[] legacyBytes;
    private static Map<String,Object> ownerBefore, legacyBefore, legacyPublicationBefore;
    private static String bySourceBefore, firstCreditSnapshot;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired OvertureDraftReviewService reviews;
    @Autowired OvertureDraftMediaService media;
    @Autowired OverturePhotoCreditService credits;
    @Autowired HallsService halls;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean OverturePublicationProperties publicationFlag;
    @MockitoBean VenuePlacesClient places;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url=System.getenv("OVERTURE_PHOTO_CREDIT_TEST_DB"), bucket=System.getenv("OVERTURE_PHOTO_CREDIT_TEST_BUCKET");
        if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4d_test_[a-zA-Z0-9_]+")
                || bucket==null || !bucket.matches("venuemart-phase4d-test-[a-z0-9-]+"))
            throw new IllegalStateException("Fresh local Slice 4D database/private bucket required");
        Flyway.configure().dataSource(url,"venue_app","venue_app_password").locations("classpath:db/migration").target("41").load().migrate();
        try(var c=DriverManager.getConnection(url,"venue_app","venue_app_password"); var s=c.createStatement()) {
            try(var rows=s.executeQuery("select count(*) from halls")) {rows.next(); if(rows.getLong(1)!=0) throw new IllegalStateException("Test DB must be fresh");}
            s.executeUpdate("insert into users(full_name,phone,email,password_hash) values "
                    +"('Credit admin','synthetic-4d-admin','admin4d@example.invalid','synthetic'),"
                    +"('Existing owner','synthetic-4d-owner','owner4d@example.invalid','synthetic')");
            s.executeUpdate("insert into roles(name) values('ADMIN'),('HALL_OWNER') on conflict(name) do nothing");
            s.executeUpdate("insert into user_roles(user_id,role_id) select u.id,r.id from users u,roles r where "
                    +"(u.email='admin4d@example.invalid' and r.name='ADMIN') or (u.email='owner4d@example.invalid' and r.name='HALL_OWNER')");
            s.executeUpdate("insert into admins(full_name,email,contact_number,password_hash) values ('Credit admin','admin4d@example.invalid','synthetic-4d-admin','synthetic')");
            try(var rows=s.executeQuery("select id from admins where email='admin4d@example.invalid'")) {rows.next(); adminRecordId=rows.getLong(1);}
            try(var rows=s.executeQuery("select id,email from users")) {while(rows.next()) {
                if(rows.getString(2).equals("admin4d@example.invalid")) adminId=rows.getLong(1); else ownerId=rows.getLong(1);
            }}
            try(var rows=s.executeQuery("insert into halls(owner_user_id,owner_name,name,city,area,status,latitude,longitude,cover_image_url) values ("
                    +ownerId+",'Existing owner','Existing owner hall','Chennai','Adyar','APPROVED',13.4,80.4,'https://example.invalid/owner.jpg') returning id")) {
                rows.next(); ownerHallId=rows.getLong(1);
            }
            s.executeUpdate("insert into hall_media(hall_id,media_type,url) values("+ownerHallId+",'IMAGE','https://example.invalid/owner-photo.jpg')");
            legacyHallId=draft(c,"Existing team-photo listing",13.3,80.3);
            byHallId=draft(c,"Synthetic BY venue",13.0,80.1);
            cc0HallId=draft(c,"Synthetic CC0 venue",13.01,80.11);
            unknownHallId=draft(c,"Synthetic unknown-license venue",13.02,80.12);
        }
        // Existing 4C data is committed before V46, including one already-live permitted team-photo venue.
        Flyway.configure().dataSource(url,"venue_app","venue_app_password").locations("classpath:db/migration").target("45").load().migrate();
        var legacyImage=new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB); var legacyOutput=new ByteArrayOutputStream();
        ImageIO.write(legacyImage,"jpeg",legacyOutput); legacyBytes=legacyOutput.toByteArray();
        String legacyHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(legacyBytes));
        String legacyKey="drafts/"+legacyHallId+"/00000000-0000-0000-0000-000000000001.jpg";
        var sharedStorage=new UploadStorageProperties("http://localhost:9000","us-east-1","venue-media","venue_minio","venue_minio_password",
                true,"http://localhost:9000/venue-media",10*1024*1024);
        var privateStorage=new OvertureDraftMediaStorage(new OvertureDraftMediaProperties(true,bucket),sharedStorage,"http://localhost:9000",new ObjectMapper());
        privateStorage.put(legacyKey,legacyBytes,"image/jpeg");
        try(var c=DriverManager.getConnection(url,"venue_app","venue_app_password"); var s=c.createStatement()) {
            c.setAutoCommit(false);
            s.executeUpdate("update venue_overture_draft_reviews set review_version=1,review_status='VERIFIED',effective_operating_status='open',"
                    +"verifications=(select jsonb_object_agg(field,jsonb_build_object('adminId',"+adminRecordId+",'adminName','Credit admin','verifiedAt','2026-10-06T00:00:00Z','evidence','Synthetic pre46 verification')) "
                    +"from unnest(array['name','address','city','area','phone','latitude','longitude','operatingStatus','capacity']) field) where hall_id="+legacyHallId);
            long photoId;
            try(var rows=s.executeQuery("insert into venue_overture_draft_photos(hall_id,storage_key,size_bytes,width,height,sha256,source_kind,rights_basis,permission_evidence,rights_confirmed,"
                    +"uploaded_by,uploader_name,status,sort_order,reviewed_by,reviewer_name,reviewed_at,review_reason) values ("
                    +legacyHallId+",'"+legacyKey+"',"+legacyBytes.length+",20,10,'"+legacyHash+"','TEAM_PHOTO','TEAM_OWNED','Synthetic old team-owned image',true,"
                    +adminRecordId+",'Credit admin','APPROVED',0,"+adminRecordId+",'Credit admin',now(),'Synthetic pre46 image review') returning id")) {rows.next(); photoId=rows.getLong(1);}
            legacyPhotoId=photoId;
            s.executeUpdate("update venue_overture_media_state set media_version=2,cover_media_id="+photoId+" where hall_id="+legacyHallId);
            s.executeUpdate("update venue_overture_publications set publication_version=1,publication_state='PUBLISHED',published_review_version=1,published_media_version=2,cover_media_id="
                    +photoId+",changed_by="+adminRecordId+",admin_name='Credit admin',changed_at=now(),reason='Synthetic pre46 publication' where hall_id="+legacyHallId);
            s.executeUpdate("insert into venue_overture_publication_photos(hall_id,photo_id,publication_version,sort_order) values("+legacyHallId+","+photoId+",1,0)");
            s.executeUpdate("update halls set status='APPROVED' where id="+legacyHallId);
            s.executeUpdate("insert into venue_overture_publication_history(hall_id,publication_version,publication_state,review_version,media_version,cover_media_id,photo_ids,changed_by,admin_name,reason) values ("
                    +legacyHallId+",1,'PUBLISHED',1,2,"+photoId+",'["+photoId+"]',"+adminRecordId+",'Credit admin','Synthetic pre46 publication')");
            c.commit();
            JdbcTemplate before=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"venue_app","venue_app_password"));
            ownerBefore=before.queryForMap("select * from halls where id=?",ownerHallId);
            legacyBefore=before.queryForMap("select * from halls where id=?",legacyHallId);
            legacyPublicationBefore=before.queryForMap("select * from venue_overture_publications where hall_id=?",legacyHallId);
            bySourceBefore=before.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?",String.class,byHallId);
        }
        registry.add("spring.datasource.url",()->url); registry.add("spring.datasource.username",()->"venue_app");
        registry.add("spring.datasource.password",()->"venue_app_password"); registry.add("app.overture-onboarding.media.bucket",()->bucket);
    }

    private static long draft(Connection c,String name,double latitude,double longitude) throws Exception {
        long id;
        try(var statement=c.prepareStatement("insert into halls(listing_origin,status,name,address_line,city,area,latitude,longitude,hall_type,contact_number,capacity_max) "
                +"values('APPLICATION','DRAFT',?,'12 Synthetic Street','Chennai','Synthetic Area',?,?,'event_venue','+919876543210',250) returning id")) {
            statement.setString(1,name); statement.setDouble(2,latitude); statement.setDouble(3,longitude);
            try(var rows=statement.executeQuery()) {rows.next(); id=rows.getLong(1);}
        }
        try(var statement=c.prepareStatement("insert into venue_overture_imports(source_id,hall_id,catalog_version,release,category,source_operating_status,sources,created_by_admin) "
                +"values(?::uuid,? ,?,'2026-09-23.1','event_venue','open','[{\"dataset\":\"microsoft\",\"license\":\"CDLA-Permissive-2.0\",\"recordId\":\"synthetic-4d\"}]',?)")) {
            statement.setString(1,UUID.randomUUID().toString()); statement.setLong(2,id); statement.setString(3,"0".repeat(64)); statement.setLong(4,adminRecordId);
            statement.executeUpdate();
        }
        return id;
    }
    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(""+adminId,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
    private String publication(long hall) {return "/v1/admin/overture-onboarding/publications/"+hall;}
    private String creditUrl(long hall,long photo) {return "/v1/admin/overture-onboarding/drafts/"+hall+"/media/"+photo+"/credits";}
    private JsonNode admin(MockHttpServletRequestBuilder request,int expectedStatus) throws Exception {
        byte[] bytes=mvc.perform(request.with(user(""+adminId).roles("ADMIN"))).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray();
        return bytes.length==0?mapper.nullNode():mapper.readTree(bytes);
    }
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value));
    }
    private long mediaVersion(long hall) {return jdbc.queryForObject("select media_version from venue_overture_media_state where hall_id=?",Long.class,hall);}
    private long creditVersion(long hall,long photo) {return jdbc.queryForObject("select coalesce(max(credit_version),0) from venue_overture_photo_credits where hall_id=? and photo_id=?",Long.class,hall,photo);}
    private Map<String,Object> saveBody(long hall,long photo,String code,String title) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("expectedVersion",mediaVersion(hall)); body.put("expectedCreditVersion",creditVersion(hall,photo));
        body.put("title",title); body.put("creator","Public synthetic photographer"); body.put("creatorUrl",null);
        body.put("sourceUrl","https://images.example.com/source/synthetic%20venue.jpg"); body.put("licenseCode",code);
        body.put("changesNotice","No earlier edits supplied\nCropping checked by the team."); body.put("requiredNotices","Synthetic public retained notice");
        return body;
    }
    private Map<String,Object> reviewBody(long hall,long photo,String state) {
        return Map.of("expectedVersion",mediaVersion(hall),"expectedCreditVersion",creditVersion(hall,photo),"status",state,
                "reason","PRIVATE_4D_CREDIT_REVIEW_REASON","rightsConfirmed",state.equals("APPROVED"),"attributionConfirmed",state.equals("APPROVED"));
    }
    private Map<String,Object> publishBody(JsonNode detail) {
        return Map.of("expectedPublicationVersion",detail.path("publicationVersion").asLong(),"expectedReviewVersion",detail.path("reviewVersion").asLong(),
                "expectedMediaVersion",detail.path("mediaVersion").asLong(),"reason","Synthetic licensed publication checked");
    }
    private long upload(long hall,String license) throws Exception {
        Detail value=reviews.detail(hall,auth());
        reviews.update(hall,new OvertureDraftReviewRequest.Update(value.reviewVersion(),value.facts(),OvertureDraftReviewPolicy.REQUIRED,
                ReviewStatus.VERIFIED,"PRIVATE_4D_FACTUAL_REVIEW_EVIDENCE",DuplicateDecision.NOT_REVIEWED,null,List.of()),auth());
        var image=new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB); var bytes=new ByteArrayOutputStream(); ImageIO.write(image,"png",bytes);
        var file=new MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray());
        Gallery gallery=media.upload(hall,file,new OvertureDraftMediaRequest.Upload(0,"Synthetic licensed image",SourceKind.LICENSED_IMAGE,
                "https://images.example.com/private-photo-source-token?private=true",RightsBasis.OPEN_LICENSE,license,"PRIVATE_4D_USAGE_EVIDENCE",true),auth());
        long photo=gallery.items().getFirst().id();
        gallery=media.review(hall,photo,new OvertureDraftMediaRequest.Review(gallery.mediaVersion(),PhotoStatus.APPROVED,"PRIVATE_4D_PHOTO_REVIEW_REASON"),auth());
        media.arrange(hall,new OvertureDraftMediaRequest.Arrangement(gallery.mediaVersion(),photo,List.of(photo)),auth());
        return photo;
    }
    private void photoStatus(long hall,long photo,long version,int expectedStatus) throws Exception {
        mvc.perform(get("/v1/halls/"+hall+"/application-photos/"+photo).param("publicationVersion",""+version)).andExpect(status().is(expectedStatus));
    }

    @Test @Order(0) void javaAndDatabaseUrlValidationAgreeOnCanonicalLinksAndStrictUnicodeDecoding() {
        for(String valid:List.of("https://commons.wikimedia.org/wiki/File:Example%20venue.jpg","https://commons.wikimedia.org/wiki/User:Example",
                "https://example.org/%E0%AE%A4.jpg","https://example.org/படம்.jpg","https://example.org/100%25.jpg",
                "https://EXAMPLE.org:443/a","https://example.org/Photo+exterior.jpg")) {
            assertEquals(valid,OverturePhotoCreditPolicy.publicUrl(valid,true));
            assertTrue(jdbc.queryForObject("select overture_safe_public_credit_url(?)",Boolean.class,valid),valid);
        }
        for(String invalid:List.of("http://example.org/a","HTTPS://example.org/a","https://user:pass@example.org/a","https://example.org/a?token=secret",
                "https://example.org/a#fragment","https://localhost/a","https://127.0.0.1/a","https://0x7f.0.0.1/a","https://example.local/a",
                "https://photos.google.com/a","https://lh3.googleusercontent.com/a","https://www.google.com/%6daps/a",
                "https://example.org/a%0Ab","https://example.org/a%250Ab","https://example.org/a%2525250Ab",
                "https://example.org/a%25literal%250Ab","https://example.org/a%80b","https://example.org/a%FFb","https://example.org/a%C0%AFb",
                "https://example.org/a%ED%A0%80b","https://example.org/a%5Cb","https://example.org/a%40b","https://example.org/a\u200Bb",
                "https://example.org/a%E2%80%8Bb","https://example.org/a%F3%A0%80%81b","https://example.org/a"+new String(Character.toChars(0xE0001))+"b",
                "https://example.org/a%25F3%25A0%2580%2581b","https://example.org/%"+"25".repeat(18)+"0A")) {
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->OverturePhotoCreditPolicy.publicUrl(invalid,true),invalid);
            assertFalse(jdbc.queryForObject("select overture_safe_public_credit_url(?)",Boolean.class,invalid),invalid);
        }
    }

    @Test @Order(1) void upgradePreservesOwnerAndExistingTeamPublicationThenUploadsRealPrivateLicensedPhotos() throws Exception {
        assertEquals(46,jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success",Integer.class));
        assertEquals(ownerBefore,jdbc.queryForMap("select * from halls where id=?",ownerHallId));
        assertEquals(legacyBefore,jdbc.queryForMap("select * from halls where id=?",legacyHallId));
        assertEquals(legacyPublicationBefore,jdbc.queryForMap("select * from venue_overture_publications where hall_id=?",legacyHallId));
        assertNull(halls.getPublicHall(""+ownerHallId).getApplicationPhotos());
        var legacy=halls.getPublicHall(""+legacyHallId); assertFalse(legacy.getApplicationPhotos().getFirst().requiresCredit());
        assertNull(legacy.getApplicationPhotos().getFirst().credit());
        mvc.perform(get("/v1/halls/"+legacyHallId+"/application-photos/"+legacyPhotoId).param("publicationVersion","1"))
                .andExpect(status().isOk()).andExpect(content().bytes(legacyBytes));
        assertEquals(0,jdbc.queryForObject("select count(*) from venue_overture_publication_photo_credits",Long.class));
        byPhotoId=upload(byHallId," Creative   Commons Attribution 4.0 International ");
        cc0PhotoId=upload(cc0HallId,"CC0-1.0"); unknownPhotoId=upload(unknownHallId,"Unsupported photo license");
        assertFalse(admin(get(publication(byHallId)),200).path("ready").asBoolean());
        photoStatus(byHallId,byPhotoId,1,404);
    }

    @Test @Order(2) void strictJsonUnsafeUrlsAndImmutableLicenseMismatchStayPrivate() throws Exception {
        Map<String,Object> valid=saveBody(byHallId,byPhotoId,"CC_BY_4_0","Public BY hall photo");
        for(String unsafe:List.of("http://images.example.com/a","https://user:pass@images.example.com/a","https://images.example.com/a?token=secret",
                "https://localhost/a","https://127.0.0.1/a","https://lh3.googleusercontent.com/a","https://www.google.com/%6daps/test",
                "https://images.example.com/%2525250A")) {
            Map<String,Object> body=new LinkedHashMap<>(valid); body.put("sourceUrl",unsafe); admin(json(put(creditUrl(byHallId,byPhotoId)),body),400);
        }
        String raw=mapper.writeValueAsString(valid);
        for(String invalid:List.of(raw.substring(0,raw.length()-1)+",\"changedBy\":7}",raw+" {}",
                raw.replace("\"expectedVersion\":3","\"expectedVersion\":\"3\""),raw.replace("\"expectedVersion\":3","\"expectedVersion\":3.5"),
                raw.substring(0,raw.length()-1)+",\"title\":\"Duplicate key\"}"))
            admin(put(creditUrl(byHallId,byPhotoId)).contentType(MediaType.APPLICATION_JSON).content(invalid),400);
        assertEquals(0,creditVersion(byHallId,byPhotoId)); assertEquals(3,mediaVersion(byHallId));
        admin(json(put(creditUrl(byHallId,byPhotoId)),saveBody(byHallId,byPhotoId,"CC0_1_0","Public mismatched-license image")),200);
        admin(json(post(creditUrl(byHallId,byPhotoId)+"/review"),reviewBody(byHallId,byPhotoId,"APPROVED")),409);
        assertEquals(1,creditVersion(byHallId,byPhotoId));
        admin(json(put(creditUrl(byHallId,byPhotoId)),saveBody(byHallId,byPhotoId,"CC_BY_4_0","Public BY hall photo")),200);
        admin(json(put(creditUrl(unknownHallId,unknownPhotoId)),saveBody(unknownHallId,unknownPhotoId,"CC_BY_4_0","Public unsupported image")),200);
        admin(json(post(creditUrl(unknownHallId,unknownPhotoId)+"/review"),reviewBody(unknownHallId,unknownPhotoId,"APPROVED")),409);
        assertFalse(admin(get(publication(unknownHallId)),200).path("ready").asBoolean());
    }

    @Test @Order(3) void creditCasAndAuditFailuresRollbackRevisionAndMediaVersionBeforeApproval() throws Exception {
        Map<String,Object> stale=saveBody(byHallId,byPhotoId,"CC_BY_4_0","Stale edit"); stale.put("expectedVersion",mediaVersion(byHallId)-1);
        admin(json(put(creditUrl(byHallId,byPhotoId)),stale),409);
        stale=saveBody(byHallId,byPhotoId,"CC_BY_4_0","Stale edit"); stale.put("expectedCreditVersion",1);
        admin(json(put(creditUrl(byHallId,byPhotoId)),stale),409);
        long beforeMedia=mediaVersion(byHallId), beforeCredit=creditVersion(byHallId,byPhotoId);
        jdbc.execute("create function slice4d_reject_audit() returns trigger language plpgsql as $$ begin if NEW.action in ('OVERTURE_PHOTO_CREDIT_SAVED','OVERTURE_PHOTO_CREDIT_REVIEWED') then raise exception 'Synthetic credit audit failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger slice4d_audit_failure before insert on audit_events for each row execute function slice4d_reject_audit()");
        try {
            assertThrows(RuntimeException.class,()->credits.save(byHallId,byPhotoId,mapper.convertValue(saveBody(byHallId,byPhotoId,"CC_BY_4_0","Rolled back edit"),OverturePhotoCreditRequest.Save.class),auth()));
            assertThrows(RuntimeException.class,()->credits.review(byHallId,byPhotoId,mapper.convertValue(reviewBody(byHallId,byPhotoId,"APPROVED"),OverturePhotoCreditRequest.Review.class),auth()));
        } finally {jdbc.execute("drop trigger slice4d_audit_failure on audit_events"); jdbc.execute("drop function slice4d_reject_audit()");}
        assertEquals(beforeMedia,mediaVersion(byHallId)); assertEquals(beforeCredit,creditVersion(byHallId,byPhotoId));
        admin(json(post(creditUrl(byHallId,byPhotoId)+"/review"),reviewBody(byHallId,byPhotoId,"APPROVED")),200);
        admin(json(put(creditUrl(cc0HallId,cc0PhotoId)),saveBody(cc0HallId,cc0PhotoId,"CC0_1_0","Public CC0 hall photo")),200);
        admin(json(post(creditUrl(cc0HallId,cc0PhotoId)+"/review"),reviewBody(cc0HallId,cc0PhotoId,"APPROVED")),200);
        assertTrue(admin(get(publication(byHallId)),200).path("ready").asBoolean());
        assertTrue(admin(get(publication(cc0HallId)),200).path("ready").asBoolean());
    }

    @Test @Order(4) void latestPendingSupersedesOlderApprovalAndCreditHistoryCannotBeChanged() throws Exception {
        assertThrows(DataAccessException.class,()->jdbc.update("update venue_overture_photo_credits set title='Forged title' where hall_id=?",byHallId));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from venue_overture_photo_credits where hall_id=?",byHallId));
        admin(json(put(creditUrl(byHallId,byPhotoId)),saveBody(byHallId,byPhotoId,"CC_BY_4_0","Public corrected BY image")),200);
        JsonNode detail=admin(get(publication(byHallId)),200); assertFalse(detail.path("ready").asBoolean());
        admin(json(post(publication(byHallId)+"/publish"),publishBody(detail)),409);
        assertFalse(jdbc.queryForObject("select overture_photo_credit_ready(?,?,?)",Boolean.class,byHallId,byPhotoId,3L));
        admin(json(post(creditUrl(byHallId,byPhotoId)+"/review"),reviewBody(byHallId,byPhotoId,"REJECTED")),200);
        assertFalse(admin(get(publication(byHallId)),200).path("ready").asBoolean());
        admin(json(put(creditUrl(byHallId,byPhotoId)),saveBody(byHallId,byPhotoId,"CC_BY_4_0","Public approved BY image")),200);
        admin(json(post(creditUrl(byHallId,byPhotoId)+"/review"),reviewBody(byHallId,byPhotoId,"APPROVED")),200);
        assertTrue(admin(get(publication(byHallId)),200).path("ready").asBoolean());
    }

    @Test @Order(5) void publishCreatesSafeImmutableSnapshotsAndSqlCannotForgeOrEditThem() throws Exception {
        // Reach the real snapshot INSERT trigger with a current publication shell, then roll it all back.
        JsonNode ready=admin(get(publication(byHallId)),200);
        var template=new TransactionTemplate(transactions);
        assertThrows(DataAccessException.class,()->template.executeWithoutResult(tx->{
            jdbc.update("update venue_overture_publications set publication_version=1,publication_state='PUBLISHED',published_review_version=?,published_media_version=?,cover_media_id=?,changed_by=?,admin_name='Credit admin',changed_at=now(),reason='Synthetic forged snapshot check' where hall_id=?",
                    ready.path("reviewVersion").asLong(),ready.path("mediaVersion").asLong(),byPhotoId,adminRecordId,byHallId);
            jdbc.update("insert into venue_overture_publication_photos(hall_id,photo_id,publication_version,sort_order) values(?,?,1,0)",byHallId,byPhotoId);
            jdbc.update("insert into venue_overture_publication_photo_credits(hall_id,publication_version,photo_id,credit_version,credit_snapshot) select ?,1,?,?,overture_public_photo_credit_snapshot(?,?,?)||'{\"permissionEvidence\":\"PRIVATE\"}'::jsonb",
                    byHallId,byPhotoId,creditVersion(byHallId,byPhotoId),byHallId,byPhotoId,creditVersion(byHallId,byPhotoId));
        }));
        assertEquals(0,admin(get(publication(byHallId)),200).path("publicationVersion").asLong());
        for(long hall:List.of(byHallId,cc0HallId)) {
            JsonNode live=admin(json(post(publication(hall)+"/publish"),publishBody(admin(get(publication(hall)),200))),200);
            assertEquals(1,live.path("publicationVersion").asLong());
            var response=halls.getPublicHall(""+hall); JsonNode publicJson=mapper.valueToTree(response);
            JsonNode photo=publicJson.path("applicationPhotos").get(0); assertTrue(photo.path("requiresCredit").asBoolean());
            JsonNode credit=photo.path("credit"); Set<String> keys=new java.util.HashSet<>(); credit.fieldNames().forEachRemaining(keys::add);
            assertEquals(Set.of("title","creator","creatorUrl","sourceUrl","licenseCode","licenseLabel","licenseUrl","changesNotice","processingNotice","requiredNotices"),keys);
            assertEquals(OverturePhotoCreditPolicy.PROCESSING_NOTICE,credit.path("processingNotice").asText());
            assertEquals(hall==byHallId?"CC_BY_4_0":"CC0_1_0",credit.path("licenseCode").asText());
            assertEquals(hall==byHallId?"https://creativecommons.org/licenses/by/4.0/":"https://creativecommons.org/publicdomain/zero/1.0/",credit.path("licenseUrl").asText());
            String text=publicJson.toString();
            for(String privateValue:List.of("PRIVATE_4D","private-photo-source-token","permissionEvidence","sourceReference","reviewReason","creditVersion","reviewedBy","Credit admin","storage_key"))
                assertFalse(text.contains(privateValue),"Private field leaked: "+privateValue);
            long photoId=hall==byHallId?byPhotoId:cc0PhotoId;
            mvc.perform(get("/v1/halls/"+hall+"/application-photos/"+photoId).param("publicationVersion","1"))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andExpect(header().string("Cache-Control","private, no-store"));
        }
        firstCreditSnapshot=jdbc.queryForObject("select credit_snapshot::text from venue_overture_publication_photo_credits where hall_id=? and publication_version=1",String.class,byHallId);
        assertThrows(DataAccessException.class,()->jdbc.update("update venue_overture_publication_photo_credits set credit_snapshot=credit_snapshot||'{\"sourceReference\":\"PRIVATE\"}' where hall_id=?",byHallId));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from venue_overture_publication_photo_credits where hall_id=?",byHallId));
        assertThrows(DataAccessException.class,()->jdbc.update("insert into venue_overture_photo_credits select hall_id,photo_id,credit_version+1,title,creator,creator_url,source_url,license_code,changes_notice,required_notices,'PENDING',changed_by,admin_name,now(),null,null,null,null,false,false from venue_overture_photo_credits where hall_id=? and credit_version=?",
                byHallId,creditVersion(byHallId,byPhotoId)));
        admin(json(put(creditUrl(byHallId,byPhotoId)),saveBody(byHallId,byPhotoId,"CC_BY_4_0","Live editing prohibited")),404);
    }

    @Test @Order(6) void licensedKillSwitchHidesReadsButAllowsWithdrawalAndDoesNotHideOtherListings() throws Exception {
        org.mockito.Mockito.doReturn(false).when(publicationFlag).isLicensedPhotosEnabled();
        try {
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->halls.getPublicHall(""+byHallId));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->halls.getPublicHall(""+cc0HallId));
            photoStatus(byHallId,byPhotoId,1,404); photoStatus(cc0HallId,cc0PhotoId,1,404);
            assertEquals(ownerBefore.get("name"),halls.getPublicHall(""+ownerHallId).getName());
            assertEquals(legacyBefore.get("name"),halls.getPublicHall(""+legacyHallId).getName());
            mvc.perform(get("/v1/halls/"+legacyHallId+"/application-photos/"+legacyPhotoId).param("publicationVersion","1"))
                    .andExpect(status().isOk()).andExpect(content().bytes(legacyBytes));
            admin(json(post(publication(byHallId)+"/unpublish"),Map.of("expectedPublicationVersion",1,"reason","Synthetic licensed kill switch withdrawal")),200);
        } finally {org.mockito.Mockito.reset(publicationFlag);}
        photoStatus(byHallId,byPhotoId,1,404); assertEquals("DRAFT",jdbc.queryForObject("select status from halls where id=?",String.class,byHallId));
        assertEquals(firstCreditSnapshot,jdbc.queryForObject("select credit_snapshot::text from venue_overture_publication_photo_credits where hall_id=? and publication_version=1",String.class,byHallId));
    }

    @Test @Order(7) void correctionAndRepublishingRetainOldSnapshotsAndNeverRestoreAnOldImageRevision() throws Exception {
        admin(json(put(creditUrl(byHallId,byPhotoId)),saveBody(byHallId,byPhotoId,"CC_BY_4_0","Public replacement credit title")),200);
        assertFalse(admin(get(publication(byHallId)),200).path("ready").asBoolean());
        admin(json(post(creditUrl(byHallId,byPhotoId)+"/review"),reviewBody(byHallId,byPhotoId,"APPROVED")),200);
        JsonNode live=admin(json(post(publication(byHallId)+"/publish"),publishBody(admin(get(publication(byHallId)),200))),200);
        assertEquals(3,live.path("publicationVersion").asLong()); photoStatus(byHallId,byPhotoId,1,404); photoStatus(byHallId,byPhotoId,3,200);
        assertEquals("Public replacement credit title",halls.getPublicHall(""+byHallId).getApplicationPhotos().getFirst().credit().title());
        assertEquals(2,jdbc.queryForObject("select count(*) from venue_overture_publication_photo_credits where hall_id=?",Long.class,byHallId));
        assertEquals(firstCreditSnapshot,jdbc.queryForObject("select credit_snapshot::text from venue_overture_publication_photo_credits where hall_id=? and publication_version=1",String.class,byHallId));
        assertEquals(bySourceBefore,jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?",String.class,byHallId));
        assertEquals(ownerBefore,jdbc.queryForMap("select * from halls where id=?",ownerHallId));
        assertEquals(legacyBefore,jdbc.queryForMap("select * from halls where id=?",legacyHallId));
        assertEquals(legacyPublicationBefore,jdbc.queryForMap("select * from venue_overture_publications where hall_id=?",legacyHallId));
        assertEquals(0,jdbc.queryForObject("select count(*) from bookings",Long.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from lead_notification_jobs",Long.class));
        assertTrue(jdbc.queryForObject("select count(*) from audit_events where action='OVERTURE_PHOTO_CREDIT_REVIEWED'",Long.class)>0);
    }
}
