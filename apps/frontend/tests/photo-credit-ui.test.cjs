const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
function compile(file, dependencies = {}) {
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../", file), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX } }).outputText; const mod = { exports: {} };
  new Function("require", "module", "exports", code)((name) => dependencies[name] ?? require(name), mod, mod.exports); return mod.exports;
}
const helpers = compile("features/halls/photo-credit.ts");
const credits = compile("components/halls/PublicPhotoCredit.tsx");
const credit = { title: "Venue <exterior>", creator: "Creator & team", creatorUrl: "https://commons.wikimedia.org/wiki/User:Creator", sourceUrl: "https://commons.wikimedia.org/wiki/File:Venue.jpg", licenseCode: "CC_BY_4_0", licenseLabel: "CC BY 4.0", licenseUrl: "https://creativecommons.org/licenses/by/4.0/", changesNotice: "Cropped before upload.", processingNotice: helpers.PHOTO_PROCESSING_NOTICE, requiredNotices: "Copyright Creator\nRetain this notice." };
const image = { default: ({ src, alt, unoptimized }) => React.createElement("img", { src, alt, "data-unoptimized": String(Boolean(unoptimized)) }) };
const link = { default: ({ href, children, ...props }) => React.createElement("a", { href, ...props }, children) };
const baseHall = { id: "71", name: "Discovered Venue", city: "Chennai", area: "Adyar", capacity: null, startingPrice: null, rating: null, reviewCount: 0, imageUrl: "https://api.example.org/api/v1/halls/71/application-photos/6?publicationVersion=3", galleryUrls: [], venueType: "Event Venue", amenities: [], isVerified: false, availableThisMonth: false, listingOrigin: "APPLICATION", applicationPhotos: [] };
function creditAncestors(markup) {
  const stack = [], found = []; const tags = /<\/?([a-z\d]+)\b[^>]*>/gi; let match;
  while ((match = tags.exec(markup))) {
    if (match[0].startsWith("</")) { while (stack.length && stack.pop() !== match[1]) {} }
    else { if (match[0].includes('aria-label="Photo credit:')) found.push([...stack]); if (!/^(img|input|br|meta|link|hr)$/.test(match[1]) && !match[0].endsWith("/>")) stack.push(match[1]); }
  }
  return found;
}
test("public credit display retains plaintext notices and safe outbound links with no private fields", () => {
  const markup = renderToStaticMarkup(React.createElement(credits.PublicPhotoCredit, { credit: { ...credit, permissionEvidence: "PRIVATE EVIDENCE", reviewReason: "PRIVATE REASON" } }));
  assert.match(markup, /Venue &lt;exterior&gt;/); assert.match(markup, /Creator &amp; team/); assert.match(markup, /Copyright Creator/); assert.match(markup, /Cropped before upload/); assert.match(markup, /normalized this image to JPEG/); assert.equal((markup.match(/rel="noopener noreferrer"/g) ?? []).length, 3); assert.doesNotMatch(markup, /PRIVATE|<exterior>/);
  assert.equal(renderToStaticMarkup(React.createElement(credits.PublicPhotoCredit, { credit: null })), "");
});
test("HallCard displays licensed cover credits outside image/detail links and preserves OWNER flow", () => {
  const { HallCard } = compile("components/halls/HallCard.tsx", { "next/image": image, "next/link": link, "@/components/customer/SaveHallButton": { SaveHallButton: () => null }, "@/lib/display-format": { formatGuestCount: String }, "@/components/halls/PublicPhotoCredit": credits });
  const hall = { ...baseHall, applicationPhotos: [{ photoId: 6, url: baseHall.imageUrl, requiresCredit: true, credit }] };
  const markup = renderToStaticMarkup(React.createElement(HallCard, { hall })); assert.match(markup, /Photo source/); assert.match(markup, /data-unoptimized="true"/); assert.equal(creditAncestors(markup).length, 1); assert.ok(creditAncestors(markup).every((ancestors) => !ancestors.includes("a") && !ancestors.includes("button")));
  const owner = renderToStaticMarkup(React.createElement(HallCard, { hall: { ...hall, listingOrigin: "OWNER", startingPrice: 50000 } })); assert.doesNotMatch(owner, /Photo source|Photo credit:/); assert.match(owner, /data-unoptimized="false"/); assert.match(owner, /View details/);
});
test("gallery cover, thumbnail and fullscreen all show photo-specific credits outside controls", () => {
  const photos = [{ photoId: 6, url: baseHall.imageUrl, requiresCredit: true, credit }, { photoId: 7, url: baseHall.imageUrl.replace("/6?", "/7?"), requiresCredit: true, credit: { ...credit, title: "Venue interior", creator: "Interior creator" } }];
  const deps = { "next/image": image, "@/components/halls/PublicPhotoCredit": credits };
  const props = { coverImage: photos[0].url, galleryImages: photos.map((p) => p.url), hallName: baseHall.name, applicationPhotos: photos, unoptimized: true };
  const gallery = compile("components/halls/HallPhotoGallery.tsx", deps); const markup = renderToStaticMarkup(React.createElement(gallery.HallPhotoGallery, props));
  assert.equal(creditAncestors(markup).length, 2); assert.ok(creditAncestors(markup).every((ancestors) => !ancestors.includes("a") && !ancestors.includes("button"))); assert.match(markup, /group relative block aspect-\[4\/3\] w-full/);
  let index = 0; const hooks = { ...React, useEffect() {}, useRef: () => ({ current: null }), useState: () => [index++ === 0 ? 1 : true, () => {}] };
  const viewer = compile("components/halls/HallPhotoGallery.tsx", { ...deps, react: hooks }); const full = renderToStaticMarkup(React.createElement(viewer.HallPhotoGallery, props));
  assert.match(full, /Full-screen photo credit/); assert.equal((full.match(/Photo credit: Venue interior/g) ?? []).length, 2); assert.ok(creditAncestors(full).every((ancestors) => !ancestors.includes("a") && !ancestors.includes("button"))); assert.match(full, /max-h-\[35dvh\]/);
  const legacy = renderToStaticMarkup(React.createElement(gallery.HallPhotoGallery, { ...props, applicationPhotos: undefined, unoptimized: false })); assert.doesNotMatch(legacy, /Photo credit:|Photo source/); assert.match(legacy, /data-unoptimized="false"/);
});
const privateForm = compile("features/admin/overture-credit-form.ts", { "@/features/halls/photo-credit": helpers });
const mediaForm = compile("features/admin/overture-media-form.ts");
const privatePhoto = { id: 6, sourceKind: "LICENSED_IMAGE", licenseName: "CC BY 4.0", status: "APPROVED", permissionEvidence: "PRIVATE PERMISSION", sourceReference: "PRIVATE SOURCE", credit: null };
function editor(photo, state = {}) {
  let index = 0; const hooks = { ...React, useEffect() {}, useState(initial) { const key = index++; return [Object.prototype.hasOwnProperty.call(state, key) ? state[key] : typeof initial === "function" ? initial() : initial, () => {}]; } };
  const module = compile("components/admin/AdminOverturePhotoCredits.tsx", { react: hooks, "@/features/halls/photo-credit": helpers, "@/features/admin/overture-credit-form": privateForm, "@/features/admin/overture-media-form": mediaForm, "@/components/halls/PublicPhotoCredit": credits });
  return renderToStaticMarkup(React.createElement(module.AdminOverturePhotoCredits, { photo, mediaVersion: 3, disabled: false, onDirtyChange() {}, onSave: async () => {}, onReview: async () => {} }));
}
test("private credit fields begin blank, retain stable labels and require independent review confirmations", () => {
  const blank = editor(privatePhoto); assert.match(blank, /aria-label="Photo source URL for photo 6"[^>]*value=""/); assert.doesNotMatch(blank, /PRIVATE PERMISSION|PRIVATE SOURCE|Draft public credit preview/);
  const saved = { ...credit, creditVersion: 1, status: "PENDING", changedBy: { adminName: "Admin" }, changedAt: "2026-10-06T00:00:00Z", reviewedBy: null };
  const pending = editor({ ...privatePhoto, credit: saved }, { 1: "Private credit review reason" }); assert.match(pending, /textarea aria-label="Credit review reason for photo 6"[^>]*>Private credit review reason/); assert.match(pending, /disabled=""[^>]*>Approve credits for photo #6/); assert.match(pending, /Rights checked for photo 6/); assert.match(pending, /Attribution checked for photo 6/);
  const confirmed = editor({ ...privatePhoto, credit: saved }, { 1: "Private credit review reason", 2: true, 3: true }); assert.doesNotMatch(confirmed, /disabled=""[^>]*>Approve credits for photo #6/);
  assert.match(editor({ ...privatePhoto, licenseName: "CC BY-SA 4.0" }), /original license is not supported/);
});
test("credit revision99 reserves final review slot; revision100 locks further credit mutations", () => {
  const saved = { ...credit, creditVersion: 99, status: "PENDING", changedBy: { adminName: "Admin" }, changedAt: "2026-10-06T00:00:00Z", reviewedBy: null };
  const markup = editor({ ...privatePhoto, credit: saved }, { 1: "Final explicit review reason", 2: true, 3: true }); assert.match(markup, /final revision slot is reserved/); assert.doesNotMatch(markup, /disabled=""[^>]*>Approve credits for photo #6/);
  const limit = editor({ ...privatePhoto, credit: { ...saved, creditVersion: 100 } }); assert.match(limit, /100-revision credit limit/); assert.equal((limit.match(/<fieldset[^>]*disabled=""/g) ?? []).length, 2);
});
test("signed-in mobile header reserves room for the fullscreen entry controls without changing desktop spacing", () => {
  const header = fs.readFileSync(path.join(__dirname, "../components/layout/SiteHeader.tsx"), "utf8");
  assert.match(header, /items-center gap-2 px-4 sm:gap-6 sm:px-6/);
  assert.match(header, /aria-label="My account"[^>]*px-2[^>]*sm:px-3/);
});
