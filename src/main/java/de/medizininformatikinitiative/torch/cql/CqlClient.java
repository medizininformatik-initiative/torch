package de.medizininformatikinitiative.torch.cql;

import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;


public class CqlClient {
    private static final Logger logger = LoggerFactory.getLogger(CqlClient.class);
    private final FhirHelper fhirHelper;

    private final DataStore dataStore;

    public CqlClient(
            FhirHelper fhirHelper, DataStore dataStore) {
        this.fhirHelper = fhirHelper;
        this.dataStore = dataStore;

    }


    public Mono<List<String>> getPatientListByCql(String cqlQuery) {
        var libraryUri = "urn:uuid:" + UUID.randomUUID();
        var measureUri = "urn:uuid:" + UUID.randomUUID();
        Parameters params;
        logger.debug(" Library {} \n Measure {}", libraryUri, measureUri);

        try {
            params = fhirHelper.getListExecutionParams();
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve list execution parameters", e);
        }

        params.setParameter("measure", measureUri);
        Parameters finalParams = params;

        return Mono.fromCallable(() -> fhirHelper.createBundle(cqlQuery, libraryUri, measureUri))
                .doOnError(e -> logger.error("Error creating FHIR bundle with CQL query: {}. Library URI: {}, Measure URI: {}. Error: {}",
                        cqlQuery, libraryUri, measureUri, e.getMessage(), e))
                .flatMap(bundle -> dataStore.transmitBundle(bundle)  // transmitBundle returns Mono<Void>
                        .doOnSuccess(aVoid -> logger.info("Successfully transmitted FHIR bundle."))
                        .doOnError(e -> logger.error("Error transmitting FHIR bundle to the server. Bundle: {}. Error: {}",
                                bundle, e.getMessage(), e))
                        .then(Mono.defer(() -> {
                            logger.info("Proceeding to measure evaluation.");
                            return dataStore.evaluateMeasure(finalParams)
                                    .doOnError(e -> logger.error("Error evaluating measure for measureUri: {}. Parameters: {}. Error: {}",
                                            measureUri, finalParams, e.getMessage(), e));
                        }))
                )
                .flatMap(measureReport -> {
                    var subjectListId = measureReport.getGroupFirstRep()
                            .getPopulationFirstRep()
                            .getSubjectResults()
                            .getReferenceElement()
                            .getIdPart();

                    QueryParams queryParams = QueryParams.of("_list", stringValue(subjectListId));
                    Query fhirQuery = new Query("Patient", queryParams);

                    return dataStore.executeCollectPatientIds(fhirQuery)
                            .doOnError(e -> logger.error("Error executing FHIR query for patient list. Query: {}. Error: {}",
                                    fhirQuery, e.getMessage(), e));
                })
                .doOnError(error -> logger.error("An unexpected error occurred during the patient list retrieval process. CQL query: {}, Library URI: {}, Measure URI: {}. Error: {}",
                        cqlQuery, libraryUri, measureUri, error.getMessage(), error));
    }


}
