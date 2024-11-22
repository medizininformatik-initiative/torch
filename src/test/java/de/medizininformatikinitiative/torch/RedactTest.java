package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedactTest {
    private final IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();

    private final FhirContext fhirContext = FhirContext.forR4();

    @ParameterizedTest
    @ValueSource(strings = {"Diagnosis1.json", "Diagnosis2.json", "DiagnosisWithInvalidSliceCode.json"})
    public void testDiagnosis(String resource) throws IOException {
        DomainResource src = integrationTestSetup.readResource("src/test/resources/InputResources/Condition/" + resource);
        DomainResource expected = integrationTestSetup.readResource("src/test/resources/RedactTest/expectedOutput/" + resource);

        src = (DomainResource) integrationTestSetup.redaction().redact(src);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src)).
                isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Observation_lab_Missing_Elements_Unknown_Slices.json"})
    public void testObservation(String resource) throws IOException {
        DomainResource src = integrationTestSetup.readResource("src/test/resources/InputResources/Observation/" + resource);
        DomainResource expected = integrationTestSetup.readResource("src/test/resources/RedactTest/expectedOutput/" + resource);

        src = (DomainResource) integrationTestSetup.redaction().redact(src);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(src)).
                isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }

    @Test
    public void unknownSlice() throws IOException {
        DomainResource expected = integrationTestSetup.readResource("src/test/resources/RedactTest/expectedOutput/unknownSlice.json");
        Condition src = new Condition();
        Meta meta = new Meta();
        meta.setProfile(List.of(new CanonicalType("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        src.setMeta(meta);
        Coding code = new Coding("Test", "Test", "Test");
        CodeableConcept concept = new CodeableConcept();
        concept.setCoding(List.of(code));
        src.setCode(concept);

        DomainResource tgt = (DomainResource) integrationTestSetup.redaction().redact(src);

        assertThat(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt)).
                isEqualTo(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expected));
    }


}
