#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TORCH_BASE_URL="http://localhost:8080"
TARGET_SERVER="http://localhost:8084/fhir"
CRTDL_FILE="$ROOT_DIR/src/test/resources/CrtdlItTests/CRTDL_test_it-kds-perf-w-ref.json"
LOG="$(mktemp -t torch-delete-XXXXXXXXXX.log)"

fail() { echo "❌ $*" >&2; exit 1; }

status_url() {
  printf '%s/fhir/__status/%s' "$TORCH_BASE_URL" "$1"
}

status_code() {
  curl -s -o /dev/null -w '%{http_code}' \
    -H "Accept: application/fhir+json" \
    "$(status_url "$1")"
}

echo "➡️ Start extraction..."
: > "$LOG"

TORCH_BASE_URL="$TORCH_BASE_URL" \
"$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" \
  -c "$CRTDL_FILE" -t "$TARGET_SERVER" > "$LOG" 2>&1 &
PID=$!

echo "⏳ Waiting for export ID..."
while ! grep -q "TORCH_EXPORT_ID=" "$LOG"; do sleep 1; done
EXPORT_ID=$(grep "TORCH_EXPORT_ID=" "$LOG" | cut -d= -f2 | xargs)

echo "✅ Export ID: $EXPORT_ID"

sleep 3

echo "🗑️ Delete..."
curl -s -X DELETE \
  "${TORCH_BASE_URL}/fhir/Task/${EXPORT_ID}" \
  -H "Accept: application/fhir+json" \
  -o /dev/null -w '%{http_code}' | grep -q 204 || fail "Delete failed"

echo "⏳ Waiting for 404..."
for _ in $(seq 1 30); do
  code="$(status_code "$EXPORT_ID")"
  [[ "$code" == "404" ]] && break
  sleep 1
done

code="$(status_code "$EXPORT_ID")"
[[ "$code" == "404" ]] || fail "Expected 404 after delete, got $code"

kill "$PID" || true

echo "🎉 Delete test passed"
