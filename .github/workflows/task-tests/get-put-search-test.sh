#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TORCH_BASE_URL="http://localhost:8080"
TARGET_SERVER="http://localhost:8084/fhir"
CRTDL_FILE="$ROOT_DIR/src/test/resources/CrtdlItTests/CRTDL_test_it-kds-perf-w-ref.json"
LOG="$(mktemp -t torch-get-put-search-XXXXXXXXXX.log)"

fail() { echo "❌ $*" >&2; exit 1; }

task_url() {
  printf '%s/fhir/Task/%s' "$TORCH_BASE_URL" "$1"
}

cleanup() { kill "${PID:-}" 2>/dev/null || true; }
trap cleanup EXIT

# ── Submit a job and capture the export ID ────────────────────────────────────

echo "➡️  Start extraction..."
: > "$LOG"

TORCH_BASE_URL="$TORCH_BASE_URL" \
"$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" \
  -c "$CRTDL_FILE" -t "$TARGET_SERVER" > "$LOG" 2>&1 &
PID=$!

echo "⏳ Waiting for export ID..."
while ! grep -q "TORCH_EXPORT_ID=" "$LOG"; do
  sleep 1
done
EXPORT_ID=$(grep "TORCH_EXPORT_ID=" "$LOG" | tail -1 | cut -d= -f2- | xargs)
echo "✅ Export ID: $EXPORT_ID"

# ── GET /fhir/Task/{id} ────────────────────────────────────────────────────────

echo "🔍 GET Task by ID..."
TASK_JSON=$(curl -sf \
  -H "Accept: application/fhir+json" \
  "$(task_url "$EXPORT_ID")")

echo "$TASK_JSON" | jq -e '.resourceType == "Task"' > /dev/null \
  || fail "GET Task did not return a Task resource"

TASK_ID=$(echo "$TASK_JSON" | jq -r '.id')
[[ "$TASK_ID" == "$EXPORT_ID" ]] \
  || fail "GET Task returned wrong id: expected $EXPORT_ID, got $TASK_ID"

TASK_STATUS=$(echo "$TASK_JSON" | jq -r '.status')
echo "   Task status: $TASK_STATUS"

VERSION=$(echo "$TASK_JSON" | jq -r '.meta.versionId')
[[ "$VERSION" =~ ^[0-9]+$ ]] \
  || fail "Task versionId is not numeric: $VERSION"
echo "   Task version: $VERSION"

# ── GET /fhir/Task/{id} for unknown ID returns 404 ───────────────────────────

echo "🔍 GET Task with unknown ID expects 404..."
UNKNOWN_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -H "Accept: application/fhir+json" \
  "$(task_url "00000000-0000-0000-0000-000000000000")")
[[ "$UNKNOWN_CODE" == "404" ]] \
  || fail "Expected 404 for unknown task, got $UNKNOWN_CODE"

# ── PUT /fhir/Task/{id} — missing If-Match returns 428 ───────────────────────

echo "🔒 PUT without If-Match expects 428..."
MISSING_IFMATCH_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -X PUT "$(task_url "$EXPORT_ID")" \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"Task","priority":"asap"}')
[[ "$MISSING_IFMATCH_CODE" == "428" ]] \
  || fail "Expected 428 when If-Match is absent, got $MISSING_IFMATCH_CODE"

# ── PUT /fhir/Task/{id} — stale version returns 412 ──────────────────────────

echo "🔒 PUT with stale version expects 412..."
STALE_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -X PUT "$(task_url "$EXPORT_ID")" \
  -H "Content-Type: application/fhir+json" \
  -H "If-Match: W/\"99999\"" \
  -d '{"resourceType":"Task","priority":"asap"}')
[[ "$STALE_CODE" == "412" ]] \
  || fail "Expected 412 for stale version, got $STALE_CODE"

# ── PUT /fhir/Task/{id} — correct version changes priority to HIGH ────────────

echo "⬆️  PUT priority to HIGH (version $VERSION)..."
UPDATED_JSON=$(curl -sf \
  -X PUT "$(task_url "$EXPORT_ID")" \
  -H "Content-Type: application/fhir+json" \
  -H "If-Match: W/\"$VERSION\"" \
  -d '{"resourceType":"Task","priority":"asap"}')

