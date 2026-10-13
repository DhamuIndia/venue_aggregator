package com.staminal.venue.overture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class OverturePhotoCreditResponseTest {
    @Test void privateLatestPendingCreditHasNoBorrowedApprovalAndPublicDtoHasOnlyWhitelist() throws Exception {
        ResultSet row=mock(ResultSet.class);
        when(row.getObject("credit_version",Long.class)).thenReturn(3L);
        when(row.getString("credit_status")).thenReturn("PENDING");
        when(row.getString("credit_license_code")).thenReturn("CC_BY_4_0");
        when(row.getString("credit_title")).thenReturn("Venue exterior");
        when(row.getString("credit_creator")).thenReturn("Photographer");
        when(row.getString("credit_source_url")).thenReturn("https://commons.wikimedia.org/wiki/File:Example.jpg");
        when(row.getString("credit_changes_notice")).thenReturn("No creative edits");
        when(row.getLong("credit_changed_by")).thenReturn(4L); when(row.getString("credit_admin_name")).thenReturn("Private reviewer name");
        when(row.getTimestamp("credit_changed_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-06T10:00:00Z")));
        var credit=OverturePhotoCreditResponse.readGallery(row);
        assertThat(credit.creditVersion()).isEqualTo(3); assertThat(credit.status()).isEqualTo(OverturePhotoCreditResponse.Status.PENDING);
        assertThat(credit.reviewedBy()).isNull(); assertThat(credit.reviewReason()).isNull();
        var tree=new ObjectMapper().valueToTree(credit.publicCredit());
        assertThat(tree.size()).isEqualTo(10); assertThat(tree.has("creatorUrl")).isTrue(); assertThat(tree.get("creatorUrl").isNull()).isTrue();
        assertThat(tree.get("licenseUrl").asText()).isEqualTo("https://creativecommons.org/licenses/by/4.0/");
        assertThat(tree.get("processingNotice").asText()).isEqualTo(OverturePhotoCreditPolicy.PROCESSING_NOTICE);
        assertThat(tree.toString()).doesNotContain("Private reviewer name","reviewReason","changedBy","creditVersion","permissionEvidence","sourceReference");
    }
    @Test void missingCreditIsNullRatherThanAConstructedFallback() throws Exception {
        assertThat(OverturePhotoCreditResponse.readGallery(mock(ResultSet.class))).isNull();
    }
}
