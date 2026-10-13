const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../features/admin/overture-media-form.ts"), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
const helpersModule = { exports: {} }; new Function("module", "exports", compiled)(helpersModule, helpersModule.exports); const helpers = helpersModule.exports;
const form = () => ({ ...helpers.emptyOvertureMediaForm(), permissionEvidence: "QA team took the image at a permitted venue visit", rightsConfirmed: true });

test("private uploads allow only non-empty JPEG/PNG at or below 8 MiB", () => {
  for (const type of ["image/jpeg", "image/png"]) helpers.validateOvertureMediaFile({ type, size: 8 * 1024 * 1024, name: "qa" });
  for (const type of ["image/webp", "image/svg+xml", "image/gif", "text/html", ""]) assert.throws(() => helpers.validateOvertureMediaFile({ type, size: 1, name: "qa" }), /JPEG or PNG/);
  for (const size of [0, 8 * 1024 * 1024 + 1]) assert.throws(() => helpers.validateOvertureMediaFile({ type: "image/png", size, name: "qa" }), /non-empty image/);
});
test("source choice determines an exact rights pairing with complete nullable metadata", () => {
  for (const [sourceKind, rightsBasis] of [["TEAM_PHOTO", "TEAM_OWNED"], ["BUSINESS_PROVIDED", "BUSINESS_PERMISSION"], ["LICENSED_IMAGE", "OPEN_LICENSE"]]) {
    const metadata = helpers.overtureMediaMetadata({ ...form(), sourceKind, sourceReference: sourceKind === "LICENSED_IMAGE" ? "QA licensed collection" : "", licenseName: sourceKind === "LICENSED_IMAGE" ? "CC BY 4.0" : "" }, 2);
    assert.equal(metadata.rightsBasis, rightsBasis); assert.equal(metadata.expectedVersion, 2); assert.equal(metadata.caption, null); assert.equal(metadata.rightsConfirmed, true);
    assert.deepEqual(Object.keys(metadata).sort(), ["expectedVersion", "caption", "sourceKind", "sourceReference", "rightsBasis", "licenseName", "permissionEvidence", "rightsConfirmed"].sort());
  }
});
test("permission evidence and affirmative rights confirmation are required", () => {
  assert.throws(() => helpers.overtureMediaMetadata({ ...form(), rightsConfirmed: false }, 1), /Confirm/);
  assert.throws(() => helpers.overtureMediaMetadata({ ...form(), permissionEvidence: "short" }, 1), /at least 10/);
  assert.throws(() => helpers.overtureMediaMetadata({ ...form(), permissionEvidence: "x".repeat(4001) }, 1), /exceeds/);
  assert.throws(() => helpers.overtureMediaMetadata({ ...form(), sourceKind: "toString" }, 1), /supported photo source/);
});
test("licensed images require source and license; Maps sources and control characters are rejected", () => {
  for (const update of [{ sourceReference: "", licenseName: "CC0" }, { sourceReference: "QA source", licenseName: "" }]) assert.throws(() => helpers.overtureMediaMetadata({ ...form(), sourceKind: "LICENSED_IMAGE", ...update }, 1), /requires/);
  for (const sourceReference of ["Google Maps screenshot", "Google+Maps+screenshot", "https://maps.app.goo.gl/qa", "https://maps.google.com/qa", "https://www.google.co.in/maps/qa", "https://lh3.googleusercontent.com/qa", "https%3A%2F%2Fmaps.google.com%2Fqa", "https://%4D%41%50%53.%47%4F%4F%47%4C%45.com/qa"]) assert.throws(() => helpers.overtureMediaMetadata({ ...form(), sourceReference }, 1), /Google Maps/);
  assert.throws(() => helpers.overtureMediaMetadata({ ...form(), caption: "bad\ncaption" }, 1), /invalid characters/);
  assert.throws(() => helpers.overtureMediaMetadata({ ...form(), permissionEvidence: "QA\u200Bhidden evidence" }, 1), /invalid characters/);
  assert.ok(helpers.overtureMediaMetadata({ ...form(), permissionEvidence: "QA first line\nQA second line" }, 1));
});
test("review and archive require meaningful reasons, not empty or short success placeholders", () => {
  for (const reason of ["", "  ", "short"]) assert.throws(() => helpers.overtureMediaReason(reason), /at least 10/);
  assert.equal(helpers.overtureMediaReason("  QA permission and venue checked  "), "QA permission and venue checked");
});
test("only approved photos participate in cover/order lists, without mutating server data", () => {
  const gallery = { items: [{ id: 5, status: "APPROVED", sortOrder: 2 }, { id: 3, status: "PENDING", sortOrder: 0 }, { id: 7, status: "ARCHIVED", sortOrder: 0 }, { id: 8, status: "REJECTED", sortOrder: 0 }, { id: 9, status: "APPROVED", sortOrder: 1 }] };
  const before = JSON.stringify(gallery); assert.deepEqual(helpers.overtureApprovedMediaIds(gallery), [9, 5]); assert.equal(JSON.stringify(gallery), before);
});
test("photo ordering moves exactly one approved ID and respects boundaries", () => {
  const ids = [1, 2, 3]; assert.deepEqual(helpers.moveOverturePhoto(ids, 2, -1), [2, 1, 3]); assert.deepEqual(helpers.moveOverturePhoto(ids, 2, 1), [1, 3, 2]);
  assert.equal(helpers.moveOverturePhoto(ids, 1, -1), ids); assert.equal(helpers.moveOverturePhoto(ids, 99, 1), ids); assert.deepEqual(ids, [1, 2, 3]);
});
test("unrelated media changes preserve an unsaved null or approved cover selection", () => {
  const gallery = { coverMediaId: 1, items: [{ id: 1, status: "APPROVED", sortOrder: 0 }, { id: 2, status: "APPROVED", sortOrder: 1 }, { id: 3, status: "ARCHIVED", sortOrder: 2 }] };
  assert.equal(helpers.retainedOvertureMediaCover(null, gallery), null);
  assert.equal(helpers.retainedOvertureMediaCover(2, gallery), 2);
  assert.equal(helpers.retainedOvertureMediaCover(3, gallery), 1);
});
