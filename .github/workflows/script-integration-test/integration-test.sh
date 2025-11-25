#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
# 1. Upload BlazeBundle to source server (seed data)
echo "➡️ Uploading initial data bundle..."

echo "Root dir: $ROOT_DIR"
ls "$ROOT_DIR/src/test/resources/BlazeBundle.json"

if curl -s http://localhost:8083/fhir/metadata?_elements=software | jq -r '.software.name' | grep -iq blaze; then
  echo "✅ Source FHIR Server Live"
else
  echo "❌ Source FHIR Server Not Working"
  exit 1
fi


echo "📤 Posting BlazeBundle.json to http://localhost:8083/fhir"
curl -i -s \
  -X POST "http://localhost:8083/fhir" \
  -H "Content-Type: application/fhir+json" \
  --data-binary @"$ROOT_DIR/src/test/resources/BlazeBundle.json"

TARGET_SERVER=http://localhost:8084/fhir

# 2. Run extraction + transfer script
echo "➡️ Running extraction and transfer..."
TORCH_BASE_URL=http://localhost:8080 \
"$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" -c "$ROOT_DIR/src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json" -t "$TARGET_SERVER"

# 3. Assertions – Patient count
EXPECTED_PATIENT_COUNT=4
ACTUAL_PATIENT_COUNT=$(curl -s "${TARGET_SERVER}/Patient?_summary=count" | jq '.total')

if [ "$ACTUAL_PATIENT_COUNT" -eq "$EXPECTED_PATIENT_COUNT" ]; then
  echo "✅ Patient count is correct: $ACTUAL_PATIENT_COUNT"
else
  echo "❌ Patient count is $ACTUAL_PATIENT_COUNT, expected $EXPECTED_PATIENT_COUNT"
  exit 1
fi

# 4. Assertions – Observation count
EXPECTED_OBSERVATION_COUNT=4
ACTUAL_OBSERVATION_COUNT=$(curl -s "${TARGET_SERVER}/Observation?_summary=count" | jq '.total')

if [ "$ACTUAL_OBSERVATION_COUNT" -eq "$EXPECTED_OBSERVATION_COUNT" ]; then
  echo "✅ Observation count is correct: $ACTUAL_OBSERVATION_COUNT"
else
  echo "❌ Observation count is $ACTUAL_OBSERVATION_COUNT, expected $EXPECTED_OBSERVATION_COUNT"
  exit 1
fi


echo "🎉 All integration tests passed!"
