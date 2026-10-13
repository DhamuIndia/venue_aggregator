const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
function loadApi() {
  const calls = []; const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../lib/api-client.ts"), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const apiModule = { exports: {} };
  new Function("module", "exports", "fetch", "process", compiled)(apiModule, apiModule.exports, async (url, options) => { calls.push({ url, options }); return new Response("{}", { headers: { "Content-Type": "application/json" } }); }, { env: { NEXT_PUBLIC_API_BASE_URL: "http://qa.invalid/api/v1" } });
  return { api: apiModule.exports, calls };
}
test("JSON API requests keep their default content-type, auth and no-store behavior", async () => {
  const { api, calls } = loadApi(); await api.apiRequest("/test", { token: "admin", method: "PUT", cache: "no-store", body: JSON.stringify({ caption: null }) });
  assert.equal(api.API_BASE_URL, "http://qa.invalid/api/v1"); assert.equal(calls[0].options.headers["Content-Type"], "application/json");
  assert.equal(calls[0].options.headers.Authorization, "Bearer admin"); assert.equal(calls[0].options.cache, "no-store"); assert.equal(calls[0].options.body, '{"caption":null}');
});
test("multipart requests let the browser supply its boundary without dropping auth or cache controls", async () => {
  const { api, calls } = loadApi(); const body = new FormData(); body.append("metadata", new Blob(["{}"], { type: "application/json" }));
  await api.apiRequest("/upload", { token: "admin", method: "POST", cache: "no-store", body });
  assert.equal(calls[0].options.headers["Content-Type"], undefined); assert.equal(calls[0].options.headers.Authorization, "Bearer admin");
  assert.equal(calls[0].options.cache, "no-store"); assert.equal(calls[0].options.body, body);
});
