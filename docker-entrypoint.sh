#!/bin/sh

TRUSTSTORE_FILE="/app/truststore/self-signed-truststore.jks"
TRUSTSTORE_PASS=${TRUSTSTORE_PASS:-changeit}
KEY_PASS=${KEY_PASS:-changeit}

ca_files=$(find certs -type f -name '*.pem')

if [ -n "$ca_files" ]; then

    echo "# At least one CA file with extension *.pem found in certs folder -> starting torch with own CAs"

    if [ -f "$TRUSTSTORE_FILE" ]; then
          echo "## Truststore already exists -> resetting truststore"
          rm "$TRUSTSTORE_FILE"
    fi

    keytool -genkey -alias self-signed-truststore -keyalg RSA -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -keypass "$KEY_PASS" -dname "CN=self-signed,OU=self-signed,O=self-signed,L=self-signed,S=self-signed,C=TE"
    keytool -delete -alias self-signed-truststore -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -noprompt

    for filename in $ca_files; do

      echo "### ADDING CERT: $filename"
      keytool -delete -alias "$filename" -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -noprompt > /dev/null 2>&1
      keytool -importcert -alias "$filename" -file "$filename" -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -noprompt

    done

    java -Djavax.net.ssl.trustStore="$TRUSTSTORE_FILE" -Djavax.net.ssl.trustStorePassword="$TRUSTSTORE_PASS" -jar torch.jar
else
    echo "# No CA *.pem cert files found in /app/certs -> starting torch without own CAs"
    java -jar torch.jar
fi