echo "$UPDATED_JSON" | jq -e '.priority == "asap"' > /dev/null \
  || fail "PUT did not update priority to asap"

NEW_VERSION=$(echo "$UPDATED_JSON" | jq -r '.meta.versionId')
[[ "$NEW_VERSION" =~ ^[0-9]+$ ]] \
  || fail "Updated versionId is not numeric: $NEW_VERSION"
(( NEW_VERSION > VERSION )) \
  || fail "Version was not incremented after PUT (old=$VERSION, new=$NEW_VERSION)"
echo "   New version: $NEW_VERSION"

# ── GET /fhir/Task?unsupportedParam — lenient by default ─────────────────────

echo "🙂 GET /fhir/Task with unsupported param is ignored by default..."
LENIENT_JSON=$(curl -sf \
  -H "Accept: application/fhir+json" \
  "${TORCH_BASE_URL}/fhir/Task?subject=Patient/123")

echo "$LENIENT_JSON" | jq -e '.resourceType == "Bundle"' > /dev/null \
  || fail "Expected Bundle for lenient unsupported search param"

echo "$LENIENT_JSON" | jq -e --arg id "$EXPORT_ID" '
  any(.entry[]?; .resource.id == $id)
' > /dev/null || fail "Lenient search did not include our job $EXPORT_ID"

# ── GET /fhir/Task?unsupportedParam with Prefer: handling=strict — 400 ───────

echo "🚫 GET /fhir/Task with unsupported param and strict handling expects 400..."
STRICT_RESPONSE_FILE="$(mktemp -t torch-strict-search-XXXXXXXXXX.json)"

STRICT_CODE=$(curl -s \
  -o "$STRICT_RESPONSE_FILE" \
  -w '%{http_code}' \
  -H "Accept: application/fhir+json" \
  -H "Prefer: handling=strict" \
  "${TORCH_BASE_URL}/fhir/Task?subject=Patient/123")

[[ "$STRICT_CODE" == "400" ]] \
  || fail "Expected 400 for strict unsupported search param, got $STRICT_CODE"

jq -e '.resourceType == "OperationOutcome"' "$STRICT_RESPONSE_FILE" > /dev/null \
  || fail "Strict unsupported search did not return OperationOutcome"

jq -e '.issue[0].code == "invalid"' "$STRICT_RESPONSE_FILE" > /dev/null \
  || fail "Strict unsupported search did not return issue code invalid"

jq -e '.issue[0].diagnostics | contains("Unsupported search parameters")' "$STRICT_RESPONSE_FILE" > /dev/null \
  || fail "Strict unsupported search diagnostics missing expected message"

rm -f "$STRICT_RESPONSE_FILE"

# ── GET /fhir/Task?status=BOGUS with Prefer: handling=strict — 400 ───────────

echo "🚫 GET /fhir/Task with invalid status and strict handling expects 400..."
STRICT_STATUS_FILE="$(mktemp -t torch-strict-status-XXXXXXXXXX.json)"

STRICT_STATUS_CODE=$(curl -s \
  -o "$STRICT_STATUS_FILE" \
  -w '%{http_code}' \
  -H "Accept: application/fhir+json" \
  -H "Prefer: handling=strict" \
  "${TORCH_BASE_URL}/fhir/Task?status=BOGUS")

[[ "$STRICT_STATUS_CODE" == "400" ]] \
  || fail "Expected 400 for strict invalid status, got $STRICT_STATUS_CODE"

jq -e '.resourceType == "OperationOutcome"' "$STRICT_STATUS_FILE" > /dev/null \
  || fail "Strict invalid status did not return OperationOutcome"

jq -e '.issue[0].code == "invalid"' "$STRICT_STATUS_FILE" > /dev/null \
  || fail "Strict invalid status did not return issue code invalid"

jq -e '.issue[0].diagnostics | contains("Invalid status values")' "$STRICT_STATUS_FILE" > /dev/null \
  || fail "Strict invalid status diagnostics missing expected message"

rm -f "$STRICT_STATUS_FILE"

# ── GET /fhir/Task?_id=bad-id with Prefer: handling=strict — 400 ─────────────

echo "🚫 GET /fhir/Task with invalid _id and strict handling expects 400..."
STRICT_ID_FILE="$(mktemp -t torch-strict-id-XXXXXXXXXX.json)"

