package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.model.Crtdl;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;


public class CrtdlTest extends BaseTest {


    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_diagnosis_basic_date.json")) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            assertNotNull(crtdl);
            Attribute attribute1 = crtdl.getDataExtraction().getAttributeGroups().getFirst().getAttributes().getFirst();
            assertEquals("Condition.code", attribute1.getAttributeRef());
            assertFalse(attribute1.isMustHave());
        } catch (Exception e) {
            logger.error(" ", e);
        }

    }


    @Test
    public void testObservation() {
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            byte[] bytes = fis.readAllBytes();
            Crtdl crtdl = objectMapper.readValue(bytes, Crtdl.class);
            assertNotNull(crtdl);
            Attribute attribute2 = crtdl.getDataExtraction().getAttributeGroups().getFirst().getAttributes().get(1);
            assertEquals("Observation.encounter", attribute2.getAttributeRef());
            assertFalse(attribute2.isMustHave());

        } catch (Exception e) {
            logger.error(" ", e);
        }


    }


}

