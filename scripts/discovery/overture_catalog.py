#!/usr/bin/env python3
"""Prepare a small licensed Overture Places catalog outside application requests.

Requires DuckDB 1.4.1. Source is fixed to Overture's public AWS bucket. No photos,
social profiles, email addresses, or owner identities are exported. Licenses are
resolved from https://docs.overturemaps.org/attribution/#places when a source row
omits its license; an explicit inconsistent license is rejected.
"""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import date, datetime, timezone
import ipaddress
import json
import math
import multiprocessing
import os
from pathlib import Path
import re
import struct
import sys
import tempfile
from typing import Any
import unicodedata
from urllib.parse import urlsplit
from uuid import UUID

DEFAULT_RELEASE = "2026-09-23.1"
DEFAULT_BBOX = (80.18, 12.92, 80.32, 13.17)
CATEGORIES = ("event_venue", "exhibition_and_trade_fair_venue")
LICENSES = {
    "meta": "CDLA-Permissive-2.0",
    "microsoft": "CDLA-Permissive-2.0",
    "pinmeto": "CDLA-Permissive-2.0",
    "krick": "CDLA-Permissive-2.0",
    "renderseo": "CDLA-Permissive-2.0",
    "dac": "CDLA-Permissive-2.0",
    "brightquery": "CDLA-Permissive-2.0",
    "foursquare": "Apache-2.0",
    "alltheplaces": "CC0-1.0",
    # The release annotates derived confidence with this explicit source license.
    # Do not infer a license when an Overture-origin row omits it.
    "overture": "CDLA-Permissive-2.0",
}
MAX_RECORDS = 500
MAX_OUTPUT_BYTES = 5 * 1024 * 1024


class CatalogError(ValueError):
    """A sample cannot safely be represented by the catalog contract."""


def release_path(release: str) -> str:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}\.\d{1,2}", release):
        raise CatalogError("Release must be a pinned YYYY-MM-DD.N identifier")
    try:
        date.fromisoformat(release.split(".")[0])
    except ValueError as exc:
        raise CatalogError("Release date is invalid") from exc
    return f"s3://overturemaps-us-west-2/release/{release}/theme=places/type=place/*.parquet"


def validate_bbox(bbox: tuple[float, ...]) -> tuple[float, float, float, float]:
    if len(bbox) != 4 or any(not math.isfinite(v) for v in bbox):
        raise CatalogError("bbox requires four finite coordinates")
    west, south, east, north = bbox
    if not (-180 <= west < east <= 180 and -90 <= south < north <= 90):
        raise CatalogError("bbox coordinates are out of range or reversed")
    if east - west > .5 or north - south > .5:
        raise CatalogError("A catalog region may span at most 0.5 degrees on each axis")
    return west, south, east, north


def clean_text(value: Any, maximum: int, *, required: bool = False) -> str | None:
    if value is None:
        if required:
            raise CatalogError("Required source text is missing")
        return None
    if not isinstance(value, str):
        raise CatalogError("Source text has an unexpected type")
    value = value.strip()
    if not value:
        if required:
            raise CatalogError("Required source text is blank")
        return None
    if len(value.encode("utf-16-le")) // 2 > maximum or any(unicodedata.category(char) in {"Cc", "Cf"} for char in value):
        raise CatalogError("Source text is oversized or contains control characters")
    return value


def safe_website(value: Any) -> str | None:
    try:
        value = clean_text(value, 2000)
        if value is None:
            return None
        parsed = urlsplit(value)
        if parsed.scheme not in {"https", "http"} or not parsed.hostname:
            return None
        if parsed.username or parsed.password or parsed.port not in {None, 80, 443}:
            return None
        hostname = parsed.hostname.rstrip(".").lower()
        if hostname == "localhost" or hostname.endswith((".localhost", ".local")) or "." not in hostname:
            return None
        try:
            if not ipaddress.ip_address(hostname).is_global:
                return None
        except ValueError:
            pass
        return value
    except (ValueError, CatalogError):
        return None


