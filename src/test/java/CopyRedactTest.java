import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.FhirExtensionsUtil;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class CopyRedactTest {







    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;
    public CopyRedactTest() {
        ctx=FhirContext.forR4();
        parser = ctx.newJsonParser();
        CDS= new CDSStructureDefinitionHandler();
        try {

            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/StructureDefinition-mii-pr-person-patient.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-DiagnosticReportLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ObservationLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ServiceRequestLab.json");

            assertNotNull(CDS.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab"), "The element should be contained in the map");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }




    @Test
    public void testManualOperations() {

        /*
        parse resource from file
         */
        StructureDefinition definition = CDS.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab");


        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
        Class<? extends DomainResource> resourceClass = (Class<? extends DomainResource>) ctx.getResourceDefinition(definition.getType()).getImplementingClass();
        DomainResource resource;
        try {
            resource = resourceClass.getDeclaredConstructor().newInstance();
            // Check that the resource is a Patient
            assertEquals("InputResources/DiagnosticReport", definition.getType(), "Resource type should be DiagnosticReport");
            resource.setProperty("code",new CodeableConcept().addExtension(FhirExtensionsUtil.createAbsentReasonExtension("masked")));
            resource.setProperty("identifier",new Identifier().addExtension(FhirExtensionsUtil.createAbsentReasonExtension("masked")));

            // Check that the resource is a Patient
            assertEquals("Identifier",resource.getChildByName("identifier").getTypeCode());
            // Add more assertions as needed for other elements

            String resourceJson = parser.setPrettyPrint(true).encodeResourceToString(resource);
            System.out.println(resourceJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
