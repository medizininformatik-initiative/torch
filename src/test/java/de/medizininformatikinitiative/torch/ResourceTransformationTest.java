package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.FhirSearchBuilder;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.FileInputStream;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class ResourceTransformationTest {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformationTest.class);

    // Create an instance of BaseTestSetup
    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();

    private static WebClient webClient;  // Assuming this will be mocked or autowired in the Spring context

    private static DataStore dataStore;
    private static final FhirSearchBuilder fhirSearchBuilder = new FhirSearchBuilder(null);

    @BeforeAll
    static void setup() {
        // Initialize DataStore with dependencies from baseTestSetup
        dataStore = new DataStore(webClient, INTEGRATION_TEST_SETUP.getFhirContext(), Clock.systemDefaultZone(), 500);
    }

    @Test
    public void testObservation() {

        ResourceTransformer transformer = null;
        try {
            transformer = new ResourceTransformer(
                    dataStore,
                    new ConsentHandler(
                            dataStore,
                            new ConsentCodeMapper("src/test/resources/mappings/consent-mappings.json",new ObjectMapper()),
                            "src/test/resources/mappings/profile_to_consent.json",
                            INTEGRATION_TEST_SETUP.getCds(),
                            INTEGRATION_TEST_SETUP.getFhirContext(),
                            fhirSearchBuilder
                    ), INTEGRATION_TEST_SETUP.getCopier(), INTEGRATION_TEST_SETUP.getRedaction(),INTEGRATION_TEST_SETUP.getFhirContext(),
                    fhirSearchBuilder
            );

            FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json");
            Crtdl crtdl = INTEGRATION_TEST_SETUP.getObjectMapper().readValue(fis, Crtdl.class);

            DomainResource resourcesrc = INTEGRATION_TEST_SETUP.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = INTEGRATION_TEST_SETUP.readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");

            DomainResource tgt = (DomainResource) transformer.transform(resourcesrc, crtdl.dataExtraction().attributeGroups().getFirst());

            assertNotNull(tgt);
            assertEquals(
                    INTEGRATION_TEST_SETUP.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceexpected),
                    INTEGRATION_TEST_SETUP.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt),
                    "Expected not equal to actual output"
            );
            fis.close();
        } catch (Exception e) {
            logger.error("", e);
        }
    }
}
