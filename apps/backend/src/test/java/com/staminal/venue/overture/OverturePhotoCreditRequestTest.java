package com.staminal.venue.overture;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.overture.OverturePhotoCreditResponse.Status;

class OverturePhotoCreditRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();
    static final String SAVE = "{\"expectedVersion\":0,\"expectedCreditVersion\":0,\"title\":\"Venue exterior\",\"creator\":\"Example\",\"creatorUrl\":null,\"sourceUrl\":\"https://example.org/credit\",\"licenseCode\":\"CC_BY_4_0\",\"changesNotice\":\"No creative edits\",\"requiredNotices\":null}";
    static final String REVIEW = "{\"expectedVersion\":1,\"expectedCreditVersion\":1,\"status\":\"APPROVED\",\"reason\":\"License and attribution independently reviewed\",\"rightsConfirmed\":true,\"attributionConfirmed\":true}";
    @Test void strictSaveAndReviewAreAcceptedWithoutScalarCoercion() throws Exception {
        assertThat(mapper.readValue(SAVE,OverturePhotoCreditRequest.Save.class).creatorUrl()).isNull();
        assertThat(mapper.readValue(REVIEW,OverturePhotoCreditRequest.Review.class).status()).isEqualTo(Status.APPROVED);
        assertThat(mapper.readValue(REVIEW.replace("APPROVED","REJECTED").replace("true","false"),OverturePhotoCreditRequest.Review.class).rightsConfirmed()).isFalse();
    }
    @Test void saveRejectsNullUnknownDuplicateMissingWrongTypeAndTrailingJson() {
        for (String json : List.of("null","[]",SAVE+" {}", SAVE.replace("}",",\"rightsConfirmed\":true}"),
                SAVE.replace("}",",\"review_status\":\"APPROVED\"}"), SAVE.replace("expectedVersion\":0,","expectedVersion\":0,\"expectedVersion\":0,"),
                SAVE.replace("\"expectedCreditVersion\":0,",""), SAVE.replace("expectedVersion\":0","expectedVersion\":\"0\""),
                SAVE.replace("expectedVersion\":0","expectedVersion\":0.1"), SAVE.replace("expectedVersion\":0","expectedVersion\":-1"),
                SAVE.replace("expectedCreditVersion\":0","expectedCreditVersion\":null"), SAVE.replace("\"creatorUrl\":null","\"creatorUrl\":5"),
                SAVE.replace("\"title\":\"Venue exterior\"","\"title\":null"), SAVE.replace("CC_BY_4_0","CC_BY_SA_4_0")))
            assertThatThrownBy(() -> mapper.readValue(json,OverturePhotoCreditRequest.Save.class),json).isInstanceOf(Exception.class);
    }
    @Test void reviewRequiresExplicitTypedApprovalAttestationsAndBothVersions() {
        for (String json : List.of("null","[]",REVIEW+" {}", REVIEW.replace("}",",\"licenseCode\":\"CC0_1_0\"}"),
                REVIEW.replace("\"rightsConfirmed\":true","\"rightsConfirmed\":false"),REVIEW.replace("\"attributionConfirmed\":true","\"attributionConfirmed\":false"),
                REVIEW.replace("\"rightsConfirmed\":true,",""), REVIEW.replace("\"rightsConfirmed\":true","\"rightsConfirmed\":\"true\""),
                REVIEW.replace("\"attributionConfirmed\":true","\"attributionConfirmed\":null"), REVIEW.replace("APPROVED","PENDING"),
                REVIEW.replace("expectedCreditVersion\":1","expectedCreditVersion\":9223372036854775808"),
                REVIEW.replace("expectedVersion\":1","expectedVersion\":1,\"expectedVersion\":1")))
            assertThatThrownBy(() -> mapper.readValue(json,OverturePhotoCreditRequest.Review.class),json).isInstanceOf(Exception.class);
    }
}
