const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");

// Execute the real TypeScript client with its API dependency replaced. No network,
// credentials, Google requests, or production mock mode are used by these tests.
function loadClient(responder = async () => ({ content: [] })) {
  const calls = [];
  const source = fs.readFileSync(path.join(__dirname, "../features/admin/discovery-client.ts"), "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText;
  const clientModule = { exports: {} };
  new Function("require", "module", "exports", compiled)((name) => {
    assert.equal(name, "@/lib/api-client");
    return { apiRequest: (url, options) => { calls.push({ url, options }); return responder(url, options); } };
  }, clientModule, clientModule.exports);
  return { client: clientModule.exports, calls };
}

test("all discovery endpoints require authentication and never fall back to mock data", async () => {
  const { client, calls } = loadClient();
  for (const request of [
    () => client.getDiscoverySettings(),
    () => client.searchDiscoveryVenues({ city: "Chennai", area: "", venueType: "BANQUET_HALL" }),
    () => client.getDiscoveryRuns(0),
    () => client.getDiscoveryRun(1),
    () => client.getDiscoveryCandidates(0, "ALL"),
    () => client.getDiscoveryPreview(1),
    () => client.reviewDiscoveryCandidate({ id: 1, status: "DISCOVERED" }, "SHORTLISTED")
  ]) {
    await assert.rejects(request, /sign in with an admin account/);
  }
  assert.equal(calls.length, 0);
});

test("search is an explicit uncached POST with only the requested search fields", async () => {
  const result = { run: { id: 10 }, candidates: [], previews: [] };
  const { client, calls } = loadClient(async () => result);
  assert.equal(calls.length, 0);
  const input = { city: "Chennai", area: "Adyar", venueType: "BANQUET_HALL" };
  assert.equal(await client.searchDiscoveryVenues(input, "test-admin-token"), result);
  assert.deepEqual(calls, [{ url: "/admin/venue-discovery/search", options: {
    method: "POST", token: "test-admin-token", cache: "no-store", body: JSON.stringify(input)
  } }]);
});

test("candidate and run pagination do not trigger billable previews", async () => {
  const { client, calls } = loadClient();
  const signal = new AbortController().signal;
  await client.getDiscoveryCandidates(2, "SHORTLISTED", "token", signal);
  await client.getDiscoveryCandidates(0, "ALL", "token");
  await client.getDiscoveryRuns(3, "token", signal);
  await client.getDiscoveryRun(42, "token");
  assert.deepEqual(calls.map((item) => item.url), [
    "/admin/venue-discovery/candidates?page=2&size=20&status=SHORTLISTED",
    "/admin/venue-discovery/candidates?page=0&size=20",
    "/admin/venue-discovery/runs?page=3&size=20",
    "/admin/venue-discovery/runs/42"
  ]);
  assert.equal(calls[0].options.signal, signal);
  assert.equal(calls[2].options.signal, signal);
  assert.ok(calls.every((item) => item.options.cache === "no-store"));
});

test("review includes the expected status to protect concurrent decisions", async () => {
  const { client, calls } = loadClient();
  await client.reviewDiscoveryCandidate({ id: 12, status: "DISCOVERED" }, "SHORTLISTED", "token");
  assert.equal(calls[0].url, "/admin/venue-discovery/candidates/12");
  assert.equal(calls[0].options.method, "PATCH");
  assert.deepEqual(JSON.parse(calls[0].options.body), { status: "SHORTLISTED", expectedStatus: "DISCOVERED" });
});

test("preview fetch is an explicit uncached POST", async () => {
  const { client, calls } = loadClient();
  await client.getDiscoveryPreview(8, "token");
  assert.deepEqual(calls, [{ url: "/admin/venue-discovery/candidates/8/preview", options: {
    method: "POST", token: "token", cache: "no-store"
  } }]);
});

test("API failures propagate without retry or fabricated success", async () => {
  const failure = new Error("Daily limit reached");
  const { client, calls } = loadClient(async () => { throw failure; });
  await assert.rejects(() => client.searchDiscoveryVenues({ city: "Chennai", area: "", venueType: "BANQUET_HALL" }, "token"), (error) => error === failure);
  assert.equal(calls.length, 1);
});

test("external links only allow absolute HTTPS URLs without embedded credentials", () => {
  const { client } = loadClient();
  for (const value of [null, undefined, "", "javascript:alert(1)", "data:text/html,test", "http://example.com", "//example.com", "/relative", "https://user:secret@example.com", "not a url"]) {
    assert.equal(client.safeDiscoveryExternalUrl(value), undefined, String(value));
  }
  assert.equal(client.safeDiscoveryExternalUrl("https://maps.google.com/?cid=123"), "https://maps.google.com/?cid=123");
  assert.equal(client.safeDiscoveryExternalUrl("https://example.com/attribution"), "https://example.com/attribution");
});
