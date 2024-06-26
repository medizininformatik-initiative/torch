package de.medizininformatikinitiative;

import de.medizininformatikinitiative.model.Crtdl;
import de.medizininformatikinitiative.util.ElementCopier;
import de.medizininformatikinitiative.util.FhirSearchBuilder;
import de.medizininformatikinitiative.util.Redaction;
import org.hl7.fhir.r4.model.DomainResource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.FileInputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.ArrayList;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class ResourceTransformationTest extends BaseTest {

    @Autowired
    private WebClient webClient;

    private DataStore dataStore;

    @BeforeAll void setup(){
        dataStore = new DataStore(webClient, ctx);
    }

    @Test
    public void testObservation() {

        Redaction redaction = new Redaction(cds);
        ElementCopier copier = new ElementCopier(cds);
        ResourceTransformer transformer = new ResourceTransformer(dataStore, cds);
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = (DomainResource) readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");
            DomainResource tgt = (DomainResource) transformer.transform(resourcesrc,crtdl.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0));
            assertNotNull(tgt);
            //System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
            assertEquals(parser.setPrettyPrint(true).encodeResourceToString(resourceexpected), parser.setPrettyPrint(true).encodeResourceToString(tgt), " Expected not equal to actual output");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testDataStore() {
        DataStore dataStore = new DataStore(webClient, ctx);
        Redaction redaction = new Redaction(cds);
        ElementCopier copier = new ElementCopier(cds);
        ResourceTransformer transformer = new ResourceTransformer(dataStore, cds);
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            FhirSearchBuilder builder = new FhirSearchBuilder();
            // Build search batches with MultiValueMap<String, String>
            crtdl.getCohortDefinition().getDataExtraction().getAttributeGroups().forEach(
                    group->{
                       List<String> searchStrings = builder.getSearchBatches(
                                group,
                                Stream.of("1", "2", "3", "4", "5", "7", "8", "9", "10").collect(Collectors.toCollection(ArrayList::new)),
                                2
                        );
                        assertNotNull(searchStrings);
                    }
            );

            // Subscribe to the Flux to see the results (for demonstration purposes)
            /*allResources.subscribe(resource -> {
                // Process each resource
                System.out.println(resource);
            });*/

        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}

