const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
class ApiError extends Error { constructor(status, message, details) { super(message); this.status = status; this.details = details; } }
function loadClient(responder = async () => ({ mediaVersion: 1, items: [] }), binary = async () => new Response(new Uint8Array([255, 216, 255]), { headers: { "Content-Type": "image/jpeg" } })) {
  const calls = []; const contentCalls = [];
  const source = fs.readFileSync(path.join(__dirname, "../features/admin/overture-media-client.ts"), "utf8");
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const clientModule = { exports: {} };
  new Function("require", "module", "exports", "fetch", compiled)((name) => {
    assert.equal(name, "@/lib/api-client");
    return { ApiError, API_BASE_URL: "http://qa.invalid/api/v1", apiRequest: (url, options) => { calls.push({ url, options }); return responder(url, options); } };
  }, clientModule, clientModule.exports, (url, options) => { contentCalls.push({ url, options }); return binary(url, options); });
  return { client: clientModule.exports, calls, contentCalls };
}
const metadata = { expectedVersion: 3, caption: null, sourceKind: "TEAM_PHOTO", sourceReference: null, rightsBasis: "TEAM_OWNED", licenseName: null, permissionEvidence: "QA team owns this photo", rightsConfirmed: true };

test("all private-photo endpoints require admin authentication before any network or local fallback", async () => {
  const { client, calls, contentCalls } = loadClient();
  const file = new File([new Uint8Array([1])], "qa.jpg", { type: "image/jpeg" });
  for (const token of [undefined, null, "", "   "]) for (const operation of [
    () => client.getOvertureMedia(7, token), () => client.uploadOvertureMedia(7, file, metadata, token),
    () => client.reviewOvertureMedia(7, 8, 3, "APPROVED", "QA checked rights", token),
    () => client.arrangeOvertureMedia(7, 3, null, [], token), () => client.archiveOvertureMedia(7, 8, 3, "QA archive reason", token),
    () => client.getOvertureMediaContent(7, 8, token)
  ]) await assert.rejects(operation, /sign in with an admin account/);
  assert.equal(calls.length, 0); assert.equal(contentCalls.length, 0);
});

test("private gallery reads and mutations use exact versioned authenticated uncached endpoints", async () => {
  const { client, calls } = loadClient(); const signal = new AbortController().signal;
  await client.getOvertureMedia(7, "admin", signal);
  await client.reviewOvertureMedia(7, 8, 3, "REJECTED", "QA permission missing", "admin");
  await client.arrangeOvertureMedia(7, 4, null, [9, 10], "admin");
  await client.archiveOvertureMedia(7, 8, 5, "QA wrong venue photo", "admin");
  assert.deepEqual(calls.map(({ url, options }) => ({ url, method: options.method, body: options.body ? JSON.parse(options.body) : null })), [
    { url: "/admin/overture-onboarding/drafts/7/media", method: undefined, body: null },
    { url: "/admin/overture-onboarding/drafts/7/media/8/review", method: "PUT", body: { expectedVersion: 3, status: "REJECTED", reason: "QA permission missing" } },
    { url: "/admin/overture-onboarding/drafts/7/media/arrangement", method: "PUT", body: { expectedVersion: 4, coverMediaId: null, orderedMediaIds: [9, 10] } },
    { url: "/admin/overture-onboarding/drafts/7/media/8/archive", method: "POST", body: { expectedVersion: 5, reason: "QA wrong venue photo" } }
  ]);
  assert.ok(calls.every(({ options }) => options.token === "admin" && options.cache === "no-store"));
  assert.equal(calls[0].options.signal, signal);
});

