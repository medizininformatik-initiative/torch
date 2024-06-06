import de.medizininformatikinitiative.util.ElementInfo;
import de.medizininformatikinitiative.StructureDefinitionParser;
import de.medizininformatikinitiative.util.FhirExtensionsUtil;
import de.medizininformatikinitiative.util.BluePrint;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class StructureDefinitionParserTest {





    private StructureDefinitionParser parser = new StructureDefinitionParser();

    private BluePrint min;

    private   DomainResource resource;



    public StructureDefinitionParserTest() {
        try {
            parser.readStructureDefinition("src/test/resources/StructureDefinition-mii-pr-person-patient.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        min = parser.resourceMap.get("https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient");
        resource=min.getResource();
    }

    @Test
    public void testElementMap() {

        // Retrieve the element info map from the resource creation
        Map<String, ElementInfo> elementInfoMap = min.getElementInfoMap();

        // Verify that the element info map contains expected entries
        assertNotNull(elementInfoMap, "Element info map should not be null");
        assertFalse(elementInfoMap.isEmpty(), "Element info map should not be empty");

        // Verify specific elements
        ElementInfo identifierInfo = elementInfoMap.get("Patient.identifier");
        assertNotNull(identifierInfo, "Identifier info should not be null");
        assertEquals("Patient.identifier", identifierInfo.path, "Path should match");
        assertEquals("Identifier", identifierInfo.dataType, "Data type should match");

        ElementInfo nameInfo = elementInfoMap.get("Patient.name");
        assertNotNull(nameInfo, "Name info should not be null");
        assertEquals("Patient.name", nameInfo.path, "Path should match");
        assertEquals("HumanName", nameInfo.dataType, "Data type should match");

    }



    @Test
    public void testCreateMinimalResource() {

            // Check that the resource is a Patient
            assertEquals("Patient", resource.getResourceType().name());
            Address address =  new Address();
            address.setProperty("city", new StringType("test").addExtension(FhirExtensionsUtil.createAbsentReasonExtension("masked")));

        resource.setProperty("address",address);
            resource.setProperty("identifier",new Identifier().addExtension(FhirExtensionsUtil.createAbsentReasonExtension("masked")));

            // Check that the resource is a Patient
            assertEquals("Identifier",resource.getChildByName("identifier").getTypeCode());
            // Add more assertions as needed for other elements

            String resourceJson = parser.jsonParser.setPrettyPrint(true).encodeResourceToString(resource);
            System.out.println(resourceJson);
    }
}
