FROM eclipse-temurin:25.0.1_8-jre-alpine@sha256:9c65fe190cb22ba92f50b8d29a749d0f1cfb2437e09fe5fbf9ff927c45fc6e99

ENV JAVA_TOOL_OPTIONS="-Xmx4g"
ENV CERTIFICATE_PATH=/app/certs
ENV TRUSTSTORE_PATH=/app/truststore
ENV TRUSTSTORE_FILE=self-signed-truststore.jks

COPY target/torch.jar /app/
COPY mappings  /app/mappings
COPY ontology /app/ontology
COPY search-parameters.json /app/

RUN mkdir -p "$CERTIFICATE_PATH" "$TRUSTSTORE_PATH" /app/output \
 && chown -R 1001:1001 /app \
 && chmod 755 /app/output

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod 755 /docker-entrypoint.sh

WORKDIR /app
USER 1001

ENTRYPOINT ["/bin/sh", "/docker-entrypoint.sh"]
