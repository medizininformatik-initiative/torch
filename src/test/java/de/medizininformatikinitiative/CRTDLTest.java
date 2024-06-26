import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.util.FHIRSearchBuilder;
import de.medizininformatikinitiative.util.model.Attribute;
import de.medizininformatikinitiative.util.model.CRTDL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class CRTDLTest {


    private final ObjectMapper objectMapper = new ObjectMapper();

    private FHIRSearchBuilder searchBuilder;

    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_diagnosis_withoutCCDL.json"))) {
            CRTDL CRTDL = objectMapper.readValue(fis, CRTDL.class);
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
            CRTDL CRTDL = objectMapper.readValue(fis, CRTDL.class);
            assertNotNull(CRTDL);
            Attribute attribute2 = CRTDL.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().get(1);
            assertEquals("Observation.value[x]",attribute2.getAttributeRef());
            assertEquals(true,attribute2.isMustHave());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }




}

