#!/usr/bin/env bash
set -euo pipefail

SOURCE_REMOTE="${SOURCE_REMOTE:-}"
TARGET_REMOTE="${TARGET_REMOTE:-}"
MODE="${MODE:-copy}"
DRY_RUN="${DRY_RUN:-yes}"

if [ -z "$SOURCE_REMOTE" ] || [ -z "$TARGET_REMOTE" ]; then
  echo "Usage: SOURCE_REMOTE=hetzner-minio:venue-media TARGET_REMOTE=aws-s3:venue-media $0" >&2
  exit 1
fi

if ! command -v rclone >/dev/null 2>&1; then
  echo "rclone is required. Install and configure remotes first." >&2
  exit 1
fi

ARGS=(--checksum --progress)

if [ "$DRY_RUN" != "no" ]; then
  ARGS+=(--dry-run)
fi

case "$MODE" in
  copy)
    rclone copy "$SOURCE_REMOTE" "$TARGET_REMOTE" "${ARGS[@]}"
    ;;
  sync)
    rclone sync "$SOURCE_REMOTE" "$TARGET_REMOTE" "${ARGS[@]}"
    ;;
  *)
    echo "MODE must be copy or sync." >&2
    exit 1
    ;;
esac

echo "Media $MODE completed from $SOURCE_REMOTE to $TARGET_REMOTE"
