FROM eclipse-temurin:21.0.8_9-jre@sha256:ea350fe1d8bc9b18dea4ad46bb9a0f4d6fbd0dbb3548092e3ff8f1a3fef09efe

RUN apt-get update && apt-get upgrade -y && \
    apt-get purge wget libbinutils libctf0 libctf-nobfd0 libncurses6 -y && \
    apt-get autoremove -y && apt-get clean && \
    rm -rf /var/lib/apt/lists/

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

ENTRYPOINT ["/bin/bash", "/docker-entrypoint.sh"]
