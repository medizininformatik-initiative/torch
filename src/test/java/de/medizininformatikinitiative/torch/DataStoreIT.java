package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.Crtdl;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class DataStoreIT extends AbstractIT {

    private static final String RESOURCE_PATH_PREFIX = "src/test/resources/";

    @Autowired
    public DataStoreIT(@Qualifier("fhirClient") WebClient webClient,
                            @Qualifier("flareClient") WebClient flareClient, ResourceTransformer transformer, DataStore dataStore, CdsStructureDefinitionHandler cds, FhirContext context, IParser parser, BundleCreator bundleCreator, ObjectMapper objectMapper) {
        super(webClient, flareClient, transformer, dataStore, cds, context, parser, bundleCreator, objectMapper);
    }

    @Test
    public void testFhirSearchCondition() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/diagnosis_basic_bundle.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic_date.json",
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic_code.json",
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_basic.json"
                ),
                1
        );
    }

    @Test
    public void testFhirSearchObservation() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_basic_bundle_id3.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation.json"
                ),
                1
        );
    }

    @Test
    public void testFhirSearchConditionObservation() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_diagnosis_basic_bundle_id3.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_diagnosis_observation.json"
                ),
                1
        );
    }

    @Test
    public void testAllFields() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_all_fields.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation_all_fields.json"
                ),
                1
        );
    }



    @Test
    public void testFhirSearchBatch() throws IOException, PatientIdNotFoundException {
        executeTest(
                List.of(
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_basic_bundle_id3.json",
                        RESOURCE_PATH_PREFIX + "DataStoreIT/expectedOutput/observation_basic_bundle_id4.json"
                ),
                List.of(
                        RESOURCE_PATH_PREFIX + "CRTDL/CRTDL_observation.json"
                ),
                2
        );
    }

    @Test
    public void testMustHave() throws IOException {

        FileInputStream fis = new FileInputStream(RESOURCE_PATH_PREFIX+"CRTDL/CRTDL_observation_must_have.json");
        Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
        logger.info("ResourceType {}", crtdl.getResourceType());
        List<String> patients = new ArrayList<>();
        patients.add("3");
        Mono<Map<String, Collection<Resource>>> collectedResourcesMono = transformer.collectResourcesByPatientReference(crtdl, patients, 1);
        Map<String, Collection<Resource>> result = collectedResourcesMono.block(); // Blocking to get the result
        assert result != null;
        Assertions.assertTrue(result.isEmpty());

    }


    private void executeTest(List<String> expectedResourceFilePaths, List<String> filePaths, int batchSize) throws IOException, PatientIdNotFoundException {
        Map<String, Bundle> expectedResources = loadExpectedResources(expectedResourceFilePaths);
        expectedResources.values().forEach(Assertions::assertNotNull);
        List<String> patients = new ArrayList<>(expectedResources.keySet());

        for (String filePath : filePaths) {
            processFile(filePath, patients, expectedResources, batchSize);
        }
    }

    private void processFile(String filePath, List<String> patients, Map<String, Bundle> expectedResources, int batchSize) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            Mono<Map<String, Collection<Resource>>> collectedResourcesMono = transformer.collectResourcesByPatientReference(crtdl, patients, batchSize);

            StepVerifier.create(collectedResourcesMono)
                    .expectNextMatches(combinedResourcesByPatientId -> {
                        Map<String, Bundle> bundles = bundleCreator.createBundles(combinedResourcesByPatientId);
                        validateBundles(bundles, expectedResources);
                        return true;
                    })
                    .expectComplete()
                    .verify();
        } catch (IOException e) {
            logger.error("CRTDL file not found: {}", filePath, e);
        }
    }

    private void validateBundles(Map<String, Bundle> bundles, Map<String, Bundle> expectedResources) {
        for (Map.Entry<String, Bundle> entry : bundles.entrySet()) {
            String patientId = entry.getKey();
            Bundle bundle = entry.getValue();
            Bundle expectedBundle = expectedResources.get(patientId);
            // Remove the meta.lastUpdated field from both bundles
            // You can calculate milliseconds using a tool or method or directly input the correct value.


            // Remove meta.lastUpdated from all contained resources in the bundle
            removeMetaLastUpdated(bundle);
            removeMetaLastUpdated(expectedBundle);

            //logger.debug(parser.setPrettyPrint(true).encodeResourceToString(bundle));
            Assertions.assertNotNull(expectedBundle, "No expected bundle found for patientId " + patientId);
            Assertions.assertEquals(parser.setPrettyPrint(true).encodeResourceToString(expectedBundle),parser.setPrettyPrint(true).encodeResourceToString(bundle),

                    bundle + " Expected not equal to actual output");
        }
    }

    private void removeMetaLastUpdated(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource != null && resource.hasMeta() && resource.getMeta().hasLastUpdated()) {
                logger.info("Removed lastUpdated ");
                resource.getMeta().setLastUpdated(null);
            }
        }
    }
}
