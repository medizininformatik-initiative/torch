FROM eclipse-temurin:21.0.8_9-jre-alpine@sha256:4ca7eff3ab0ef9b41f5fefa35efaeda9ed8d26e161e1192473b24b3a6c348aef

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
