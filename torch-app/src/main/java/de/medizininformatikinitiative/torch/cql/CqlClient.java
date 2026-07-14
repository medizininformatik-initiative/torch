package de.medizininformatikinitiative.torch.cql;

import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;

public class CqlClient {

    private static final Logger logger = LoggerFactory.getLogger(CqlClient.class);

    private final FhirHelper fhirHelper;
    private final DataStore dataStore;

    public CqlClient(FhirHelper fhirHelper, DataStore dataStore) {
        this.fhirHelper = fhirHelper;
        this.dataStore = dataStore;
    }

    public Flux<String> fetchPatientIds(String cqlQuery) {
        var libraryUri = "urn:uuid:" + UUID.randomUUID();
        var measureUri = "urn:uuid:" + UUID.randomUUID();
        logger.debug("Fetch patient IDs: Library {}, Measure {}", libraryUri, measureUri);

        Parameters params = fhirHelper.getListExecutionParams();
        params.setParameter("measure", measureUri);

        Bundle measureLibraryBundle = fhirHelper.createBundle(cqlQuery, libraryUri, measureUri);

        return dataStore.transact(measureLibraryBundle)
                .then(Mono.defer(() -> dataStore.evaluateMeasure(params)))
                .map(CqlClient::extractSubjectListId)
                .map(CqlClient::createPatientQuery)
                .flux()
                .flatMap(query -> dataStore.search(query, Patient.class))
                .flatMap(patient -> {
                    var id = patient.getIdPart();
                    return id == null ? Flux.error(new RuntimeException("Encountered Patient Resource without ID")) : Flux.just(id);
                });
    }

    private static String extractSubjectListId(MeasureReport measureReport) {
        return measureReport.getGroupFirstRep()
                .getPopulationFirstRep()
                .getSubjectResults()
                .getReferenceElement()
                .getIdPart();
    }

    private static Query createPatientQuery(String subjectListId) {
        return new Query("Patient", QueryParams.of("_list", stringValue(subjectListId)));
    }
}
