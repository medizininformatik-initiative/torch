FROM eclipse-temurin:25.0.2_10-jre-alpine@sha256:f10d6259d0798c1e12179b6bf3b63cea0d6843f7b09c9f9c9c422c50e44379ec

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
