package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class RedactTest {

    private static final Logger logger = LoggerFactory.getLogger(RedactTest.class);

    // Create an instance of BaseTestSetup
    private final IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();

    private final FhirContext fhirContext = FhirContext.forR4();

    @ParameterizedTest
    @ValueSource(strings = {"Diagnosis1.json", "Diagnosis2.json"})
    public void testDiagnosis(String resource) throws IOException {
        logger.info("Resource Handled {}", resource);
        DomainResource resourceSrc = integrationTestSetup.readResource("src/test/resources/InputResources/Condition/" + resource);
        DomainResource resourceExpected = integrationTestSetup.readResource("src/test/resources/RedactTest/expectedOutput/" + resource);

        resourceSrc = (DomainResource) integrationTestSetup.redaction().redact(resourceSrc);

        assertThat(fhirContext.newJsonParser().encodeResourceToString(resourceSrc)).
                isEqualTo(fhirContext.newJsonParser().encodeResourceToString(resourceExpected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Observation_lab_missing_Elements.json"})
    public void testObservation(String resource) throws IOException {
        DomainResource resourceSrc = integrationTestSetup.readResource("src/test/resources/InputResources/Observation/" + resource);
        DomainResource resourceExpected = integrationTestSetup.readResource("src/test/resources/RedactTest/expectedOutput/" + resource);

        resourceSrc = (DomainResource) integrationTestSetup.redaction().redact(resourceSrc);

        assertThat(fhirContext.newJsonParser().encodeResourceToString(resourceSrc)).
                isEqualTo(fhirContext.newJsonParser().encodeResourceToString(resourceExpected));
    }
}
