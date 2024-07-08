package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.model.Crtdl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



public class CrtdlTest {


    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_diagnosis_withoutCCDL.json"))) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            assertNotNull(crtdl);
            Attribute attribute1 = crtdl.getDataExtraction().getAttributeGroups().get(0).getAttributes().get(0);
            assertEquals("Condition.code",attribute1.getAttributeRef());
            assertEquals(false,attribute1.isMustHave());
        } catch (Exception e) {
            e.printStackTrace();
        }



    }


    @Test
    public void testObservation() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_observation.json"))) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            assertNotNull(crtdl);
            Attribute attribute2 = crtdl.getDataExtraction().getAttributeGroups().get(0).getAttributes().get(1);
            assertEquals("Observation.value[x]",attribute2.getAttributeRef());
            assertEquals(true,attribute2.isMustHave());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }




}

