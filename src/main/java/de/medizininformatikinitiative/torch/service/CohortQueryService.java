package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.structured_query.StructuredQuery;
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
 * Runs the cohort query described by an {@link AnnotatedCrtdl} and returns the matching patient IDs.
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
        try {
            return useCql ? fetchPatientListUsingCql(crtdl) : fetchPatientListFromFlare(crtdl);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public Mono<List<String>> fetchPatientListFromFlare(AnnotatedCrtdl crtdl) {
        return webClient.post()
                .uri("/query/execute-cohort")
                .contentType(MediaType.parseMediaType("application/sq+json"))
                .bodyValue(crtdl.cohortDefinition().toString())
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

    public Mono<List<String>> fetchPatientListUsingCql(AnnotatedCrtdl crtdl) throws JsonProcessingException {
        StructuredQuery ccdl = objectMapper.treeToValue(crtdl.cohortDefinition(), StructuredQuery.class);
        return cqlClient.fetchPatientIds(cqlQueryTranslator.toCql(ccdl).print())
                .collectList();
    }
}
