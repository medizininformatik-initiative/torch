#!/bin/sh
umask 0022
TRUSTSTORE_FILE=${TRUSTSTORE_FILE:-""}
TRUSTSTORE_PASS=${TRUSTSTORE_PASS:-changeit}
KEY_PASS=${KEY_PASS:-changeit}
GENERATED_TRUSTSTORE="/app/truststore/self-signed-truststore.jks"

if [ -n "$TRUSTSTORE_FILE" ]; then

    echo "# TRUSTSTORE_FILE is set -> starting torch with pre-configured truststore: $TRUSTSTORE_FILE"
    java -Djavax.net.ssl.trustStore="$TRUSTSTORE_FILE" -Djavax.net.ssl.trustStorePassword="$TRUSTSTORE_PASS" -jar torch.jar

else

    ca_files=$(find certs -type f -name '*.pem')

    if [ -n "$ca_files" ]; then

        echo "# At least one CA file with extension *.pem found in certs folder -> starting torch with own CAs"

        if [ -f "$GENERATED_TRUSTSTORE" ]; then
              echo "## Truststore already exists -> resetting truststore"
              rm "$GENERATED_TRUSTSTORE"
        fi

        keytool -genkey -alias self-signed-truststore -keyalg RSA -keystore "$GENERATED_TRUSTSTORE" -storepass "$TRUSTSTORE_PASS" -keypass "$KEY_PASS" -dname "CN=self-signed,OU=self-signed,O=self-signed,L=self-signed,S=self-signed,C=TE"
        keytool -delete -alias self-signed-truststore -keystore "$GENERATED_TRUSTSTORE" -storepass "$TRUSTSTORE_PASS" -noprompt

        for filename in $ca_files; do

          echo "### ADDING CERT: $filename"
          keytool -delete -alias "$filename" -keystore "$GENERATED_TRUSTSTORE" -storepass "$TRUSTSTORE_PASS" -noprompt > /dev/null 2>&1
          keytool -importcert -alias "$filename" -file "$filename" -keystore "$GENERATED_TRUSTSTORE" -storepass "$TRUSTSTORE_PASS" -noprompt

        done

        java -Djavax.net.ssl.trustStore="$GENERATED_TRUSTSTORE" -Djavax.net.ssl.trustStorePassword="$TRUSTSTORE_PASS" -jar torch.jar

    else
        echo "# No CA *.pem cert files found in /app/certs -> starting torch without own CAs"
        java -jar torch.jar
    fi

fi
