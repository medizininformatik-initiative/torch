#!/bin/bash
set -euo pipefail

# 1. Install blazectl
VERSION="1.0.0"
URL="https://github.com/samply/blazectl/releases/download/v${VERSION}/blazectl-${VERSION}-linux-amd64.tar.gz"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "📦 Downloading blazectl ${VERSION}..."
curl -LO "$URL"

echo "📂 Extracting binary..."
tar xzf "blazectl-${VERSION}-linux-amd64.tar.gz"

echo "➡️ Installing to /usr/local/bin..."
sudo mv blazectl /usr/local/bin/blazectl
chmod +x /usr/local/bin/blazectl

echo "🔍 Installed version:"
blazectl --version

rm blazectl-${VERSION}-linux-amd64.tar.gz

# 2. Upload BlazeBundle to source server (seed data)
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

# 3. Run extraction + transfer script
echo "➡️ Running extraction and transfer..."
TORCH_BASE_URL=http://localhost:8080 \
"$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" -c "$ROOT_DIR/src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json" -t "$TARGET_SERVER"

# 4. Assertions – Patient count
EXPECTED_PATIENT_COUNT=4
ACTUAL_PATIENT_COUNT=$(curl -s "${TARGET_SERVER}/Patient?_summary=count" | jq '.total')

if [ "$ACTUAL_PATIENT_COUNT" -eq "$EXPECTED_PATIENT_COUNT" ]; then
  echo "✅ Patient count is correct: $ACTUAL_PATIENT_COUNT"
else
  echo "❌ Patient count is $ACTUAL_PATIENT_COUNT, expected $EXPECTED_PATIENT_COUNT"
  exit 1
fi

# 5. Assertions – Observation count
EXPECTED_OBSERVATION_COUNT=4
ACTUAL_OBSERVATION_COUNT=$(curl -s "${TARGET_SERVER}/Observation?_summary=count" | jq '.total')

if [ "$ACTUAL_OBSERVATION_COUNT" -eq "$EXPECTED_OBSERVATION_COUNT" ]; then
  echo "✅ Observation count is correct: $ACTUAL_OBSERVATION_COUNT"
else
  echo "❌ Observation count is $ACTUAL_OBSERVATION_COUNT, expected $EXPECTED_OBSERVATION_COUNT"
  exit 1
fi


echo "🎉 All integration tests passed!"
