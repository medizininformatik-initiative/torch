FROM eclipse-temurin:25-jre-alpine@sha256:bf9c91071c4f90afebb31d735f111735975d6fe2b668a82339f8204202203621

ENV JAVA_TOOL_OPTIONS="-Xmx4g"
ENV CERTIFICATE_PATH=/app/certs
ENV TRUSTSTORE_PATH=/app/truststore
ENV TRUSTSTORE_FILE=self-signed-truststore.jks

COPY target/torch.jar /app/
COPY structureDefinitions /app/structureDefinitions
COPY mappings  /app/mappings
COPY ontology /app/ontology
COPY search-parameters.json /app/

RUN mkdir -p $CERTIFICATE_PATH $TRUSTSTORE_PATH
RUN mkdir /app/output

RUN chown -R 1001:1001 /app

COPY docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

WORKDIR /app
USER 1001

ENTRYPOINT ["/bin/sh", "/docker-entrypoint.sh"]
