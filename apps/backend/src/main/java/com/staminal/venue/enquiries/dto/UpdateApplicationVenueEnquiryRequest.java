package com.staminal.venue.enquiries.dto;

import java.io.IOException;
import java.util.Set;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.staminal.venue.enums.EnquiryStatus;

@JsonDeserialize(using = UpdateApplicationVenueEnquiryRequest.Deserializer.class)
public record UpdateApplicationVenueEnquiryRequest(long expectedVersion, EnquiryStatus status,
        String responseMessage, String reason) {
    public UpdateApplicationVenueEnquiryRequest validated() {
        if (expectedVersion < 0 || (status != EnquiryStatus.CONTACTED && status != EnquiryStatus.CLOSED)) invalid();
        return new UpdateApplicationVenueEnquiryRequest(expectedVersion, status,
                bounded(responseMessage, 1, 2000), bounded(reason, 10, 1000));
    }

    public static final class Deserializer extends JsonDeserializer<UpdateApplicationVenueEnquiryRequest> {
        private static final Set<String> FIELDS = Set.of("expectedVersion", "status", "responseMessage", "reason");
        @Override public UpdateApplicationVenueEnquiryRequest getNullValue(DeserializationContext context)
                throws JsonMappingException {
            // Jackson bypasses deserialize() for a JSON null literal.
            throw JsonMappingException.from(context.getParser(), "VenueMart enquiry update must be an object");
        }
        @Override public UpdateApplicationVenueEnquiryRequest deserialize(JsonParser parser,
                DeserializationContext context) throws IOException {
            try {
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
                JsonNode root = parser.getCodec().readTree(parser);
                if (root == null || !root.isObject() || root.size() != FIELDS.size()) invalid();
                root.fieldNames().forEachRemaining(field -> { if (!FIELDS.contains(field)) invalid(); });
                JsonNode version = root.get("expectedVersion");
                if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0) invalid();
                var request = new UpdateApplicationVenueEnquiryRequest(version.longValue(),
                        EnquiryStatus.valueOf(text(root.get("status"))), text(root.get("responseMessage")), text(root.get("reason"))).validated();
                if (parser.nextToken() != null) invalid();
                return request;
            } catch (IllegalArgumentException exception) {
                throw JsonMappingException.from(parser, "Invalid VenueMart enquiry update");
            }
        }
    }
    private static String text(JsonNode value) {
        if (value == null || !value.isTextual()) invalid();
        return value.textValue();
    }
    private static String bounded(String value, int min, int max) {
        if (value == null) invalid();
        String trimmed = value.trim();
        if (trimmed.length() < min || trimmed.length() > max || trimmed.indexOf('\0') >= 0) invalid();
        return trimmed;
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid VenueMart enquiry update"); }
}
