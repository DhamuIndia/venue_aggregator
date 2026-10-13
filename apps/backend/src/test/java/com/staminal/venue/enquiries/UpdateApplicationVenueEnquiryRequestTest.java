package com.staminal.venue.enquiries;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.enquiries.dto.UpdateApplicationVenueEnquiryRequest;
import com.staminal.venue.enums.EnquiryStatus;

class UpdateApplicationVenueEnquiryRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String VALID = "{\"expectedVersion\":0,\"status\":\"CONTACTED\",\"responseMessage\":\" Response for customer \",\"reason\":\" Operational reason \"}";

    @Test void exactStrictContractNormalizesOnlyWhitespace() throws Exception {
        var request = mapper.readValue(VALID, UpdateApplicationVenueEnquiryRequest.class);
        assertThat(request.expectedVersion()).isEqualTo(0);
        assertThat(request.status()).isEqualTo(EnquiryStatus.CONTACTED);
        assertThat(request.responseMessage()).isEqualTo("Response for customer");
        assertThat(request.reason()).isEqualTo("Operational reason");
    }

    @Test void authorityInjectionDuplicatesMissingFieldsAndTrailingObjectsAreRejected() {
        for (String json : List.of(VALID.replace("}", ",\"routingTarget\":\"OWNER\"}"),
                VALID.replace("}", ",\"customerId\":1}"), VALID.replace("}", ",\"publicationVersion\":5}"),
                VALID.replace("}", ",\"ownerResponseMessage\":\"Injected\"}"),
                VALID.replace("\"expectedVersion\":0", "\"expectedVersion\":0,\"expectedVersion\":0"),
                VALID.replace("\"expectedVersion\":0,", ""), VALID + " {}", "[]"))
            assertThatThrownBy(() -> mapper.readValue(json, UpdateApplicationVenueEnquiryRequest.class), json).isInstanceOf(Exception.class);
    }

    @Test void topLevelNullIsRejectedByJacksonNullHandling() {
        assertThatThrownBy(() -> mapper.readValue("null", UpdateApplicationVenueEnquiryRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class)
                .hasMessageContaining("must be an object");
    }

    @Test void scalarCoercionUnsafeStatusesAndBoundsAreRejected() {
        for (String json : List.of(VALID.replace(":0,", ":\"0\","), VALID.replace(":0,", ":0.0,"),
                VALID.replace(":0,", ":-1,"), VALID.replace(":0,", ":null,"),
                VALID.replace(":0,", ":9223372036854775808,"), VALID.replace("\"CONTACTED\"", "null"),
                VALID.replace("\"CONTACTED\"", "\"CONFIRMED\""), VALID.replace("\"CONTACTED\"", "\"NEW\""),
                VALID.replace("\" Response for customer \"", "true"), VALID.replace("\" Response for customer \"", "\" \""),
                VALID.replace(" Operational reason ", "short"), VALID.replace(" Response for customer ", "x".repeat(2001)),
                VALID.replace(" Operational reason ", "x".repeat(1001))))
            assertThatThrownBy(() -> mapper.readValue(json, UpdateApplicationVenueEnquiryRequest.class), json).isInstanceOf(Exception.class);
    }
}