def normalize_sources(raw: Any) -> list[dict[str, Any]]:
    if not isinstance(raw, list) or not raw or len(raw) > 100:
        raise CatalogError("Missing or oversized source provenance")
    result: dict[tuple[str, str, str | None], dict[str, Any]] = {}
    # Preserve every field-level origin, including origins outside the root record.
    for source in raw:
        if not isinstance(source, dict):
            raise CatalogError("Source provenance has an unexpected type")
        dataset = clean_text(source.get("dataset"), 80, required=True).casefold()
        expected = LICENSES.get(dataset)
        if expected is None:
            raise CatalogError("Source dataset is outside the approved license mapping")
        explicit = clean_text(source.get("license"), 80)
        if dataset == "overture" and explicit is None:
            raise CatalogError("Overture-derived provenance requires an explicit license")
        if explicit is not None and explicit != expected:
            raise CatalogError("Explicit source license does not match its dataset")
        record_id = clean_text(source.get("record_id"), 512)
        key = (dataset, expected, record_id)
        result[key] = {"dataset": dataset, "license": expected, "recordId": record_id}
    if len(result) > 20:
        raise CatalogError("Too many distinct source origins for the catalog contract")
    return sorted(result.values(), key=lambda source: (source["dataset"], source["license"], source["recordId"] or ""))


def point_coordinates(geometry: Any) -> tuple[float, float]:
    """Read only standard Point WKB; do not guess coordinates from rounded bbox."""
    if not isinstance(geometry, bytes) or len(geometry) != 21 or geometry[0] not in {0, 1}:
        raise CatalogError("Expected a two-dimensional Point WKB")
    endian = "<" if geometry[0] else ">"
    kind, longitude, latitude = struct.unpack(endian + "Idd", geometry[1:])
    if kind != 1 or not math.isfinite(longitude) or not math.isfinite(latitude):
        raise CatalogError("Expected a finite two-dimensional Point WKB")
    return longitude, latitude


def normalize_record(row: dict[str, Any], bbox: tuple[float, ...], min_confidence: float) -> dict[str, Any]:
    try:
        source_id = clean_text(row.get("id"), 36, required=True)
        canonical_id = str(UUID(source_id))
        if canonical_id != source_id.lower():
            raise ValueError("Noncanonical ID")
    except (ValueError, AttributeError) as exc:
        raise CatalogError("Place ID must be a canonical UUID") from exc
    category = row.get("category")
    if category not in CATEGORIES:
        raise CatalogError("Unsupported source venue category")
    confidence = row.get("confidence")
    if isinstance(confidence, bool) or not isinstance(confidence, (int, float)) or not math.isfinite(confidence):
        raise CatalogError("Confidence is missing or invalid")
    if not min_confidence <= confidence <= 1:
        raise CatalogError("Confidence is below threshold or invalid")
    status = row.get("operating_status")
    if status not in {None, "open"}:
        raise CatalogError("Source operating status is closed or unsupported")
    longitude, latitude = point_coordinates(row.get("geometry"))
    west, south, east, north = validate_bbox(bbox)
    if not west <= longitude <= east or not south <= latitude <= north:
        raise CatalogError("Point is outside the requested region")
    addresses = row.get("addresses") or []
    if not isinstance(addresses, list):
        raise CatalogError("Addresses have an unexpected type")
    address = next((item for item in addresses if isinstance(item, dict) and item.get("country") == "IN"), {})
    # Neither region label nor freeform address is used to infer locality or area.
    phone = None
    for candidate in row.get("phones") or []:
        candidate = clean_text(candidate, 20)
        if candidate and re.fullmatch(r"\+?[0-9][0-9 ()-]{5,18}", candidate):
            phone = candidate
            break
    website = next((url for url in (safe_website(item) for item in row.get("websites") or []) if url), None)
    return {
        "id": canonical_id,
        "name": clean_text(row.get("name"), 180, required=True),
        "category": category,
        "address": clean_text(address.get("freeform"), 120),
        "city": clean_text(address.get("locality"), 120),
        "area": clean_text(address.get("sublocality"), 120),
        "postcode": clean_text(address.get("postcode"), 16),
        "latitude": latitude,
        "longitude": longitude,
        "phone": phone,
        "website": website,
        "confidence": float(confidence),
        "operatingStatus": status or "unknown",
        "sources": normalize_sources(row.get("sources")),
    }


