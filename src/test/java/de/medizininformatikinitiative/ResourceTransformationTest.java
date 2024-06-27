package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.BaseTest;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.ElementCopier;
import de.medizininformatikinitiative.util.Exceptions.mustHaveViolatedException;
import de.medizininformatikinitiative.util.Redaction;
import de.medizininformatikinitiative.util.model.Attribute;
import de.medizininformatikinitiative.util.model.CRTDL;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileInputStream;
import java.io.IOException;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



public class ResourceTransformationTest extends BaseTest {
    @Mock
    DataStore dataStore;


    @Test
    public void testObservation() {
        Redaction redaction = new Redaction(CDS);
        ElementCopier copier = new ElementCopier(CDS);
        ResourceTransformer transformer= new ResourceTransformer( dataStore, copier,redaction, CDS,ctx);
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            CRTDL CRTDL = objectMapper.readValue(fis, CRTDL.class);
            DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = (DomainResource) readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");
            DomainResource tgt = (DomainResource) transformer.transform(resourcesrc, CRTDL);
            assertNotNull(tgt);
            //System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
            assertEquals( parser.setPrettyPrint(true).encodeResourceToString(resourceexpected),parser.setPrettyPrint(true).encodeResourceToString(tgt), " Expected not equal to actual output");

        } catch (Exception e) {
            e.printStackTrace();
        }


    }





}

