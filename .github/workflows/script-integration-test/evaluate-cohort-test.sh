#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TORCH_BASE_URL="http://localhost:8080"

fail() { echo "❌ $*" >&2; exit 1; }

# 1. Upload BlazeBundle to source server (seed data)
echo "➡️ Uploading initial data bundle..."

if curl -s http://localhost:8083/fhir/metadata?_elements=software | jq -r '.software.name' | grep -iq blaze; then
  echo "✅ Source FHIR Server Live"
else
  fail "Source FHIR Server Not Working"
fi

echo "📤 Posting BlazeBundle.json to http://localhost:8083/fhir"
curl -i -s \
  -X POST "http://localhost:8083/fhir" \
  -H "Content-Type: application/fhir+json" \
  --data-binary @"$ROOT_DIR/src/test/resources/BlazeBundle.json"

CRTDL_FILE="$ROOT_DIR/src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json"
CRTDL_NO_MATCH_FILE="$ROOT_DIR/src/test/resources/CRTDL/CRTDL_observation_not_contained.json"
EXPECTED_PATIENT_COUNT="${EXPECTED_PATIENT_COUNT:-4}"

# 2. POST full CRTDL — returns a FHIR List of the matching patients

echo "➡️  POST \$evaluate-cohort with full CRTDL..."
RESPONSE_FILE="$(mktemp -t torch-evaluate-cohort-XXXXXXXXXX.json)"
CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  -X POST "${TORCH_BASE_URL}/fhir/\$evaluate-cohort" \
  -H "Content-Type: application/json" \
  --data-binary @"$CRTDL_FILE")

[[ "$CODE" == "200" ]] || fail "Expected 200, got $CODE: $(cat "$RESPONSE_FILE")"

jq -e '.resourceType == "List"' "$RESPONSE_FILE" > /dev/null \
  || fail "Response is not a FHIR List resource"

ENTRY_COUNT=$(jq '.entry | length' "$RESPONSE_FILE")
[[ "$ENTRY_COUNT" == "$EXPECTED_PATIENT_COUNT" ]] \
  || fail "Expected $EXPECTED_PATIENT_COUNT entries, got $ENTRY_COUNT"

jq -e '.entry[0].item.reference | startswith("Patient/")' "$RESPONSE_FILE" > /dev/null \
  || fail "List entry does not reference a Patient"

echo "✅ Cohort evaluated: $ENTRY_COUNT patients"

# 3. POST the bare cohortDefinition (CCDL) — same result as the wrapped CRTDL

echo "➡️  POST \$evaluate-cohort with bare CCDL..."
CCDL_FILE="$(mktemp -t torch-evaluate-cohort-ccdl-req-XXXXXXXXXX.json)"
jq '.cohortDefinition' "$CRTDL_FILE" > "$CCDL_FILE"

CCDL_RESPONSE_FILE="$(mktemp -t torch-evaluate-cohort-ccdl-XXXXXXXXXX.json)"
CCDL_CODE=$(curl -s -o "$CCDL_RESPONSE_FILE" -w '%{http_code}' \
  -X POST "${TORCH_BASE_URL}/fhir/\$evaluate-cohort" \
  -H "Content-Type: application/json" \
  --data-binary @"$CCDL_FILE")

[[ "$CCDL_CODE" == "200" ]] || fail "Expected 200 for bare CCDL, got $CCDL_CODE: $(cat "$CCDL_RESPONSE_FILE")"

CCDL_ENTRY_COUNT=$(jq '.entry | length' "$CCDL_RESPONSE_FILE")
[[ "$CCDL_ENTRY_COUNT" == "$ENTRY_COUNT" ]] \
  || fail "Bare CCDL returned $CCDL_ENTRY_COUNT entries, expected $ENTRY_COUNT (same as wrapped CRTDL)"

echo "✅ Bare CCDL matches wrapped CRTDL result"

# 4. POST a cohort definition with no matches — an empty List, not an error

echo "➡️  POST \$evaluate-cohort with non-matching cohort..."
EMPTY_RESPONSE_FILE="$(mktemp -t torch-evaluate-cohort-empty-XXXXXXXXXX.json)"
EMPTY_CODE=$(curl -s -o "$EMPTY_RESPONSE_FILE" -w '%{http_code}' \
  -X POST "${TORCH_BASE_URL}/fhir/\$evaluate-cohort" \
  -H "Content-Type: application/json" \
  --data-binary @"$CRTDL_NO_MATCH_FILE")

[[ "$EMPTY_CODE" == "200" ]] || fail "Expected 200 for non-matching cohort, got $EMPTY_CODE"

jq -e '.resourceType == "List" and ((.entry // []) | length == 0)' "$EMPTY_RESPONSE_FILE" > /dev/null \
  || fail "Non-matching cohort did not return an empty List"

echo "✅ Non-matching cohort returns an empty List"

# 5. POST malformed JSON — 400 with an OperationOutcome

echo "➡️  POST \$evaluate-cohort with malformed JSON expects 400..."
BAD_RESPONSE_FILE="$(mktemp -t torch-evaluate-cohort-bad-XXXXXXXXXX.json)"
BAD_CODE=$(curl -s -o "$BAD_RESPONSE_FILE" -w '%{http_code}' \
  -X POST "${TORCH_BASE_URL}/fhir/\$evaluate-cohort" \
  -H "Content-Type: application/json" \
  -d 'not json')

[[ "$BAD_CODE" == "400" ]] || fail "Expected 400 for malformed JSON, got $BAD_CODE"

jq -e '.resourceType == "OperationOutcome"' "$BAD_RESPONSE_FILE" > /dev/null \
  || fail "Malformed JSON did not return an OperationOutcome"

rm -f "$RESPONSE_FILE" "$CCDL_FILE" "$CCDL_RESPONSE_FILE" "$EMPTY_RESPONSE_FILE" "$BAD_RESPONSE_FILE"

echo "🎉 All evaluate-cohort tests passed!"
