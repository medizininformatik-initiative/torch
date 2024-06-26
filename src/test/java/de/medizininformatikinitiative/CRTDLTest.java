package de.medizininformatikinitiative;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.util.model.Attribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class CRTDLTest {


    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_diagnosis_withoutCCDL.json"))) {
            de.medizininformatikinitiative.util.model.CRTDL CRTDL = objectMapper.readValue(fis, de.medizininformatikinitiative.util.model.CRTDL.class);
            assertNotNull(CRTDL);
            Attribute attribute1 = CRTDL.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().get(0);
            assertEquals("Condition.code",attribute1.getAttributeRef());
            assertEquals(false,attribute1.isMustHave());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    @Test
    public void testObservation() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_observation.json"))) {
            de.medizininformatikinitiative.util.model.CRTDL CRTDL = objectMapper.readValue(fis, de.medizininformatikinitiative.util.model.CRTDL.class);
            assertNotNull(CRTDL);
            Attribute attribute2 = CRTDL.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().get(1);
            assertEquals("Observation.value[x]",attribute2.getAttributeRef());
            assertEquals(true,attribute2.isMustHave());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }




}

