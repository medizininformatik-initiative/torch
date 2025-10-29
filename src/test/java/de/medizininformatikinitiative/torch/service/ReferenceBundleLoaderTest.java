package de.medizininformatikinitiative.torch.service;


import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceBundleLoaderTest {
    public static final String OBSERVATION_REF = "Observation/12";
    int pageCount = 4;
    @Mock
    private CompartmentManager compartmentManager;
    @Mock
    private DataStore dataStore;
    @Mock
    private ConsentValidator consentValidator;
    private ReferenceBundleLoader referenceBundleLoader;

    @BeforeEach
    void setup() {
        referenceBundleLoader = new ReferenceBundleLoader(compartmentManager, dataStore, consentValidator, pageCount);
    }

    @Nested
    class GetUnloadedRefs {
        Map<ResourceGroup, List<ReferenceWrapper>> groupReferenceMap;

        @BeforeEach
        void setUp() {
            groupReferenceMap = new HashMap<>();
            AnnotatedAttribute referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", true, List.of("Medication1"));
            ReferenceWrapper referenceWrapper = new ReferenceWrapper(referenceAttribute, List.of(OBSERVATION_REF), "Encounter1", "Encounter/123");
            groupReferenceMap.put(new ResourceGroup("Encounter/123", "Encounter1"), List.of(referenceWrapper));
        }

        @Test
        void findsObservation() {
            when(compartmentManager.isInCompartment(OBSERVATION_REF)).thenReturn(true);

            Set<String> result = referenceBundleLoader.findUnloadedReferences(groupReferenceMap, new PatientResourceBundle("Test"), new ResourceBundle());

            assertThat(result).containsExactlyInAnyOrder(OBSERVATION_REF);
        }

        @Test
        void doesNotFindLoadedObservation() {
            PatientResourceBundle patientResourceBundle = new PatientResourceBundle("Test");
            patientResourceBundle.put(OBSERVATION_REF);
            when(compartmentManager.isInCompartment(OBSERVATION_REF)).thenReturn(true);

            Set<String> result = referenceBundleLoader.findUnloadedReferences(groupReferenceMap, patientResourceBundle, new ResourceBundle());

            assertThat(result).isEmpty();
        }

        @Test
        void doesSkipPatientResourceReferencesInCoreBundle() {
            ResourceBundle coreBundle = new ResourceBundle();
            when(compartmentManager.isInCompartment(OBSERVATION_REF)).thenReturn(true);

            Set<String> result = referenceBundleLoader.findUnloadedReferences(groupReferenceMap, null, coreBundle);

            assertThat(result).isEmpty();
            assertThat(coreBundle.cache())
                    .containsExactly(entry(OBSERVATION_REF, Optional.empty()));

        }


    }


    @Nested
    class GroupReferencesByTypeInChunks {

        @Test
        void withoutChunking() {
            var chunks = referenceBundleLoader.groupReferencesByTypeInChunks(Set.of(
                    "Observation/123", "Patient/2", "Patient/1", "Medication/123"));

            assertThat(chunks).containsExactly(Map.of(
                    "Observation", Set.of("123"),
                    "Patient", Set.of("2", "1"),
                    "Medication", Set.of("123")));
        }

        @Test
        void withChunking() {
            var chunks = referenceBundleLoader.groupReferencesByTypeInChunks(Set.of(
                    "Observation/123", "Patient/2", "Patient/1", "Medication/123", "MedicationAdministration/4"));

            assertThat(chunks).containsExactly(Map.of(
                            "Observation", Set.of("123"),
                            "MedicationAdministration", Set.of("4"),
                            "Medication", Set.of("123"),
                            "Patient", Set.of("1")),
                    Map.of(
                            "Patient", Set.of("2")
                    ));

        }


    }


}
