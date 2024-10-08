package de.medizininformatikinitiative.torch.cql;

import de.medizininformatikinitiative.flare.model.fhir.Query;
import de.medizininformatikinitiative.flare.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.service.DataStore;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static de.medizininformatikinitiative.flare.model.fhir.QueryParams.stringValue;


@Slf4j
public class CqlClient {
    private final FhirConnector fhirConnector;
    private final FhirHelper fhirHelper;

    private final DataStore dataStore;

    public CqlClient(FhirConnector fhirConnector,
                     FhirHelper fhirHelper, DataStore dataStore) {
        this.fhirConnector = Objects.requireNonNull(fhirConnector);
        this.fhirHelper = fhirHelper;
        this.dataStore = dataStore;

    }


    public Mono<List<String>> getPatientListByCql(String cqlQuery) {

        var libraryUri = "urn:uuid" + UUID.randomUUID();
        var measureUri = "urn:uuid" + UUID.randomUUID();
        MeasureReport measureReport;

        try {
            Bundle bundle = fhirHelper.createBundle(cqlQuery, libraryUri, measureUri);
            fhirConnector.transmitBundle(bundle);

            Parameters params = fhirHelper.getListExecutionParams();
            params.setParameter("measure", measureUri);
            measureReport = fhirConnector.evaluateMeasure(params);

            var subjectListId = measureReport.getGroupFirstRep().getPopulationFirstRep().getSubjectResults().getReferenceElement().getIdPart();

            QueryParams queryParams = QueryParams.of("_list", stringValue(subjectListId));
            Query fhirQuery = new Query("Patient", queryParams);
            return dataStore.executeCollectPatientIds(fhirQuery);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
