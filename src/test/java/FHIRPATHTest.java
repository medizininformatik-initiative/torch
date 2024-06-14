import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.Redaction;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class FHIRPATHTest {




    String [] resources =  {"Diagnosis4.json","Diagnosis3.json","Diagnosis1.json","Diagnosis2.json"};


    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;
    public FHIRPATHTest() {
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

            StructureDefinition definition = CDS.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
            assertNotNull(definition, "The element should be contained in the map");
            assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    @Test
    public void testDiagnosis() {

        Redaction redaction = new Redaction(CDS);

            Arrays.stream(resources).forEach(resource ->{
                try {
                DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/RedactTest/Input/"+resource);
                String fhirPath = "Condition.meta";
                Optional<Element> classTest = ctx.newFhirPath().evaluateFirst(resourcesrc, fhirPath, Element.class);
                assertNotNull(classTest, "The element should be contained in the map");
                //assertEquals(resourcesrc, resourceexpected, resource+" Expected not equal to actual output");
            } catch (Exception e) {
                e.printStackTrace();
            }

            });





    }






}

