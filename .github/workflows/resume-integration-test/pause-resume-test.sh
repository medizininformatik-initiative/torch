#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TORCH_BASE_URL="${TORCH_BASE_URL:-http://localhost:8080}"
TARGET_SERVER="${TARGET_SERVER:-http://localhost:8084/fhir}"
CRTDL_FILE="${CRTDL_FILE:-$ROOT_DIR/src/test/resources/CrtdlItTests/CRTDL_test_it-kds-perf-w-ref.json}"
LOG="${LOG:-/tmp/transfer-pause-resume.log}"
EXPECTED="${EXPECTED:-25000}"
FHIR_JSON="application/fhir+json"
PUT_RESPONSE_FILE="${PUT_RESPONSE_FILE:-/tmp/task-put-response.json}"

fail() {
  echo "❌ $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

trim_value() {
  printf '%s' "${1:-}" | tr -d '\r\n' | xargs
}

validate_base_url() {
  local url
  url="$(trim_value "$1")"
  [[ "$url" =~ ^https?://[^[:space:]]+$ ]] || fail "Invalid URL: <$url>"
  printf '%s' "$url"
}

task_url() {
  local export_id
  export_id="$(trim_value "${1:-}")"
  [[ -n "$export_id" ]] || fail "Empty export id"

  case "$export_id" in
    http://*|https://*)
      fail "Export id unexpectedly looks like a full URL: <$export_id>"
      ;;
  esac

  printf '%s/fhir/Task/%s' "$TORCH_BASE_URL" "$export_id"
}

require_cmd curl
require_cmd jq
require_cmd grep
require_cmd awk
require_cmd cut
require_cmd tr
require_cmd xargs

TORCH_BASE_URL="$(validate_base_url "$TORCH_BASE_URL")"
TARGET_SERVER="$(validate_base_url "$TARGET_SERVER")"

[[ -f "$CRTDL_FILE" ]] || fail "CRTDL file not found: $CRTDL_FILE"

task_get() {
  local export_id="$1"
  curl -fsS \
    -H "Accept: $FHIR_JSON" \
    "$(task_url "$export_id")"
}

task_status() {
  jq -r '.status // empty'
}

task_version() {
  jq -r '.meta.versionId // empty'
}

task_etag() {
  local export_id="$1"
  local header_file
  header_file="$(mktemp)"

  curl -fsS \
    -D "$header_file" \
    -o /dev/null \
    -H "Accept: $FHIR_JSON" \
    "$(task_url "$export_id")"

  awk -F': ' 'tolower($1)=="etag" {gsub("\r","",$2); print $2; exit}' "$header_file" \
    | tr -d '\r\n'

  rm -f "$header_file"
}

task_put() {
  local export_id="$1"
  local status="$2"
  local version="$3"
  local etag="$4"

  export_id="$(trim_value "$export_id")"
  version="$(trim_value "$version")"
  etag="$(trim_value "$etag")"

  [[ -n "$export_id" ]] || fail "Missing export id for PUT"
  [[ -n "$version" && "$version" != "null" ]] || fail "Missing task version for PUT"
  [[ -n "$etag" ]] || fail "Missing ETag for PUT"

  local body_file
  body_file="$(mktemp)"

  jq -n \
    --arg id "$export_id" \
    --arg status "$status" \
    --arg version "$version" \
    '{
      resourceType: "Task",
      id: $id,
      status: $status,
      meta: { versionId: $version }
    }' > "$body_file"

  local code
  code="$(
    curl -sS \
      -o "$PUT_RESPONSE_FILE" \
      -w '%{http_code}' \
      -X PUT \
      -H "Content-Type: ${FHIR_JSON}" \
      -H "Accept: ${FHIR_JSON}" \
      -H "If-Match: ${etag}" \
      --data-binary "@${body_file}" \
      "$(task_url "$export_id")"
  )"

  rm -f "$body_file"
  printf '%s' "$code"
}

wait_for_export_id() {
  echo "⏳ Waiting for TORCH_EXPORT_ID..." >&2
  for _ in $(seq 1 120); do
    if grep -q "TORCH_EXPORT_ID=" "$LOG"; then
      local export_id
      export_id="$(
        grep "TORCH_EXPORT_ID=" "$LOG" \
          | tail -1 \
          | cut -d= -f2- \
          | tr -d '\r' \
          | xargs
      )"
      [[ -n "$export_id" ]] || fail "Found TORCH_EXPORT_ID line, but extracted id is empty"
      printf '%s\n' "$export_id"
      return 0
    fi
    sleep 1
  done
  fail "Timed out waiting for TORCH_EXPORT_ID in log"
}

wait_for_runnable_task() {
  local export_id="$1"

  echo "⏳ Waiting for runnable task..."
  for _ in $(seq 1 180); do
    local body status
    body="$(task_get "$export_id" || true)"
    status="$(echo "$body" | task_status)"

    case "$status" in
      requested|ready|in-progress)
        echo "✅ Task is runnable: $status"
        return 0
        ;;
      on-hold)
        echo "✅ Task already paused"
        return 0
        ;;
      completed)
        fail "Task completed before pause could be tested"
        ;;
      failed|cancelled)
        fail "Task entered terminal state before pause: $status"
        ;;
      *)
        sleep 1
        ;;
    esac
  done

  fail "Timed out waiting for runnable task"
}

