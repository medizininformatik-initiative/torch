#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default configurations (can be overridden via env vars)
TORCH_BASE_URL="${TORCH_BASE_URL:-http://localhost:8080}"

CRTDL_FILE=""
TARGET_SERVER=""
EXPORT_ID=""
INTERVAL="${INTERVAL:-5}"
MAX_RUNTIME_MINS="${MAX_RUNTIME_MINS:-10}" # Default 10 minutes

TMP_DIR="$(mktemp -d)"
cleanup() {
  echo "🧹 Cleaning up temporary files in $TMP_DIR"
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "🔧 Transfer Extraction To DUP FHIR Server Configuration:"
echo "  TORCH_BASE_URL = $TORCH_BASE_URL"
echo "  MAX_RUNTIME_MINS    = $MAX_RUNTIME_MINS"
echo "  INTERVAL  = $INTERVAL "

print_usage() {
  cat <<EOF
Usage:
  New extraction:
    $0 -c <crtdl_file> -t http://fhirserver:8080/fhir

  Resume / poll existing job:
    $0 -j <status_id> -t http://fhirserver:8080/fhir

Options:
  -c <file>      Path to CRTDL JSON
  -j <id>        Existing export/status id
  -t <string>    Target DUP FHIR server URL
  --help         Show this help message

Environment variables:
  TORCH_BASE_URL    Default: http://localhost:8080
  MAX_RUNTIME_MINS  Default: 10   Maximum runtime in minutes
  INTERVAL          Default: 5 polling interval in seconds
EOF
}

# === PARSE INPUT ===
while [[ $# -gt 0 ]]; do
  case "$1" in
    -c)
      CRTDL_FILE="${2:-}"
      shift 2
      ;;
    -j|--job-id|--export-id)
      EXPORT_ID="${2:-}"
      shift 2
      ;;
    -t)
      TARGET_SERVER="${2:-}"
      shift 2
      ;;
    --help)
      print_usage
      exit 0
      ;;
    *)
      echo "❌ Unknown option: $1" >&2
      print_usage
      exit 1
      ;;
  esac
done

if [[ -z "$TARGET_SERVER" ]]; then
  echo "❌ Target FHIR server URL must be provided via -t" >&2
  exit 1
fi

if [[ -n "$CRTDL_FILE" && -n "$EXPORT_ID" ]]; then
  echo "❌ Provide either -c (new extraction) OR -j (resume), not both." >&2
  exit 1
fi

if [[ -z "$CRTDL_FILE" && -z "$EXPORT_ID" ]]; then
  echo "❌ You must provide -c (new extraction) OR -j (resume by id)." >&2
  exit 1
fi

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "crtdl"
    }
  ]
}
END
}

# === CREATE JOB (NEW EXTRACTION MODE) ===
if [[ -z "$EXPORT_ID" ]]; then
  if [[ ! -f "$CRTDL_FILE" ]]; then
    echo "❌ CRTDL file not found: $CRTDL_FILE" >&2
    exit 1
  fi

  echo "➡️ Generating and uploading Parameters from: $CRTDL_FILE"
  echo "📤 Posting to $TORCH_BASE_URL/fhir/\$extract-data"

  RESPONSE=$(
    parameters \
      | jq --arg content "$(cat "$CRTDL_FILE")" -cM '.parameter[0].valueBase64Binary = ($content | @base64)' \
      | curl -i -s \
          -X POST "$TORCH_BASE_URL/fhir/\$extract-data" \
          -H "Content-Type: application/fhir+json" \
          --data-binary @-
  )

  RAW_LOCATION=$(echo "$RESPONSE" | awk '/Content-Location:/ {print $2}' | tr -d '\r\n')
  if [[ -z "$RAW_LOCATION" ]]; then
    echo "❌ Failed to capture Content-Location header." >&2
    exit 1
  fi

  EXPORT_ID=$(echo "$RAW_LOCATION" | sed -n 's#.*/__status/##p' | tr -d '\r\n')
  if [[ -z "$EXPORT_ID" ]]; then
    echo "❌ Could not extract status ID." >&2
    exit 1
  fi

  echo "📍 Extracted Status ID: $EXPORT_ID"
  echo "🆔 TORCH_EXPORT_ID=$EXPORT_ID"
  echo "▶️  Resume later with: $0 -j $EXPORT_ID -t $TARGET_SERVER"
else
  echo "▶️  Resume mode: using provided Status ID: $EXPORT_ID"
  echo "🆔 TORCH_EXPORT_ID=$EXPORT_ID"
fi

