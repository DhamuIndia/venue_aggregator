const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
const { createRequire } = require("node:module");
const frontendRequire = createRequire(path.join(__dirname, "../package.json"));

// Exercise the real client while substituting the network boundary. Tests never
// load a catalog, create a database record, or contact an external service.
function loadClient(responder = async () => ({ content: [] })) {
  const calls = [];
  const source = fs.readFileSync(path.join(__dirname, "../features/admin/overture-client.ts"), "utf8");
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

test("all onboarding endpoints reject absent admin tokens without issuing a request", async () => {
  const { client, calls } = loadClient();
  for (const token of [undefined, null, "", "   "]) {
    for (const request of [
      () => client.getOvertureSettings(token),
      () => client.getOvertureCatalog(0, "", token),
      () => client.previewOvertureVenues(["venue-id"], token),
      () => client.importOvertureVenues("catalog-version", ["venue-id"], token),
      () => client.getOvertureDrafts(0, token),
      () => client.getOvertureDraft(1, token),
      () => client.updateOvertureDraft(1, {}, token)
    ]) await assert.rejects(request, /sign in with an admin account/);
  }
  assert.equal(calls.length, 0);
});

test("reading settings, catalog and drafts sends uncached authenticated GET requests", async () => {
  const { client, calls } = loadClient();
  const signal = new AbortController().signal;
  await client.getOvertureSettings("token", signal);
  await client.getOvertureCatalog(3, "  Kalyana & Hall  ", "token", signal);
  await client.getOvertureCatalog(0, "  ", "token");
  await client.getOvertureDrafts(2, "token", signal);
  assert.deepEqual(calls.map((item) => item.url), [
    "/admin/overture-onboarding/settings",
    "/admin/overture-onboarding/catalog?page=3&size=20&query=Kalyana+%26+Hall",
    "/admin/overture-onboarding/catalog?page=0&size=20",
    "/admin/overture-onboarding/drafts?page=2&size=20"
  ]);
  assert.ok(calls.every((item) => item.options.token === "token" && item.options.cache === "no-store" && !item.options.method && !item.options.body));
  assert.equal(calls[0].options.signal, signal);
  assert.equal(calls[1].options.signal, signal);
  assert.equal(calls[3].options.signal, signal);
});

test("preview sends only selected IDs and importing requires the reviewed catalog version", async () => {
  const response = { catalogVersion: "a".repeat(64), items: [] };
  const { client, calls } = loadClient(async () => response);
  assert.equal(calls.length, 0);
  assert.equal(await client.previewOvertureVenues(["selected-id"], "token"), response);
  await client.importOvertureVenues(response.catalogVersion, ["selected-id"], "token");
  assert.deepEqual(calls.map((item) => ({ url: item.url, method: item.options.method, body: JSON.parse(item.options.body) })), [
    { url: "/admin/overture-onboarding/preview", method: "POST", body: { ids: ["selected-id"] } },
    { url: "/admin/overture-onboarding/import", method: "POST", body: { catalogVersion: response.catalogVersion, ids: ["selected-id"] } }
  ]);
  assert.ok(calls.every((item) => item.options.token === "token" && item.options.cache === "no-store"));
});

test("import conflicts and other errors propagate without retry or fabricated success", async () => {
  const failure = Object.assign(new Error("Catalog changed"), { status: 409 });
  const { client, calls } = loadClient(async () => { throw failure; });
  await assert.rejects(() => client.importOvertureVenues("old-version", ["id"], "token"), (exception) => exception === failure);
  assert.equal(calls.length, 1);
});

test("only currently selected READY rows from the preview are eligible for creation", () => {
  const { client } = loadClient();
  const preview = { items: [
    { venue: { id: "ready" }, outcome: "READY" },
    { venue: { id: "ready" }, outcome: "READY" },
    { venue: { id: "unselected" }, outcome: "READY" },
    { venue: { id: "existing" }, outcome: "ALREADY_IMPORTED" },
    { venue: { id: "duplicate" }, outcome: "POSSIBLE_DUPLICATE" },
    { venue: { id: "incomplete" }, outcome: "INCOMPLETE" }
  ] };
  assert.deepEqual(client.readyOvertureIds(preview, ["ready", "existing", "duplicate", "incomplete", "not-in-preview"]), ["ready"]);
  assert.deepEqual(client.readyOvertureIds(preview, []), []);
  assert.deepEqual(client.readyOvertureIds(null, ["ready"]), []);
});

test("venue website links require HTTPS without credentials and exclude Google content URLs", () => {
  const { client } = loadClient();
  for (const value of [null, undefined, "", "javascript:alert(1)", "data:text/html,test", "http://example.com", "//example.com", "/relative", "https://user:secret@example.com", "not a url", "https://maps.google.com/?cid=123", "https://maps.app.goo.gl/sample", "https://www.google.co.in/maps/test"]) {
    assert.equal(client.safeOvertureWebsite(value), undefined, String(value));
  }
  assert.equal(client.safeOvertureWebsite("https://example.com/venue"), "https://example.com/venue");
});

test("HTTP source websites stay visible as validated text without becoming clickable", () => {
  const { client } = loadClient();
  assert.equal(client.overtureWebsiteText("http://example.com/venue"), "http://example.com/venue");
  assert.equal(client.safeOvertureWebsite("http://example.com/venue"), undefined);
  assert.equal(client.overtureWebsiteText("https://example.com/venue"), "https://example.com/venue");
  for (const value of [null, "", "javascript:alert(1)", "//example.com", "http://user:secret@example.com", "http://maps.google.com/venue", "http://maps.app.goo.gl/sample"]) {
    assert.equal(client.overtureWebsiteText(value), undefined, String(value));
  }
});

test("draft detail and review updates carry uncached admin auth and the exact versioned full body", async () => {
  const response = { hallId: 42, reviewVersion: 8, status: "DRAFT" };
  const { client, calls } = loadClient(async () => response);
  const signal = new AbortController().signal;
  const update = { expectedVersion: 7, facts: { name: "Test hall", phone: null, capacity: null, amenities: { ac: false, lift: null } },
    verifiedFields: ["amenities.ac"], reviewStatus: "IN_REVIEW", reviewNotes: "Site visit", duplicateDecision: "NOT_REVIEWED", duplicateNotes: null, reviewedDuplicateHallIds: [] };
  assert.equal(await client.getOvertureDraft(42, "admin-token", signal), response);
  assert.equal(await client.updateOvertureDraft(42, update, "admin-token", signal), response);
  assert.deepEqual(calls.map((call) => call.url), ["/admin/overture-onboarding/drafts/42", "/admin/overture-onboarding/drafts/42"]);
  assert.equal(calls[0].options.method, undefined);
  assert.equal(calls[1].options.method, "PUT");
  assert.deepEqual(JSON.parse(calls[1].options.body), update);
  assert.ok(calls.every((call) => call.options.token === "admin-token" && call.options.cache === "no-store" && call.options.signal === signal));
});

test("draft review conflict propagates without retry or auto-incrementing its expected version", async () => {
  const failure = Object.assign(new Error("Draft changed"), { status: 409 });
  const { client, calls } = loadClient(async () => { throw failure; });
  await assert.rejects(() => client.updateOvertureDraft(9, { expectedVersion: 3 }, "token"), (exception) => exception === failure);
  assert.equal(calls.length, 1);
  assert.deepEqual(JSON.parse(calls[0].options.body), { expectedVersion: 3 });
});

test("the venue facts display shows supplied HTTP text and keeps HTTPS websites clickable", () => {
  const { client } = loadClient();
  const source = fs.readFileSync(path.join(__dirname, "../components/admin/AdminOvertureOnboarding.tsx"), "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX }
  }).outputText;
  const componentModule = { exports: {} };
  new Function("require", "module", "exports", `${compiled}\nmodule.exports.VenueFacts = VenueFacts;`)((name) => {
    if (name === "@/features/admin/overture-client") return client;
    if (name === "@/components/admin/AdminOvertureDraftReview") return { AdminOvertureDraftReview: () => null };
    if (name === "@/features/auth/AuthProvider") return { useAuth: () => ({ accessToken: null }) };
    if (name === "@/lib/api-client") return { ApiError: class ApiError extends Error {} };
    return frontendRequire(name);
  }, componentModule, componentModule.exports);
  const React = frontendRequire("react");
  const { renderToStaticMarkup } = frontendRequire("react-dom/server");
  const venue = {
    category: "event_venue", address: "Example address", city: "Chennai", area: "Adyar",
    phone: "+919000000000", operatingStatus: "open", confidence: 0.9, sources: []
  };
  const render = (website) => renderToStaticMarkup(React.createElement(componentModule.exports.VenueFacts, { venue: { ...venue, website } }));
  const http = render("http://example.com/venue");
  assert.match(http, /Website<\/dt><dd><span[^>]*>http:\/\/example\.com\/venue<\/span>/);
  assert.doesNotMatch(http, /href="http:\/\//);
  assert.doesNotMatch(http, /Not provided/);
  const https = render("https://example.com/venue");
  assert.match(https, /href="https:\/\/example\.com\/venue"/);
  assert.match(https, /Visit venue website/);
  assert.match(render(null), /Website<\/dt><dd>Not provided<\/dd>/);
});
