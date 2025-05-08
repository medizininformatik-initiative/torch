package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PatientBatchToCoreBundleWriterTest {

    private PatientBatchToCoreBundleWriter writer;
    private CompartmentManager compartmentManager;
    private ResourceBundle patientBundle;
    private ResourceBundle coreBundle;

    private AnnotatedAttribute annotatedAttribute1 = new AnnotatedAttribute("test", "test", "test", false);
    private AnnotatedAttribute annotatedAttribute2 = new AnnotatedAttribute("med", "med", "med", false);

    private ResourceGroup patientGroup = new ResourceGroup("Patient/123", "Group1");
    private ResourceAttribute attribute = new ResourceAttribute("attribute1", annotatedAttribute1);
    private ResourceAttribute attribute2 = new ResourceAttribute("attribute2", annotatedAttribute1);
    private ResourceGroup medicationGroup = new ResourceGroup("Medication/123", "Group1");
    private ResourceGroup medicationGroup2 = new ResourceGroup("Medication/321", "Group1");

    @BeforeEach
    void setUp() {
        compartmentManager = Mockito.mock(CompartmentManager.class);
        writer = new PatientBatchToCoreBundleWriter(compartmentManager);
        patientBundle = new ResourceBundle();
        coreBundle = new ResourceBundle();
    }

    @Nested
    class Extraction {

        @Test
        void OnlyPatientAttribute() {
            ResourceGroup group = new ResourceGroup("Patient/123", "Group1");

            patientBundle.addResourceGroupValidity(group, true);
            patientBundle.addAttributeToParent(attribute, patientGroup);
            patientBundle.addAttributeToChild(attribute, medicationGroup);

            patientBundle.addResourceGroupValidity(patientGroup, true);
            patientBundle.addResourceGroupValidity(medicationGroup, true);
            patientBundle.setResourceAttributeValid(attribute);

            when(compartmentManager.isInCompartment(patientGroup)).thenReturn(true);
            when(compartmentManager.isInCompartment(medicationGroup)).thenReturn(false);

            ImmutableResourceBundle extractedData = writer.extractRelevantPatientData(new ImmutableResourceBundle(patientBundle));

            assertThat(extractedData.resourceGroupValidity()).containsExactly(Map.entry(medicationGroup, true));
            assertThat(extractedData.resourceAttributeValidity()).isEmpty();
            assertThat(extractedData.childResourceGroupToResourceAttributesMap()).isEmpty();
            assertThat(extractedData.parentResourceGroupToResourceAttributesMap()).isEmpty();
            assertThat(extractedData.resourceAttributeToChildResourceGroup()).isEmpty();
            assertThat(extractedData.resourceAttributeToParentResourceGroup()).isEmpty();
        }

        @Test
        void PatientResourceReferencesMedicationThatReferencesAnotherMedication() {
            patientBundle.addAttributeToParent(attribute, patientGroup);
            patientBundle.addAttributeToChild(attribute, medicationGroup);
            patientBundle.addAttributeToParent(attribute2, medicationGroup);
            patientBundle.addAttributeToChild(attribute2, medicationGroup2);

            patientBundle.addResourceGroupValidity(patientGroup, true);
            patientBundle.addResourceGroupValidity(medicationGroup, true);
            patientBundle.addResourceGroupValidity(medicationGroup2, true);
            patientBundle.setResourceAttributeValid(attribute);
            patientBundle.setResourceAttributeValid(attribute2);

            when(compartmentManager.isInCompartment(patientGroup)).thenReturn(true);
            when(compartmentManager.isInCompartment(medicationGroup)).thenReturn(false);

            ImmutableResourceBundle extractedData = writer.extractRelevantPatientData(new ImmutableResourceBundle(patientBundle));

            assertThat(extractedData.resourceGroupValidity()).containsExactly(Map.entry(medicationGroup, true), Map.entry(medicationGroup2, true));
            assertThat(extractedData.resourceAttributeValidity()).containsExactly(Map.entry(attribute2, true));
            assertThat(extractedData.childResourceGroupToResourceAttributesMap()).containsExactly(Map.entry(medicationGroup2, Set.of(attribute2)));
            assertThat(extractedData.parentResourceGroupToResourceAttributesMap()).containsExactly(Map.entry(medicationGroup, Set.of(attribute2)));
            assertThat(extractedData.resourceAttributeToChildResourceGroup()).containsExactly(Map.entry(attribute2, Set.of(medicationGroup2)));
        }

        @Test
        void PatientResourceReferencesMedicationGroupThatReferencesAnotherMedicationGroup() {
            ResourceBundle patientBundle = new ResourceBundle();

            ResourceGroup patientGroup1 = new ResourceGroup("Patient/101", "GroupA");
            ResourceGroup patientGroup2 = new ResourceGroup("Patient/102", "GroupA");

            ResourceAttribute attribute1 = new ResourceAttribute("attr1", annotatedAttribute1); // Will be removed
            ResourceAttribute attribute2 = new ResourceAttribute("attr2", annotatedAttribute1); // Will survive
            ResourceAttribute attribute3 = new ResourceAttribute("attr3", annotatedAttribute2);
            ResourceAttribute attribute4 = new ResourceAttribute("attr4", annotatedAttribute2);// Will survive

            ResourceGroup medicationGroup1 = new ResourceGroup("Medication/201", "GroupA");
            ResourceGroup medicationGroup2 = new ResourceGroup("Medication/202", "GroupA");
            ResourceGroup organizationGroup = new ResourceGroup("Organization/501", "GroupX");

            // Patient 1 references Medication 201 (should be removed)
            patientBundle.addAttributeToParent(attribute1, patientGroup1);
            patientBundle.addAttributeToChild(attribute1, medicationGroup1);
            patientBundle.addResourceGroupValidity(patientGroup1, true);
            patientBundle.addResourceGroupValidity(medicationGroup1, true);
            patientBundle.setResourceAttributeValid(attribute1);

            // Patient 2 references Medication 202 (should be removed)
            patientBundle.addAttributeToParent(attribute2, patientGroup2);
            patientBundle.addAttributeToChild(attribute2, medicationGroup2);
            patientBundle.addResourceGroupValidity(patientGroup2, true);
            patientBundle.addResourceGroupValidity(medicationGroup2, true);
            patientBundle.setResourceAttributeValid(attribute2);

            // Medications reference an external Organization
            // Patient 2 references Medication 202 (should be removed)
            patientBundle.addAttributeToParent(attribute3, medicationGroup1); // Medication 201 → Organization 501
            patientBundle.addAttributeToChild(attribute3, organizationGroup);
            patientBundle.addAttributeToParent(attribute4, medicationGroup2); // Medication 202 → Organization 501
            patientBundle.addAttributeToChild(attribute4, organizationGroup);
            patientBundle.addResourceGroupValidity(organizationGroup, true);
            patientBundle.setResourceAttributeValid(attribute3);
            patientBundle.setResourceAttributeValid(attribute4);

            // Mocking compartment manager
            when(compartmentManager.isInCompartment(patientGroup1)).thenReturn(true);
            when(compartmentManager.isInCompartment(patientGroup2)).thenReturn(true);
            when(compartmentManager.isInCompartment(medicationGroup1)).thenReturn(false);
            when(compartmentManager.isInCompartment(medicationGroup2)).thenReturn(false);
            when(compartmentManager.isInCompartment(organizationGroup)).thenReturn(false);


            ImmutableResourceBundle processedBundle = writer.extractRelevantPatientData(new ImmutableResourceBundle(patientBundle));

            // Patients should be removed, but Medications and Organization should remain
            assertThat(processedBundle.resourceGroupValidity()).containsExactlyInAnyOrderEntriesOf(Map.of(medicationGroup1, true, medicationGroup2, true, organizationGroup, true));


            assertThat(processedBundle.resourceAttributeToChildResourceGroup()).containsExactlyInAnyOrderEntriesOf(Map.of(attribute3, Set.of(organizationGroup), attribute4, Set.of(organizationGroup)));

            assertThat(processedBundle.resourceAttributeToParentResourceGroup()).containsExactlyInAnyOrderEntriesOf(Map.of(attribute3, Set.of(medicationGroup1), attribute4, Set.of(medicationGroup2)));

            // Ensure attribute1 does NOT exist in the final bundle
            assertThat(processedBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attribute1);

            // Attribute1 should be removed because it only referenced a removed patient.
            // Attribute2 and Attribute3 should survive because they link to an organization.
            assertThat(processedBundle.resourceAttributeValidity()).containsExactlyInAnyOrderEntriesOf(Map.of(attribute3, true, attribute4, true));


        }


    }


    @Nested
    class Batch {
        @Test
        void processPatientBatch_WithMedicationsPointingToOrganization() {
            ResourceBundle patientBundle1 = new ResourceBundle();
            ResourceBundle patientBundle2 = new ResourceBundle();

            ResourceGroup patientGroup1 = new ResourceGroup("Patient/101", "GroupA");
            ResourceGroup patientGroup2 = new ResourceGroup("Patient/102", "GroupA");

            ResourceAttribute attribute1 = new ResourceAttribute("attr1", annotatedAttribute1); // Will be removed
            ResourceAttribute attribute2 = new ResourceAttribute("attr2", annotatedAttribute1); // Will survive
            ResourceAttribute attribute3 = new ResourceAttribute("attr3", annotatedAttribute2); // Will survive
            ResourceAttribute attribute4 = new ResourceAttribute("attr4", annotatedAttribute2); // Will survive

            ResourceGroup medicationGroup1 = new ResourceGroup("Medication/201", "GroupA");
            ResourceGroup medicationGroup2 = new ResourceGroup("Medication/202", "GroupA");
            ResourceGroup organizationGroup = new ResourceGroup("Organization/501", "GroupX");

            // Patient 1 references Medication 201 (should be removed)
            patientBundle1.addAttributeToParent(attribute1, patientGroup1);
            patientBundle1.addAttributeToChild(attribute1, medicationGroup1);
            patientBundle1.addResourceGroupValidity(patientGroup1, true);
            patientBundle1.addResourceGroupValidity(medicationGroup1, true);
            patientBundle1.setResourceAttributeValid(attribute1);

            // Patient 2 references Medication 202 (should be removed)
            patientBundle2.addAttributeToParent(attribute2, patientGroup2);
            patientBundle2.addAttributeToChild(attribute2, medicationGroup2);
            patientBundle2.addResourceGroupValidity(patientGroup2, true);
            patientBundle2.addResourceGroupValidity(medicationGroup2, true);
            patientBundle2.setResourceAttributeValid(attribute2);

            // Medications reference an external Organization
            patientBundle1.addAttributeToParent(attribute3, medicationGroup1); // Medication 201 → Organization 501
            patientBundle1.addAttributeToChild(attribute3, organizationGroup);
            patientBundle2.addAttributeToParent(attribute4, medicationGroup2); // Medication 202 → Organization 501
            patientBundle2.addAttributeToChild(attribute4, organizationGroup);

            patientBundle1.addResourceGroupValidity(organizationGroup, true);
            patientBundle2.addResourceGroupValidity(organizationGroup, true);

            patientBundle1.setResourceAttributeValid(attribute3);
            patientBundle2.setResourceAttributeValid(attribute4);

            // Mocking compartment manager
            when(compartmentManager.isInCompartment(patientGroup1)).thenReturn(true);
            when(compartmentManager.isInCompartment(patientGroup2)).thenReturn(true);
            when(compartmentManager.isInCompartment(medicationGroup1)).thenReturn(false);
            when(compartmentManager.isInCompartment(medicationGroup2)).thenReturn(false);
            when(compartmentManager.isInCompartment(organizationGroup)).thenReturn(false);

            // Create patient batch with both bundles
            PatientBatchWithConsent batch = PatientBatchWithConsent.fromList(List.of(
                    new PatientResourceBundle("patient1", patientBundle1),
                    new PatientResourceBundle("patient2", patientBundle2)
            ));

            ImmutableResourceBundle mergedBundle = writer.processPatientBatch(batch);

            // Patients should be removed, but Medications and Organization should remain
            assertThat(mergedBundle.resourceGroupValidity()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    medicationGroup1, true,
                    medicationGroup2, true,
                    organizationGroup, true
            ));

            // Attribute1 should be removed because it only referenced a removed patient.
            // Attribute2, Attribute3, and Attribute4 should survive because they link to an organization.
            assertThat(mergedBundle.resourceAttributeValidity()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    attribute3, true,
                    attribute4, true
            ));

            // Check surviving relationships
            assertThat(mergedBundle.resourceAttributeToChildResourceGroup()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    attribute3, Set.of(organizationGroup),
                    attribute4, Set.of(organizationGroup)
            ));

            assertThat(mergedBundle.resourceAttributeToParentResourceGroup()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    attribute3, Set.of(medicationGroup1),
                    attribute4, Set.of(medicationGroup2)
            ));

            // Ensure attribute1 does NOT exist in the final bundle
            assertThat(mergedBundle.resourceAttributeToChildResourceGroup()).doesNotContainKey(attribute1);
        }


        @Test
        void processPatientBatch_MergesMultiplePatientsCorrectly() {
            ResourceBundle patientBundle1 = new ResourceBundle();
            ResourceBundle patientBundle2 = new ResourceBundle();
            ResourceBundle patientBundle3 = new ResourceBundle();

            ResourceGroup patientGroup1 = new ResourceGroup("Patient/101", "GroupA");
            ResourceGroup patientGroup2 = new ResourceGroup("Patient/102", "GroupA");
            ResourceGroup patientGroup3 = new ResourceGroup("Patient/103", "GroupB");

            ResourceAttribute attribute1 = new ResourceAttribute("attr1", annotatedAttribute1);
            ResourceAttribute attribute2 = new ResourceAttribute("attr2", annotatedAttribute1);
            ResourceAttribute attribute3 = new ResourceAttribute("attr3", annotatedAttribute2);

            ResourceGroup medicationGroup1 = new ResourceGroup("Medication/201", "GroupA");
            ResourceGroup medicationGroup2 = new ResourceGroup("Medication/202", "GroupA");
            ResourceGroup medicationGroup3 = new ResourceGroup("Medication/203", "GroupB");

            patientBundle1.addAttributeToParent(attribute1, patientGroup1);
            patientBundle1.addAttributeToChild(attribute1, medicationGroup1);
            patientBundle1.addResourceGroupValidity(patientGroup1, true);
            patientBundle1.addResourceGroupValidity(medicationGroup1, true);
            patientBundle1.setResourceAttributeValid(attribute1);

            patientBundle2.addAttributeToParent(attribute2, patientGroup2);
            patientBundle2.addAttributeToChild(attribute2, medicationGroup2);
            patientBundle2.addResourceGroupValidity(patientGroup2, true);
            patientBundle2.addResourceGroupValidity(medicationGroup2, true);
            patientBundle2.setResourceAttributeValid(attribute2);

            patientBundle3.addAttributeToParent(attribute3, patientGroup3);
            patientBundle3.addAttributeToChild(attribute3, medicationGroup3);
            patientBundle3.addResourceGroupValidity(patientGroup3, true);
            patientBundle3.addResourceGroupValidity(medicationGroup3, true);
            patientBundle3.setResourceAttributeValid(attribute3);

            when(compartmentManager.isInCompartment(patientGroup1)).thenReturn(true);
            when(compartmentManager.isInCompartment(patientGroup2)).thenReturn(true);
            when(compartmentManager.isInCompartment(patientGroup3)).thenReturn(true);
            when(compartmentManager.isInCompartment(medicationGroup1)).thenReturn(false);
            when(compartmentManager.isInCompartment(medicationGroup2)).thenReturn(false);
            when(compartmentManager.isInCompartment(medicationGroup3)).thenReturn(false);

            PatientBatchWithConsent batch = PatientBatchWithConsent.fromList(List.of(new PatientResourceBundle("patient1", patientBundle1), new PatientResourceBundle("patient2", patientBundle2), new PatientResourceBundle("patient3", patientBundle3)));
            ImmutableResourceBundle mergedBundle = writer.processPatientBatch(batch);

            assertThat(mergedBundle.resourceAttributeValidity()).isEmpty();

            assertThat(mergedBundle.resourceGroupValidity()).containsExactlyInAnyOrderEntriesOf(Map.of(medicationGroup1, true, medicationGroup2, true, medicationGroup3, true));

        }


    }


}


