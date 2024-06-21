import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.model.Attribute;
import de.medizininformatikinitiative.util.ElementCopier;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class CopyTest {





    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;

    public CopyTest() {
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
    public void testDiagnosis() {
        ElementCopier copier = new ElementCopier(CDS);
        String[] resources = {"Diagnosis1.json"};
        Arrays.stream(resources).forEach(resource -> {
            try {
                DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Condition/" + resource);
                DomainResource resourceexpected = (DomainResource) readResource("src/test/resources/CopyTest/expectedOutput/"+resource);
                Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
                DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
                copier.copy(resourcesrc, tgt, new Attribute("Condition.onset.as(DateTime)", false));
                copier.copy(resourcesrc, tgt, new Attribute("Condition.meta", true));
                copier.copy(resourcesrc, tgt, new Attribute("Condition.id", true));
                copier.copy(resourcesrc, tgt, new Attribute("Condition.code", false));

                assertNotNull(tgt);
                //System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
                //System.out.println(parser.setPrettyPrint(true).encodeResourceToString(resourceexpected));

                assertEquals(parser.setPrettyPrint(true).encodeResourceToString(tgt), parser.setPrettyPrint(true).encodeResourceToString(resourceexpected), resource + " Expected not equal to actual output");
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }


    @Test
    public void testObservation() {
        ElementCopier copier = new ElementCopier(CDS);
        try {
            DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
            List<Base> elements = ctx.newFhirPath().evaluate(resourcesrc, "Observation.value.as(Quantity)", Base.class);
            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.low", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.high", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.interpretation", false));
            assertNotNull(tgt);
           // System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}

