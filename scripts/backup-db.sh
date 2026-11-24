#!/usr/bin/env bash
# Dumps the whole database and uploads it to the backup bucket.
# Run by .github/workflows/backup.yml every night, and by `make backup` locally.
#
# Needs Docker (pg_dump and the S3 client run in containers, so nothing else is installed).
# Configuration comes from environment variables:
#   DATABASE_URL           postgresql://user:password@host:5432/dbname (a role that BYPASSES
#                          row-level security, or the dump would silently miss rows)
#   STORAGE_ENDPOINT       S3 endpoint, e.g. https://<account-id>.r2.cloudflarestorage.com
#   STORAGE_BUCKET         the backup bucket (not the attachments bucket)
#   AWS_ACCESS_KEY_ID      keys for that bucket
#   AWS_SECRET_ACCESS_KEY
#   AWS_DEFAULT_REGION     "auto" for R2
#   DOCKER_NETWORK         optional: attach the containers to this network (local compose only)
set -euo pipefail

: "${DATABASE_URL:?}" "${STORAGE_ENDPOINT:?}" "${STORAGE_BUCKET:?}"
: "${AWS_ACCESS_KEY_ID:?}" "${AWS_SECRET_ACCESS_KEY:?}" "${AWS_DEFAULT_REGION:?}"

# pg_dump must be at least as new as the server; a newer client can dump an older server.
PG_IMAGE=postgres:18-alpine
network=()
[ -n "${DOCKER_NETWORK:-}" ] && network=(--network "$DOCKER_NETWORK")

key="backups/hisaab-$(date -u +%Y%m%dT%H%M%SZ).dump"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

# 1. Dump to a file first, so a dump that dies halfway is never uploaded.
#    Custom format is already compressed and lets pg_restore pick what to restore.
docker run --rm "${network[@]}" "$PG_IMAGE" pg_dump --format=custom "$DATABASE_URL" >"$tmp"

# 2. Prove the file is a readable archive (fails on a truncated or empty dump).
docker run --rm -i "$PG_IMAGE" pg_restore --list <"$tmp" >/dev/null

# 3. Upload from stdin. "when_required" stops the AWS CLI adding checksum headers R2 can reject
#    (the same setting ObjectStorage uses in the API).
docker run --rm -i "${network[@]}" \
  -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION \
  -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
  amazon/aws-cli --endpoint-url "$STORAGE_ENDPOINT" \
  s3 cp - "s3://$STORAGE_BUCKET/$key" <"$tmp"

echo "Backed up $(wc -c <"$tmp" | tr -d ' ') bytes to s3://$STORAGE_BUCKET/$key"