test("upload sends only an image file and full JSON metadata as multipart, preserving explicit nulls", async () => {
  const { client, calls } = loadClient(); const file = new File([new Uint8Array([255, 216, 255])], "qa.jpg", { type: "image/jpeg" });
  await client.uploadOvertureMedia(7, file, metadata, "admin");
  const { url, options } = calls[0]; assert.equal(url, "/admin/overture-onboarding/drafts/7/media/upload");
  assert.equal(options.method, "POST"); assert.equal(options.cache, "no-store"); assert.equal(options.token, "admin");
  assert.ok(options.body instanceof FormData); assert.deepEqual([...options.body.keys()], ["file", "metadata"]);
  assert.equal(options.body.get("file").name, "qa.jpg"); assert.equal(options.body.get("file").type, "image/jpeg");
  assert.equal(options.body.get("metadata").type, "application/json");
  assert.deepEqual(JSON.parse(await options.body.get("metadata").text()), metadata);
  assert.equal(options.headers, undefined);
});

test("media conflicts propagate once without retry, changing expected version or fabricating success", async () => {
  const failure = new ApiError(409, "Gallery changed"); const { client, calls } = loadClient(async () => { throw failure; });
  await assert.rejects(() => client.arrangeOvertureMedia(7, 3, 9, [9], "admin"), (error) => error === failure);
  assert.equal(calls.length, 1); assert.deepEqual(JSON.parse(calls[0].options.body), { expectedVersion: 3, coverMediaId: 9, orderedMediaIds: [9] });
});

test("private preview requires explicit bearer fetch and returns bytes, never a public URL", async () => {
  const { client, calls, contentCalls } = loadClient(); const signal = new AbortController().signal;
  const blob = await client.getOvertureMediaContent(7, 8, "admin", signal);
  assert.ok(blob instanceof Blob); assert.equal(blob.type, "image/jpeg"); assert.equal(blob.size, 3); assert.equal(calls.length, 0);
  assert.deepEqual(contentCalls, [{ url: "http://qa.invalid/api/v1/admin/overture-onboarding/drafts/7/media/8/content", options: { headers: { Authorization: "Bearer admin" }, cache: "no-store", redirect: "error", signal } }]);
});

test("preview rejects unauthorized, non-JPEG, empty and oversized responses without a fallback", async () => {
  for (const response of [new Response(JSON.stringify({ detail: "Not authorized" }), { status: 403, headers: { "Content-Type": "application/json" } }),
    new Response("<html>not an image</html>", { headers: { "Content-Type": "text/html" } }),
    new Response(new Uint8Array([]), { headers: { "Content-Type": "image/jpeg" } }),
    new Response(new Uint8Array([1]), { headers: { "Content-Type": "image/jpeg", "Content-Length": String(4 * 1024 * 1024 + 1) } })]) {
    const { client, contentCalls } = loadClient(undefined, async () => response);
    await assert.rejects(() => client.getOvertureMediaContent(7, 8, "admin")); assert.equal(contentCalls.length, 1);
  }
});

test("chunked previews are bounded by actual bytes even without or with false Content-Length", async () => {
  for (const headers of [{ "Content-Type": "image/jpeg" }, { "Content-Type": "image/jpeg", "Content-Length": "1" }]) {
    let chunks = 0; let cancelled = false;
    const body = new ReadableStream({ pull(controller) { chunks++; controller.enqueue(new Uint8Array(1024 * 1024)); }, cancel() { cancelled = true; } });
    const { client } = loadClient(undefined, async () => new Response(body, { headers }));
    await assert.rejects(() => client.getOvertureMediaContent(7, 8, "admin"), /exceeds the supported size/);
    assert.equal(cancelled, true); assert.ok(chunks <= 6);
  }
});

test("MIME and declared-size rejection cancel unread response streams", async () => {
  for (const headers of [{ "Content-Type": "text/html" }, { "Content-Type": "image/jpeg", "Content-Length": String(4 * 1024 * 1024 + 1) }]) {
    let cancelled = false; const body = new ReadableStream({ cancel() { cancelled = true; } });
    const { client } = loadClient(undefined, async () => new Response(body, { headers }));
    await assert.rejects(() => client.getOvertureMediaContent(7, 8, "admin")); assert.equal(cancelled, true);
  }
});
