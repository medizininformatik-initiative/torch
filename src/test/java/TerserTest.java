import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.TerserUtil;
import ca.uhn.fhir.util.TerserUtilHelper;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.CRTDL.Attribute;
import de.medizininformatikinitiative.util.ElementCopier;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static ca.uhn.fhir.util.TerserUtilHelper.newHelper;
import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class TerserTest {


    String[] resources = {"Diagnosis1.json", "Diagnosis2.json"};


    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;

    public TerserTest() {
        ctx = FhirContext.forR4();
        parser = ctx.newJsonParser();
        CDS = new CDSStructureDefinitionHandler(ctx);
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
    public void testCondition() {

            try {
                Condition tgt = new Condition();
                Coding coding = new Coding().setSystem("Test1");
                Coding coding2 = new Coding().setSystem("Test2");
                //TerserUtil.setFieldByFhirPath(ctx, "code.coding", tgt, coding);
                TerserUtilHelper helper =  newHelper(ctx,tgt);
                helper.setField("code","codeableConcept", coding);
                helper.setField("code","codeableConcept", coding2);
                assertNotNull(tgt);
                List<Element> elements = ctx.newFhirPath().evaluate(tgt, "Condition.code.coding", Element.class);
                System.out.println("Elements: "+elements.size());
                System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
            } catch (Exception e) {
                e.printStackTrace();
            }




    }
    @Test
    public void testObservation(){
        try {
            Observation tgt = new Observation();
            Quantity low = new Quantity(12.0);
            //TerserUtil.setFieldByFhirPath(ctx, "code.coding", tgt, coding);
            TerserUtilHelper helper =  newHelper(ctx,tgt);
            //helper.setField("referenceRange.low","Quantity", low);
            TerserUtil.setFieldByFhirPath(ctx, "Observation.referenceRange.low", tgt, low);
            assertNotNull(tgt);
            List<Element> elements = ctx.newFhirPath().evaluate(tgt, "referenceRange.low", Element.class);
            System.out.println("Elements: "+elements.size());
            System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }



}

