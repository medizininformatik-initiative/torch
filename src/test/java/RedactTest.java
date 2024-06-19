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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class RedactTest {




    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;
    public RedactTest() {
        ctx=FhirContext.forR4();
        parser = ctx.newJsonParser();
        CDS= new CDSStructureDefinitionHandler(ctx);
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


            // String [] resources =  {"Diagnosis4.json","Diagnosis3.json","Diagnosis1.json","Diagnosis2.json"};

            String [] resources =  {"Diagnosis1.json","Diagnosis2.json"};

            Arrays.stream(resources).forEach(resource ->{
                try {

                DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Condition/"+resource);
                DomainResource resourceexpected = (DomainResource) readResource("src/test/resources/RedactTest/expectedOutput/"+resource);
                resourcesrc =(DomainResource) redaction.redact(resourcesrc,"",1);
                assertTrue(resourcesrc.equalsDeep(resourceexpected),resource+" Expected not equal to actual output");
            } catch (Exception e) {
                e.printStackTrace();
            }

            });





    }






}

