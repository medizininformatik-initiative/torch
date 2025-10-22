package de.medizininformatikinitiative.torch.service;


import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.config.FhirProperties;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Condition;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReferenceBundleLoaderIT {

    @Autowired
    ReferenceBundleLoader referenceBundleLoader;

    @Autowired
    @Qualifier("fhirClient")
    WebClient webClient;
    @Autowired
    @Value("${torch.fhir.testPopulation.path}")
    String testPopulationPath;
    @Autowired
    private FhirProperties fhirProperties;

    @BeforeAll
    void init() throws IOException {
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }


    @Test
    void fetchesUnknownReferences() {

        AnnotatedAttribute attribute1 = new AnnotatedAttribute("Condition.onset[x]", "Condition.onset", "Condition.onset[x]", false);
        ReferenceWrapper reference1 = new ReferenceWrapper(attribute1, List.of("Condition/UnknownResource"), "group1", "test");
        ReferenceWrapper reference2 = new ReferenceWrapper(attribute1, List.of("Medication/UnknownResource"), "group1", "test");
        ReferenceWrapper reference3 = new ReferenceWrapper(attribute1, List.of("Patient/1"), "group1", "test");
        PatientResourceBundle patientBundle = new PatientResourceBundle("1");

        ResourceBundle coreBundle = new ResourceBundle();
        Mono<Void> result = referenceBundleLoader.fetchUnknownResources(Map.of(new ResourceGroup("Patient/1", "test"), List.of(reference1, reference2, reference3)), patientBundle, coreBundle, false);

        StepVerifier.create(result).verifyComplete();
        ConcurrentHashMap<String, Optional<Resource>> cache = patientBundle.bundle().cache();


        long countEmpty = cache.values().stream()
                .filter(opt -> opt.equals(Optional.empty()))
                .count();
        assertThat(cache.keySet()).containsExactlyInAnyOrder("Patient/1", "Condition/UnknownResource");
        assertThat(countEmpty).isEqualTo(1);
        assertThat(coreBundle.cache().keySet()).containsExactlyInAnyOrder("Medication/UnknownResource");


    }

    @Test
    void fetchesUnknownReferencesWithMultiplePages() {

        List<String> refs = new ArrayList<>();
        for (int i = 1; i <= fhirProperties.page().count(); i++) {
            refs.add("Observation/" + UUID.randomUUID());
        }

        AnnotatedAttribute attribute1 = new AnnotatedAttribute("Condition.onset[x]", "Condition.onset", "Condition.onset[x]", false);
        ReferenceWrapper reference1 = new ReferenceWrapper(attribute1, refs, "group1", "test");
        ReferenceWrapper reference2 = new ReferenceWrapper(attribute1, List.of("Patient/1"), "group1", "test");
        PatientResourceBundle patientBundle = new PatientResourceBundle("1");

        ResourceBundle coreBundle = new ResourceBundle();
        Mono<Void> result = referenceBundleLoader.fetchUnknownResources(Map.of(new ResourceGroup("Patient/1", "test"), List.of(reference1, reference2)), patientBundle, coreBundle, false);

        StepVerifier.create(result).verifyComplete();
        ConcurrentHashMap<String, Optional<Resource>> cache = patientBundle.bundle().cache();

        long countEmpty = cache.values().stream()
                .filter(opt -> opt.equals(Optional.empty()))
                .count();
        assertThat(countEmpty).isEqualTo(fhirProperties.page().count());


    }


    @Test
    void doesNotDoubleFetchUnknownResources() {

        AnnotatedAttribute attribute1 = new AnnotatedAttribute("Condition.onset[x]", "Condition.onset", "Condition.onset[x]", false);
        ReferenceWrapper reference1 = new ReferenceWrapper(attribute1, List.of("Condition/UnknownResource"), "group1", "test");
        ReferenceWrapper reference2 = new ReferenceWrapper(attribute1, List.of("Medication/UnknownResource"), "group1", "test");
        ReferenceWrapper reference3 = new ReferenceWrapper(attribute1, List.of("Patient/1"), "group1", "test");
        PatientResourceBundle patientBundle = new PatientResourceBundle("1");

        ResourceBundle coreBundle = new ResourceBundle();
        Mono<Void> result = referenceBundleLoader.fetchUnknownResources(Map.of(new ResourceGroup("Patient/1", "test"), List.of(reference1, reference2, reference3)), patientBundle, coreBundle, false);

        StepVerifier.create(result).verifyComplete();
        ConcurrentHashMap<String, Optional<Resource>> cache = patientBundle.bundle().cache();
        cache.put("Condition/UnknownResource", Optional.of(new Condition()));
        Mono<Void> rerun = referenceBundleLoader.fetchUnknownResources(Map.of(new ResourceGroup("Patient/1", "test"), List.of(reference1, reference2, reference3)), patientBundle, coreBundle, false);
        StepVerifier.create(rerun).verifyComplete();

        long countEmpty = cache.values().stream()
                .filter(opt -> opt.equals(Optional.empty()))
                .count();
        assertThat(cache.keySet()).containsExactlyInAnyOrder("Patient/1", "Condition/UnknownResource");
        assertThat(countEmpty).isZero();
        assertThat(coreBundle.cache().keySet()).containsExactlyInAnyOrder("Medication/UnknownResource");


    }


}
