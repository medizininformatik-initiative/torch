package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class CrtdlTest {

    private static final Logger logger = LoggerFactory.getLogger(CrtdlTest.class);

    // Create an instance of BaseTestSetup
    private final IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();

    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_diagnosis_basic_date.json")) {
            Crtdl crtdl = integrationTestSetup.getObjectMapper().readValue(fis, Crtdl.class);
            assertNotNull(crtdl);
            Attribute attribute1 = crtdl.dataExtraction().attributeGroups().getFirst().attributes().getFirst();
            assertEquals("Condition.code", attribute1.attributeRef());
            assertFalse(attribute1.mustHave());
        } catch (Exception e) {
            logger.error(" ", e);
        }
    }

    @Test
    public void testObservation() {
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            byte[] bytes = fis.readAllBytes();
            Crtdl crtdl = integrationTestSetup.getObjectMapper().readValue(bytes, Crtdl.class);
            assertNotNull(crtdl);
            Attribute attribute2 = crtdl.dataExtraction().attributeGroups().getFirst().attributes().get(1);
            assertEquals("Observation.encounter", attribute2.attributeRef());
            assertFalse(attribute2.mustHave());
        } catch (Exception e) {
            logger.error(" ", e);
        }
    }
}
