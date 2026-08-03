import type { Metadata } from "next";
import { LegalDocument } from "@/components/legal/LegalDocument";
import { APP_NAME } from "@/lib/constants";

export const metadata: Metadata = {
  title: "Privacy Policy"
};

const sections = [
  {
    title: "1. Scope",
    body: [
      `This Privacy Policy explains how ${APP_NAME} collects, uses, shares, stores, and protects personal data when you use our website, accounts, dashboards, enquiry flows, listing tools, media uploads, and review features.`,
      "This policy applies to customers, hall owners, vendors, admins, and visitors who use the platform."
    ]
  },
  {
    title: "2. Personal data we collect",
    body: [
      "Account data: full name, mobile number, email address, password hash, role, account status, and login/session information.",
      "Customer enquiry data: event type, event date, preferred slots, guest count, budget, location, notes, selected hall or vendor, and contact details needed to respond to the enquiry.",
      "Hall and vendor data: business names, owner or contact names, phone numbers, email addresses, addresses, service categories, prices, packages, amenities, availability, media, documents, and approval status.",
      "Reviews and activity: ratings, review text, moderation status, saved venues, notifications, booking or lead status, and audit records needed for platform safety.",
      "Technical data: IP address, browser/device information, pages visited, approximate usage timestamps, and local storage/cookie data used for login and product functionality.",
      "Location data: venue address, area, pincode, city, map pin, latitude/longitude, and user-provided event location where relevant."
    ]
  },
  {
    title: "3. Why we use personal data",
    body: [
      "To create and secure accounts, authenticate users, route users by role, and prevent unauthorised access.",
      "To publish approved halls and vendors, show discovery results, process enquiries, manage availability, support reviews, and run admin moderation.",
      "To help providers respond to customer enquiries and help customers track enquiry, booking, and review status.",
      "To detect abuse, investigate disputes, maintain audit logs, improve product quality, comply with law, and communicate important service updates."
    ]
  },
  {
    title: "4. Sharing",
    body: [
      "We share customer enquiry details with the hall owner or vendor selected by the customer so they can respond to the request.",
      "Approved public listing information, including business details, venue details, pricing, services, media, ratings, and reviews, may be visible to visitors and users.",
      "We may share data with hosting, storage, database, analytics, security, communication, payment, or support providers who help operate the platform.",
      "We may disclose information if required by law, court order, government request, fraud prevention, safety investigation, or to protect the rights of users and the platform."
    ]
  },
  {
    title: "5. Media and documents",
    body: [
      "Photos uploaded for hall or vendor portfolios may become public after approval or when shown in dashboards, discovery pages, or detail pages.",
      "Verification documents or internal review notes, where collected, are used for moderation and trust checks and are not intended for public display."
    ]
  },
  {
    title: "6. Cookies and local storage",
    body: [
      "We use browser storage and similar technologies to keep users signed in, remember session state, improve security, and support product features.",
      "You can clear browser storage from your device, but doing so may sign you out or reset local preferences."
    ]
  },
  {
    title: "7. Retention",
    body: [
      "We keep personal data only as long as needed for account operation, enquiry handling, listing management, reviews, security, audit, legal compliance, and dispute resolution.",
      "When data is no longer needed, we delete, anonymise, or archive it according to operational and legal requirements."
    ]
  },
  {
    title: "8. Security",
    body: [
      "We use reasonable technical and organisational safeguards to protect personal data, including authenticated APIs, role-based access, protected media upload flows, and controlled admin access.",
      "No internet service is completely secure. Please use a strong password and contact us promptly if you believe your account or data has been misused."
    ]
  },
  {
    title: "9. Your choices and rights",
    body: [
      "You may request access, correction, update, or deletion of your personal data, subject to identity verification and legal or operational limits.",
      "You may withdraw consent where processing is based on consent. Some features may stop working if the data required to provide that feature is deleted or consent is withdrawn.",
      "You may raise privacy concerns or complaints by contacting admin@bookvenuemart.in."
    ]
  },
  {
    title: "10. Children",
    body: [
      "The platform is intended for users who can lawfully create accounts and enter into service discussions. Users under 18 should use the platform only with parent or guardian involvement."
    ]
  },
  {
    title: "11. Updates and contact",
    body: [
      "We may update this Privacy Policy as the product, law, or operational practices change. The latest version will be posted on this page.",
      "For privacy questions, data requests, or complaints, contact admin@bookvenuemart.in."
    ]
  }
];

export default function PrivacyPage() {
  return (
    <LegalDocument
      description={`This policy explains what personal data ${APP_NAME} collects, why we use it, who we share it with, and how users can contact us.`}
      sections={sections}
      title="Privacy Policy"
    />
  );
}
