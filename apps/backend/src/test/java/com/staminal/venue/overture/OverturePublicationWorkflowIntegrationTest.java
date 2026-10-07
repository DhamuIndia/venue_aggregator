package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.discovery.places.VenuePlacesClient;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

/** Full synthetic workflow on explicitly disposable local PostgreSQL and private MinIO. */
@EnabledIfEnvironmentVariable(named="OVERTURE_PUBLICATION_TEST_DB",
        matches="jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4c_test_[a-zA-Z0-9_]+")
@SpringBootTest(properties={
        "app.overture-onboarding.enabled=true", "app.overture-onboarding.catalog-path=",
        "app.overture-onboarding.media.enabled=true", "app.overture-onboarding.publication.enabled=true",
        "app.storage.s3.endpoint=http://localhost:9000", "app.storage.s3.presign-endpoint=http://localhost:9000",
        "app.storage.s3.bucket=venue-media", "app.storage.s3.access-key=venue_minio",
        "app.storage.s3.secret-key=venue_minio_password", "app.storage.s3.region=us-east-1",
        "app.storage.s3.path-style-access=true", "app.features.venue-discovery-enabled=false",
        "app.venue-discovery.live-api-enabled=false", "app.notifications.whatsapp.sending-enabled=false",
        "app.notifications.whatsapp.webhook-enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OverturePublicationWorkflowIntegrationTest {
    private static long adminId, ownerId, customerId, hallId, ownerHallId, approvedId, pendingId;
    private static Map<String,Object> ownerBefore;
    private static String sourceBefore, enquiryId;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired OvertureDraftReviewService reviews;
    @Autowired OvertureDraftMediaService media;
    @Autowired HallsService halls;
    @Autowired OverturePublicationService publications;
    @MockitoSpyBean OverturePublicationProperties publicationFlag;
    @MockitoBean VenuePlacesClient places;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url=System.getenv("OVERTURE_PUBLICATION_TEST_DB"), bucket=System.getenv("OVERTURE_PUBLICATION_TEST_BUCKET");
        if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4c_test_[a-zA-Z0-9_]+")
                || bucket==null || !bucket.matches("venuemart-phase4c-test-[a-z0-9-]+"))
            throw new IllegalStateException("Fresh local Slice 4C database/private bucket required");
        Flyway.configure().dataSource(url,"venue_app","venue_app_password").locations("classpath:db/migration").target("41").load().migrate();
        try(var c=DriverManager.getConnection(url,"venue_app","venue_app_password"); var s=c.createStatement()) {
            try(var rows=s.executeQuery("select count(*) from halls")) {rows.next(); if(rows.getLong(1)!=0) throw new IllegalStateException("Test DB must be fresh");}
            s.executeUpdate("insert into users(full_name,phone,email,password_hash) values "
                    +"('Publication admin','synthetic-4c-admin','admin4c@example.invalid','synthetic'),"
                    +"('Existing owner','synthetic-4c-owner','owner4c@example.invalid','synthetic'),"
                    +"('Synthetic customer','synthetic-4c-customer','customer4c@example.invalid','synthetic')");
            s.executeUpdate("insert into roles(name) values('ADMIN'),('CUSTOMER'),('HALL_OWNER') on conflict(name) do nothing");
            s.executeUpdate("insert into user_roles(user_id,role_id) select u.id,r.id from users u,roles r where "
                    +"(u.email='admin4c@example.invalid' and r.name='ADMIN') or "
                    +"(u.email='customer4c@example.invalid' and r.name='CUSTOMER') or "
                    +"(u.email='owner4c@example.invalid' and r.name='HALL_OWNER')");
            s.executeUpdate("insert into admins(full_name,email,contact_number,password_hash) values "
                    +"('Publication admin','admin4c@example.invalid','synthetic-4c-admin','synthetic')");
            try(var rows=s.executeQuery("select id,email from users")) {while(rows.next()) switch(rows.getString(2)) {
                case "admin4c@example.invalid" -> adminId=rows.getLong(1);
                case "owner4c@example.invalid" -> ownerId=rows.getLong(1);
                case "customer4c@example.invalid" -> customerId=rows.getLong(1);
            }}
            try(var rows=s.executeQuery("insert into halls(owner_user_id,owner_name,name,city,area,status,latitude,longitude,cover_image_url) values ("
                    +ownerId+",'Existing owner','Existing hall','Chennai','Adyar','APPROVED',13.2,80.3,'https://example.invalid/owner.jpg') returning id")) {
                rows.next(); ownerHallId=rows.getLong(1);
            }
            s.executeUpdate("insert into hall_media(hall_id,media_type,url) values("+ownerHallId+",'IMAGE','https://example.invalid/owner-photo.jpg')");
            try(var rows=s.executeQuery("insert into halls(listing_origin,status,name,address_line,city,area,latitude,longitude,hall_type) values "
                    +"('APPLICATION','DRAFT','Synthetic publication venue','12 Test Street','Chennai','Test Area',13.0,80.1,'event_venue') returning id")) {
                rows.next(); hallId=rows.getLong(1);
            }
            s.executeUpdate("insert into venue_overture_imports(source_id,hall_id,catalog_version,release,category,source_operating_status,sources,created_by_admin) select "
                    +"'7408f6e5-1a56-4d09-8557-86134b75c689',"+hallId+",'"+"0".repeat(64)+"','2026-09-23.1','event_venue','unknown',"
                    +"'[{\"dataset\":\"microsoft\",\"license\":\"CDLA-Permissive-2.0\",\"recordId\":\"synthetic-4c\"}]'::jsonb,id from admins where email='admin4c@example.invalid'");
        }
        registry.add("spring.datasource.url",()->url); registry.add("spring.datasource.username",()->"venue_app");
        registry.add("spring.datasource.password",()->"venue_app_password"); registry.add("app.overture-onboarding.media.bucket",()->bucket);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(""+adminId,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
    private String publication() {return "/v1/admin/overture-onboarding/publications/"+hallId;}
    private JsonNode admin(MockHttpServletRequestBuilder request,int status) throws Exception {
        return result(request.with(user(""+adminId).roles("ADMIN")),status);
    }
    private JsonNode result(MockHttpServletRequestBuilder request,int status) throws Exception {
        byte[] content=mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsByteArray();
        return content.length==0 ? mapper.nullNode() : mapper.readTree(content);
    }
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value));
    }
    private Map<String,Object> publishBody(JsonNode detail) {
        return Map.of("expectedPublicationVersion",detail.path("publicationVersion").asLong(),
                "expectedReviewVersion",detail.path("reviewVersion").asLong(),"expectedMediaVersion",detail.path("mediaVersion").asLong(),
                "reason","Synthetic publication checks completed");
    }
    private long count(String table) {return jdbc.queryForObject("select count(*) from "+table,Long.class);}

    @Test @Order(1) void upgradeKeepsOwnerAndDraftPrivateUntilReadinessComplete() throws Exception {
        assertEquals(46,jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success",Integer.class));
        ownerBefore=jdbc.queryForMap("select * from halls where id=?",ownerHallId);
        sourceBefore=jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?",String.class,hallId);
        JsonNode initial=admin(get(publication()),200);
        assertFalse(initial.path("ready").asBoolean()); assertEquals(0,initial.path("publicationVersion").asLong());
        assertThrows(ResponseStatusException.class,()->halls.getPublicHall(""+hallId));
        admin(json(post(publication()+"/publish"),publishBody(initial)),409);
        Detail current=reviews.detail(hallId,auth()); Facts f=current.facts();
        Facts verified=new Facts(f.name(),f.address(),f.city(),f.area(),f.postcode(),f.latitude(),f.longitude(),
                "+919876543210",null,"open",250,"Facts checked for a synthetic test",f.amenities());
        reviews.update(hallId,new OvertureDraftReviewRequest.Update(current.reviewVersion(),verified,
                OvertureDraftReviewPolicy.REQUIRED,ReviewStatus.VERIFIED,"Verified synthetic venue records",
                DuplicateDecision.NOT_REVIEWED,null,List.of()),auth());
        BufferedImage image=new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB); var output=new ByteArrayOutputStream(); ImageIO.write(image,"png",output);
        var file=new MockMultipartFile("file","synthetic.png","image/png",output.toByteArray());
        Gallery g=media.upload(hallId,file,OvertureDraftMediaFixtures.upload(),auth()); approvedId=g.items().getFirst().id();
        g=media.review(hallId,approvedId,new OvertureDraftMediaRequest.Review(g.mediaVersion(),PhotoStatus.APPROVED,"Rights and image checked in synthetic test"),auth());
        g=media.arrange(hallId,new OvertureDraftMediaRequest.Arrangement(g.mediaVersion(),approvedId,List.of(approvedId)),auth());
        var pending=new OvertureDraftMediaRequest.Upload(g.mediaVersion(),"Pending photo",SourceKind.TEAM_PHOTO,null,RightsBasis.TEAM_OWNED,null,"Synthetic team image usage evidence",true);
        g=media.upload(hallId,file,pending,auth()); pendingId=g.items().stream().filter(p->p.status()==PhotoStatus.PENDING).findFirst().orElseThrow().id();
        assertTrue(admin(get(publication()),200).path("ready").asBoolean());
    }

    @Test @Order(2) void freshDuplicateVersionAndAuditChecksFailClosed() throws Exception {
        JsonNode ready=admin(get(publication()),200);
        jdbc.update("update halls set latitude=13.0,longitude=80.1 where id=?",ownerHallId);
        try {assertFalse(admin(get(publication()),200).path("ready").asBoolean()); admin(json(post(publication()+"/publish"),publishBody(ready)),409);}
        finally {jdbc.update("update halls set latitude=13.2,longitude=80.3 where id=?",ownerHallId);}
        Map<String,Object> stale=new java.util.HashMap<>(publishBody(ready)); stale.put("expectedMediaVersion",0);
        admin(json(post(publication()+"/publish"),stale),409);
        jdbc.execute("create function slice4c_reject_audit() returns trigger language plpgsql as $$ begin if NEW.action='OVERTURE_VENUE_PUBLISHED' then raise exception 'Synthetic audit failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger slice4c_audit_failure before insert on audit_events for each row execute function slice4c_reject_audit()");
        try {assertThrows(RuntimeException.class,()->publications.publish(hallId,
                mapper.convertValue(publishBody(ready),OverturePublicationRequest.Publish.class),auth()));}
        finally {jdbc.execute("drop trigger slice4c_audit_failure on audit_events"); jdbc.execute("drop function slice4c_reject_audit()");}
        assertEquals("DRAFT",jdbc.queryForObject("select status from halls where id=?",String.class,hallId));
        JsonNode live=admin(json(post(publication()+"/publish"),publishBody(ready)),200);
        assertEquals("PUBLISHED",live.path("publicationState").asText()); assertEquals(1,live.path("publicationVersion").asLong());
        admin(json(post(publication()+"/publish"),publishBody(ready)),409);
        var publicHall=halls.getPublicHall(""+hallId);
        JsonNode publicJson=mapper.valueToTree(publicHall);
        assertEquals("APPLICATION",publicJson.path("listingOrigin").asText()); assertTrue(publicJson.path("enquiryOnly").asBoolean());
        assertEquals("VENUEMART",publicJson.path("enquiryRoutingTarget").asText()); assertTrue(publicJson.path("startingPrice").isNull());
        assertFalse(publicJson.path("availableThisMonth").asBoolean()); assertFalse(publicJson.path("verified").asBoolean());
        assertTrue(publicJson.path("ownerName").isNull()); assertTrue(publicJson.path("contactNumber").isNull());
        mvc.perform(get("/v1/halls/"+hallId+"/application-photos/"+approvedId).param("publicationVersion","1"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(get("/v1/halls/"+hallId+"/application-photos/"+pendingId).param("publicationVersion","1")).andExpect(status().isNotFound());
        assertThrows(ResponseStatusException.class,()->reviews.detail(hallId,auth()));
        assertThrows(ResponseStatusException.class,()->media.gallery(hallId,auth()));
    }

    @Test @Order(3) void customerRequestGoesToTeamWithoutOwnerBookingOrMessageJobs() throws Exception {
        Map<String,Object> request=Map.of("hallId",""+hallId,"eventDate",LocalDate.now().plusMonths(2).toString(),
                "eventType","Synthetic celebration","guestCount",100,"slot","MORNING","notes","Please confirm availability through VenueMart.");
        JsonNode enquiry=result(json(post("/v1/public/enquiries"),request).with(user(""+customerId).roles("CUSTOMER")),201);
        enquiryId=enquiry.path("id").asText(); assertFalse(enquiryId.isBlank());
        assertEquals("VENUEMART",enquiry.path("routingTarget").asText()); assertEquals("NEW",enquiry.path("status").asText());
        assertEquals(1,enquiry.path("publicationVersion").asLong()); assertTrue(enquiry.path("ownerResponseMessage").isNull());
        assertEquals(0,count("bookings")); assertEquals(0,count("lead_notification_jobs"));
        long numericEnquiryId=jdbc.queryForObject("select id from enquiries where routing_target='VENUEMART'",Long.class);
        assertThrows(RuntimeException.class,()->jdbc.update("update enquiries set status='CONFIRMED',version=version+1 where id=?",numericEnquiryId));
        assertThrows(RuntimeException.class,()->jdbc.update("update enquiries set routing_target='OWNER',publication_version=null where id=?",numericEnquiryId));
        assertThrows(RuntimeException.class,()->jdbc.update("insert into bookings(hall_id,customer_user_id,enquiry_id,status) values(?,?,?,'REQUESTED')",hallId,customerId,numericEnquiryId));
        assertEquals(0,count("bookings"));
        assertEquals(0,jdbc.queryForObject("select count(*) from notifications where recipient_user_id=?",Long.class,ownerId));
        assertTrue(jdbc.queryForObject("select count(*) from notifications where recipient_user_id=?",Long.class,adminId)>0);
        assertFalse(admin(get("/v1/admin/application-venue-enquiries"),200).path("items").isEmpty());
        admin(json(put("/v1/admin/application-venue-enquiries/"+enquiryId),Map.of(
                "expectedVersion",enquiry.path("version").asLong(),"status","CLOSED","responseMessage","Invalid direct closure","reason","Synthetic direct closure rejection")),409);
        result(json(patch("/v1/owner/enquiries/"+enquiryId+"/status"),Map.of("status","CONFIRMED","expectedVersion",enquiry.path("version").asLong()))
                .with(user(""+ownerId).roles("HALL_OWNER")),403);
        JsonNode contacted=admin(json(put("/v1/admin/application-venue-enquiries/"+enquiryId),Map.of(
                "expectedVersion",enquiry.path("version").asLong(),"status","CONTACTED","responseMessage","VenueMart is checking the requested date.","reason","Synthetic team followup initiated")),200);
        assertEquals("CONTACTED",contacted.path("status").asText()); assertTrue(contacted.path("ownerResponseMessage").isNull());
        assertEquals("VenueMart is checking the requested date.",contacted.path("responseMessage").asText());
        admin(json(put("/v1/admin/application-venue-enquiries/"+enquiryId),Map.of(
                "expectedVersion",enquiry.path("version").asLong(),"status","CONTACTED","responseMessage","Stale change","reason","Synthetic stale revision check")),409);
        JsonNode closed=admin(json(put("/v1/admin/application-venue-enquiries/"+enquiryId),Map.of(
                "expectedVersion",contacted.path("version").asLong(),"status","CLOSED","responseMessage","Synthetic request closed without a booking.","reason","Synthetic administrative closure")),200);
        assertEquals("CLOSED",closed.path("status").asText());
        admin(json(put("/v1/admin/application-venue-enquiries/"+enquiryId),Map.of(
                "expectedVersion",closed.path("version").asLong(),"status","CONTACTED","responseMessage","Invalid reopening","reason","Synthetic reopening rejection")),409);
        result(get("/v1/customer/enquiries/"+enquiryId).with(user(""+customerId).roles("CUSTOMER")),200);
        assertEquals(0,count("bookings")); assertEquals(0,count("lead_notification_jobs"));
    }

    @Test @Order(4) void unpublishRevokesPublicPhotosSavedListingsAndNewEnquiries() throws Exception {
        result(put("/v1/customer/saved-halls/"+hallId).with(user(""+customerId).roles("CUSTOMER")),200);
        JsonNode hidden=admin(json(post(publication()+"/unpublish"),Map.of("expectedPublicationVersion",1,"reason","Synthetic withdrawal for updated review")),200);
        assertEquals("UNPUBLISHED",hidden.path("publicationState").asText()); assertEquals(2,hidden.path("publicationVersion").asLong());
        assertThrows(ResponseStatusException.class,()->halls.getPublicHall(""+hallId));
        mvc.perform(get("/v1/halls/"+hallId+"/application-photos/"+approvedId).param("publicationVersion","1")).andExpect(status().isNotFound());
        JsonNode saved=result(get("/v1/customer/saved-halls").with(user(""+customerId).roles("CUSTOMER")),200);
        assertFalse(saved.toString().contains("Synthetic publication venue"));
        Map<String,Object> request=Map.of("hallId",""+hallId,"eventDate",LocalDate.now().plusMonths(2).toString(),"eventType","Test","guestCount",50,"slot","MORNING");
        result(json(post("/v1/public/enquiries"),request).with(user(""+customerId).roles("CUSTOMER")),404);
        JsonNode ready=admin(get(publication()),200); admin(json(post(publication()+"/publish"),publishBody(ready)),200);
        mvc.perform(get("/v1/halls/"+hallId+"/application-photos/"+approvedId).param("publicationVersion","1")).andExpect(status().isNotFound());
        mvc.perform(get("/v1/halls/"+hallId+"/application-photos/"+approvedId).param("publicationVersion","3")).andExpect(status().isOk());
        admin(json(post(publication()+"/unpublish"),Map.of("expectedPublicationVersion",3,"reason","Synthetic fixture cleanup withdrawal")),200);
    }

    @Test @Order(5) void ownerSourceAndAllExistingBusinessPathsRemainUnchanged() throws Exception {
        assertEquals(ownerBefore,jdbc.queryForMap("select * from halls where id=?",ownerHallId));
        assertEquals(sourceBefore,jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?",String.class,hallId));
        assertEquals("Existing hall",halls.getPublicHall(""+ownerHallId).getName());
        assertEquals(1,count("hall_media")); assertEquals(0,count("bookings")); assertEquals(0,count("lead_notification_jobs"));
        assertEquals(3,count("users")); assertEquals(0,count("vendors"));
        assertEquals(4,admin(get(publication()),200).path("history").size());
        assertThrows(RuntimeException.class,()->jdbc.update("insert into enquiries(hall_id,customer_user_id,routing_target,publication_version,status,version) values(?,?,'VENUEMART',1,'NEW',0)",ownerHallId,customerId));
        assertThrows(RuntimeException.class,()->jdbc.update("insert into enquiries(hall_id,customer_user_id,routing_target,status,version) values(?,?,'OWNER','PENDING_OWNER_RESPONSE',0)",hallId,customerId));
        assertThrows(RuntimeException.class,()->jdbc.update("update halls set status='APPROVED' where id=?",hallId));
        assertThrows(RuntimeException.class,()->jdbc.update("update venue_overture_publication_history set reason='Synthetic tampering attempt' where hall_id=?",hallId));
        result(get(publication()).with(user(""+customerId).roles("CUSTOMER")),403);
        jdbc.update("update users set status='INACTIVE' where id=?",adminId);
        try {admin(get(publication()),403); admin(get("/v1/admin/application-venue-enquiries"),403);}
        finally {jdbc.update("update users set status='ACTIVE' where id=?",adminId);}
    }

    @Test @Order(6) void concurrentPublicationHasOneWinnerAndKillSwitchKeepsWithdrawalAvailable() throws Exception {
        JsonNode current=admin(get(publication()),200);
        var request=mapper.convertValue(publishBody(current),OverturePublicationRequest.Publish.class);
        java.util.concurrent.Callable<Boolean> attempt=()->{
            try {publications.publish(hallId,request,auth()); return true;}
            catch(ResponseStatusException e) {assertEquals(409,e.getStatusCode().value()); return false;}
        };
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=executor.invokeAll(List.of(attempt,attempt));
            assertNotEquals(futures.get(0).get(),futures.get(1).get(),"Only one versioned publication may commit");
        }
        assertEquals(5,admin(get(publication()),200).path("publicationVersion").asLong());
        org.mockito.Mockito.doReturn(false).when(publicationFlag).isEnabled();
        try {
            assertThrows(ResponseStatusException.class,()->halls.getPublicHall(""+hallId));
            assertEquals("Existing hall",halls.getPublicHall(""+ownerHallId).getName());
            mvc.perform(get("/v1/halls/"+hallId+"/application-photos/"+approvedId).param("publicationVersion","5")).andExpect(status().isNotFound());
            JsonNode withdrawn=admin(json(post(publication()+"/unpublish"),Map.of("expectedPublicationVersion",5,"reason","Synthetic kill switch withdrawal")),200);
            assertEquals("UNPUBLISHED",withdrawn.path("publicationState").asText());
        } finally {org.mockito.Mockito.reset(publicationFlag);}
        assertEquals(0,count("bookings")); assertEquals(0,count("lead_notification_jobs"));
    }

    @Test @Order(7) void duplicateAssessmentOverflowBlocksPublishingButNeverWithdrawal() throws Exception {
        for (int nearbyCount : List.of(101, 201)) {
            JsonNode ready=admin(get(publication()),200);
            assertTrue(ready.path("ready").asBoolean());
            JsonNode live=admin(json(post(publication()+"/publish"),publishBody(ready)),200);
            long version=live.path("publicationVersion").asLong();
            jdbc.update("""
                    insert into halls(owner_user_id,owner_name,name,city,area,status,latitude,longitude)
                    select ?,'Existing owner','Synthetic duplicate overflow','Chennai','Test Area','APPROVED',13.0,80.1
                    from generate_series(1,?)
                    """,ownerId,nearbyCount);
            try {
                JsonNode management=admin(get(publication()),200);
                assertFalse(management.path("ready").asBoolean());
                JsonNode withdrawn=admin(json(post(publication()+"/unpublish"),Map.of(
                        "expectedPublicationVersion",version,"reason","Synthetic duplicate overflow withdrawal")),200);
                assertEquals("UNPUBLISHED",withdrawn.path("publicationState").asText());
                assertFalse(withdrawn.path("ready").asBoolean());
                assertThrows(ResponseStatusException.class,()->halls.getPublicHall(""+hallId));
                mvc.perform(get("/v1/halls/"+hallId+"/application-photos/"+approvedId)
                        .param("publicationVersion",""+version)).andExpect(status().isNotFound());
                assertEquals(409,assertThrows(ResponseStatusException.class,()->reviews.detail(hallId,auth()))
                        .getStatusCode().value(),"The factual editor must retain its strict duplicate bound");
                admin(json(post(publication()+"/publish"),publishBody(withdrawn)),409);
            } finally {
                jdbc.update("delete from halls where owner_user_id=? and name='Synthetic duplicate overflow'",ownerId);
            }
        }
        assertEquals(0,count("bookings")); assertEquals(0,count("lead_notification_jobs"));
    }
}
