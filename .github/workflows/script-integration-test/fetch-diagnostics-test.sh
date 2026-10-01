#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TORCH_BASE_URL="http://localhost:8080"

fail() { echo "❌ $*" >&2; exit 1; }

# 1. Upload consent test patient to source server (seed data)
echo "➡️ Uploading consent test bundle..."

if curl -s http://localhost:8083/fhir/metadata?_elements=software | jq -r '.software.name' | grep -iq blaze; then
  echo "✅ Source FHIR Server Live"
else
  fail "Source FHIR Server Not Working"
fi

curl -sf -o /dev/null \
  -X POST "http://localhost:8083/fhir" \
  -H "Content-Type: application/fhir+json" \
  --data-binary @"$ROOT_DIR/src/test/resources/Bundle-test-consent-ext.json" \
  || fail "Uploading consent test bundle failed"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

# Reuse the CRTDL of the FhirControllerIT consent diagnostics test
CRTDL_FILE="$WORK_DIR/crtdl.json"
jq -r '.parameter[] | select(.name == "crtdl") | .valueBase64Binary' \
  "$ROOT_DIR/src/test/resources/CRTDL_Parameters/Parameters_consent_diagnostics.json" \
  | base64 -d > "$CRTDL_FILE"

FETCH_SCRIPT="$ROOT_DIR/scripts/fetch-diagnostics.sh"
PATIENT="mii-exa-test-data-patient-1"

assert_diagnostics() {
  local dir="$1"

  for f in manifest.json job-summary.json resource-exclusions.csv patient-exclusions.csv \
           raw-provisions.csv final-periods.csv consent-considered-resources.csv; do
    [[ -f "$dir/$f" ]] || fail "Missing $f in $dir"
  done

  jq -e '."Num-Final-Patients" == 1' "$dir/job-summary.json" > /dev/null \
    || fail "job-summary.json does not report 1 final patient: $(cat "$dir/job-summary.json")"

  # one permit row each for .6 (data) and .8 (gate)
  [[ "$(tail -n +2 "$dir/raw-provisions.csv" | wc -l)" -eq 2 ]] \
    || fail "Expected 2 raw provisions, got: $(cat "$dir/raw-provisions.csv")"
  grep -q '"2.16.840.1.113883.3.1937.777.24.5.3.6"' "$dir/raw-provisions.csv" \
    || fail "raw-provisions.csv lacks the .6 provision"

  grep -q "\"$PATIENT\",\"2024-02-23\",\"2054-01-31\"" "$dir/final-periods.csv" \
    || fail "final-periods.csv lacks the expected period: $(cat "$dir/final-periods.csv")"

  grep -q "\"Consent/$PATIENT-consent-1\"" "$dir/consent-considered-resources.csv" \
    || fail "consent-considered-resources.csv lacks the Consent: $(cat "$dir/consent-considered-resources.csv")"

  echo "✅ All diagnostics present and correct in $dir"
}

# 2. New extraction
echo "➡️ Running fetch-diagnostics for $PATIENT..."
OUTPUT=$(TORCH_BASE_URL=$TORCH_BASE_URL "$FETCH_SCRIPT" -c "$CRTDL_FILE" --patients "$PATIENT" -o "$WORK_DIR/new")
echo "$OUTPUT"
assert_diagnostics "$WORK_DIR/new"

# 3. Resume by job id
EXPORT_ID=$(echo "$OUTPUT" | sed -n 's/.*TORCH_EXPORT_ID=//p' | head -n 1)
[[ -n "$EXPORT_ID" ]] || fail "Could not read TORCH_EXPORT_ID from script output"

echo "➡️ Re-fetching diagnostics of job $EXPORT_ID..."
TORCH_BASE_URL=$TORCH_BASE_URL "$FETCH_SCRIPT" -j "$EXPORT_ID" -o "$WORK_DIR/resumed"
assert_diagnostics "$WORK_DIR/resumed"

echo "🎉 All fetch-diagnostics tests passed!"
