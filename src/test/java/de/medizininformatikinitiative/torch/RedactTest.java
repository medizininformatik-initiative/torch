package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;


public class RedactTest extends BaseTest {

    @Test
    public void testDiagnosis() {


        String[] resources = {"Diagnosis1.json", "Diagnosis2.json"};

        Arrays.stream(resources).forEach(resource -> {
            try {
                logger.info("Resource Handled {}",resource);
                DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Condition/" + resource);
                DomainResource resourceExpected = (DomainResource) ResourceReader.readResource("src/test/resources/RedactTest/expectedOutput/" + resource);
                resourceSrc = (DomainResource) redaction.redact(resourceSrc);
                Assertions.assertEquals(parser.setPrettyPrint(true).encodeResourceToString(resourceExpected), parser.setPrettyPrint(true).encodeResourceToString(resourceSrc), " Expected not equal to actual output");
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
                resourceSrc = (DomainResource) redaction.redact(resourceSrc);
                Assertions.assertEquals(parser.setPrettyPrint(true).encodeResourceToString(resourceExpected), parser.setPrettyPrint(true).encodeResourceToString(resourceSrc), " Expected not equal to actual output");

            } catch (Exception e) {
                logger.error(" ", e);
            }

        });


    }


}

