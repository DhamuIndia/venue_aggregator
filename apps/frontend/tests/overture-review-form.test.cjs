const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");

const source = fs.readFileSync(path.join(__dirname, "../features/admin/overture-review-form.ts"), "utf8");
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
const helpersModule = { exports: {} };
new Function("module", "exports", compiled)(helpersModule, helpersModule.exports);
const helpers = helpersModule.exports;

function facts() {
  return { name: "Private test hall", address: null, city: "Chennai", area: null, postcode: null, latitude: 0, longitude: 0,
    phone: null, website: null, operatingStatus: "unknown", capacity: null, description: null,
    amenities: { ac: false, carParking: null, bikeParking: true, dining: null, generator: null, lift: null, bridalRoom: null, cateringKitchen: null } };
}

test("draft review forms round-trip every full fact, including false, null and zero coordinates", () => {
  const original = facts();
  const form = helpers.overtureFormFromFacts(original);
  assert.equal(form["amenities.ac"], "no");
  assert.equal(form["amenities.bikeParking"], "yes");
  assert.equal(form["amenities.lift"], "");
  assert.equal(form.latitude, "0");
  assert.deepEqual(helpers.overtureFactsFromForm(form), original);
  assert.equal(Object.keys(form).length, 20);
});

test("empty optional inputs explicitly clear values instead of retaining stale facts", () => {
  const form = helpers.overtureFormFromFacts({ ...facts(), phone: "123", capacity: 500, description: "Old description" });
  form.phone = "   "; form.capacity = ""; form.description = "  ";
  const next = helpers.overtureFactsFromForm(form);
  assert.equal(next.phone, null); assert.equal(next.capacity, null); assert.equal(next.description, null);
  assert.equal(Object.keys(next.amenities).length, 8);
});

test("coordinates require finite decimal values, range checks and a supplied pair", () => {
  for (const value of ["NaN", "Infinity", "1e3", "90.0001", "12abc", "0x1"]) {
    const form = helpers.overtureFormFromFacts(facts()); form.latitude = value;
    assert.throws(() => helpers.overtureFactsFromForm(form), /Latitude must be/);
  }
  const form = helpers.overtureFormFromFacts(facts()); form.longitude = "";
  assert.throws(() => helpers.overtureFactsFromForm(form), /both latitude and longitude/);
  form.latitude = "";
  const result = helpers.overtureFactsFromForm(form);
  assert.equal(result.latitude, null); assert.equal(result.longitude, null);
});

test("capacity must be a whole number in the supported range, with unknown distinct from zero", () => {
  for (const capacity of ["0", "-1", "1.2", "100001", "Infinity", "12 seats"]) {
    const form = helpers.overtureFormFromFacts(facts()); form.capacity = capacity;
    assert.throws(() => helpers.overtureFactsFromForm(form), /Capacity must be/);
  }
  const form = helpers.overtureFormFromFacts(facts()); form.capacity = "100000";
  assert.equal(helpers.overtureFactsFromForm(form).capacity, 100000);
});

test("business phones preserve supported formatting and reject malformed or short values", () => {
  const form = helpers.overtureFormFromFacts(facts());
  for (const phone of ["12345", "+91test9000", "++919000000000", "919000000000 ext 4", "1".repeat(21)]) {
    form.phone = phone;
    assert.throws(() => helpers.overtureFactsFromForm(form), /Phone must be a business number/);
  }
  form.phone = "+91 (90000) 00000";
  assert.equal(helpers.overtureFactsFromForm(form).phone, form.phone);
  form.phone = "";
  assert.equal(helpers.overtureFactsFromForm(form).phone, null);
});

test("venue names are required and amenity selects cannot coerce arbitrary values into booleans", () => {
  const form = helpers.overtureFormFromFacts(facts()); form.name = "  ";
  assert.throws(() => helpers.overtureFactsFromForm(form), /Venue name is required/);
  form.name = "  A hall  "; form["amenities.ac"] = "false";
  assert.throws(() => helpers.overtureFactsFromForm(form), /Unknown, Yes or No/);
  form["amenities.ac"] = "no";
  assert.equal(helpers.overtureFactsFromForm(form).name, "A hall");
});

test("unknown facts and explicit false amenities have different display labels", () => {
  assert.equal(helpers.overtureDisplayFact(false), "No");
  assert.equal(helpers.overtureDisplayFact(true), "Yes");
  assert.equal(helpers.overtureDisplayFact(null), "Unknown / not supplied");
  assert.equal(helpers.overtureDisplayFact(0), "0");
  assert.equal(helpers.overtureDisplayFact("Source_Hall"), "Source_Hall");
  assert.equal(helpers.overtureFactValue(facts(), "amenities.ac"), false);
  assert.equal(helpers.overtureFactValue(facts(), "latitude"), 0);
});
