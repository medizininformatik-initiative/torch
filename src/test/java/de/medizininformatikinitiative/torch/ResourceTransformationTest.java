package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.setup.BaseTestSetup;
import de.medizininformatikinitiative.torch.util.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.FileInputStream;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResourceTransformationTest {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformationTest.class);

    // Create an instance of BaseTestSetup
    private static final BaseTestSetup baseTestSetup = new BaseTestSetup();

    @Autowired
    private static WebClient webClient;  // Assuming this will be mocked or autowired in the Spring context

    private static DataStore dataStore;

    @BeforeAll
    static void setup() {
        // Initialize DataStore with dependencies from baseTestSetup
        dataStore = new DataStore(webClient, baseTestSetup.getFhirContext(), Clock.systemDefaultZone(), 500);
    }

    @Test
    public void testObservation() {

        ResourceTransformer transformer = null;
        try {
            transformer = new ResourceTransformer(
                    dataStore,
                    new ConsentHandler(
                            dataStore,
                            new ConsentCodeMapper("src/test/resources/mappings/consent-mappings.json"),
                            "src/test/resources/mappings/profile_to_consent.json",
                            baseTestSetup.getCds(),
                            baseTestSetup.getFhirContext()
                    ),baseTestSetup.getCopier(),baseTestSetup.getRedaction()
            );

            FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json");
            Crtdl crtdl = baseTestSetup.getObjectMapper().readValue(fis, Crtdl.class);

            DomainResource resourcesrc = baseTestSetup.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = baseTestSetup.readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");

            DomainResource tgt = (DomainResource) transformer.transform(resourcesrc, crtdl.getDataExtraction().getAttributeGroups().getFirst());

            assertNotNull(tgt);
            assertEquals(
                    baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceexpected),
                    baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt),
                    "Expected not equal to actual output"
            );
            fis.close();
        } catch (Exception e) {
            logger.error("", e);
        }
    }
}
