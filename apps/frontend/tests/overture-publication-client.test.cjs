const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
function load(file, responder = async () => ({})) {
  const calls = []; const mod = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../", file), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  new Function("require", "module", "exports", code)((name) => {
    if (name === "@/features/enquiries/enquiry-client") return { toStoredEnquiry: (value) => value };
    assert.equal(name, "@/lib/api-client"); return { apiRequest: (url, options) => { calls.push({ url, options }); return responder(url, options); } };
  }, mod, mod.exports);
  return { client: mod.exports, calls };
}
const detail = { hallId: 17, publicationVersion: 2, reviewVersion: 4, mediaVersion: 9 };
test("publication requires authentication on every read and write", async () => {
  const { client, calls } = load("features/admin/overture-publication-client.ts");
  for (const token of [null, undefined, "", "  "]) for (const operation of [() => client.getPublicationList(0, token), () => client.getPublication(17, token), () => client.publishApplicationVenue(detail, "Audit reason", token), () => client.unpublishApplicationVenue(detail, "Audit reason", token)]) assert.throws(operation, /Sign in as an admin/);
  assert.equal(calls.length, 0);
});
test("publication uses exact independent CAS versions and uncached authenticated routes", async () => {
  const { client, calls } = load("features/admin/overture-publication-client.ts"); const signal = new AbortController().signal;
  await client.getPublicationList(2, "admin", signal); await client.getPublication(17, "admin", signal);
  await client.publishApplicationVenue(detail, "  Fact and photo checks done  ", "admin"); await client.unpublishApplicationVenue(detail, "  Withdraw pending verification  ", "admin");
  assert.deepEqual(calls.map((c) => ({ url: c.url, method: c.options.method, body: c.options.body ? JSON.parse(c.options.body) : null })), [
    { url: "/admin/overture-onboarding/publications?page=2&size=20", method: undefined, body: null },
    { url: "/admin/overture-onboarding/publications/17", method: undefined, body: null },
    { url: "/admin/overture-onboarding/publications/17/publish", method: "POST", body: { expectedPublicationVersion: 2, expectedReviewVersion: 4, expectedMediaVersion: 9, reason: "Fact and photo checks done" } },
    { url: "/admin/overture-onboarding/publications/17/unpublish", method: "POST", body: { expectedPublicationVersion: 2, reason: "Withdraw pending verification" } }
  ]);
  assert.ok(calls.every((c) => c.options.token === "admin" && c.options.cache === "no-store")); assert.equal(calls[0].options.signal, signal);
});
test("publication errors propagate without retry or changing versions", async () => {
  const failure = new Error("Conflict"); const { client, calls } = load("features/admin/overture-publication-client.ts", async () => { throw failure; });
  await assert.rejects(() => client.publishApplicationVenue(detail, "Publish after review", "admin"), (e) => e === failure); assert.equal(calls.length, 1); assert.equal(JSON.parse(calls[0].options.body).expectedPublicationVersion, 2);
});
test("VenueMart admin queue is scoped, authenticated and uncached", async () => {
  const response = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }; const { client, calls } = load("features/admin/application-enquiry-client.ts", async () => response);
  await assert.rejects(() => client.getApplicationEnquiries(0, "ALL"), /Sign in as an admin/);
  await client.getApplicationEnquiries(0, "ALL", "admin"); await client.getApplicationEnquiries(1, "CONTACTED", "admin");
  assert.equal(calls[0].url, "/admin/application-venue-enquiries?page=0&size=20"); assert.equal(calls[1].url, "/admin/application-venue-enquiries?page=1&size=20&status=CONTACTED");
  assert.ok(calls.every((c) => c.options.token === "admin" && c.options.cache === "no-store"));
});
test("team enquiries only advance NEW to CONTACTED then CLOSED with exact CAS payload", async () => {
  const { client, calls } = load("features/admin/application-enquiry-client.ts", async (_, options) => ({ routingTarget: "VENUEMART", version: 8, status: JSON.parse(options.body).status }));
  const enquiry = { id: "51", version: 7, routingTarget: "VENUEMART", status: "NEW" };
  await client.updateApplicationEnquiry(enquiry, "  Team contacted you  ", "  Checked customer request  ", "admin");
  await client.updateApplicationEnquiry({ ...enquiry, status: "CONTACTED" }, "Request closed", "Customer asked to close", "admin");
  await assert.rejects(() => client.updateApplicationEnquiry({ ...enquiry, status: "CLOSED" }, "Reopen", "No reopening allowed", "admin"), /cannot be reopened/);
  assert.deepEqual(JSON.parse(calls[0].options.body), { expectedVersion: 7, status: "CONTACTED", responseMessage: "Team contacted you", reason: "Checked customer request" }); assert.equal(JSON.parse(calls[1].options.body).status, "CLOSED"); assert.equal(calls.length, 2);
});
test("team queue rejects owner records and mutation errors without local success", async () => {
  const bad = load("features/admin/application-enquiry-client.ts", async () => ({ items: [{ routingTarget: "OWNER", status: "NEW", version: 0 }] }));
  await assert.rejects(() => bad.client.getApplicationEnquiries(0, "ALL", "admin"), /invalid VenueMart enquiry/);
  const failure = new Error("Version changed"); const { client, calls } = load("features/admin/application-enquiry-client.ts", async () => { throw failure; });
  await assert.rejects(() => client.updateApplicationEnquiry({ id: "51", version: 7, status: "NEW" }, "Contacted", "Checked customer request", "admin"), (e) => e === failure); assert.equal(calls.length, 1);
});
