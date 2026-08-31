package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.structured_query.StructuredQuery;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;


/**
 * Runs the cohort query described by an {@link AnnotatedCrtdl} or a bare cohort definition, returning either
 * the matching patient IDs or the underlying FHIR {@link ListResource}.
 * <p>
 * Depending on {@code torch.useCql}, this either calls Flare or
 * translates the structured query to CQL and calls the CQL client.
 */
public class CohortQueryService {
    private static final Logger logger = LoggerFactory.getLogger(CohortQueryService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CqlClient cqlClient;
    private final Translator cqlQueryTranslator;
    private final boolean useCql;

    public CohortQueryService(@Qualifier("flareClient") WebClient webClient,
                              Translator cqlQueryTranslator,
                              CqlClient cqlClient,
                              @Value("${torch.useCql}") boolean useCql
    ) {
        this.webClient = webClient;
        this.objectMapper = new ObjectMapper();
        this.cqlClient = cqlClient;
        this.useCql = useCql;
        this.cqlQueryTranslator = cqlQueryTranslator;
    }

    /**
     * Executes the cohort definition contained in the given {@link AnnotatedCrtdl}.
     *
     * @param crtdl annotated CRTDL containing a cohort definition (structured query)
     * @return mono emitting the list of matching patient IDs
     */
    public Mono<List<String>> runCohortQuery(AnnotatedCrtdl crtdl) {
        JsonNode cohortDefinition = crtdl.cohortDefinition();
        return useCql ? fetchPatientListUsingCql(cohortDefinition) : fetchPatientListFromFlare(cohortDefinition);
    }

    /**
     * Executes the given cohort definition (structured query) against Flare or CQL, per {@code torch.useCql},
     * and returns the resulting cohort as the underlying FHIR {@link ListResource} rather than a plain ID list.
     * <p>
     * On the CQL path this is the actual subject-results list created by the FHIR server's
     * {@code $evaluate-measure} operation. On the Flare path, which has no such server-side resource, a
     * {@link ListResource} is synthesized locally from the returned patient IDs.
     *
     * @param cohortDefinition the structured query JSON node (the {@code cohortDefinition} field of a CRTDL)
     * @return mono emitting the matching cohort as a FHIR {@link ListResource}
     */
    public Mono<ListResource> evaluateCohortAsFhirList(JsonNode cohortDefinition) {
        return useCql ? fetchPatientListAsFhirList(cohortDefinition)
                : fetchPatientListFromFlare(cohortDefinition).map(CohortQueryService::toFhirList);
    }

    Mono<List<String>> fetchPatientListFromFlare(JsonNode cohortDefinition) {
        return webClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(cohortDefinition.toString())
                .retrieve()
                .onStatus(status -> status.value() >= 400, ClientResponse::createException)
                .bodyToMono(String.class)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(response -> {
                    try {
                        List<String> list = objectMapper.readValue(response, new TypeReference<>() {
                        });
                        logger.debug("Got  {} patient IDs", list.size());
                        return Mono.just(list);
                    } catch (JsonProcessingException e) {
                        logger.error("Error parsing response: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Error parsing response", e));
                    }
                })
                .doOnSubscribe(subscription -> logger.debug("Fetching patient list from Flare"))
                .doOnError(e -> logger.error("Error fetching patient list from Flare: {}", e.getMessage()));
    }

    Mono<List<String>> fetchPatientListUsingCql(JsonNode cohortDefinition) {
        return toCqlString(cohortDefinition)
                .flatMapMany(cqlClient::fetchPatientIds)
                .collectList();
    }

    private Mono<ListResource> fetchPatientListAsFhirList(JsonNode cohortDefinition) {
        return toCqlString(cohortDefinition).flatMap(cqlClient::fetchPatientList);
    }

    private Mono<String> toCqlString(JsonNode cohortDefinition) {
        return Mono.fromCallable(() -> objectMapper.treeToValue(cohortDefinition, StructuredQuery.class))
                .map(ccdl -> cqlQueryTranslator.toCql(ccdl).print());
    }

    static ListResource toFhirList(List<String> patientIds) {
        var list = new ListResource();
        list.setStatus(ListResource.ListStatus.CURRENT);
        list.setMode(ListResource.ListMode.WORKING);
        patientIds.forEach(id -> list.addEntry().setItem(new Reference("Patient/" + id)));
        return list;
    }

    /**
     * Translates a cohort definition (structured query) to a CQL string without executing it.
     *
     * @param cohortDefinition the structured query JSON node (the {@code cohortDefinition} field of a CRTDL)
     * @return mono emitting the CQL representation of the cohort query
     */
    public Mono<String> translateToCql(JsonNode cohortDefinition) {
        return Mono.fromCallable(() -> {
            try {
                StructuredQuery sq = objectMapper.treeToValue(cohortDefinition, StructuredQuery.class);
                return cqlQueryTranslator.toCql(sq).print();
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid cohort definition: " + e.getMessage(), e);
            }
        });
    }
}
