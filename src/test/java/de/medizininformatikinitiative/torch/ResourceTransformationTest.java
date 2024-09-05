package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.DomainResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class ResourceTransformationTest extends BaseTest {
    @Autowired
    private WebClient webClient;

    @Autowired
    private ResultFileManager resultFileManager;

    private DataStore dataStore;

    @BeforeAll void setup(){
        dataStore = new DataStore(webClient, ctx);
    }

    @Test
    public void testObservation() {

        ResourceTransformer transformer = new ResourceTransformer(dataStore, cds,resultFileManager);
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            DomainResource resourcesrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = (DomainResource) ResourceReader.readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");
            DomainResource tgt = (DomainResource) transformer.transform(resourcesrc,crtdl.getDataExtraction().getAttributeGroups().getFirst());
            assertNotNull(tgt);
            assertEquals(parser.setPrettyPrint(true).encodeResourceToString(resourceexpected), parser.setPrettyPrint(true).encodeResourceToString(tgt), " Expected not equal to actual output");

        } catch (Exception e) {
            logger.error("",e);
        }

    }

}