# === POLL STATUS ===
EXPORT_STATUS_URL="${TORCH_BASE_URL%/}/fhir/__status/${EXPORT_ID#/}"
echo "📡 Monitoring export: $EXPORT_ID (Deadline: ${MAX_RUNTIME_MINS}m)"

START_TIME=$(date +%s)
END_TIME=$((START_TIME + (MAX_RUNTIME_MINS * 60)))
LAST_DIAGNOSTICS=""

while true; do
  CURRENT_TIME=$(date +%s)
  if [[ "$CURRENT_TIME" -ge "$END_TIME" ]]; then
    echo "❌ Timeout: Job did not complete within ${MAX_RUNTIME_MINS} minutes." >&2
    exit 1
  fi

  # Quietly fetch status
  response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$EXPORT_STATUS_URL")
  body=$(echo "$response" | sed -e 's/HTTPSTATUS:.*//g')
  status=$(echo "$response" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

  # Extract INFORMATION diagnostics from the Java-generated OperationOutcome
  CURRENT_DIAGNOSTICS=$(echo "$body" | jq -r '.issue[]? | select(.severity=="information") | .diagnostics' 2>/dev/null || echo "")

  # Only print if the Java OperationOutcome "information" block changes
  if [[ -n "$CURRENT_DIAGNOSTICS" && "$CURRENT_DIAGNOSTICS" != "$LAST_DIAGNOSTICS" ]]; then
      echo -e "\n$CURRENT_DIAGNOSTICS"
      LAST_DIAGNOSTICS="$CURRENT_DIAGNOSTICS"
  fi

  if [[ "$status" == "200" ]]; then
    url_count=$(echo "$body" | jq -r '.output | length' 2>/dev/null || echo "0")
    if [[ "$url_count" -gt 0 ]]; then
      echo -e "\n✅ Export complete."
      export_json="$body"
      break
    fi
  elif [[ "$status" != "202" && "$status" != "102" ]]; then
    echo "❌ Error: Received HTTP $status" >&2
    echo "$body"
    exit 1
  fi
  sleep $INTERVAL
done

# === PARSE OUTPUT URLS ===
urls=()
while IFS= read -r url; do
  [[ -n "$url" ]] && urls+=("$url")
done < <(echo "$export_json" | jq -r '.output[].url')

if [[ ${#urls[@]} -eq 0 ]]; then
  echo "⚠️ No NDJSON URLs found."
  exit 1
fi

# Wait until URL is actually downloadable (nginx + file finalized)
# Tries HEAD first; if HEAD is blocked, falls back to a small ranged GET.
wait_for_url() {
  local url="$1"
  local tries="${2:-10}"
  local sleep_s="${3:-10}"

  local i=1
  while (( i <= tries )); do
    # Try HEAD (fast)
    local code
    code=$(curl -sS -o /dev/null -I -w "%{http_code}" "$url" || echo "000")

    if [[ "$code" == "200" ]]; then
      local cl
      cl=$(curl -sS -I "$url" | awk -F': ' 'tolower($1)=="content-length"{print $2}' | tr -d '\r')
      if [[ -z "$cl" || "$cl" -gt 0 ]]; then
        return 0
      fi
    fi

    if [[ "$code" == "403" || "$code" == "405" || "$code" == "000" || "$code" == "404" ]]; then
      code=$(curl -sS -o /dev/null -w "%{http_code}" -H "Range: bytes=0-0" "$url" || echo "000")
      if [[ "$code" == "206" || "$code" == "200" ]]; then
        return 0
      fi
    fi

    echo "⏳ File not ready yet (HTTP $code): $url"
    echo "🔁 Waiting ${sleep_s}s... ($i/$tries)"
    sleep "$sleep_s"
    i=$((i + 1))
  done

  return 1
}

process_file() {
  local url="$1"
  local filename="$TMP_DIR/$(basename "$url")"

    # wait up to 10x10s by default (override if you want)
    if ! wait_for_url "$url" 10 10; then
      echo "❌ Timed out waiting for file to become available: $url" >&2
      exit 1
    fi

  echo "🌐 Uploading: $url"
  if curl -s -o "$filename" "$url"; then
    echo "⬆️ Uploading '$filename' to $TARGET_SERVER"
    blazectl upload --server "$TARGET_SERVER" "$TMP_DIR"
    rm -f "$filename"
  else
    echo "❌ Upload failed: $url" >&2
    exit 1
  fi
}

# core.ndjson first
for url in "${urls[@]}"; do
  [[ "$url" == *core.ndjson ]] && process_file "$url" && break
done

# rest
for url in "${urls[@]}"; do
  [[ "$url" != *core.ndjson ]] && process_file "$url"
done

echo "✅ Extraction transfer completed"
echo "🆔 TORCH_EXPORT_ID=$EXPORT_ID"
