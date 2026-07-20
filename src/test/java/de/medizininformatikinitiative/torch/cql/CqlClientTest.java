package de.medizininformatikinitiative.torch.cql;

import de.medizininformatikinitiative.torch.exceptions.MeasureReportShapeException;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CqlClientTest {

    @Mock
    FhirHelper fhirHelper;

    @Mock
    DataStore dataStore;

    CqlClient cqlClient;

    @BeforeEach
    void setUp() {
        cqlClient = new CqlClient(fhirHelper, dataStore);

        when(fhirHelper.getListExecutionParams()).thenReturn(new Parameters());
        when(fhirHelper.createBundle(any(), any(), any())).thenReturn(new Bundle());
        when(dataStore.transact(any(Bundle.class))).thenReturn(Mono.empty());
    }

    @Test
    void fetchPatientIds_withWellFormedMeasureReport_returnsPatientIds() {
        var measureReport = new MeasureReport();
        measureReport.addGroup().addPopulation().setSubjectResults(new Reference("List/list-id"));
        when(dataStore.evaluateMeasure(any(Parameters.class))).thenReturn(Mono.just(measureReport));

        var patient = new Patient();
        patient.setId("patient-id");
        when(dataStore.search(any(Query.class), org.mockito.ArgumentMatchers.eq(Patient.class)))
                .thenReturn(Flux.just(patient));

        StepVerifier.create(cqlClient.fetchPatientIds("cql-query"))
                .expectNext("patient-id")
                .verifyComplete();
    }

    @Test
    void fetchPatientIds_withMeasureReportMissingGroup_errors() {
        when(dataStore.evaluateMeasure(any(Parameters.class))).thenReturn(Mono.just(new MeasureReport()));

        StepVerifier.create(cqlClient.fetchPatientIds("cql-query"))
                .verifyError(MeasureReportShapeException.class);
    }

    @Test
    void fetchPatientIds_withGroupMissingPopulation_errors() {
        var measureReport = new MeasureReport();
        measureReport.addGroup().setCode(new CodeableConcept().setText("group-without-population"));
        when(dataStore.evaluateMeasure(any(Parameters.class))).thenReturn(Mono.just(measureReport));

        StepVerifier.create(cqlClient.fetchPatientIds("cql-query"))
                .verifyError(MeasureReportShapeException.class);
    }

    @Test
    void fetchPatientIds_withMeasureReportMissingSubjectResults_errors() {
        var measureReport = new MeasureReport();
        measureReport.addGroup().addPopulation().setCount(1);
        when(dataStore.evaluateMeasure(any(Parameters.class))).thenReturn(Mono.just(measureReport));

        StepVerifier.create(cqlClient.fetchPatientIds("cql-query"))
                .verifyError(MeasureReportShapeException.class);
    }

    @Test
    void fetchPatientIds_withSubjectResultsReferenceMissingIdPart_errors() {
        var measureReport = new MeasureReport();
        measureReport.addGroup().addPopulation().setSubjectResults(new Reference().setDisplay("no reference string"));
        when(dataStore.evaluateMeasure(any(Parameters.class))).thenReturn(Mono.just(measureReport));

        StepVerifier.create(cqlClient.fetchPatientIds("cql-query"))
                .verifyError(MeasureReportShapeException.class);
    }
}
