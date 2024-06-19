import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.model.Attribute;
import de.medizininformatikinitiative.util.model.AttributeGroup;
import de.medizininformatikinitiative.util.FhirExtensionsUtil;
import de.medizininformatikinitiative.util.ResourceReader;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class CDSTest {







    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;
    public CDSTest() {
        ctx=FhirContext.forR4();
        parser = ctx.newJsonParser();
        CDS= new CDSStructureDefinitionHandler();
        try {

            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/StructureDefinition-mii-pr-person-patient.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-DiagnosticReportLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ObservationLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ServiceRequestLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ServiceRequestLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/StructureDefinition-mii-pr-diagnose-condition.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }




    @Test
    public void testDiagnosticReport() {
        /*
        parse CDS from file
         */
        StructureDefinition definition = CDS.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab");
        assertNotNull(definition, "The element should be contained in the map");
        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
        StructureDefinition.StructureDefinitionSnapshotComponent elementMap = CDS.getSnapshot("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab");
        ElementDefinition coding = elementMap.getElementByPath("DiagnosticReport.code.coding");
        assertNotNull(coding, "The element should be contained in the snapshot");

        List <Attribute > attributeList =  new ArrayList<>();
        attributeList.add(new Attribute("DiagnosticReport.code.coding",true));
        AttributeGroup attributeGroup = null;
        try {
            attributeGroup = new AttributeGroup(new URL("https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab"));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        attributeGroup.setAttributes(attributeList);



        HashMap<String, AttributeGroup> AttributeGroups=new HashMap<>();;
        AttributeGroups.put("DiagnosticReport",attributeGroup);

        try {
            DomainResource resource = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/DiagnosticReport/Example-MI-Initative-Laborprofile-Laborbefund.json");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDiagnosis() {
        /*
        parse CDS from file
         */
        StructureDefinition definition = CDS.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        assertNotNull(definition, "The element should be contained in the map");



        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
        Class<? extends DomainResource> resourceClass = (Class<? extends DomainResource>) ctx.getResourceDefinition(definition.getType()).getImplementingClass();
        DomainResource resource;
        try {
            resource = resourceClass.getDeclaredConstructor().newInstance();
            // Check that the resource is a Diagnostic Report
            assertEquals("Condition", definition.getType(), "Resource type should be Condition");
            //resource.setProperty("code",new CodeableConcept().addExtension(FhirExtensionsUtil.createAbsentReasonExtension("masked")));
            resource.setProperty("identifier",new Identifier().addExtension(FhirExtensionsUtil.createAbsentReasonExtension("masked")));

            assertEquals("Identifier",resource.getChildByName("identifier").getTypeCode());
            // Add more assertions as needed for other elements

            String resourceJson = parser.setPrettyPrint(true).encodeResourceToString(resource);
            System.out.println(resourceJson);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

