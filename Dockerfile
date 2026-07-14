FROM eclipse-temurin:25.0.3_9-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0

RUN apk update && apk upgrade --no-cache

ENV JAVA_TOOL_OPTIONS="-Xmx4g"
ENV CERTIFICATE_PATH=/app/certs
ENV TRUSTSTORE_PATH=/app/truststore

COPY torch-app/target/torch.jar /app/
COPY torch-app/mappings  /app/mappings
COPY torch-app/ontology /app/ontology
COPY torch-app/search-parameters.json /app/

# Default consent implementation, packaged as a separate, swappable jar rather than bundled into
# torch.jar -- replace it in this directory (or point LOADER_PATH elsewhere) to use a different
# ConsentEvaluator implementation without rebuilding torch-app. See issue #1068.
COPY torch-consent-mii/target/torch-consent-mii-*.jar /app/plugins/torch-consent-mii.jar

RUN mkdir -p "$CERTIFICATE_PATH" "$TRUSTSTORE_PATH" /app/output \
 && chown -R 1001:1001 /app \
 && chmod 755 /app/output

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod 755 /docker-entrypoint.sh

WORKDIR /app
USER 1001

ENTRYPOINT ["/bin/sh", "/docker-entrypoint.sh"]
