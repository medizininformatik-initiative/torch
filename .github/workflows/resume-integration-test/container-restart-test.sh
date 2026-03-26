#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TARGET_SERVER="http://localhost:8084/fhir"
LOG="/tmp/transfer.log"

# 1. Run extraction + transfer script
echo "➡️ Running extraction and transfer..."
TORCH_BASE_URL="http://localhost:8080" \
 "$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" \
  -c "$ROOT_DIR/src/test/resources/CrtdlItTests/CRTDL_test_it-kds-perf-w-ref.json" \
  -t "$TARGET_SERVER" > "$LOG" 2>&1 &
PID=$!

# 2. Capture Export ID and Interrupt
echo "Waiting for ID and interrupting..."
while ! grep -q "TORCH_EXPORT_ID=" "$LOG"; do sleep 1; done
EXPORT_ID=$(grep "TORCH_EXPORT_ID=" "$LOG" | cut -d= -f2 | tr -d '[:space:]')
sleep $RESTART_AFTER_SECONDS

COMPOSE_FILE="$ROOT_DIR/.github/workflows/infra/docker-compose.yml"
kill "$PID" || true
docker compose -f "$COMPOSE_FILE" stop torch nginx
echo "Interrupted job: $EXPORT_ID. Restarting services..."
docker compose -f "$COMPOSE_FILE" up --wait -d  torch nginx
echo "Restarted services..."


# 3. Resume
echo "Resuming transfer..."
TORCH_BASE_URL="http://localhost:8080" "$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" \
  -j "$EXPORT_ID" -t "$TARGET_SERVER"

# 4. Assertions
echo -e " Verifying data..."
EXPECTED=25000
P_ACTUAL=$(curl -s "${TARGET_SERVER}/Patient?_summary=count" | jq -r '.total // 0')
M_ACTUAL=$(curl -s "${TARGET_SERVER}/Medication?_summary=count" | jq -r '.total // 0')

echo "   Patients: $P_ACTUAL (expected: $EXPECTED)"
echo "   Medications: $M_ACTUAL (expected: $EXPECTED)"

[[ "$P_ACTUAL" -eq "$EXPECTED" ]] && echo "✅ Patient count OK" || { echo "❌ Patient fail"; exit 1; }
[[ "$M_ACTUAL" -eq "$EXPECTED" ]] && echo "✅ Medication count OK" || { echo "❌ Medication fail"; exit 1; }


echo "🎉 All integration tests passed!"
