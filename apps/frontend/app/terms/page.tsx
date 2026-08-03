import type { Metadata } from "next";
import { LegalDocument } from "@/components/legal/LegalDocument";
import { APP_NAME } from "@/lib/constants";

export const metadata: Metadata = {
  title: "Terms"
};

const sections = [
  {
    title: "1. Acceptance",
    body: [
      `These Terms govern your access to and use of ${APP_NAME}, including venue discovery, vendor discovery, enquiries, reviews, owner dashboards, vendor workspaces, and admin workflows.`,
      `By creating an account, submitting a listing, sending an enquiry, uploading media, or using the service, you agree to these Terms. If you use ${APP_NAME} for a business, you confirm that you are authorised to act for that business.`
    ]
  },
  {
    title: "2. Platform role",
    body: [
      `${APP_NAME} is a marketplace and discovery platform. We help customers find halls and event service providers, and we help owners and vendors receive enquiries.`,
      "Unless expressly stated otherwise, we are not a party to the offline contract between a customer and a hall owner or vendor. Pricing, availability, service quality, cancellation terms, refunds, taxes, permits, and event execution are handled between the customer and the relevant provider."
    ]
  },
  {
    title: "3. Accounts",
    body: [
      "You must provide accurate account information, keep your login credentials secure, and tell us promptly if you suspect unauthorised access.",
      "You are responsible for actions taken through your account. You must not create fake accounts, impersonate another person or business, or use another user's account without permission."
    ]
  },
  {
    title: "4. Listings, media, and reviews",
    body: [
      "Hall owners and vendors are responsible for the accuracy of business names, addresses, capacity, pricing, packages, amenities, services, media, and availability submitted to the platform.",
      "You must own, have permission to use, or have the required rights for any photos, logos, documents, descriptions, or other content you upload.",
      "Reviews must be based on genuine experiences. We may remove reviews that are fake, abusive, irrelevant, promotional, discriminatory, defamatory, or otherwise harmful to the platform."
    ]
  },
  {
    title: "5. Enquiries and communication",
    body: [
      "Customers may submit event details such as event type, date, preferred slots, guest count, location, budget, notes, and contact information.",
      "Providers must use enquiry information only to respond to that customer request and must not misuse contact details for spam, harassment, unauthorised marketing, or unrelated sales."
    ]
  },
  {
    title: "6. Verification and moderation",
    body: [
      "We may review, approve, reject, suspend, or remove listings, vendors, reviews, media, enquiries, or accounts to protect customers and maintain marketplace quality.",
      "Approval, verification, or a badge on the platform does not guarantee legal compliance, service performance, safety, availability, or future behaviour of any provider."
    ]
  },
  {
    title: "7. Payments",
    body: [
      "The current version of the platform may allow enquiry and booking management without processing online payments. Where payments are handled offline, users and providers are responsible for receipts, taxes, cancellations, and refunds.",
      "If online payments are introduced later, additional payment terms may apply before those features are used."
    ]
  },
  {
    title: "8. Prohibited use",
    body: [
      "You must not use the platform for unlawful activity, fraudulent listings, fake reviews, misleading pricing, abusive conduct, scraping, reverse engineering, security attacks, malware, or activity that disrupts the platform.",
      "You must not upload content that violates another person's rights or contains illegal, obscene, hateful, violent, or harmful material."
    ]
  },
  {
    title: "9. Availability and changes",
    body: [
      "We aim to keep the platform available, but we do not guarantee uninterrupted service. We may change, suspend, or discontinue features when needed for maintenance, security, legal, or product reasons.",
      "We may update these Terms. Material changes will be reflected on this page with a new last updated date."
    ]
  },
  {
    title: "10. Liability",
    body: [
      "To the maximum extent permitted by law, the platform is provided on an as-is and as-available basis. We are not responsible for indirect, incidental, special, consequential, or punitive damages arising from your use of the platform.",
      "Nothing in these Terms limits rights that cannot be limited under applicable law."
    ]
  },
  {
    title: "11. Governing law and contact",
    body: [
      "These Terms are governed by the laws of India. Courts in Chennai, Tamil Nadu will have jurisdiction unless applicable law requires otherwise.",
      "For questions, account issues, or legal notices, contact admin@bookvenuemart.in."
    ]
  }
];

export default function TermsPage() {
  return (
    <LegalDocument
      description={`Please read these terms before using ${APP_NAME} as a customer, hall owner, vendor, or admin.`}
      sections={sections}
      title="Terms of Use"
    />
  );
}
