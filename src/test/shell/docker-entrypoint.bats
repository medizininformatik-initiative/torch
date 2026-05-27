#!/usr/bin/env bats
# Unit tests for docker-entrypoint.sh

SCRIPT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../../.." && pwd)/docker-entrypoint.sh"

setup() {
    WORK_DIR="$(mktemp -d)"
    MOCK_BIN="$(mktemp -d)"

    # Mock java: record the full argument list so assertions can inspect it
    cat > "$MOCK_BIN/java" << 'EOF'
#!/bin/sh
echo "java_called $*"
EOF
    chmod +x "$MOCK_BIN/java"

    # Mock keytool: succeed silently (we only check whether it was invoked)
    cat > "$MOCK_BIN/keytool" << 'EOF'
#!/bin/sh
echo "keytool_called $*"
EOF
    chmod +x "$MOCK_BIN/keytool"

    export PATH="$MOCK_BIN:$PATH"

    # The script runs 'find certs …' relative to CWD
    mkdir -p "$WORK_DIR/certs"
    cd "$WORK_DIR"

    unset TRUSTSTORE_FILE
    unset TRUSTSTORE_PASS
    unset KEY_PASS
}

teardown() {
    rm -rf "$WORK_DIR" "$MOCK_BIN"
}

# ---------------------------------------------------------------------------
# Default path: no TRUSTSTORE_FILE, no PEM certs
# ---------------------------------------------------------------------------

@test "starts without truststore flags when TRUSTSTORE_FILE is unset and no PEM certs exist" {
    run sh "$SCRIPT"
    [ "$status" -eq 0 ]
    [[ "$output" == *"java_called -jar torch.jar"* ]]
    [[ "$output" != *"trustStore"* ]]
}

# ---------------------------------------------------------------------------
# New path: pre-configured truststore via TRUSTSTORE_FILE
# ---------------------------------------------------------------------------

@test "passes TRUSTSTORE_FILE to java when the variable is set" {
    export TRUSTSTORE_FILE="/custom/my-truststore.jks"
    run sh "$SCRIPT"
    [ "$status" -eq 0 ]
    [[ "$output" == *"-Djavax.net.ssl.trustStore=/custom/my-truststore.jks"* ]]
}

@test "passes TRUSTSTORE_PASS to java when TRUSTSTORE_FILE is set" {
    export TRUSTSTORE_FILE="/custom/my-truststore.jks"
    export TRUSTSTORE_PASS="s3cr3t"
    run sh "$SCRIPT"
    [ "$status" -eq 0 ]
    [[ "$output" == *"-Djavax.net.ssl.trustStorePassword=s3cr3t"* ]]
}

@test "defaults TRUSTSTORE_PASS to 'changeit' when not provided" {
    export TRUSTSTORE_FILE="/custom/my-truststore.jks"
    run sh "$SCRIPT"
    [ "$status" -eq 0 ]
    [[ "$output" == *"-Djavax.net.ssl.trustStorePassword=changeit"* ]]
}

@test "does not call keytool when TRUSTSTORE_FILE is set" {
    export TRUSTSTORE_FILE="/custom/my-truststore.jks"
    run sh "$SCRIPT"
    [ "$status" -eq 0 ]
    [[ "$output" != *"keytool_called"* ]]
}

@test "TRUSTSTORE_FILE takes priority over PEM certs in the certs directory" {
    export TRUSTSTORE_FILE="/custom/my-truststore.jks"
    touch "$WORK_DIR/certs/ca.pem"
    run sh "$SCRIPT"
    [ "$status" -eq 0 ]
    [[ "$output" == *"-Djavax.net.ssl.trustStore=/custom/my-truststore.jks"* ]]
    [[ "$output" != *"keytool_called"* ]]
}

# ---------------------------------------------------------------------------
# Existing path: build truststore from PEM certs (behaviour unchanged)
# ---------------------------------------------------------------------------

@test "calls keytool and passes generated truststore to java when PEM certs are present" {
    touch "$WORK_DIR/certs/ca.pem"
    run sh "$SCRIPT"
    [ "$status" -eq 0 ]
    [[ "$output" == *"keytool_called"* ]]
    [[ "$output" == *"-Djavax.net.ssl.trustStore="* ]]
}
