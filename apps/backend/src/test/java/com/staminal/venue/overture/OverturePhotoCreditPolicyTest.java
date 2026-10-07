package com.staminal.venue.overture;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.staminal.venue.overture.OverturePhotoCreditResponse.LicenseCode;

class OverturePhotoCreditPolicyTest {
    static OverturePhotoCreditRequest.Save save() {
        return new OverturePhotoCreditRequest.Save(3,0," Venue exterior "," Example photographer ",
                "https://commons.wikimedia.org/wiki/User:Example", "https://commons.wikimedia.org/wiki/File:Example%20venue.jpg",
                LicenseCode.CC_BY_4_0," No creative edits. "," Original copyright notice retained. ");
    }
    @Test void completeCreditUsesDedicatedPublicFieldsAndDerivedLicenseValues() {
        var valid = OverturePhotoCreditPolicy.validate(save());
        assertThat(valid.title()).isEqualTo("Venue exterior"); assertThat(valid.creator()).isEqualTo("Example photographer");
        assertThat(valid.sourceUrl()).endsWith("Example%20venue.jpg");
        assertThat(OverturePhotoCreditPolicy.licenseLabel("CC_BY_4_0")).isEqualTo("CC BY 4.0");
        assertThat(OverturePhotoCreditPolicy.licenseUrl("CC_BY_4_0")).isEqualTo("https://creativecommons.org/licenses/by/4.0/");
        assertThat(OverturePhotoCreditPolicy.licenseLabel("CC0_1_0")).isEqualTo("CC0 1.0");
        assertThat(OverturePhotoCreditPolicy.licenseUrl("CC0_1_0")).isEqualTo("https://creativecommons.org/publicdomain/zero/1.0/");
    }
    @ParameterizedTest @ValueSource(strings={"CC BY 4.0", "CC-BY-4.0", "CREATIVE COMMONS ATTRIBUTION 4.0 INTERNATIONAL", " cc  by 4.0 "})
    void supportedAttributionAliasesMatchOnlyTheImmutableUploadLicense(String name) {
        OverturePhotoCreditPolicy.requireLicenseMatch(name, LicenseCode.CC_BY_4_0);
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.requireLicenseMatch(name, LicenseCode.CC0_1_0)).hasMessageContaining("409 CONFLICT");
    }
    @ParameterizedTest @ValueSource(strings={"CC0 1.0", "CC0-1.0", "CC0 1.0 UNIVERSAL", "CREATIVE COMMONS ZERO 1.0 UNIVERSAL"})
    void supportedZeroAliasesAreExplicitAndCannotBeRelabeledBy(String name) {
        OverturePhotoCreditPolicy.requireLicenseMatch(name, LicenseCode.CC0_1_0);
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.requireLicenseMatch(name, LicenseCode.CC_BY_4_0)).hasMessageContaining("409 CONFLICT");
    }
    @ParameterizedTest @ValueSource(strings={"CC BY", "CC-BY-3.0", "CC BY-SA 4.0", "CC BY-NC 4.0", "Public domain", "CC0", "License approved by owner", ""})
    void UnsupportedLicensesCannotGainPublicationThroughCreditMetadata(String name) {
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.requireLicenseMatch(name, LicenseCode.CC_BY_4_0)).hasMessageContaining("409 CONFLICT");
    }
    @ParameterizedTest @ValueSource(strings={"http://example.org/photo", "https://example.org/photo?token=private", "https://example.org/photo#private",
            "https://user:password@example.org/photo", "https://localhost/photo", "https://127.0.0.1/photo", "https://10.0.0.1/photo",
            "https://[::1]/photo", "https://2130706433/photo", "https://0x7f.0.0.1/photo", "https://example.local/photo", "https://example.invalid/photo",
            "https://photos.google.com/photo", "https://maps.google.com/photo", "https://maps.app.goo.gl/photo", "https://goo.gl/photo",
            "https://lh3.googleusercontent.com/photo", "https://www.google.com/maps/123", "https://www.google.com/%6daps/123",
            "https://example.org/a%0Ab", "https://example.org/a%250Ab", "https://example.org/a%25250Ab", "https://example.org/a%2525250Ab",
            "https://example.org/a%25literal%250Ab", "https://example.org/a%80b", "https://example.org/a%5Cb",
            "https://example.org:8443/photo", "https://example.org/a b", "https://example.org/a%40b", "https://example.org/a\u200Bb", "https://example.org/a%E2%80%8Bb",
            "https://example.org/a\uDB40\uDC01b", "https://example.org/a%F3%A0%80%81b", "https://example.org/a%25F3%25A0%2580%2581b",
            "https://example.org\\@example.com/photo", "javascript:alert(1)"})
    void unsafeOrPrivateLinksCannotBecomePublicCredits(String url) {
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.publicUrl(url,true)).as(url).hasMessageContaining("400 BAD_REQUEST");
    }
    @Test void nullableUrlsAndNoticesDoNotCopyPrivateSourceEvidence() {
        assertThat(OverturePhotoCreditPolicy.publicUrl(null,false)).isNull();
        assertThat(OverturePhotoCreditPolicy.publicUrl(" ",false)).isNull();
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.publicUrl(null,true)).hasMessageContaining("400 BAD_REQUEST");
        var request = new OverturePhotoCreditRequest.Save(0,0,"Title","Creator",null,"https://example.org/credit",LicenseCode.CC0_1_0,"No original edits.",null);
        assertThat(OverturePhotoCreditPolicy.validate(request).requiredNotices()).isNull();
    }
    @Test void multilineNoticesArePlainTextAndNormalizedWithoutLosingAttribution() {
        var request = new OverturePhotoCreditRequest.Save(0,0,"Title","Creator",null,"https://example.org/credit",LicenseCode.CC0_1_0,
                "First line\r\nSecond line\tvalue", "Copyright notice\rAdditional disclaimer");
        var valid = OverturePhotoCreditPolicy.validate(request);
        assertThat(valid.changesNotice()).isEqualTo("First line\nSecond line\tvalue");
        assertThat(valid.requiredNotices()).isEqualTo("Copyright notice\nAdditional disclaimer");
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.reason("short")).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.reason("Invalid\u0000 reason")).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.validate(new OverturePhotoCreditRequest.Save(0,0,"Title\nextra","Creator",null,
                "https://example.org/credit",LicenseCode.CC0_1_0,"No edits",null))).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.validate(new OverturePhotoCreditRequest.Save(0,0,"Title","Creator\u200B",null,
                "https://example.org/credit",LicenseCode.CC0_1_0,"No edits",null))).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.validate(new OverturePhotoCreditRequest.Save(0,0,"Title","Creator",null,
                "https://example.org/credit",LicenseCode.CC0_1_0,"No edits","Copyright\u202E hidden"))).hasMessageContaining("400 BAD_REQUEST");
    }
    @Test void publicPathAllowsOrdinaryEncodedSpacesUnicodeAndLiteralPercentButHasBoundedDepth() {
        for (String url : java.util.List.of("https://example.org/Photo%20exterior.jpg", "https://example.org/%E0%AE%A4.jpg", "https://example.org/100%25.jpg"))
            assertThat(OverturePhotoCreditPolicy.publicUrl(url,true)).isEqualTo(url);
        String nested = "%" + "25".repeat(18) + "0A";
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.publicUrl("https://example.org/" + nested,true)).hasMessageContaining("400 BAD_REQUEST");
    }
    @Test void supplementaryFormatCharactersAreRejectedInEveryPublicTextFieldAndReviewReason() {
        String hidden = new String(Character.toChars(0xE0001));
        assertThat(Character.getType(0xE0001)).isEqualTo(Character.FORMAT);
        for (var request : java.util.List.of(
                new OverturePhotoCreditRequest.Save(0,0,"Title"+hidden,"Creator",null,"https://example.org/credit",LicenseCode.CC_BY_4_0,"No edits",null),
                new OverturePhotoCreditRequest.Save(0,0,"Title","Creator"+hidden,null,"https://example.org/credit",LicenseCode.CC_BY_4_0,"No edits",null),
                new OverturePhotoCreditRequest.Save(0,0,"Title","Creator",null,"https://example.org/credit",LicenseCode.CC_BY_4_0,"No edits"+hidden,null),
                new OverturePhotoCreditRequest.Save(0,0,"Title","Creator",null,"https://example.org/credit",LicenseCode.CC_BY_4_0,"No edits","Copyright"+hidden)))
            assertThatThrownBy(() -> OverturePhotoCreditPolicy.validate(request)).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> OverturePhotoCreditPolicy.reason("Reviewed rights"+hidden)).hasMessageContaining("400 BAD_REQUEST");
    }
    @Test void declaredBoundsAndVersionsAreEnforced() {
        var baseline = save();
        for (var request : java.util.List.of(new OverturePhotoCreditRequest.Save(-1,0,"T","C",null,baseline.sourceUrl(),baseline.licenseCode(),"No edits",null),
                new OverturePhotoCreditRequest.Save(0,-1,"T","C",null,baseline.sourceUrl(),baseline.licenseCode(),"No edits",null),
                new OverturePhotoCreditRequest.Save(0,0,"x".repeat(201),"C",null,baseline.sourceUrl(),baseline.licenseCode(),"No edits",null),
                new OverturePhotoCreditRequest.Save(0,0,"T","x".repeat(301),null,baseline.sourceUrl(),baseline.licenseCode(),"No edits",null),
                new OverturePhotoCreditRequest.Save(0,0,"T","C",null,baseline.sourceUrl(),baseline.licenseCode(),"x".repeat(1001),null),
                new OverturePhotoCreditRequest.Save(0,0,"T","C",null,baseline.sourceUrl(),baseline.licenseCode(),"No edits","x".repeat(2001))))
            assertThatThrownBy(() -> OverturePhotoCreditPolicy.validate(request)).hasMessageContaining("400 BAD_REQUEST");
    }
}
