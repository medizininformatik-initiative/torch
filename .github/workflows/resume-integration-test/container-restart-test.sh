#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SOURCE_SERVER="http://localhost:8083/fhir"
TARGET_SERVER="http://localhost:8084/fhir"
COMPOSE_FILE="$ROOT_DIR/.github/workflows/resume-integration-test/docker-compose.yml"
CRTDL_FILE="$ROOT_DIR/torch-app/src/test/resources/CRTDL/CRTDL_observation_all_fields_withoutReference.json"
LOG="/tmp/transfer.log"

# 1. Seed source FHIR server
echo "➡️ Uploading test data to source FHIR server..."
curl -s -X POST "$SOURCE_SERVER" \
  -H "Content-Type: application/fhir+json" \
  --data-binary @"$ROOT_DIR/torch-app/src/test/resources/BlazeBundle.json" > /dev/null

# 2. Run extraction + transfer script
echo "➡️ Running extraction and transfer..."
: > "$LOG"
TORCH_BASE_URL="http://localhost:8080" \
  "$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" \
  -c "$CRTDL_FILE" -t "$TARGET_SERVER" > "$LOG" 2>&1 &
PID=$!

# 3. Capture Export ID and Interrupt
echo "Waiting for ID and interrupting..."
while ! grep -q "TORCH_EXPORT_ID=" "$LOG"; do sleep 1; done
EXPORT_ID=$(grep "TORCH_EXPORT_ID=" "$LOG" | cut -d= -f2 | tr -d '[:space:]')
sleep "$RESTART_AFTER_SECONDS"

kill "$PID" || true
docker compose -f "$COMPOSE_FILE" stop torch nginx
echo "Interrupted job: $EXPORT_ID. Restarting services..."
docker compose -f "$COMPOSE_FILE" up --wait -d torch nginx
echo "Restarted services..."

# 4. Resume
echo "Resuming transfer..."
TORCH_BASE_URL="http://localhost:8080" "$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" \
  -j "$EXPORT_ID" -t "$TARGET_SERVER"

# 5. Assertions
echo " Verifying data..."
EXPECTED_PATIENTS=4
EXPECTED_OBSERVATIONS=4

P_ACTUAL=$(curl -s "${TARGET_SERVER}/Patient?_summary=count" | jq -r '.total // 0')
O_ACTUAL=$(curl -s "${TARGET_SERVER}/Observation?_summary=count" | jq -r '.total // 0')

echo "   Patients:     $P_ACTUAL (expected: $EXPECTED_PATIENTS)"
echo "   Observations: $O_ACTUAL (expected: $EXPECTED_OBSERVATIONS)"

[[ "$P_ACTUAL" -eq "$EXPECTED_PATIENTS" ]] && echo "✅ Patient count OK" || { echo "❌ Patient fail"; exit 1; }
[[ "$O_ACTUAL" -eq "$EXPECTED_OBSERVATIONS" ]] && echo "✅ Observation count OK" || { echo "❌ Observation fail"; exit 1; }

echo "🎉 All integration tests passed!"
