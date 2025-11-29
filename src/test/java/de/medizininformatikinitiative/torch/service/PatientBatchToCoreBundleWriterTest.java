package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PatientBatchToCoreBundleWriterTest {

    private PatientBatchToCoreBundleWriter writer;
    private CompartmentManager compartmentManager;

    private final AnnotatedAttribute annotatedAttribute1 = new AnnotatedAttribute("test", "test", false);

    @BeforeEach
    void setUp() {
        compartmentManager = Mockito.mock(CompartmentManager.class);
        writer = new PatientBatchToCoreBundleWriter(compartmentManager);
    }

    // Helper: build an ExtractionPatientBatch from patient ResourceBundles
    private ExtractionPatientBatch extractionBatchFrom(Map<String, ResourceBundle> patientBundles) {
        Map<String, ExtractionResourceBundle> extractionBundles = new ConcurrentHashMap<>();
        patientBundles.forEach((patientId, bundle) ->
                extractionBundles.put(patientId, ExtractionResourceBundle.of(bundle))
        );
        // For these tests we don't pre-populate the batch-level coreBundle
        return new ExtractionPatientBatch(extractionBundles, new ExtractionResourceBundle());
    }

    @Nested
    class HandlePatientBundlesTests {

        @Test
        void updateCore_shouldMergeOnlyNonCompartmentResources() {
            ResourceBundle patientBundle = new ResourceBundle();

            ResourceGroup patientGroup = new ResourceGroup("Patient/123", "PG1");
            ResourceGroup medicationGroup = new ResourceGroup("Medication/456", "MG1");

            // Mark both groups as valid in the bundle
            patientBundle.addResourceGroupValidity(patientGroup, true);
            patientBundle.addResourceGroupValidity(medicationGroup, true);

            // simple attribute from patient → medication (not really used by writer directly,
            // but ensures that ResourceExtractionInfo groups are correctly populated)
            ResourceAttribute attr = new ResourceAttribute("attr1", annotatedAttribute1);
            patientBundle.addAttributeToParent(attr, patientGroup);
            patientBundle.addAttributeToChild(attr, medicationGroup);
            patientBundle.setResourceAttributeValid(attr);

            // Compartment manager: patient in compartment, medication is not
            when(compartmentManager.isInCompartment("Patient/123")).thenReturn(true);
            when(compartmentManager.isInCompartment("Medication/456")).thenReturn(false);

            ExtractionPatientBatch batch = extractionBatchFrom(
                    Map.of("patient1", patientBundle)
            );
            ExtractionResourceBundle core = new ExtractionResourceBundle();

            writer.updateCore(core, batch);

            // Expect only medication resourceId in core
            assertThat(core.extractionInfoMap().keySet())
                    .containsExactly("Medication/456");

            ResourceExtractionInfo medInfo = core.extractionInfoMap().get("Medication/456");
            assertThat(medInfo.groups()).containsExactly("MG1");
        }

        @Test
        void updateCore_shouldNotAddCompartmentResourcesToCore() {
            ResourceBundle patientBundle = new ResourceBundle();

            ResourceGroup patientGroup = new ResourceGroup("Patient/999", "PGX");
            patientBundle.addResourceGroupValidity(patientGroup, true);

            when(compartmentManager.isInCompartment("Patient/999")).thenReturn(true);

            ExtractionPatientBatch batch = extractionBatchFrom(
                    Map.of("p", patientBundle)
            );
            ExtractionResourceBundle core = new ExtractionResourceBundle();

            writer.updateCore(core, batch);

            assertThat(core.extractionInfoMap()).isEmpty();
            assertThat(core.cache()).isEmpty();
        }

        @Test
        void updateCore_shouldMergeCacheOnlyForNonCompartmentResources() {
            ResourceBundle patientBundle = new ResourceBundle();

            // ResourceIds
            String patientId = "Patient/111";
            String medId = "Medication/222";

            // Groups
            ResourceGroup patientGroup = new ResourceGroup(patientId, "PG1");
            ResourceGroup medGroup = new ResourceGroup(medId, "MG1");

            patientBundle.addResourceGroupValidity(patientGroup, true);
            patientBundle.addResourceGroupValidity(medGroup, true);

            // Add entries to cache
            Patient pRes = new Patient();
            pRes.setId(patientId);
            Medication mRes = new Medication();
            mRes.setId(medId);

            patientBundle.cache().put(patientId, Optional.of(pRes));
            patientBundle.cache().put(medId, Optional.of(mRes));

            // Compartment manager: patient in compartment, medication not
            when(compartmentManager.isInCompartment(patientId)).thenReturn(true);
            when(compartmentManager.isInCompartment(medId)).thenReturn(false);

            ExtractionPatientBatch batch = extractionBatchFrom(
                    Map.of("patient1", patientBundle)
            );
            ExtractionResourceBundle core = new ExtractionResourceBundle();

            writer.updateCore(core, batch);

            // Cache should contain only medication resource
            assertThat(core.cache().keySet())
                    .containsExactly(medId);

            assertThat(core.cache().get(medId)).contains(mRes);
        }

        @Test
        void updateCore_forMultiplePatients_shouldAccumulateNonCompartmentResources() {
            ResourceBundle bundle1 = new ResourceBundle();
            ResourceBundle bundle2 = new ResourceBundle();

            // Patient 1: Patient/101, Medication/201
            ResourceGroup p1 = new ResourceGroup("Patient/101", "PG1");
            ResourceGroup m1 = new ResourceGroup("Medication/201", "MG1");
            bundle1.addResourceGroupValidity(p1, true);
            bundle1.addResourceGroupValidity(m1, true);

            // Patient 2: Patient/102, Medication/202
            ResourceGroup p2 = new ResourceGroup("Patient/102", "PG2");
            ResourceGroup m2 = new ResourceGroup("Medication/202", "MG2");
            bundle2.addResourceGroupValidity(p2, true);
            bundle2.addResourceGroupValidity(m2, true);

            when(compartmentManager.isInCompartment("Patient/101")).thenReturn(true);
            when(compartmentManager.isInCompartment("Patient/102")).thenReturn(true);
            when(compartmentManager.isInCompartment("Medication/201")).thenReturn(false);
            when(compartmentManager.isInCompartment("Medication/202")).thenReturn(false);

            ExtractionPatientBatch batch = extractionBatchFrom(
                    Map.of(
                            "patient1", bundle1,
                            "patient2", bundle2
                    )
            );
            ExtractionResourceBundle core = new ExtractionResourceBundle();

            writer.updateCore(core, batch);

            assertThat(core.extractionInfoMap().keySet())
                    .containsExactlyInAnyOrder("Medication/201", "Medication/202");
        }
    }

    @Nested
    class HandleSourceCoreBundleTests {

        @Test
        void updateCore_shouldMergeSourceCoreBundleIntoGlobalCore() {
            // Existing global core with one resource
            ExtractionResourceBundle globalCore = new ExtractionResourceBundle();
            globalCore.extractionInfoMap().put(
                    "Observation/1",
                    new ResourceExtractionInfo(Set.of("G-OBS-1"), Map.of())
            );

            // Batch-level core with overlapping and new resource
            ExtractionResourceBundle batchCore = new ExtractionResourceBundle();
            batchCore.extractionInfoMap().put(
                    "Observation/1",
                    new ResourceExtractionInfo(Set.of("G-OBS-2"), Map.of())
            );
            batchCore.extractionInfoMap().put(
                    "Medication/777",
                    new ResourceExtractionInfo(Set.of("G-MED-1"), Map.of())
            );

            ExtractionPatientBatch batch = new ExtractionPatientBatch(
                    Map.of(), // no patient bundles in this scenario
                    batchCore
            );

            // No patient bundles, but updateCore should still merge the batch.coreBundle()
            writer.updateCore(globalCore, batch);

            assertThat(globalCore.extractionInfoMap().keySet())
                    .containsExactlyInAnyOrder("Observation/1", "Medication/777");

            ResourceExtractionInfo obsInfo = globalCore.extractionInfoMap().get("Observation/1");
            // union of groups from both
            assertThat(obsInfo.groups())
                    .containsExactlyInAnyOrder("G-OBS-1", "G-OBS-2");

            ResourceExtractionInfo medInfo = globalCore.extractionInfoMap().get("Medication/777");
            assertThat(medInfo.groups()).containsExactly("G-MED-1");
        }

        @Test
        void updateCore_shouldMergeSourceCoreCache() {
            ExtractionResourceBundle globalCore = new ExtractionResourceBundle();

            ExtractionResourceBundle batchCore = new ExtractionResourceBundle();
            Organization org = new Organization();
            org.setId("Organization/501");
            batchCore.cache().put("Organization/501", Optional.of(org));

            ExtractionPatientBatch batch = new ExtractionPatientBatch(
                    Map.of(),
                    batchCore
            );

            writer.updateCore(globalCore, batch);

            assertThat(globalCore.cache().keySet())
                    .containsExactly("Organization/501");

            assertThat(globalCore.cache().get("Organization/501")).contains(org);
        }
    }
}
