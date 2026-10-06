const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function compile(file, dependencies) {
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../", file), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  const mod = { exports: {} };
  new Function("require", "module", "exports", code)((name) => dependencies[name] ?? require(name), mod, mod.exports);
  return mod.exports;
}
const baseHall = { id: "71", name: "Discovered Venue", city: "Chennai", area: "Adyar", capacity: null, startingPrice: null, rating: null, reviewCount: 0, imageUrl: "", galleryUrls: [], venueType: "Event Venue", amenities: [], isVerified: false, availableThisMonth: false, description: "Verified venue description", reviews: [], listingOrigin: "APPLICATION", enquiryOnly: true, sourceRelease: "2026-09-23.0", sourceAttribution: [{ dataset: "Open Places", license: "CDLA Permissive 2.0", recordId: "abc" }] };
const nullComponent = () => null;
test("APPLICATION detail is request-dynamic and never claims verified service, ratings, calendar or amenities", async () => {
  const module = compile("app/(public)/halls/[id]/page.tsx", {
    "next/link": { default: ({ href, children }) => React.createElement("a", { href }, children) },
    "next/navigation": { notFound: () => { throw new Error("Not found"); } },
    "@/components/common/ShareButton": { ShareButton: nullComponent },
    "@/components/enquiries/EnquiryPanel": { EnquiryPanel: nullComponent },
    "@/components/customer/SaveHallButton": { SaveHallButton: nullComponent },
    "@/components/halls/HallAvailabilityCalendar": { HallAvailabilityCalendar: () => React.createElement("p", null, "LIVE CALENDAR") },
    "@/components/halls/HallPhotoGallery": { HallPhotoGallery: nullComponent },
    "@/components/layout/SiteHeader": { SiteHeader: nullComponent },
    "@/features/halls/hall-client": { getPublicHall: async () => baseHall },
    "@/lib/display-format": { formatGuestCount: String }
  });
  assert.equal(module.dynamic, "force-dynamic"); assert.equal(module.generateStaticParams, undefined);
  const markup = renderToStaticMarkup(await module.default({ params: Promise.resolve({ id: "71" }) }));
  assert.match(markup, /VenueMart-managed listing/); assert.match(markup, /Availability, pricing and arrangements are not confirmed/); assert.match(markup, /To be confirmed/); assert.match(markup, /Amenities have not been confirmed/); assert.match(markup, /CDLA Permissive 2.0/);
  for (const claim of [/Verified service/, /Verified customer reviews/, /LIVE CALENDAR/, /Air conditioning/, />Available</, /3 slots/, /INR/]) assert.doesNotMatch(markup, claim);
});
test("APPLICATION request form has no live availability or price claims; OWNER controls remain", () => {
  const slots = compile("features/halls/slot-model.ts", {});
  const { EnquiryPanel } = compile("components/enquiries/EnquiryPanel.tsx", {
    "next/navigation": { useRouter: () => ({ push() {} }) },
    "@/features/auth/AuthProvider": { useAuth: () => ({ user: { id: "1", fullName: "Customer" }, getValidAccessToken: async () => "customer" }) },
    "@/features/enquiries/enquiry-client": { createEnquiry: async () => { throw new Error("No live submission from render"); } },
    "@/features/halls/availability-client": { getPublicHallAvailability: async () => [] },
    "@/features/halls/slot-selection-events": { HALL_SLOT_SELECTION_EVENT: "qa-slot-selection" },
    "@/features/halls/slot-model": slots,
    "@/lib/display-format": { formatGuestCount: String }
  });
  const app = renderToStaticMarkup(React.createElement(EnquiryPanel, { hall: baseHall }));
  assert.match(app, /Request availability/); assert.match(app, /Request pricing/); assert.match(app, /VenueMart team enquiry/); assert.match(app, /not a booking or availability guarantee/); assert.doesNotMatch(app, /Check availability/); assert.doesNotMatch(app, /Starting from/); assert.doesNotMatch(app, /INR/);
  const owner = renderToStaticMarkup(React.createElement(EnquiryPanel, { hall: { ...baseHall, listingOrigin: "OWNER", startingPrice: 50000, capacity: 200 } }));
  assert.match(owner, /Check availability/); assert.match(owner, /Starting from/); assert.match(owner, /INR 50,000/); assert.match(owner, /Send enquiry/);
});
test("filled publication and team response forms retain stable explicit accessible names after conflicts", () => {
  const hooks = (state) => { let index = 0; return { ...React, useEffect() {}, useRef: (value) => ({ current: value }), useState: (initial) => [index in state ? state[index++] : (index++, typeof initial === "function" ? initial() : initial), () => {}] }; };
  const auth = { useAuth: () => ({ accessToken: "admin", user: { id: "1", role: "ADMIN" } }) };
  const detail = { ...baseHall, hallId: 71, publicationState: "UNPUBLISHED", publicationVersion: 2, reviewVersion: 3, mediaVersion: 4, approvedPhotoIds: [], coverMediaId: null, ready: false, blockers: [], history: [] };
  const publication = compile("components/admin/AdminOverturePublications.tsx", {
    react: hooks({ 4: detail, 5: "Preserved publication reason", 10: true }),
    "next/link": { default: ({ children }) => React.createElement("span", null, children) },
    "@/features/auth/AuthProvider": auth,
    "@/lib/api-client": { ApiError: class extends Error {} },
    "@/features/admin/overture-publication-client": {}
  });
  const publicationMarkup = renderToStaticMarkup(React.createElement(publication.AdminOverturePublications));
  assert.match(publicationMarkup, /<textarea aria-label="Audit reason"[^>]*>Preserved publication reason<\/textarea>/);
  const queue = compile("components/admin/AdminApplicationVenueEnquiries.tsx", {
    react: hooks({ 4: { id: "51", hallName: "Discovered Venue", status: "NEW", version: 2, publicationVersion: 3, eventDate: "2026-12-01", slot: "FULL_DAY", guestCount: 50, eventType: "Wedding" }, 5: "Preserved response", 6: "Preserved internal reason", 11: true }),
    "@/features/auth/AuthProvider": auth,
    "@/lib/api-client": { ApiError: class extends Error {} },
    "@/features/admin/application-enquiry-client": {},
    "@/features/enquiries/enquiry-display": compile("features/enquiries/enquiry-display.ts", {}),
    "@/features/halls/slot-model": compile("features/halls/slot-model.ts", {})
  });
  const queueMarkup = renderToStaticMarkup(React.createElement(queue.AdminApplicationVenueEnquiries));
  assert.match(queueMarkup, /<textarea aria-label="Customer-visible team response"[^>]*>Preserved response<\/textarea>/);
  assert.match(queueMarkup, /<textarea aria-label="Internal audit reason"[^>]*>Preserved internal reason<\/textarea>/);
});
test("mixed venue discovery explains enquiry-only team listings without blanket verified, pricing or availability claims", () => {
  const { HallDiscovery } = compile("components/halls/HallDiscovery.tsx", {
    "@/lib/display-format": { formatGuestCount: String, guestCapacityOptions: [100, 200] },
    "@/features/halls/hall-client": { searchPublicHalls: async () => ({ halls: [], total: 0, source: "api" }) },
    "./HallCard": { HallCard: () => null }
  });
  const markup = renderToStaticMarkup(React.createElement(HallDiscovery));
  assert.match(markup, /Availability and pricing need confirmation/);
  assert.match(markup, /VenueMart-managed listings accept availability enquiries only/);
  assert.match(markup, /Their pricing and dates are unconfirmed/);
  assert.match(markup, /our team handles the enquiry; it is not a confirmed booking/);
  assert.match(markup, /VenueMart-managed listings do not accept online bookings or payments/);
  for (const claim of [/Compare verified halls/, /Verified listings with transparent starting prices/, /live slot availability/, /Work with approved owners/, /confirm with confidence/]) assert.doesNotMatch(markup, claim);
});
