package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OverturePublicationRequestTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private static final String VALID="{\"expectedPublicationVersion\":0,\"expectedReviewVersion\":2,\"expectedMediaVersion\":3,\"reason\":\"Ready for controlled publication\"}";
    @Test void exactExplicitVersionsAndReasonAreAccepted() throws Exception {
        var request=mapper.readValue(VALID,OverturePublicationRequest.Publish.class);
        assertEquals(0,request.expectedPublicationVersion()); assertEquals(2,request.expectedReviewVersion()); assertEquals(3,request.expectedMediaVersion());
        assertEquals(0,mapper.readValue("{\"expectedPublicationVersion\":0,\"reason\":\"Withdraw for review\"}",OverturePublicationRequest.Unpublish.class).expectedPublicationVersion());
    }
    @ParameterizedTest @ValueSource(strings={"status","ownerUserId","publicUrl","photoIds","publishedBy","hallId"})
    void serverAuthorityFieldsAreRejected(String field) {
        String injected=VALID.substring(0,VALID.length()-1)+",\""+field+"\":\"Injected\"}";
        assertThrows(Exception.class,()->mapper.readValue(injected,OverturePublicationRequest.Publish.class));
    }
    @Test void versionsCannotBeOmittedCoercedNegativeFractionalOrOverflowed() {
        for(String value:List.of("\"0\"","null","-1","1.5","9223372036854775808","false"))
            assertThrows(Exception.class,()->mapper.readValue(VALID.replace("\"expectedPublicationVersion\":0","\"expectedPublicationVersion\":"+value),OverturePublicationRequest.Publish.class));
        assertThrows(Exception.class,()->mapper.readValue(VALID.replace("\"expectedMediaVersion\":3,",""),OverturePublicationRequest.Publish.class));
    }
    @Test void duplicateKeysTrailingTokensNullAndNonStringReasonAreRejected() {
        for(String bad:List.of(VALID+" {}",VALID.replace("\"expectedReviewVersion\":2","\"expectedReviewVersion\":2,\"expectedReviewVersion\":3"),
                VALID.replace("\"Ready for controlled publication\"","null"),VALID.replace("\"Ready for controlled publication\"","123"),"null","[]"))
            assertThrows(Exception.class,()->mapper.readValue(bad,OverturePublicationRequest.Publish.class));
    }
}