def build_catalog(rows: list[dict[str, Any]], *, release: str, city: str, bbox: tuple[float, ...],
                  max_records: int, min_confidence: float, generated_at: str | None = None) -> tuple[dict[str, Any], dict[str, Any]]:
    release_path(release)
    bbox = validate_bbox(bbox)
    if not 1 <= max_records <= MAX_RECORDS or len(rows) > MAX_RECORDS + 1:
        raise CatalogError("Record count exceeds the bounded catalog size")
    if not .5 <= min_confidence <= 1 or not math.isfinite(min_confidence):
        raise CatalogError("Minimum confidence must be between 0.5 and 1")
    city = clean_text(city, 120, required=True)
    venues: dict[str, dict[str, Any]] = {}
    rejected = Counter()
    duplicate_count = 0
    # Order before normalization so duplicate handling is independent of parquet order.
    for row in sorted(rows, key=lambda item: (str(item.get("id", "")), str(item.get("name", "")))):
        try:
            venue = normalize_record(row, bbox, min_confidence)
        except CatalogError as exc:
            rejected[str(exc)] += 1
            continue
        if venue["id"] in venues:
            # Duplicate IDs must not silently discard different names or provenance.
            if venues[venue["id"]] != venue:
                raise CatalogError("Conflicting records share one place ID")
            duplicate_count += 1
        else:
            venues[venue["id"]] = venue
    ordered = [venues[key] for key in sorted(venues)]
    truncated = len(rows) > max_records
    ordered = ordered[:max_records]
    catalog = {
        "schemaVersion": 1,
        "provider": "OVERTURE_PLACES",
        "release": release,
        "generatedAt": generated_at or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "region": {"city": city, "bbox": list(bbox)},
        "venues": ordered,
    }
    report = {
        "release": release,
        "region": catalog["region"],
        "sourceRows": len(rows),
        "catalogVenues": len(ordered),
        "duplicateIds": duplicate_count,
        "possibleSameNameLocations": sum(count - 1 for count in Counter((venue["name"].casefold(), round(venue["latitude"], 4), round(venue["longitude"], 4)) for venue in ordered).values() if count > 1),
        "truncated": truncated,
        "rejections": dict(sorted(rejected.items())),
        "categories": dict(sorted(Counter(venue["category"] for venue in ordered).items())),
        "operatingStatuses": dict(sorted(Counter(venue["operatingStatus"] for venue in ordered).items())),
        "sources": dict(sorted(Counter(source["dataset"] for venue in ordered for source in venue["sources"]).items())),
        "missingFields": {field: sum(venue[field] is None for venue in ordered) for field in ("address", "city", "area", "postcode", "phone", "website")},
        "representativeNames": [venue["name"] for venue in ordered[:8]],
    }
    return catalog, report


def _extract_worker(connection: Any, release: str, bbox: tuple[float, ...], max_records: int,
                    min_confidence: float, parent_workspace: str) -> None:
    try:
        import duckdb
        with tempfile.TemporaryDirectory(prefix="query-", dir=parent_workspace) as workspace:
            db = duckdb.connect(config={"threads": 2, "memory_limit": "512MB", "temp_directory": workspace})
            try:
                db.execute("SET max_temp_directory_size='256MB'")
                db.execute("INSTALL httpfs")
                db.execute("LOAD httpfs")
                db.execute("SET s3_region='us-west-2'")
                db.execute("SET http_timeout=20")
                db.execute("SET http_retries=1")
                path = release_path(release)
                columns = {row[0]: row[1] for row in db.execute("DESCRIBE SELECT * FROM read_parquet(?)", [path]).fetchall()}
                required = {"id", "names", "taxonomy", "geometry", "sources", "confidence", "addresses", "phones", "websites", "operating_status", "bbox"}
                if not required <= columns.keys() or '"primary" VARCHAR' not in columns["taxonomy"]:
                    raise CatalogError("Pinned source has an unsupported places schema")
                west, south, east, north = bbox
                query = """
                    SELECT id, names.primary AS name, taxonomy.primary AS category,
                           geometry, sources, confidence, addresses, phones, websites,
                           operating_status
                    FROM read_parquet(?)
                    WHERE bbox.xmin BETWEEN ? AND ? AND bbox.ymin BETWEEN ? AND ?
                      AND taxonomy.primary IN (?, ?) AND confidence >= ?
                      AND (operating_status IS NULL OR operating_status = 'open')
                    ORDER BY id LIMIT ?
                """
                cursor = db.execute(query, [path, west, east, south, north, *CATEGORIES, min_confidence, max_records + 1])
                names = [column[0] for column in cursor.description]
                rows = [dict(zip(names, values)) for values in cursor.fetchall()]
                connection.send({"rows": rows, "sourceSchema": {key: columns[key] for key in ("taxonomy", "addresses", "sources", "geometry")}})
            finally:
                db.close()
    except Exception as exc:
        # No source data, contact details, URLs, or arbitrary DuckDB text in error output.
        connection.send({"error": type(exc).__name__})
    finally:
        connection.close()


