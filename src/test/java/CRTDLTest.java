import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.ElementCopier;
import de.medizininformatikinitiative.util.model.Attribute;
import de.medizininformatikinitiative.util.model.Root;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class CRTDLTest {


    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_diagnosis_withoutCCDL.json"))) {
            Root root = objectMapper.readValue(fis, Root.class);
            assertNotNull(root);
            Attribute attribute1 = root.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().get(0);
            assertEquals("Condition.code",attribute1.getAttributeRef());
            assertEquals(false,attribute1.isMustHave());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    @Test
    public void testObservation() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_observation.json"))) {
            Root root = objectMapper.readValue(fis, Root.class);
            assertNotNull(root);
            Attribute attribute2 = root.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().get(1);
            assertEquals("Observation.value[x]",attribute2.getAttributeRef());
            assertEquals(true,attribute2.isMustHave());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }




}

