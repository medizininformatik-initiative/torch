#!/bin/bash
set -euo pipefail

# Seeds the source FHIR server with copies of the MII KDS testdata, so that
# extraction jobs run long enough to be paused, cancelled or deleted.

COPIES=2000
SOURCE_SERVER="http://localhost:8083/fhir"
KDS_URL="https://github.com/medizininformatik-initiative/mii-testdata/releases/download/v1.0.1/kds-testdata-2024.0.1.zip"
KDS_SHA256="d5fe1fdf18a0ff4df4016ebb82e72fe85efd0d68e65b8ed7d0f29a018e5d55bd"
WORK_DIR="$(mktemp -d -t torch-kds-XXXXXXXXXX)"
trap 'rm -rf "$WORK_DIR"' EXIT

fail() { echo "❌ $*" >&2; exit 1; }

echo "➡️ Downloading KDS testdata..."
curl -fsSL -o "$WORK_DIR/kds.zip" "$KDS_URL"
echo "$KDS_SHA256  $WORK_DIR/kds.zip" | sha256sum -c --quiet -
unzip -q "$WORK_DIR/kds.zip" -d "$WORK_DIR"

# Every copy gets its own resource ids. Ids are re-prefixed instead of suffixed
# because some KDS ids already have the maximum FHIR id length of 64.
echo "➡️ Creating $COPIES copies..."
mkdir "$WORK_DIR/upload"
jq -c . "$WORK_DIR/kds-testdata-2024.0.1/resources/Bundle-mii-exa-test-data-bundle.json" \
  | awk -v copies="$COPIES" '{ for (i = 1; i <= copies; i++) { copy = $0; gsub(/mii-exa-/, "c" i "-", copy); print copy } }' \
  > "$WORK_DIR/upload/kds.ndjson"

echo "📤 Uploading to $SOURCE_SERVER..."
blazectl upload --server "$SOURCE_SERVER" --concurrency 4 "$WORK_DIR/upload"

# The task API tests select the female patients, of which every copy has one.
FEMALE_COUNT=$(curl -s "$SOURCE_SERVER/Patient?gender=female&_summary=count" | jq -r '.total // 0')
[[ "$FEMALE_COUNT" -eq "$COPIES" ]] || fail "Expected $COPIES female patients, got $FEMALE_COUNT"
echo "✅ Seeded $FEMALE_COUNT female patients"
