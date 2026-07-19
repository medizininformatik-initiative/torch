package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DataStoreIT {

    @Autowired
    DataStore dataStore;


    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;


    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }


    @Test
    void successSearch() {
        var result = dataStore.search(Query.ofType("Observation"), Observation.class);
        StepVerifier.create(result)
                .recordWith(ArrayList::new)
                .expectNextCount(5)
                .consumeRecordedWith(resources -> resources.forEach(resource ->
                        assertThat(resource.getResourceType()).hasToString("Observation")
                ))
                .verifyComplete();
    }


    @Test
    void successReferenceBundleFetch() {
        var bundle = createBundleFromQuery("Patient?_id=1,2");

        Mono<List<Resource>> result = dataStore.executeBundle(bundle);
        StepVerifier.create(result)
                .expectNextMatches(resources -> resources.size() == 2 && resources.stream().allMatch(Patient.class::isInstance))
                .verifyComplete();
    }

    @Test
    void emptyReferenceFetch() {
        var bundle = createBundleFromQuery("Observation?_id=Unknown");

        Mono<List<Resource>> result = dataStore.executeBundle(bundle);
        StepVerifier.create(result)
                .verifyComplete();
    }

    public static Bundle createBundleFromQuery(String query) {
        Bundle batchBundle = new Bundle();
        batchBundle.setType(Bundle.BundleType.BATCH);
        batchBundle.getMeta().setLastUpdated(new Date());
        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.setRequest(new Bundle.BundleEntryRequestComponent()
                .setMethod(Bundle.HTTPVerb.GET)
                .setUrl(query));
        batchBundle.addEntry(entry);

        return batchBundle;
    }
}
