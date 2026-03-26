#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TORCH_BASE_URL="http://localhost:8080"
TARGET_SERVER="http://localhost:8084/fhir"
CRTDL_FILE="$ROOT_DIR/src/test/resources/CrtdlItTests/CRTDL_test_it-kds-perf-w-ref.json"
LOG="$(mktemp -t torch-pause-resume-XXXXXXXXXX.log)"

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

cleanup() {
  kill "$PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "⏳ Waiting for export ID..."
while ! grep -q "TORCH_EXPORT_ID=" "$LOG"; do
  sleep 1
done
EXPORT_ID=$(grep "TORCH_EXPORT_ID=" "$LOG" | cut -d= -f2 | xargs)

echo "✅ Export ID: $EXPORT_ID"

sleep 3

echo "⏸️ Pause..."
curl -sf -X POST \
  "${TORCH_BASE_URL}/fhir/Task/${EXPORT_ID}/\$pause" \
  -H "Accept: application/fhir+json" \
  -o /dev/null \
  || fail "Pause failed"

echo "⏳ Ensure still running (202)..."
code="$(status_code "$EXPORT_ID")"
[[ "$code" == "202" ]] || fail "Unexpected status after pause (got $code)"

echo "▶️ Resume..."
curl -sf -X POST \
  "${TORCH_BASE_URL}/fhir/Task/${EXPORT_ID}/\$resume" \
  -H "Accept: application/fhir+json" \
  -o /dev/null \
  || fail "Resume failed"

echo "⏳ Waiting for completion..."
completed=false
for _ in $(seq 1 600); do
  code="$(status_code "$EXPORT_ID")"
  if [[ "$code" == "200" ]]; then
    completed=true
    break
  fi
  [[ "$code" == "410" ]] && fail "Cancelled unexpectedly"
  sleep 1
done

[[ "$completed" == "true" ]] || fail "Timed out waiting for completion (last status: $code)"

wait "$PID"

trap - EXIT
cleanup

echo "🎉 Pause/Resume test passed"