STRICT_ID_CODE=$(curl -s \
  -o "$STRICT_ID_FILE" \
  -w '%{http_code}' \
  -H "Accept: application/fhir+json" \
  -H "Prefer: handling=strict" \
  "${TORCH_BASE_URL}/fhir/Task?_id=bad-id")

[[ "$STRICT_ID_CODE" == "400" ]] \
  || fail "Expected 400 for strict invalid _id, got $STRICT_ID_CODE"

jq -e '.resourceType == "OperationOutcome"' "$STRICT_ID_FILE" > /dev/null \
  || fail "Strict invalid _id did not return OperationOutcome"

jq -e '.issue[0].code == "invalid"' "$STRICT_ID_FILE" > /dev/null \
  || fail "Strict invalid _id did not return issue code invalid"

jq -e '.issue[0].diagnostics | contains("Invalid _id values")' "$STRICT_ID_FILE" > /dev/null \
  || fail "Strict invalid _id diagnostics missing expected message"

rm -f "$STRICT_ID_FILE"

# ── GET /fhir/Task — no filter returns a Bundle with our job ─────────────────

echo "📋 GET /fhir/Task (no filter)..."
BUNDLE_JSON=$(curl -sf \
  -H "Accept: application/fhir+json" \
  "${TORCH_BASE_URL}/fhir/Task")

echo "$BUNDLE_JSON" | jq -e '.resourceType == "Bundle"' > /dev/null \
  || fail "GET /fhir/Task did not return a Bundle"

echo "$BUNDLE_JSON" | jq -e --arg id "$EXPORT_ID" '
  any(.entry[]?; .resource.id == $id)
' > /dev/null || fail "GET /fhir/Task bundle does not contain our job $EXPORT_ID"

# ── GET /fhir/Task?_id=... — filter by ID ────────────────────────────────────

echo "📋 GET /fhir/Task?_id=$EXPORT_ID..."
ID_FILTER_JSON=$(curl -sf \
  -H "Accept: application/fhir+json" \
  "${TORCH_BASE_URL}/fhir/Task?_id=${EXPORT_ID}")

ID_FILTER_TOTAL=$(echo "$ID_FILTER_JSON" | jq -r '.total // 0')
[[ "$ID_FILTER_TOTAL" == "1" ]] \
  || fail "_id filter returned $ID_FILTER_TOTAL entries, expected 1"

echo "$ID_FILTER_JSON" | jq -e --arg id "$EXPORT_ID" '
  any(.entry[]?; .resource.id == $id)
' > /dev/null || fail "_id filter result does not contain task $EXPORT_ID"

# ── GET /fhir/Task?status=... — filter by status ──────────────────────────────

echo "📋 GET /fhir/Task?status=CANCELLED (should not include our job)..."
CANCELLED_JSON=$(curl -sf \
  -H "Accept: application/fhir+json" \
  "${TORCH_BASE_URL}/fhir/Task?status=CANCELLED")

echo "$CANCELLED_JSON" | jq -e --arg id "$EXPORT_ID" '
  any(.entry[]?; .resource.id == $id) | not
' > /dev/null || fail "CANCELLED filter incorrectly included our in-progress job"

# ── Wait for the job to complete, then verify status filter picks it up ───────

echo "⏳ Waiting for job to complete..."
CURR_STATUS=""
for _ in $(seq 1 600); do
  CURR_STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "Accept: application/fhir+json" \
    "${TORCH_BASE_URL}/fhir/__status/${EXPORT_ID}")
  [[ "$CURR_STATUS" == "200" ]] && break
  [[ "$CURR_STATUS" == "410" ]] && fail "Job was unexpectedly cancelled"
  sleep 1
done
[[ "$CURR_STATUS" == "200" ]] || fail "Job did not complete in time"

echo "📋 GET /fhir/Task?status=COMPLETED should now include our job..."
COMPLETED_JSON=$(curl -sf \
  -H "Accept: application/fhir+json" \
  "${TORCH_BASE_URL}/fhir/Task?status=COMPLETED")

echo "$COMPLETED_JSON" | jq -e --arg id "$EXPORT_ID" '
  any(.entry[]?; .resource.id == $id)
' > /dev/null || fail "COMPLETED filter did not include our finished job"

wait "$PID"
trap - EXIT
cleanup

echo "🎉 GET / PUT / Search Task test passed"
