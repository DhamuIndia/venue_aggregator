"""Offline contract, provenance, and output-safety tests for catalog preparation."""

from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("overture_catalog", Path(__file__).with_name("overture_catalog.py"))
catalog_tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(catalog_tool)


def source_row(**changes):
    row = {
        "id": "08df8947-1544-475d-9d81-40984bbdf110",
        "name": "Example Public Convention Venue",
        "category": "event_venue",
        "geometry": b"\x01" + struct.pack("<Idd", 1, 80.25, 13.05),
        "confidence": .91,
        "operating_status": "open",
        "addresses": [{"freeform": "Example Road", "locality": "Chennai", "postcode": "600001", "country": "IN"}],
        "phones": ["+919000000000"],
        "websites": ["https://example.org/venue"],
        "sources": [{"dataset": "meta", "record_id": "public-record-1", "license": None, "property": ""}],
    }
    row.update(changes)
    return row


def build(rows, **changes):
    options = {
        "release": "2026-09-23.1", "city": "Chennai", "bbox": catalog_tool.DEFAULT_BBOX,
        "max_records": 100, "min_confidence": .8, "generated_at": "2026-10-05T00:00:00Z",
    }
    options.update(changes)
    return catalog_tool.build_catalog(rows, **options)


class OvertureCatalogTests(unittest.TestCase):
    def test_pinned_release_never_accepts_paths_sql_or_invalid_date(self):
        for release in ("latest", "2026-09-23.1/../../secret", "2026-09-23.1'; DROP TABLE x; --", "2026-02-30.1"):
            with self.subTest(release=release), self.assertRaises(catalog_tool.CatalogError):
                catalog_tool.release_path(release)

    def test_bounds_reject_global_scan_reversed_or_nonfinite_coordinates(self):
        for bbox in ((-180, -90, 180, 90), (80.32, 13, 80.18, 13.17), (80, float("nan"), 80.1, 13), (180, 13, 181, 13.1)):
            with self.subTest(bbox=bbox), self.assertRaises(catalog_tool.CatalogError):
                catalog_tool.validate_bbox(bbox)

    def test_all_field_level_sources_preserved_and_missing_licenses_resolved(self):
        provenance = [
            {"dataset": "Meta", "record_id": "A", "property": ""},
            {"dataset": "Foursquare", "license": "Apache-2.0", "record_id": "B", "property": "/addresses/0"},
            {"dataset": "Meta", "record_id": "C", "property": "/names"},
            {"dataset": "Meta", "record_id": "A", "property": "/geometry"},
        ]
        catalog, report = build([source_row(sources=provenance)])
        self.assertEqual(catalog["venues"][0]["sources"], [
            {"dataset": "foursquare", "license": "Apache-2.0", "recordId": "B"},
            {"dataset": "meta", "license": "CDLA-Permissive-2.0", "recordId": "A"},
            {"dataset": "meta", "license": "CDLA-Permissive-2.0", "recordId": "C"},
        ])
        self.assertEqual(report["catalogVenues"], 1)

    def test_unknown_source_or_explicit_license_mismatch_rejects_whole_record(self):
        for source in ({"dataset": "unknown", "license": "CDLA-Permissive-2.0"}, {"dataset": "meta", "license": "Apache-2.0"}):
            with self.subTest(source=source):
                catalog, report = build([source_row(sources=[{"dataset": "meta"}, source])])
                self.assertEqual(catalog["venues"], [])
                self.assertEqual(sum(report["rejections"].values()), 1)

    def test_overture_derived_source_requires_its_explicit_release_license(self):
        sources = [{"dataset": "Microsoft", "license": "CDLA-Permissive-2.0"},
                   {"dataset": "Overture", "property": "/properties/confidence", "license": "CDLA-Permissive-2.0"}]
        self.assertEqual(len(build([source_row(sources=sources)])[0]["venues"][0]["sources"]), 2)
        sources[1]["license"] = None
        self.assertEqual(build([source_row(sources=sources)])[0]["venues"], [])

    def test_missing_locality_not_inferred_from_region_or_freeform(self):
        catalog, report = build([source_row(addresses=[{"country": "IN", "freeform": "Chennai Example Road", "locality": None}])])
        venue = catalog["venues"][0]
        self.assertIsNone(venue["city"])
        self.assertIsNone(venue["area"])
        self.assertEqual(venue["address"], "Chennai Example Road")
        self.assertEqual(report["missingFields"]["city"], 1)

    def test_missing_operating_status_is_unknown_not_invented_open(self):
        catalog, report = build([source_row(operating_status=None)])
        self.assertEqual(catalog["venues"][0]["operatingStatus"], "unknown")
        self.assertEqual(report["operatingStatuses"], {"unknown": 1})

    def test_closed_uncertain_noncategories_and_bad_ids_are_excluded(self):
        rows = [source_row(operating_status="permanently_closed"), source_row(confidence=.79), source_row(category="wedding_planning"), source_row(id="not-a-uuid")]
        catalog, report = build(rows)
        self.assertEqual(catalog["venues"], [])
        self.assertEqual(sum(report["rejections"].values()), 4)

    def test_oversized_or_hidden_control_facts_rejected_without_truncation(self):
        for changes in ({"name": "N" * 181}, {"name": "Name\u200b"},
                        {"addresses": [{"country": "IN", "freeform": "A" * 121}]},
                        {"phones": ["+" + "9" * 21]},
                        {"addresses": [{"country": "IN", "postcode": "9" * 17}]}):
            with self.subTest(changes=changes):
                catalog, report = build([source_row(**changes)])
                self.assertEqual(catalog["venues"], [])
                self.assertEqual(sum(report["rejections"].values()), 1)

    def test_excess_distinct_source_origins_rejected_not_discarded(self):
        origins = [{"dataset": "meta", "record_id": f"record-{index}"} for index in range(21)]
        self.assertEqual(build([source_row(sources=origins)])[0]["venues"], [])

    def test_wkb_is_exact_and_bbox_does_not_replace_geometry(self):
        row = source_row(geometry=b"\x00" + struct.pack(">Idd", 1, 80.241234, 13.012345))
        venue = build([row])[0]["venues"][0]
        self.assertEqual(venue["longitude"], 80.241234)
        self.assertEqual(venue["latitude"], 13.012345)
        for geometry in (b"invalid", b"\x01" + struct.pack("<Idd", 2, 80.25, 13.05), b"\x01" + struct.pack("<Idd", 1, 81, 13.05)):
            with self.subTest(geometry=geometry):
                self.assertEqual(build([source_row(geometry=geometry)])[0]["venues"], [])

    def test_unsafe_urls_are_removed_without_fetching_or_rejecting_venue(self):
        for url in ("javascript:alert(1)", "https://user:pass@example.org", "http://127.0.0.1/admin", "http://192.168.1.1/", "https://localhost/", "https://host.local/", "https://example.org:8443/"):
            with self.subTest(url=url):
                self.assertIsNone(build([source_row(websites=[url])])[0]["venues"][0]["website"])

    def test_duplicate_ids_are_deterministic_but_conflicting_ids_fail(self):
        first = source_row()
        second = source_row(id="0adf8947-1544-475d-9d81-40984bbdf111", name="Second Venue")
        original, report = build([second, first, deepcopy(first)])
        reversed_catalog, _ = build([first, deepcopy(first), second])
        self.assertEqual(original, reversed_catalog)
        self.assertEqual(report["duplicateIds"], 1)
        with self.assertRaisesRegex(catalog_tool.CatalogError, "Conflicting"):
            build([first, source_row(name="Different Venue")])

    def test_maximum_and_truncation_are_explicit(self):
        catalog, report = build([source_row(), source_row(id="0adf8947-1544-475d-9d81-40984bbdf111")], max_records=1)
        self.assertEqual(len(catalog["venues"]), 1)
        self.assertTrue(report["truncated"])
        for count in (0, 501):
            with self.subTest(count=count), self.assertRaises(catalog_tool.CatalogError):
                build([], max_records=count)

    def test_atomic_output_refuses_replacement_unless_explicit(self):
        catalog, _ = build([source_row()])
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "catalog.json"
            catalog_tool.write_catalog(catalog, target)
            original = target.read_bytes()
            catalog["venues"] = []
            with self.assertRaisesRegex(catalog_tool.CatalogError, "already exists"):
                catalog_tool.write_catalog(catalog, target)
            self.assertEqual(target.read_bytes(), original)
            catalog_tool.write_catalog(catalog, target, overwrite=True)
            self.assertEqual(json.loads(target.read_text())["venues"], [])
            self.assertEqual([item.name for item in target.parent.iterdir()], ["catalog.json"])

    def test_output_never_follows_symlink(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "original.json"
            destination.write_text("original")
            target = Path(directory) / "linked.json"
            target.symlink_to(destination)
            with self.assertRaises(catalog_tool.CatalogError):
                catalog_tool.write_catalog(build([])[0], target, overwrite=True)
            self.assertEqual(destination.read_text(), "original")


if __name__ == "__main__":
    unittest.main()
