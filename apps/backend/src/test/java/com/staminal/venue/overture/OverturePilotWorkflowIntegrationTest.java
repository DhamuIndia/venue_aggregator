package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.staminal.venue.discovery.places.VenuePlacesClient;
import com.staminal.venue.notifications.whatsapp.WhatsAppCloudApiClient;
import com.staminal.venue.overture.OvertureDraftReviewResponse.Facts;

/**
 * One continuous synthetic venue journey, never a real venue or a rights determination.
 * Requires a fresh, explicitly named local database and a separately provisioned private test bucket.
 */
@EnabledIfEnvironmentVariable(named="OVERTURE_PILOT_TEST_DB",
        matches="jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_pilot_test_[a-zA-Z0-9_]+")
@SpringBootTest(properties={
        "app.overture-onboarding.enabled=true", "app.overture-onboarding.max-batch-size=20",
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
class OverturePilotWorkflowIntegrationTest {
    private static final String SOURCE_ID="7299c474-d86d-4ef5-a755-8c77c565eb8a";
    private static final String VENUE_NAME="Synthetic continuous VenueMart pilot";
    private static final String BASE="/v1/admin/overture-onboarding";
    private static long adminId, customerId;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean VenuePlacesClient places;
    @MockitoBean WhatsAppCloudApiClient whatsapp;

    @DynamicPropertySource
    static void localFixtures(DynamicPropertyRegistry registry) throws Exception {
        String url=System.getenv("OVERTURE_PILOT_TEST_DB"), bucket=System.getenv("OVERTURE_PILOT_TEST_BUCKET");
        if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_pilot_test_[a-zA-Z0-9_]+")
                || bucket==null || !bucket.matches("venuemart-pilot-test-[a-z0-9-]+"))
            throw new IllegalStateException("An explicitly disposable local pilot database and private bucket are required");
        // Fail before migrations or fixture writes if a named database has already been used.
        try(var connection=DriverManager.getConnection(url,"venue_app","venue_app_password"); var sql=connection.createStatement();
                var rows=sql.executeQuery("select 1 from information_schema.tables where table_schema='public' and table_type='BASE TABLE' limit 1")) {
            if(rows.next()) throw new IllegalStateException("The local pilot database must be empty; never reuse an application database");
        }
        Flyway.configure().dataSource(url,"venue_app","venue_app_password").locations("classpath:db/migration").target("40").load().migrate();
        try(var connection=DriverManager.getConnection(url,"venue_app","venue_app_password"); var sql=connection.createStatement()) {
            sql.executeUpdate("insert into users(full_name,phone,email,password_hash) values "
                    +"('Synthetic pilot admin','pilot-admin','pilot-admin@example.invalid','synthetic'),"
                    +"('Synthetic pilot customer','pilot-customer','pilot-customer@example.invalid','synthetic')");
            sql.executeUpdate("insert into roles(name) values('ADMIN'),('CUSTOMER') on conflict(name) do nothing");
            sql.executeUpdate("insert into user_roles(user_id,role_id) select u.id,r.id from users u,roles r where "
                    +"(u.email='pilot-admin@example.invalid' and r.name='ADMIN') or (u.email='pilot-customer@example.invalid' and r.name='CUSTOMER')");
            sql.executeUpdate("insert into admins(full_name,email,contact_number,password_hash) values "
                    +"('Synthetic pilot admin','pilot-admin@example.invalid','pilot-admin','synthetic')");
            try(var rows=sql.executeQuery("select id,email from users")) {while(rows.next()) {
                if(rows.getString(2).equals("pilot-admin@example.invalid")) adminId=rows.getLong(1); else customerId=rows.getLong(1);
            }}
        }
        // This is a generated fixture, not fetched Overture data or a real business/photo source.
        ObjectMapper json=new ObjectMapper(); ObjectNode catalog=(ObjectNode)json.readTree(OvertureFixtures.json());
        catalog.put("generatedAt",Instant.now().toString());
        ObjectNode venue=(ObjectNode)catalog.path("venues").get(0);
        venue.put("id",SOURCE_ID); venue.put("name",VENUE_NAME); venue.put("operatingStatus","unknown");
        ((ObjectNode)venue.path("sources").get(0)).put("recordId","synthetic-continuous-pilot");
        Path path=Files.createTempFile("venuemart-synthetic-pilot-catalog-", ".json");
        Files.writeString(path,json.writeValueAsString(catalog)); path.toFile().deleteOnExit();
        registry.add("spring.datasource.url",()->url); registry.add("spring.datasource.username",()->"venue_app");
        registry.add("spring.datasource.password",()->"venue_app_password");
        registry.add("app.overture-onboarding.catalog-path",path::toString);
        registry.add("app.overture-onboarding.media.bucket",()->bucket);
    }

    private JsonNode result(MockHttpServletRequestBuilder request,int expectedStatus) throws Exception {
        byte[] bytes=mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray();
        return bytes.length==0?mapper.nullNode():mapper.readTree(bytes);
    }
    private JsonNode admin(MockHttpServletRequestBuilder request,int expectedStatus) throws Exception {
        return result(request.with(user(""+adminId).roles("ADMIN")),expectedStatus);
    }
    private JsonNode customer(MockHttpServletRequestBuilder request,int expectedStatus) throws Exception {
        return result(request.with(user(""+customerId).roles("CUSTOMER")),expectedStatus);
    }
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value));
    }
    private long count(String table) {return jdbc.queryForObject("select count(*) from "+table,Long.class);}

    @Test void oneImportedVenueCompletesLicensedPublicationTeamRequestAndWithdrawalWithoutBookingOrSending() throws Exception {
        assertEquals(46,jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success",Integer.class));
        assertEquals(0,count("halls")); assertEquals(2,count("users"));
        Map<String,Long> unrelatedBefore=new LinkedHashMap<>();
        for(String table:List.of("vendors","hall_media","bookings","payments","vendor_subscriptions","lead_notification_jobs","whatsapp_notification_attempts")) unrelatedBefore.put(table,count(table));

        // The runtime-loaded synthetic catalog, preview and import produce the only venue in the database.
        JsonNode settings=admin(get(BASE+"/settings"),200); assertTrue(settings.path("ready").asBoolean());
        assertEquals(1,settings.path("recordCount").asInt()); String catalogVersion=settings.path("catalogVersion").asText();
        JsonNode catalog=admin(get(BASE+"/catalog"),200);
        assertEquals(SOURCE_ID,catalog.path("content").get(0).path("id").asText());
        JsonNode preview=admin(json(post(BASE+"/preview"),Map.of("ids",List.of(SOURCE_ID))),200);
        assertEquals("READY",preview.path("items").get(0).path("outcome").asText());
        JsonNode imported=admin(json(post(BASE+"/import"),Map.of("catalogVersion",catalogVersion,"ids",List.of(SOURCE_ID))),200);
        assertEquals(1,imported.path("createdCount").asInt()); long hallId=imported.path("items").get(0).path("hallId").asLong(); assertTrue(hallId>0);
        assertEquals(SOURCE_ID,jdbc.queryForObject("select source_id::text from venue_overture_imports where hall_id=?",String.class,hallId));
        assertEquals(catalogVersion,jdbc.queryForObject("select catalog_version from venue_overture_imports where hall_id=?",String.class,hallId));
        assertEquals(1,count("halls")); assertEquals(1,count("venue_overture_imports"));
        JsonNode repeated=admin(json(post(BASE+"/import"),Map.of("catalogVersion",catalogVersion,"ids",List.of(SOURCE_ID))),200);
        assertEquals(0,repeated.path("createdCount").asInt()); assertEquals("ALREADY_IMPORTED",repeated.path("items").get(0).path("outcome").asText());
        String draftUrl=BASE+"/drafts/"+hallId, publicationUrl=BASE+"/publications/"+hallId, mediaUrl=draftUrl+"/media";
        String originalFacts=jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?",String.class,hallId);
        result(get("/v1/public/halls/"+hallId),404);

        // Required facts and their evidence are reviewed; no venue owner or price is fabricated.
        JsonNode draft=admin(get(draftUrl),200); Facts source=mapper.treeToValue(draft.path("facts"),Facts.class);
        Facts facts=new Facts(source.name(),"12 Synthetic Pilot Street","Chennai","Synthetic Pilot Area","600000",source.latitude(),source.longitude(),
                "+910000000001",source.website(),"open",250,"Synthetic local pilot fixture, not a real venue.",source.amenities());
        List<String> fields=new java.util.ArrayList<>(OvertureDraftReviewPolicy.REQUIRED); fields.add("description");
        JsonNode verified=admin(json(put(draftUrl),new OvertureDraftReviewRequest.Update(draft.path("reviewVersion").asLong(),facts,fields,
                OvertureDraftReviewResponse.ReviewStatus.VERIFIED,"PRIVATE_PILOT_FACTUAL_EVIDENCE: synthetic records checked",
                OvertureDraftReviewResponse.DuplicateDecision.NOT_REVIEWED,null,List.of())),200);
        assertEquals("VERIFIED",verified.path("reviewStatus").asText());
        assertFalse(admin(get(publicationUrl),200).path("ready").asBoolean());

        // The locally generated PNG exercises the actual multipart, private-storage and credit-review paths.
        var image=new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB); var input=new ByteArrayOutputStream(); ImageIO.write(image,"png",input);
        var metadata=new OvertureDraftMediaRequest.Upload(0,"Synthetic pilot photo",OvertureDraftMediaResponse.SourceKind.LICENSED_IMAGE,
                "https://images.example.com/private-pilot-source?internal=synthetic",OvertureDraftMediaResponse.RightsBasis.OPEN_LICENSE,"CC BY 4.0",
                "PRIVATE_PILOT_USAGE_EVIDENCE: simulated CC BY rights on a generated test image",true);
        JsonNode gallery=admin(multipart(mediaUrl+"/upload")
                .file(new MockMultipartFile("file","synthetic-pilot.png","image/png",input.toByteArray()))
                .file(new MockMultipartFile("metadata","metadata.json","application/json",mapper.writeValueAsBytes(metadata))),200);
        long photoId=gallery.path("items").get(0).path("id").asLong(); assertTrue(photoId>0);
        gallery=admin(json(put(mediaUrl+"/"+photoId+"/review"),Map.of("expectedVersion",gallery.path("mediaVersion").asLong(),
                "status","APPROVED","reason","PRIVATE_PILOT_IMAGE_REVIEW: synthetic association and usage reviewed")),200);
        gallery=admin(json(put(mediaUrl+"/arrangement"),Map.of("expectedVersion",gallery.path("mediaVersion").asLong(),"coverMediaId",photoId,"orderedMediaIds",List.of(photoId))),200);
        String creditUrl=mediaUrl+"/"+photoId+"/credits";
        Map<String,Object> credit=new LinkedHashMap<>();
        credit.put("expectedVersion",gallery.path("mediaVersion").asLong()); credit.put("expectedCreditVersion",0);
        credit.put("title","Synthetic pilot image"); credit.put("creator","Synthetic VenueMart fixture"); credit.put("creatorUrl",null);
        credit.put("sourceUrl","https://images.example.com/synthetic-pilot-source"); credit.put("licenseCode","CC_BY_4_0");
        credit.put("changesNotice","Locally generated test image; no earlier creative changes."); credit.put("requiredNotices","Synthetic data only; this is not real rights evidence.");
        gallery=admin(json(put(creditUrl),credit),200);
        assertEquals("PENDING",gallery.path("items").get(0).path("credit").path("status").asText());
        assertFalse(admin(get(publicationUrl),200).path("ready").asBoolean());
        gallery=admin(json(post(creditUrl+"/review"),Map.of("expectedVersion",gallery.path("mediaVersion").asLong(),"expectedCreditVersion",1,
                "status","APPROVED","reason","PRIVATE_PILOT_CREDIT_REVIEW: simulated rights and public fields checked","rightsConfirmed",true,"attributionConfirmed",true)),200);
        assertEquals("APPROVED",gallery.path("items").get(0).path("credit").path("status").asText());
        byte[] canonical=mvc.perform(get(mediaUrl+"/"+photoId+"/content").with(user(""+adminId).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andReturn().getResponse().getContentAsByteArray();
        assertTrue(canonical.length>3);

        JsonNode ready=admin(get(publicationUrl),200); assertTrue(ready.path("ready").asBoolean());
        JsonNode live=admin(json(post(publicationUrl+"/publish"),Map.of("expectedPublicationVersion",ready.path("publicationVersion").asLong(),
                "expectedReviewVersion",ready.path("reviewVersion").asLong(),"expectedMediaVersion",ready.path("mediaVersion").asLong(),
                "reason","Synthetic continuous pilot publication reviewed")),200);
        long publicationVersion=live.path("publicationVersion").asLong(); assertEquals(1,publicationVersion);
        JsonNode publicHall=result(get("/v1/public/halls/"+hallId),200);
        assertEquals(VENUE_NAME,publicHall.path("name").asText()); assertTrue(publicHall.path("enquiryOnly").asBoolean());
        assertEquals("APPLICATION",publicHall.path("listingOrigin").asText()); assertEquals("VENUEMART",publicHall.path("enquiryRoutingTarget").asText());
        for(String field:List.of("ownerName","contactNumber","whatsappNumber","startingPrice","pricing","availabilitySummary")) assertTrue(publicHall.path(field).isNull(),field);
        assertFalse(publicHall.path("verified").asBoolean()); assertFalse(publicHall.path("availableThisMonth").asBoolean());
        JsonNode publicCredit=publicHall.path("applicationPhotos").get(0).path("credit");
        Set<String> keys=new java.util.HashSet<>(); publicCredit.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("title","creator","creatorUrl","sourceUrl","licenseCode","licenseLabel","licenseUrl","changesNotice","processingNotice","requiredNotices"),keys);
        assertEquals("CC_BY_4_0",publicCredit.path("licenseCode").asText());
        assertEquals("https://creativecommons.org/licenses/by/4.0/",publicCredit.path("licenseUrl").asText());
        for(String privateValue:List.of("PRIVATE_PILOT","private-pilot-source","permissionEvidence","reviewReason","sourceReference","Synthetic pilot admin"))
            assertFalse(publicHall.toString().contains(privateValue),privateValue);
        JsonNode search=result(get("/v1/public/halls").param("q",VENUE_NAME),200); assertEquals(1,search.path("content").size());
        assertEquals(hallId,search.path("content").get(0).path("id").asLong());
        String photoUrl="/v1/halls/"+hallId+"/application-photos/"+photoId;
        mvc.perform(get(photoUrl).param("publicationVersion",""+publicationVersion)).andExpect(status().isOk())
                .andExpect(content().bytes(canonical)).andExpect(header().string("Cache-Control","private, no-store"));

        // One customer request is routed to the real VenueMart queue, never to an invented owner.
        Map<String,Object> request=Map.of("hallId",""+hallId,"eventDate",LocalDate.now().plusMonths(2).toString(),"eventType","Synthetic pilot celebration",
                "guestCount",100,"slot","MORNING","notes","Synthetic request: please check availability; do not confirm a booking.");
        JsonNode enquiry=customer(json(post("/v1/public/enquiries"),request),201); String enquiryId=enquiry.path("id").asText(); assertFalse(enquiryId.isBlank());
        assertEquals("NEW",enquiry.path("status").asText()); assertEquals("VENUEMART",enquiry.path("routingTarget").asText());
        assertEquals(publicationVersion,enquiry.path("publicationVersion").asLong()); assertTrue(enquiry.path("ownerResponseMessage").isNull());
        JsonNode queue=admin(get("/v1/admin/application-venue-enquiries").param("status","NEW"),200);
        assertEquals(1,queue.path("items").size()); assertEquals(enquiryId,queue.path("items").get(0).path("id").asText());
        String queueUrl="/v1/admin/application-venue-enquiries/"+enquiryId;
        JsonNode contacted=admin(json(put(queueUrl),Map.of("expectedVersion",enquiry.path("version").asLong(),"status","CONTACTED",
                "responseMessage","VenueMart is checking this synthetic request; no date is confirmed.","reason","PRIVATE_PILOT_TEAM_REASON: simulated customer contact")),200);
        assertEquals("CONTACTED",contacted.path("status").asText());
        JsonNode closed=admin(json(put(queueUrl),Map.of("expectedVersion",contacted.path("version").asLong(),"status","CLOSED",
                "responseMessage","Synthetic pilot request closed without a booking or payment.","reason","PRIVATE_PILOT_TEAM_REASON: simulated closure")),200);
        assertEquals("CLOSED",closed.path("status").asText());
        JsonNode customerRequest=customer(get("/v1/customer/enquiries/"+enquiryId),200);
        assertEquals("CLOSED",customerRequest.path("status").asText()); assertEquals(closed.path("responseMessage"),customerRequest.path("responseMessage"));
        assertFalse(customerRequest.toString().contains("PRIVATE_PILOT_TEAM_REASON"));
        customer(put("/v1/customer/saved-halls/"+hallId),200);
        assertEquals(hallId,customer(get("/v1/customer/saved-halls"),200).path("items").get(0).path("id").asLong());

        // Withdrawal revokes new access while retaining the existing customer's team request and private evidence.
        JsonNode withdrawn=admin(json(post(publicationUrl+"/unpublish"),Map.of("expectedPublicationVersion",publicationVersion,"reason","Synthetic pilot complete; withdraw test venue")),200);
        assertEquals("UNPUBLISHED",withdrawn.path("publicationState").asText()); assertEquals(2,withdrawn.path("publicationVersion").asLong());
        result(get("/v1/public/halls/"+hallId),404);
        assertEquals(0,result(get("/v1/public/halls").param("q",VENUE_NAME),200).path("content").size());
        mvc.perform(get(photoUrl).param("publicationVersion",""+publicationVersion)).andExpect(status().isNotFound());
        JsonNode saved=customer(get("/v1/customer/saved-halls"),200); assertEquals(0,saved.path("total").asInt()); assertTrue(saved.path("items").isEmpty());
        customer(json(post("/v1/public/enquiries"),request),404); assertEquals(1,count("enquiries"));
        assertEquals("CLOSED",customer(get("/v1/customer/enquiries/"+enquiryId),200).path("status").asText());
        assertEquals(enquiryId,admin(get("/v1/admin/application-venue-enquiries").param("status","CLOSED"),200).path("items").get(0).path("id").asText());
        mvc.perform(get(mediaUrl+"/"+photoId+"/content").with(user(""+adminId).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().bytes(canonical));
        assertEquals(originalFacts,jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?",String.class,hallId));
        assertEquals(2,count("venue_overture_photo_credits")); // Pending metadata and approval remain separate append-only revisions.
        assertEquals(1,count("venue_overture_publication_photo_credits")); assertEquals(2,count("venue_overture_publication_history"));
        assertEquals(1,count("halls")); assertEquals(2,count("users"));
        assertNull(jdbc.queryForObject("select owner_user_id from halls where id=?",Long.class,hallId));
        for(var before:unrelatedBefore.entrySet()) assertEquals(before.getValue().longValue(),count(before.getKey()),before.getKey()+" must remain unchanged");
        for(String table:List.of("bookings","payments","vendor_subscriptions","lead_notification_jobs","whatsapp_notification_attempts")) assertEquals(0,count(table),table);
        for(String action:List.of("OVERTURE_VENUE_DRAFT_CREATED","OVERTURE_VENUE_DRAFT_REVIEWED","OVERTURE_DRAFT_PHOTO_UPLOADED",
                "OVERTURE_DRAFT_PHOTO_REVIEWED","OVERTURE_DRAFT_PHOTO_ARRANGED","OVERTURE_PHOTO_CREDIT_SAVED","OVERTURE_PHOTO_CREDIT_REVIEWED",
                "OVERTURE_VENUE_PUBLISHED","OVERTURE_VENUE_UNPUBLISHED","APPLICATION_VENUE_ENQUIRY_UPDATED"))
            assertTrue(jdbc.queryForObject("select count(*) from audit_events where action=?",Long.class,action)>0,action);
        assertTrue(jdbc.queryForObject("select count(*) from notifications where recipient_user_id=?",Long.class,adminId)>0);
        verifyNoInteractions(places);
        verifyNoInteractions(whatsapp);
    }
}
