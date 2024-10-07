package de.medizininformatikinitiative.torch.cql;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@Slf4j
public class CqlClient {
    private final FhirConnector fhirConnector;
    private final FhirHelper fhirHelper;

    public CqlClient(FhirConnector fhirConnector,
                     FhirHelper fhirHelper) {
        this.fhirConnector = Objects.requireNonNull(fhirConnector);
        this.fhirHelper = fhirHelper;
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
            measureReport = fhirConnector.evaluateMeasure(measureUri, params);

            var subjectListId = measureReport.getGroupFirstRep().getPopulationFirstRep().getSubjectResults().getReferenceElement().getIdPart();
            ListResource patientList = fhirConnector.getSubjectList(subjectListId);
            List<String> patientidList = patientList.getEntry().stream().map((var entry) -> entry.getItem().getReferenceElement().getIdPart()).toList();
            return Mono.just(patientidList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
