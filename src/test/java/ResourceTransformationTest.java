import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.ElementCopier;
import de.medizininformatikinitiative.util.Exceptions.mustHaveViolatedException;
import de.medizininformatikinitiative.util.Redaction;
import de.medizininformatikinitiative.util.model.Attribute;
import de.medizininformatikinitiative.util.model.Root;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class ResourceTransformationTest {


    private final ElementCopier copier;
    private final Redaction redaction;
    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;


    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResourceTransformationTest() {

        ctx= FhirContext.forR4();
        parser = ctx.newJsonParser();
        CDS= new CDSStructureDefinitionHandler(ctx);
        redaction = new Redaction(CDS);

        copier = new ElementCopier(CDS);
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
    public void testObservation() {
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            Root root = objectMapper.readValue(fis, Root.class);
            DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = (DomainResource) readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            root.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().forEach(attribute -> {
                try {
                    copier.copy(resourcesrc, tgt, attribute);
                } catch (mustHaveViolatedException e) {
                    throw new RuntimeException(e);
                }
            });
            //TODO define technically required in all Ressources
            copier.copy(resourcesrc, tgt, new Attribute("meta.profile", true));
            copier.copy(resourcesrc, tgt, new Attribute("id", true));
            //TODO Handle Custom ENUM Types like Status, since it has its Error in the valuesystem.
            copier.copy(resourcesrc, tgt, new Attribute("status", true));

            redaction.redact(tgt, "", 1);
            assertNotNull(tgt);
            System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
            //assertEquals(parser.setPrettyPrint(true).encodeResourceToString(tgt), parser.setPrettyPrint(true).encodeResourceToString(resourceexpected), resourcesrc + " Expected not equal to actual output");

        } catch (Exception e) {
            e.printStackTrace();
        }


    }





}

