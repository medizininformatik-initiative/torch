package de.medizininformatikinitiative.torch.management;

import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ResourceCacheTest {

    static Patient patient1 = new Patient();
    static Patient patient2 = new Patient();
    static Patient patient3 = new Patient();

    @BeforeAll
    static void setUp() {
        patient1.setId("patient1");
        patient2.setId("patient2");
        patient3.setId("patient3");

    }

    @Test
    void getMatch() {
        ResourceCache cache = new ResourceCache();
        cache.put(patient1);  // Corrected method call

        Mono<Patient> result = cache.get(patient1.getId()).cast(Patient.class);

        StepVerifier.create(result)
                .expectNext(patient1)  // Ensure it matches the expected patient
                .verifyComplete();
    }

    @Test
    void getEmpty() {
        ResourceCache cache = new ResourceCache();

        Mono<?> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .verifyComplete();  // Ensures Mono is empty
    }

    @Test
    void put() {
        ResourceCache cache = new ResourceCache();
        cache.put(patient1);

        Mono<Patient> result = cache.get(patient1.getId()).cast(Patient.class);

        StepVerifier.create(result)
                .expectNext(patient1)
                .verifyComplete();
    }

    @Test
    void invalidate() {
        ResourceCache cache = new ResourceCache();
        cache.put(patient1);
        cache.invalidate(patient1.getId());

        Mono<?> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .verifyComplete(); // Should be empty after invalidation
    }

    @Test
    void clear() {
        ResourceCache cache = new ResourceCache();
        cache.put(patient1);
        cache.put(patient2);
        cache.put(patient3);

        cache.clear();

        StepVerifier.create(cache.get("patient1")).verifyComplete();
        StepVerifier.create(cache.get("patient2")).verifyComplete();
        StepVerifier.create(cache.get("patient3")).verifyComplete();
    }
}
