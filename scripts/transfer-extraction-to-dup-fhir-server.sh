#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default configurations (can be overridden via env vars)
TORCH_BASE_URL="${TORCH_BASE_URL:-http://localhost:8080}"


PARAMS_FILE=""
CRTDL_FILE=""
TARGET_SERVER=""
MAX_RETRIES="${MAX_RETRIES:-60}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

TMP_DIR="$(mktemp -d)"
cleanup() {
  echo "🧹 Cleaning up temporary files in $TMP_DIR"
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "🔧 Transfer Extraction To DUP FHIR Server Configuration:"
echo "  TORCH_BASE_URL = $TORCH_BASE_URL"
echo "  MAX_RETRIES    = $MAX_RETRIES"
echo "  SLEEP_SECONDS  = $SLEEP_SECONDS"


# === USAGE FUNCTION ===
print_usage() {
  cat <<EOF
Usage: $0 -c <crtdl_file> -t http://fhirserver:8080/fhir

Takes a CRTDL json and generates a Parameters resource as input for TORCH \$extract-data operation.
Executes TORCH \$extract-data operation on the Torch server's \$extract-data endpoint,
Polls the export status, and transfers resulting NDJSON files to a DUP specific FHIR server.

You must provide either:


  -c <file>      Path to the CRTDL file from which Parameters JSON will be generated for the extraction.

  -t <string>    url of the target DUP server

  --help         Show this help message.

Environment variables (optional overrides):

  TORCH_BASE_URL     Default: http://localhost:8080
  MAX_RETRIES        Default: 60
  SLEEP_SECONDS      Default: 5

Notes:
  - The script relies on blazectl being installed.
EOF
}

# === PARSE INPUT ===
while [[ $# -gt 0 ]]; do
  case "$1" in
    -c)
      CRTDL_FILE="$2"
      shift 2
      ;;
    -t) TARGET_SERVER="$2";
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
  echo "❌ Target FHIR server URL must be provided via -t " >&2
  exit 1
fi


if [[  -z "$CRTDL_FILE" ]]; then
  echo "❌ You must provide -c (crtdl file)" >&2
  exit 1
fi

echo "➡️ Generating and uploading Parameters from: $CRTDL_FILE"

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

echo "📤 Posting to $TORCH_BASE_URL/fhir/\$extract-data with Content-Type: application/fhir+json"
RESPONSE=$(
  parameters | jq --arg content "$(cat "$CRTDL_FILE")" -cM '.parameter[0].valueBase64Binary = ($content | @base64)' |
  curl -i -s \
    -X POST "$TORCH_BASE_URL/fhir/\$extract-data" \
    -H "Content-Type: application/fhir+json" \
    --data-binary @-
)


#echo "📥 Raw response:"
#echo "$RESPONSE"

# 2. Extract Content-Location header
RAW_LOCATION=$(echo "$RESPONSE" | awk '/Content-Location:/ {print $2}' | tr -d '\r\n')

if [[ -z "$RAW_LOCATION" ]]; then
  echo "❌ Failed to capture Location header."
  exit 1
fi

# 3. Extract export status ID
EXPORT_ID=$(echo "$RAW_LOCATION" | sed -n 's#.*/__status/##p' | tr -d '\r\n')

if [[ -z "$EXPORT_ID" ]]; then
  echo "❌ Could not extract status ID."
  exit 1
fi

echo "📍 Extracted Status ID: $EXPORT_ID"


# 4. Poll until export is ready
EXPORT_STATUS_URL="${TORCH_BASE_URL%/}/fhir/__status/${EXPORT_ID#/}"
echo "📡 Polling export status at: $EXPORT_STATUS_URL"

attempt=0
export_json=""

while true; do
  attempt=$((attempt + 1))

  if ! response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$EXPORT_STATUS_URL"); then
    response="HTTPSTATUS:000"
  fi
  body=$(echo "$response" | sed -e 's/HTTPSTATUS:.*//g')
  status=$(echo "$response" | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

  if [[ "$status" == "200" ]]; then
    url_count=$(echo "$body" | jq -r '.output | length')
    if [[ "$url_count" -gt 0 ]]; then
      echo "✅ Export is ready with $url_count file(s)."
      export_json="$body"
      break
    else
      echo "⏳ Export in progress (no output files yet)..."
    fi
  elif [[ "$status" == "202" || "$status" == "102" ]]; then
      echo "⏳ Export still processing (status $status)..."
    else
      echo "❌ Unexpected HTTP status: $status" >&2
      exit 1
      fi

  if [[ $attempt -ge $MAX_RETRIES ]]; then
    echo "❌ Timed out waiting for export to become ready." >&2
    exit 1
  fi

  echo "🔁 Retrying in $SLEEP_SECONDS seconds... ($attempt/$MAX_RETRIES)"
  sleep "$SLEEP_SECONDS"
done

# 5. Parse export URLs
urls=()
while IFS= read -r url; do
  urls+=("$url")
done < <(echo "$export_json" | jq -r '.output[].url')

if [[ ${#urls[@]} -eq 0 ]]; then
  echo "⚠️  No NDJSON URLs found in export metadata." >&2
  exit 1
fi

# 6. Define helper to process file
process_file() {
  local url="$1"
  local filename="$TMP_DIR/$(basename "$url")"

  echo "🌐 Downloading: $url"
  echo "📥 Saving to: $filename"

  if curl -s -o "$filename" "$url"; then
    echo "⬆️  Uploading '$filename' to $TARGET_SERVER using blazectl..."
    if blazectl upload --server "$TARGET_SERVER" "$TMP_DIR"; then
      echo "✅ Upload succeeded for '$filename'"
      rm -f "$filename"
    else
      echo "❌ Upload failed for '$filename'" >&2
      rm -f "$filename"
    fi
  else
    echo "❌ Download failed for '$filename'" >&2
  fi
}

# 7. Process core.ndjson first
for url in "${urls[@]}"; do
  if [[ "$url" == *core.ndjson ]]; then
    process_file "$url"
    break
  fi
done

# 8. Process the rest
for url in "${urls[@]}"; do
  if [[ "$url" != *core.ndjson ]]; then
    process_file "$url"
  fi
done

echo "✅ Extraction transfer completed"
