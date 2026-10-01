#!/bin/bash
set -euo pipefail

# Default configurations (can be overridden via env vars)
TORCH_BASE_URL="${TORCH_BASE_URL:-http://localhost:8080}"

CRTDL_FILE=""
OUTPUT_DIR=""
EXPORT_ID=""
INTERVAL="${INTERVAL:-5}"
MAX_RUNTIME_MINS="${MAX_RUNTIME_MINS:-10}" # Default 10 minutes

PATIENTS=()

add_patients_csv() {
  local csv="${1:-}"
  [[ -z "$csv" ]] && return 0
  IFS=',' read -r -a _arr <<<"$csv"
  for p in "${_arr[@]}"; do
    # trim whitespace
    p="$(echo "$p" | awk '{$1=$1;print}')"
    [[ -n "$p" ]] && PATIENTS+=("$p")
  done
}

print_usage() {
  cat <<EOF
Usage:
  New extraction (with consent diagnostics enabled):
    $0 -c <crtdl_file> -o <output_dir> [--patients p1,p2]

  Resume / poll existing job:
    $0 -j <status_id> -o <output_dir>

Downloads the job manifest and all diagnostics reports it links to (job summary, exclusion
and consent diagnostics CSVs) into <output_dir>. Extracted data is not downloaded.

Options:
  -c <file>      Path to CRTDL JSON
  -j <id>        Existing export/status id
  -o <dir>       Directory to save the diagnostics into
  --patients     Optional comma-separated list of patients -> adds repeated Parameters.parameter[name=patient]
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
    -o)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --patients)
      add_patients_csv "${2:-}"
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

if [[ -z "$OUTPUT_DIR" ]]; then
  echo "❌ Output directory must be provided via -o" >&2
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

mkdir -p "$OUTPUT_DIR"

# === CREATE JOB (NEW EXTRACTION MODE) ===
if [[ -z "$EXPORT_ID" ]]; then
  if [[ ! -f "$CRTDL_FILE" ]]; then
    echo "❌ CRTDL file not found: $CRTDL_FILE" >&2
    exit 1
  fi

  echo "📤 Posting $CRTDL_FILE to $TORCH_BASE_URL/fhir/\$extract-data"

  RESPONSE=$(
    jq -ncM \
       --rawfile content "$CRTDL_FILE" \
       '{
          resourceType: "Parameters",
          parameter: (
            [{name: "crtdl", valueBase64Binary: ($content | @base64)}]
            + ($ARGS.positional | map({name: "patient", valueString: .}))
            + [{name: "consentDiagnostics", valueBoolean: true}]
          )
        }' \
       --args "${PATIENTS[@]}" \
      | curl -i -s \
          -X POST "$TORCH_BASE_URL/fhir/\$extract-data" \
          -H "Content-Type: application/fhir+json" \
          --data-binary @-
  )

  RAW_LOCATION=$(echo "$RESPONSE" | awk 'tolower($1) == "content-location:" {print $2}' | tr -d '\r\n')
  EXPORT_ID=$(echo "$RAW_LOCATION" | sed -n 's#.*/__status/##p' | tr -d '\r\n')
  if [[ -z "$EXPORT_ID" ]]; then
    echo "❌ Could not extract status ID from Content-Location header." >&2
    exit 1
  fi

  echo "🆔 TORCH_EXPORT_ID=$EXPORT_ID"
  echo "▶️  Resume later with: $0 -j $EXPORT_ID -o $OUTPUT_DIR"
fi

# === POLL STATUS ===
EXPORT_STATUS_URL="${TORCH_BASE_URL%/}/fhir/__status/${EXPORT_ID#/}"
echo "📡 Monitoring export: $EXPORT_ID (Deadline: ${MAX_RUNTIME_MINS}m)"

END_TIME=$(( $(date +%s) + MAX_RUNTIME_MINS * 60 ))

while true; do
  if [[ "$(date +%s)" -ge "$END_TIME" ]]; then
    echo "❌ Timeout: Job did not complete within ${MAX_RUNTIME_MINS} minutes." >&2
    exit 1
  fi

  response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$EXPORT_STATUS_URL")
  body=$(echo "$response" | sed -e 's/HTTPSTATUS:.*//g')
  status=$(echo "$response" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

  if [[ "$status" == "200" ]]; then
    echo "✅ Export complete."
    break
  elif [[ "$status" != "202" && "$status" != "102" ]]; then
    echo "❌ Error: Received HTTP $status" >&2
    echo "$body"
    exit 1
  fi
  sleep "$INTERVAL"
done

# === DOWNLOAD DIAGNOSTICS ===
echo "$body" | jq . > "$OUTPUT_DIR/manifest.json"

report_urls=()
while IFS= read -r url; do
  [[ -n "$url" ]] && report_urls+=("$url")
done < <(echo "$body" | jq -r '.extension[]? | .valueUrl // empty')

# Wait until URL is actually downloadable (nginx + file finalized)
wait_for_url() {
  local url="$1"
  local i code
  for i in $(seq 1 10); do
    code=$(curl -sS -o /dev/null -w "%{http_code}" -H "Range: bytes=0-0" "$url" || echo "000")
    if [[ "$code" == "206" || "$code" == "200" ]]; then
      return 0
    fi
    echo "⏳ File not ready yet (HTTP $code), waiting 10s... ($i/10): $url"
    sleep 10
  done
  return 1
}

for url in "${report_urls[@]}"; do
  if ! wait_for_url "$url"; then
    echo "❌ Timed out waiting for diagnostics file: $url" >&2
    exit 1
  fi
  echo "🌐 Downloading: $url"
  curl -sSf -o "$OUTPUT_DIR/$(basename "$url")" "$url"
done

if ! echo "$body" | jq -e '.extension[]? | select(.url == "torch-raw-provisions")' >/dev/null; then
  echo "⚠️ No consent diagnostics in the job manifest (not requested for this job, or no rows recorded)."
fi

echo "🩺 Diagnostics saved to $OUTPUT_DIR"
