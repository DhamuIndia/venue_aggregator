const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
function load(file) {
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../", file), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText; const mod = { exports: {} };
  new Function("require", "module", "exports", code)((name) => { assert.equal(name, "@/features/halls/photo-credit"); return load("features/halls/photo-credit.ts"); }, mod, mod.exports); return mod.exports;
}
const client = load("features/admin/overture-credit-form.ts");
const form = { title: "Venue exterior", creator: "Example photographer", creatorUrl: "", sourceUrl: "https://photos.example.org/venue", licenseCode: "CC_BY_4_0", changesNotice: "No prior changes reported.", requiredNotices: "" };
test("new credit forms are entirely blank and never derive private evidence or source references", () => {
  assert.deepEqual(client.overtureCreditForm(), { title: "", creator: "", creatorUrl: "", sourceUrl: "", licenseCode: "", changesNotice: "", requiredNotices: "" });
  assert.deepEqual(client.overtureCreditForm({ sourceReference: "Private file", permissionEvidence: "Private legal notes" }), client.overtureCreditForm());
});
test("original license matching is explicit and rejects unsupported/mismatched variants", () => {
  for (const value of ["CC BY 4.0", " cc-by-4.0 ", "Creative Commons Attribution 4.0 International"]) assert.equal(client.originalPhotoLicenseCode(value), "CC_BY_4_0");
  for (const value of ["CC0 1.0", "CC0-1.0", "CC0 1.0 Universal", "Creative Commons Zero 1.0 Universal"]) assert.equal(client.originalPhotoLicenseCode(value), "CC0_1_0");
  for (const value of [null, "", "CC BY", "CC BY 3.0", "CC BY-SA 4.0", "CC BY-NC 4.0", "All rights reserved"]) assert.equal(client.originalPhotoLicenseCode(value), null);
  assert.throws(() => client.overtureCreditSave(form, 3, 1, "CC BY-SA 4.0"), /original uploaded license is not supported/);
  assert.throws(() => client.overtureCreditSave({ ...form, licenseCode: "CC0_1_0" }, 3, 1, "CC BY 4.0"), /matching the original/);
});
test("credit save creates exact nullable metadata and normalizes multiline notices without a rights claim", () => {
  const result = client.overtureCreditSave({ ...form, changesNotice: " Cropped before upload.\r\nColors adjusted. ", requiredNotices: " Copyright Example\rAdditional notice " }, 3, 1, "CC BY 4.0");
  assert.deepEqual(result, { expectedVersion: 3, expectedCreditVersion: 1, title: form.title, creator: form.creator, creatorUrl: null, sourceUrl: form.sourceUrl, licenseCode: "CC_BY_4_0", changesNotice: "Cropped before upload.\nColors adjusted.", requiredNotices: "Copyright Example\nAdditional notice" });
  assert.equal(result.rightsConfirmed, undefined); assert.equal(result.licenseUrl, undefined); assert.equal(result.permissionEvidence, undefined);
});
test("credit text has bounded public-only fields and rejects controls/empty required values", () => {
  for (const [key, value] of [["title", ""], ["creator", ""], ["changesNotice", ""], ["title", "x".repeat(201)], ["creator", "x".repeat(301)], ["changesNotice", "x".repeat(1001)], ["requiredNotices", "x".repeat(2001)], ["creator", "Name\nOther"], ["requiredNotices", "Notice\u0000"], ["creator", "Name\u202e"], ["changesNotice", "Notice\u200b"], ["requiredNotices", "Notice\u0085"]]) assert.throws(() => client.overtureCreditSave({ ...form, [key]: value }, 3, 1, "CC BY 4.0"));
});
test("credit links reject nested encoded controls and unsafe delimiters while supporting encoded source titles", () => {
  for (const url of ["https://photos.example.org/a%2540secret", "https://photos.example.org/a%250Asecret", "https://photos.example.org/a%5Csecret", "https://photos.example.org/a%E2%80%AE", "https://photos.example.org/a%FF", "https://photos.example.org/a%broken", "https://photos.google.com/a", "https://site.gstatic.com/a", "https://site.lan/a"]) assert.throws(() => client.overtureCreditSave({ ...form, sourceUrl: url }, 3, 1, "CC BY 4.0"));
  assert.equal(client.overtureCreditSave({ ...form, sourceUrl: "https://commons.wikimedia.org/wiki/File:Venue%20exterior_%E0%AE%A4.jpg" }, 3, 1, "CC BY 4.0").sourceUrl, "https://commons.wikimedia.org/wiki/File:Venue%20exterior_%E0%AE%A4.jpg");
});
test("credit links reject credentials, queries, fragments, private IP/hosts and Google photo hosts without fetching", () => {
  for (const url of ["http://photos.example.org/a", "https://user:secret@photos.example.org/a", "https://photos.example.org/a?token=private", "https://photos.example.org/a#private", "https://localhost/a", "https://10.0.0.1/a", "https://[::1]/a", "https://intranet.local/a", "https://site.internal/a", "https://lh3.googleusercontent.com/a", "https://maps.google.com/a", "https://google.com/maps/a", "https://photos.example.org:8443/a"]) {
    assert.throws(() => client.overtureCreditSave({ ...form, sourceUrl: url }, 3, 1, "CC BY 4.0"), /public HTTPS URLs/);
    assert.throws(() => client.overtureCreditSave({ ...form, creatorUrl: url }, 3, 1, "CC BY 4.0"), /public HTTPS URLs/);
  }
});
