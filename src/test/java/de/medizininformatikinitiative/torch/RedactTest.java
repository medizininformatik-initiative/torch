package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.setup.BaseTestSetup;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class RedactTest {

    private static final Logger logger = LoggerFactory.getLogger(RedactTest.class);

    // Create an instance of BaseTestSetup
    private final BaseTestSetup baseTestSetup = new BaseTestSetup();

    @Test
    public void testDiagnosis() {
        String[] resources = {"Diagnosis1.json", "Diagnosis2.json"};

        Arrays.stream(resources).forEach(resource -> {
            try {
                logger.info("Resource Handled {}", resource);
                DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Condition/" + resource);
                DomainResource resourceExpected = (DomainResource) ResourceReader.readResource("src/test/resources/RedactTest/expectedOutput/" + resource);

                // Use redaction from BaseTestSetup
                resourceSrc = (DomainResource) baseTestSetup.getRedaction().redact(resourceSrc);

                Assertions.assertEquals(
                        baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceExpected),
                        baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceSrc),
                        "Expected not equal to actual output"
                );
            } catch (Exception e) {
                logger.error(" ", e);
            }
        });
    }

    @Test
    public void testObservation() {
        String[] resources = {"Observation_lab_missing_Elements.json"};

        Arrays.stream(resources).forEach(resource -> {
            try {
                DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Observation/" + resource);
                DomainResource resourceExpected = (DomainResource) ResourceReader.readResource("src/test/resources/RedactTest/expectedOutput/" + resource);

                // Use redaction from BaseTestSetup
                resourceSrc = (DomainResource) baseTestSetup.getRedaction().redact(resourceSrc);

                Assertions.assertEquals(
                        baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceExpected),
                        baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceSrc),
                        "Expected not equal to actual output"
                );
            } catch (Exception e) {
                logger.error(" ", e);
            }
        });
    }
}
