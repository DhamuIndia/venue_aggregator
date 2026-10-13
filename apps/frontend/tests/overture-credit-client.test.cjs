const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
function load(responder = async () => ({ mediaVersion: 4, items: [] })) {
  const calls = []; const mod = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../features/admin/overture-media-client.ts"), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  new Function("require", "module", "exports", code)((name) => { assert.equal(name, "@/lib/api-client"); return { apiRequest: (url, options) => { calls.push({ url, options }); return responder(url, options); } }; }, mod, mod.exports);
  return { client: mod.exports, calls };
}
const request = { expectedVersion: 3, expectedCreditVersion: 1, title: "Venue exterior", creator: "Example photographer", creatorUrl: null, sourceUrl: "https://photos.example.org/venue", licenseCode: "CC_BY_4_0", changesNotice: "No prior changes reported.", requiredNotices: null };
test("photo credit save and review require admin auth and do not fabricate or retry", async () => {
  const { client, calls } = load();
  for (const token of [undefined, null, "", "  "]) {
    await assert.rejects(() => client.saveOverturePhotoCredit(7, 9, request, token), /sign in with an admin account/i);
    await assert.rejects(() => client.reviewOverturePhotoCredit(7, 9, 3, 1, "APPROVED", "Checked attribution", true, true, token), /sign in with an admin account/i);
  }
  assert.equal(calls.length, 0);
  const failure = new Error("Credit version conflict"); const bad = load(async () => { throw failure; });
  await assert.rejects(() => bad.client.saveOverturePhotoCredit(7, 9, request, "admin"), (e) => e === failure); assert.equal(bad.calls.length, 1);
});
test("photo credits use exact uncached CAS payloads and explicit review confirmations", async () => {
  const { client, calls } = load(); await client.saveOverturePhotoCredit(7, 9, request, "admin");
  await client.reviewOverturePhotoCredit(7, 9, 4, 2, "APPROVED", "Rights and credits checked", true, true, "admin");
  await client.reviewOverturePhotoCredit(7, 9, 4, 2, "REJECTED", "Missing attribution checked", false, false, "admin");
  assert.deepEqual(calls.map(({ url, options }) => ({ url, method: options.method, body: JSON.parse(options.body) })), [
    { url: "/admin/overture-onboarding/drafts/7/media/9/credits", method: "PUT", body: request },
    { url: "/admin/overture-onboarding/drafts/7/media/9/credits/review", method: "POST", body: { expectedVersion: 4, expectedCreditVersion: 2, status: "APPROVED", reason: "Rights and credits checked", rightsConfirmed: true, attributionConfirmed: true } },
    { url: "/admin/overture-onboarding/drafts/7/media/9/credits/review", method: "POST", body: { expectedVersion: 4, expectedCreditVersion: 2, status: "REJECTED", reason: "Missing attribution checked", rightsConfirmed: false, attributionConfirmed: false } }
  ]); assert.ok(calls.every(({ options }) => options.token === "admin" && options.cache === "no-store"));
});
