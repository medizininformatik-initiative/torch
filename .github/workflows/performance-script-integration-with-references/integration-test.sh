#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TARGET_SERVER=http://localhost:8084/fhir

# 1. Run extraction + transfer script
echo "➡️ Running extraction and transfer..."
TORCH_BASE_URL=http://localhost:8080 \
"$ROOT_DIR/scripts/transfer-extraction-to-dup-fhir-server.sh" -c "$ROOT_DIR/torch-app/src/test/resources/CrtdlItTests/CRTDL_test_it-kds-perf-w-ref.json" -t "$TARGET_SERVER"

# 2. Assertions
echo -e " Verifying data..."
EXPECTED=25000
P_ACTUAL=$(curl -s "${TARGET_SERVER}/Patient?_summary=count" | jq -r '.total // 0')
M_ACTUAL=$(curl -s "${TARGET_SERVER}/Medication?_summary=count" | jq -r '.total // 0')

echo "   Patients: $P_ACTUAL (expected: $EXPECTED)"
echo "   Medications: $M_ACTUAL (expected: $EXPECTED)"

[[ "$P_ACTUAL" -eq "$EXPECTED" ]] && echo "✅ Patient count OK" || { echo "❌ Patient fail"; exit 1; }
[[ "$M_ACTUAL" -eq "$EXPECTED" ]] && echo "✅ Medication count OK" || { echo "❌ Medication fail"; exit 1; }


echo "🎉 All integration tests passed!"
