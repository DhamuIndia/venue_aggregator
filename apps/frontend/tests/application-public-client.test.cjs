const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
function loadClient(file, responder = async () => null, env = {}, localRecords = []) {
  const calls = []; const writes = []; const mod = { exports: {} };
  const mock = { id: "mock-id", name: "Mock Palace", city: "Mock City", area: "Mock Area", capacity: 999, startingPrice: 999999, rating: 4.9, reviewCount: 100, imageUrl: "https://mock.invalid/photo.jpg", galleryUrls: ["https://mock.invalid/extra.jpg"], amenities: ["Mock amenity"], venueType: "Marriage Hall", isVerified: true, availableThisMonth: true, description: "Mock description" };
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../", file), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  new Function("require", "module", "exports", "process", "window", code)((name) => {
    if (name === "@/lib/api-client") return { ApiError, API_BASE_URL: "https://api.qa.invalid/api/v1", apiRequest: (url, options) => { calls.push({ url, options }); return responder(url, options); } };
    if (name === "@/lib/display-format") return { toTitleCase: (value) => value };
    if (name === "./mock-data") return { halls: [mock], getHallById: () => mock };
    if (name === "./photo-credit") return loadClient("features/halls/photo-credit.ts").client;
    throw new Error(`Unexpected dependency ${name}`);
  }, mod, mod.exports, { env }, { localStorage: { getItem: () => JSON.stringify(localRecords), setItem: (...args) => writes.push(args) } });
  return { client: mod.exports, calls, writes };
}
const hall = { id: 71, name: "Discovered Venue", listingOrigin: "APPLICATION", venueType: "event_venue", publicationVersion: 3, sourceAttribution: [{ dataset: "Open Places", license: "CDLA Permissive 2.0", recordId: "abc" }], sourceRelease: "2026-09-23.0" };
test("APPLICATION records never invent prices, ratings, photos, amenities or availability from mocks", () => {
  const { client } = loadClient("features/halls/hall-client.ts"); const result = client.toHallSummary({ ...hall, startingPrice: 123, rating: 4.9, reviewCount: 80, verified: true, availableThisMonth: true });
  assert.equal(result.capacity, null); assert.equal(result.startingPrice, null); assert.equal(result.rating, null); assert.equal(result.reviewCount, 0); assert.equal(result.imageUrl, ""); assert.deepEqual(result.galleryUrls, []); assert.deepEqual(result.amenities, []); assert.deepEqual(result.reviews, []); assert.equal(result.isVerified, false); assert.equal(result.availableThisMonth, false); assert.equal(result.city, ""); assert.equal(result.area, ""); assert.equal(result.enquiryRoutingTarget, "VENUEMART"); assert.equal(result.enquiryOnly, true);
});
test("APPLICATION photos use API origin with version and source/category stay truthful", () => {
  const { client } = loadClient("features/halls/hall-client.ts"); const result = client.toHallSummary({ ...hall, venueType: "exhibition_and_trade_fair_venue", coverImageUrl: "/api/v1/halls/71/application-photos/6?publicationVersion=3", galleryUrls: ["/api/v1/halls/71/application-photos/6?publicationVersion=3", "/api/v1/halls/71/application-photos/7?publicationVersion=3"] });
  assert.equal(result.venueType, "Exhibition and Trade Fair Venue"); assert.equal(result.imageUrl, "https://api.qa.invalid/api/v1/halls/71/application-photos/6?publicationVersion=3"); assert.equal(result.galleryUrls.length, 2); assert.equal(result.publicationVersion, 3); assert.deepEqual(result.sourceAttribution, hall.sourceAttribution);
});
const publicCredit = { title: "Venue exterior", creator: "Photo creator", creatorUrl: null, sourceUrl: "https://commons.wikimedia.org/wiki/File:Venue.jpg", licenseCode: "CC_BY_4_0", licenseLabel: "CC BY 4.0", licenseUrl: "https://creativecommons.org/licenses/by/4.0/", changesNotice: "No prior changes reported.", processingNotice: "VenueMart normalized this image to JPEG and may have resized it.", requiredNotices: "Copyright Photo creator" };
const photoUrl = (id = 6, version = 3) => `/api/v1/halls/71/application-photos/${id}?publicationVersion=${version}`;
test("licensed public photos expose only reviewed credit fields and matching publication-revision URLs", () => {
  const { client } = loadClient("features/halls/hall-client.ts");
  const result = client.toHallSummary({ ...hall, coverImageUrl: photoUrl(), applicationPhotos: [{ photoId: 6, url: photoUrl(), requiresCredit: true, credit: { ...publicCredit, reviewReason: "PRIVATE REASON", permissionEvidence: "PRIVATE EVIDENCE", changedBy: { adminName: "Private actor" } } }, { photoId: 7, url: photoUrl(7), requiresCredit: false, credit: null }] });
  assert.equal(result.imageUrl, "https://api.qa.invalid" + photoUrl()); assert.equal(result.applicationPhotos.length, 2); assert.deepEqual(result.applicationPhotos[0].credit, publicCredit); assert.equal(result.applicationPhotos[1].credit, null);
  assert.doesNotMatch(JSON.stringify(result.applicationPhotos), /PRIVATE|Private actor|reviewReason|permissionEvidence|changedBy/);
});
test("missing, unsupported, unsafe or malformed required public credits suppress images without gallery fallback", () => {
  const { client } = loadClient("features/halls/hall-client.ts");
  for (const credit of [null, {}, { ...publicCredit, creator: "" }, { ...publicCredit, licenseCode: "CC_BY_SA_4_0" }, { ...publicCredit, licenseUrl: "https://example.org/wrong" }, { ...publicCredit, sourceUrl: "https://photos.example.org/a?private=token" }, { ...publicCredit, creatorUrl: "https://user@example.org/a" }, { ...publicCredit, requiredNotices: "Notice\u202e" }, { ...publicCredit, processingNotice: "" }]) {
    const result = client.toHallSummary({ ...hall, coverImageUrl: photoUrl(), galleryUrls: [photoUrl()], applicationPhotos: [{ photoId: 6, url: photoUrl(), requiresCredit: true, credit }] });
    assert.equal(result.imageUrl, ""); assert.deepEqual(result.galleryUrls, []); assert.deepEqual(result.applicationPhotos, []);
  }
  for (const value of [{}, "malformed", [{ photoId: 6, url: photoUrl(6, 2), requiresCredit: true, credit: publicCredit }], [{ photoId: 6, url: "https://example.org/photo.jpg", requiresCredit: true, credit: publicCredit }], [{ photoId: 6, url: photoUrl(), requiresCredit: "true", credit: publicCredit }], [{ photoId: 6, url: photoUrl(), requiresCredit: false, credit: publicCredit }]]) {
    const result = client.toHallSummary({ ...hall, coverImageUrl: photoUrl(), galleryUrls: [photoUrl()], applicationPhotos: value }); assert.equal(result.imageUrl, ""); assert.deepEqual(result.galleryUrls, []);
  }
});
test("CC0 canonical credits are accepted while legacy team and OWNER photos remain unchanged", () => {
  const { client } = loadClient("features/halls/hall-client.ts");
  const credit = { ...publicCredit, licenseCode: "CC0_1_0", licenseLabel: "CC0 1.0", licenseUrl: "https://creativecommons.org/publicdomain/zero/1.0/", requiredNotices: null };
  assert.deepEqual(client.toHallSummary({ ...hall, coverImageUrl: photoUrl(), applicationPhotos: [{ photoId: 6, url: photoUrl(), requiresCredit: true, credit }] }).applicationPhotos[0].credit, credit);
  for (const legacy of [undefined, null]) { const result = client.toHallSummary({ ...hall, coverImageUrl: photoUrl(), applicationPhotos: legacy }); assert.equal(result.imageUrl, "https://api.qa.invalid" + photoUrl()); assert.equal(result.applicationPhotos, undefined); }
  const owner = client.toHallSummary({ ...hall, listingOrigin: "OWNER", coverImageUrl: "https://owner.example.org/original.jpg", applicationPhotos: [{ malformed: true }] }); assert.equal(owner.imageUrl, "https://owner.example.org/original.jpg"); assert.equal(owner.applicationPhotos, undefined);
});
test("public API errors fail closed rather than fabricating listings; explicit mock mode still works", async () => {
  const { client, calls } = loadClient("features/halls/hall-client.ts", async () => { throw new ApiError(404, "Unpublished"); });
  assert.equal(await client.getPublicHall("71"), undefined); assert.deepEqual(await client.searchPublicHalls(), { halls: [], total: 0, source: "api" }); assert.ok(calls.every((c) => c.options.cache === "no-store"));
  const demo = loadClient("features/halls/hall-client.ts", undefined, { NEXT_PUBLIC_HALLS_MODE: "mock" }); assert.equal((await demo.client.getPublicHall("mock-id")).name, "Mock Palace"); assert.equal(demo.calls.length, 0);
});
const payload = { hallId: "71", hallName: "Discovered Venue", customerId: "1", eventDate: "2026-12-01", eventType: "Wedding", guestCount: 50, slot: "FULL_DAY", routingTarget: "VENUEMART", notes: "Private customer request" };
const receipt = { ...payload, id: 51, status: "NEW", submittedAt: "2026-10-06T00:00:00Z", version: 0, publicationVersion: 3 };
test("APPLICATION enquiry submission requires real auth and refuses explicit mocks/local creation", async () => {
  const { client, calls, writes } = loadClient("features/enquiries/enquiry-client.ts"); await assert.rejects(() => client.createEnquiry(payload), /Sign in/); assert.throws(() => client.createLocalEnquiry(payload), /server-confirmed/); assert.equal(calls.length, 0); assert.equal(writes.length, 0);
  const demo = loadClient("features/enquiries/enquiry-client.ts", undefined, { NEXT_PUBLIC_ENQUIRIES_MODE: "mock" }); await assert.rejects(() => demo.client.createEnquiry(payload, "customer"), /real request/);
});
test("APPLICATION enquiry network/server errors are propagated once without synthetic success or PII cache", async () => {
  for (const failure of [new ApiError(500, "Server unavailable"), new ApiError(409, "Unpublished"), new Error("Network unavailable")]) {
    const { client, calls, writes } = loadClient("features/enquiries/enquiry-client.ts", async () => { throw failure; }); await assert.rejects(() => client.createEnquiry(payload, "customer"), (e) => e === failure); assert.equal(calls.length, 1); assert.equal(writes.length, 0);
  }
});
test("APPLICATION enquiry receipt must be server-confirmed and team-routed; never fill missing ID from fallback", async () => {
  for (const response of [null, {}, { ...receipt, id: undefined }, { ...receipt, routingTarget: "OWNER" }, { ...receipt, publicationVersion: undefined }, { ...receipt, status: "CONFIRMED" }]) {
    const { client, writes } = loadClient("features/enquiries/enquiry-client.ts", async () => response); await assert.rejects(() => client.createEnquiry(payload, "customer"), /server did not confirm/); assert.equal(writes.length, 0);
  }
});
test("team receipt succeeds only from API and routing marker is never trusted in request body or persisted", async () => {
  const { client, calls, writes } = loadClient("features/enquiries/enquiry-client.ts", async () => receipt); const result = await client.createEnquiry(payload, "customer");
  assert.equal(result.id, "51"); assert.equal(result.routingTarget, "VENUEMART"); assert.equal(result.status, "NEW"); assert.equal(result.publicationVersion, 3); assert.equal(writes.length, 0); assert.equal(calls[0].url, "/public/enquiries"); assert.equal(JSON.parse(calls[0].options.body).routingTarget, undefined);
});
test("team CONTACTED/CLOSED stay enquiry statuses, while legacy owner statuses preserve normalization", () => {
  const { client } = loadClient("features/enquiries/enquiry-client.ts");
  for (const status of ["CONTACTED", "CLOSED"]) assert.equal(client.toStoredEnquiry({ ...receipt, status }).status, status);
  assert.equal(client.toStoredEnquiry({ ...receipt, routingTarget: "OWNER", status: "CONTACTED" }).status, "PENDING_OWNER_RESPONSE"); assert.equal(client.toStoredEnquiry({ ...receipt, routingTarget: "OWNER", status: "CLOSED" }).status, "COMPLETED");
});
test("stale local team records cannot manufacture confirmation or customer history after API failure", async () => {
  const owner = { ...receipt, id: "owner-21", routingTarget: "OWNER", status: "PENDING_OWNER_RESPONSE" };
  const { client, writes } = loadClient("features/enquiries/enquiry-client.ts", async () => { throw new Error("API unavailable"); }, {}, [receipt, owner]);
  assert.equal(client.getLocalEnquiry(51), undefined); assert.equal(client.getLocalEnquiry("51"), undefined);
  assert.deepEqual(client.getLocalEnquiries(), [owner]);
  assert.equal(await client.getEnquiry("51", "customer"), undefined);
  assert.deepEqual((await client.getCustomerEnquiries("customer")).enquiries, [owner]); assert.equal(writes.length, 0);
});