wait_for_status() {
  local export_id="$1"
  local expected="$2"

  echo "⏳ Waiting for task status=${expected}..."
  for _ in $(seq 1 120); do
    local body status
    body="$(task_get "$export_id" || true)"
    status="$(echo "$body" | task_status)"

    if [[ "$status" == "$expected" ]]; then
      echo "✅ Task reached ${expected}"
      return 0
    fi

    case "$status" in
      failed|cancelled)
        fail "Task entered terminal state while waiting for ${expected}: ${status}"
        ;;
    esac

    sleep 1
  done

  fail "Timed out waiting for task status=${expected}"
}

wait_for_completion() {
  local export_id="$1"

  echo "⏳ Waiting for task completion..."
  for _ in $(seq 1 900); do
    local body status
    body="$(task_get "$export_id" || true)"
    status="$(echo "$body" | task_status)"

    case "$status" in
      completed)
        echo "✅ Task completed"
        return 0
        ;;
      failed|cancelled)
        fail "Task ended in terminal state: $status"
        ;;
      *)
        sleep 1
        ;;
    esac
  done

  fail "Timed out waiting for task completion"
}

pause_task() {
  local export_id="$1"

  echo "⏸️ Pausing task..."
  for _ in $(seq 1 20); do
    local body status version etag code

    body="$(task_get "$export_id")"
    status="$(echo "$body" | task_status)"

    if [[ "$status" == "on-hold" ]]; then
      echo "✅ Task already paused"
      return 0
    fi

    if [[ "$status" == "completed" ]]; then
      fail "Task completed before pause"
    fi

    version="$(echo "$body" | task_version)"
    [[ -n "$version" && "$version" != "null" ]] || fail "Missing task version before pause"

    etag="$(task_etag "$export_id")"
    [[ -n "$etag" ]] || fail "Missing ETag before pause"

    code="$(task_put "$export_id" "on-hold" "$version" "$etag")"

    case "$code" in
      200)
        echo "✅ Pause request accepted"
        return 0
        ;;
      409)
        echo "↻ Version conflict while pausing, retrying..."
        sleep 0.5
        ;;
      *)
        [[ -f "$PUT_RESPONSE_FILE" ]] && cat "$PUT_RESPONSE_FILE" >&2 || true
        fail "Pause failed with HTTP ${code}"
        ;;
    esac
  done

  fail "Pause failed after retries"
}

resume_task() {
  local export_id="$1"

  echo "▶️ Resuming task..."
  for _ in $(seq 1 20); do
    local body status version etag code

    body="$(task_get "$export_id")"
    status="$(echo "$body" | task_status)"

    if [[ "$status" != "on-hold" ]]; then
      echo "✅ Task no longer paused: $status"
      return 0
    fi

    version="$(echo "$body" | task_version)"
    [[ -n "$version" && "$version" != "null" ]] || fail "Missing task version before resume"

    etag="$(task_etag "$export_id")"
    [[ -n "$etag" ]] || fail "Missing ETag before resume"

    code="$(task_put "$export_id" "in-progress" "$version" "$etag")"

    case "$code" in
      200)
        echo "✅ Resume request accepted"
        return 0
        ;;
      409)
        echo "↻ Version conflict while resuming, retrying..."
        sleep 0.5
        ;;
      *)
        [[ -f "$PUT_RESPONSE_FILE" ]] && cat "$PUT_RESPONSE_FILE" >&2 || true
        fail "Resume failed with HTTP ${code}"
        ;;
    esac
  done

  fail "Resume failed after retries"
}

assert_counts() {
  echo "🔎 Verifying transferred data..."

  local p_actual m_actual
  p_actual="$(curl -fsS "${TARGET_SERVER}/Patient?_summary=count" | jq -r '.total // 0')"
  m_actual="$(curl -fsS "${TARGET_SERVER}/Medication?_summary=count" | jq -r '.total // 0')"

  echo "Patients:    $p_actual (expected: $EXPECTED)"
  echo "Medications: $m_actual (expected: $EXPECTED)"

  [[ "$p_actual" -eq "$EXPECTED" ]] || fail "Patient count mismatch"
  [[ "$m_actual" -eq "$EXPECTED" ]] || fail "Medication count mismatch"

  echo "✅ Counts verified"
}

cleanup() {
  if [[ -n "${PID:-}" ]]; then
    wait "$PID" || true
  fi
}
trap cleanup EXIT

echo "➡️ Starting extraction + transfer..."
: > "$LOG"

TORCH_BASE_URL="$TORCH_BASE_URL" \
  "$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" \
  -c "$CRTDL_FILE" \
  -t "$TARGET_SERVER" > "$LOG" 2>&1 &
PID=$!

EXPORT_ID="$(wait_for_export_id)"
EXPORT_ID="$(trim_value "$EXPORT_ID")"
[[ -n "$EXPORT_ID" ]] || fail "EXPORT_ID is empty"

echo "✅ Export ID: $EXPORT_ID"

wait_for_runnable_task "$EXPORT_ID"
pause_task "$EXPORT_ID"
wait_for_status "$EXPORT_ID" "on-hold"

sleep 3
CURRENT_STATUS="$(task_get "$EXPORT_ID" | task_status)"
CURRENT_STATUS="$(trim_value "$CURRENT_STATUS")"
[[ "$CURRENT_STATUS" == "on-hold" ]] || fail "Task did not remain paused, current status=${CURRENT_STATUS}"
echo "✅ Task remained paused"

resume_task "$EXPORT_ID"
wait_for_completion "$EXPORT_ID"

wait "$PID"
assert_counts

echo "🎉 Pause/resume integration test passed"