def extract_rows(release: str, bbox: tuple[float, ...], max_records: int, min_confidence: float,
                 timeout_seconds: int) -> dict[str, Any]:
    release_path(release)
    bbox = validate_bbox(bbox)
    if not 1 <= max_records <= MAX_RECORDS or not 30 <= timeout_seconds <= 300:
        raise CatalogError("Maximum records must be 1–500 and timeout 30–300 seconds")
    if not .5 <= min_confidence <= 1 or not math.isfinite(min_confidence):
        raise CatalogError("Minimum confidence must be between 0.5 and 1")
    context = multiprocessing.get_context("spawn")
    receiving, sending = context.Pipe(duplex=False)
    workspace = tempfile.TemporaryDirectory(prefix="venuemart-overture-query-")
    process = context.Process(target=_extract_worker, args=(sending, release, bbox, max_records, min_confidence, workspace.name), daemon=True)
    process.start()
    sending.close()
    try:
        if not receiving.poll(timeout_seconds):
            raise CatalogError("Public catalog query exceeded its time limit")
        try:
            result = receiving.recv()
        except EOFError as exc:
            raise CatalogError("Public catalog query exited without a result") from exc
        if "error" in result:
            raise CatalogError(f"Public catalog query failed ({result['error']}); check connectivity, dependencies, or release schema")
        return result
    finally:
        receiving.close()
        process.join(timeout=1)
        if process.is_alive():
            process.terminate()
            process.join(timeout=2)
        if process.is_alive():
            process.kill()
            process.join(timeout=2)
        workspace.cleanup()


def write_catalog(catalog: dict[str, Any], output: Path, *, overwrite: bool = False) -> None:
    encoded = (json.dumps(catalog, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")
    if len(encoded) > MAX_OUTPUT_BYTES:
        raise CatalogError("Catalog exceeds the 5 MiB output limit")
    output = output.absolute()
    if not output.parent.is_dir() or output.is_symlink():
        raise CatalogError("Output parent must exist and output must not be a symlink")
    descriptor, temporary = tempfile.mkstemp(prefix=".venuemart-overture-", suffix=".json", dir=output.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(encoded)
            stream.flush()
            os.fsync(stream.fileno())
        if overwrite:
            os.replace(temporary, output)
        else:
            # Link is atomic and refuses a preexisting target, including racing writers.
            os.link(temporary, output)
    except FileExistsError as exc:
        raise CatalogError("Output already exists; use --overwrite to replace it explicitly") from exc
    finally:
        Path(temporary).unlink(missing_ok=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        epilog=("When sharing a catalog containing CDLA-licensed sources, copy "
                "scripts/discovery/licenses/CDLA-Permissive-2.0.txt alongside the catalog. "
                "Only the CDLA agreement is bundled. Foursquare sources additionally require "
                "the Apache-2.0 license and Foursquare NOTICE; other applicable license "
                "materials must be supplied before distributing those source records. "
                "See https://docs.overturemaps.org/attribution/#places."),
    )
    parser.add_argument("--release", default=DEFAULT_RELEASE)
    parser.add_argument("--city", default="Chennai", help="Catalog label only; source locality is preserved separately")
    parser.add_argument("--bbox", nargs=4, type=float, default=DEFAULT_BBOX, metavar=("WEST", "SOUTH", "EAST", "NORTH"))
    parser.add_argument("--max-records", type=int, default=100)
    parser.add_argument("--min-confidence", type=float, default=.8)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args(argv)
    try:
        # Validate before network access or writing any destination.
        release_path(args.release)
        validate_bbox(tuple(args.bbox))
        clean_text(args.city, 120, required=True)
        if not .5 <= args.min_confidence <= 1 or not math.isfinite(args.min_confidence):
            raise CatalogError("Minimum confidence must be between 0.5 and 1")
        if args.output.exists() and not args.overwrite:
            raise CatalogError("Output already exists; use --overwrite to replace it explicitly")
        extracted = extract_rows(args.release, tuple(args.bbox), args.max_records, args.min_confidence, args.timeout_seconds)
        catalog, report = build_catalog(extracted["rows"], release=args.release, city=args.city, bbox=tuple(args.bbox), max_records=args.max_records, min_confidence=args.min_confidence)
        write_catalog(catalog, args.output, overwrite=args.overwrite)
        report["sourceSchema"] = extracted["sourceSchema"]
        report["output"] = str(args.output.absolute())
        print(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))
        return 0
    except CatalogError as exc:
        print(f"Catalog extraction stopped: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
