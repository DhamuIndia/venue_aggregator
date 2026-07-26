package com.staminal.venue.notifications.queue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.preferences.VendorNotificationPreference;
import com.staminal.venue.notifications.preferences.VendorNotificationPreferenceRepository;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.vendors.Entity.Vendors;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeadNotificationQueueService {

    static final String NEW_MATCHING_LEAD_TEMPLATE_KEY = "NEW_MATCHING_LEAD";
    static final String DEFAULT_TEMPLATE_LANGUAGE = "en";

    private static final DateTimeFormatter EVENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

    private final VendorLeadRepository vendorLeadRepository;
    private final VendorNotificationPreferenceRepository preferenceRepository;
    private final LeadNotificationJobRepository notificationJobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeadNotificationQueueOutcome enqueueMarketplaceLead(Long vendorLeadId) {
        if (vendorLeadId == null) {
            return LeadNotificationQueueOutcome.SKIPPED_LEAD_NOT_FOUND;
        }

        VendorLead lead = vendorLeadRepository.findById(vendorLeadId).orElse(null);
        if (lead == null) {
            return LeadNotificationQueueOutcome.SKIPPED_LEAD_NOT_FOUND;
        }
        if (lead.getRequirement() == null) {
            return LeadNotificationQueueOutcome.SKIPPED_DIRECT_LEAD;
        }

        VendorNotificationPreference preference = preferenceRepository
                .findByVendor_Id(lead.getVendor().getId())
                .orElse(null);
        if (preference == null || !preference.isWhatsAppLeadNotificationsEnabled()) {
            return LeadNotificationQueueOutcome.SKIPPED_NOT_SUBSCRIBED;
        }
        if (preference.isWhatsAppLeadNotificationsPaused()) {
            return LeadNotificationQueueOutcome.SKIPPED_PAUSED;
        }
        if (!hasValidConsent(preference)) {
            return LeadNotificationQueueOutcome.SKIPPED_INVALID_CONSENT;
        }

        NotificationChannel channel = NotificationChannel.WHATSAPP;
        LeadNotificationType notificationType = LeadNotificationType.LEAD_MATCHED;
        if (notificationJobRepository.existsByVendorLead_IdAndChannelAndNotificationType(
                lead.getId(),
                channel,
                notificationType)) {
            return LeadNotificationQueueOutcome.SKIPPED_DUPLICATE;
        }

        notificationJobRepository.save(toJob(lead, preference, channel, notificationType));
        return LeadNotificationQueueOutcome.QUEUED;
    }

    private boolean hasValidConsent(VendorNotificationPreference preference) {
        return hasText(preference.getWhatsAppNumber())
                && preference.getWhatsAppConsentedAt() != null
                && preference.getWhatsAppConsentSource() != null
                && preference.getWhatsAppOptedOutAt() == null;
    }

    private LeadNotificationJob toJob(
            VendorLead lead,
            VendorNotificationPreference preference,
            NotificationChannel channel,
            LeadNotificationType notificationType) {

        CustomerRequirement requirement = lead.getRequirement();
        Vendors vendor = lead.getVendor();

        LeadNotificationJob job = new LeadNotificationJob();
        job.setVendorLead(lead);
        job.setVendor(vendor);
        job.setRequirement(requirement);
        job.setChannel(channel);
        job.setNotificationType(notificationType);
        job.setStatus(LeadNotificationJobStatus.QUEUED);
        job.setDestination(preference.getWhatsAppNumber());
        job.setTemplateKey(NEW_MATCHING_LEAD_TEMPLATE_KEY);
        job.setTemplateLanguage(DEFAULT_TEMPLATE_LANGUAGE);
        job.setLeadReference(lead.getPublicReference());
        job.setVendorName(firstText(vendor.getBusinessName(), vendor.getVendorName(), "VenueMart vendor"));
        job.setServiceText(firstText(lead.getService(), "Requested service"));
        job.setEventTypeText(firstText(lead.getEventType(), requirement.getEventType(), "Event"));
        job.setEventDateText(requirement.getEventDate().format(EVENT_DATE_FORMAT));
        job.setLocationText(firstText(lead.getLocation(), requirement.getLocation(), "Location not specified"));
        job.setBudgetText(budgetText(requirement));
        return job;
    }

    private String budgetText(CustomerRequirement requirement) {
        BigDecimal minimum = requirement.getBudgetMin();
        BigDecimal maximum = requirement.getBudgetMax();

        if (minimum != null && maximum != null) {
            if (minimum.compareTo(maximum) == 0) {
                return formatMoney(minimum);
            }
            return formatMoney(minimum) + "–" + formatMoney(maximum);
        }
        if (minimum != null) {
            return "From " + formatMoney(minimum);
        }
        if (maximum != null) {
            return "Up to " + formatMoney(maximum);
        }
        return "Not specified";
    }

    private String formatMoney(BigDecimal amount) {
        String digits = amount
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger()
                .toString();
        if (digits.length() <= 3) {
            return "₹" + digits;
        }

        String lastThreeDigits = digits.substring(digits.length() - 3);
        String leadingDigits = digits.substring(0, digits.length() - 3)
                .replaceAll("\\B(?=(\\d{2})+(?!\\d))", ",");
        return "₹" + leadingDigits + "," + lastThreeDigits;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
