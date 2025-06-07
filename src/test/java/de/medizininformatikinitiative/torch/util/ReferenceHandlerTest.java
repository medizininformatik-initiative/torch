package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Medication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceHandlerTest {

    @Mock
    private ResourceGroupValidator resourceGroupValidator;

    @InjectMocks
    private ReferenceHandler referenceHandler;

    @BeforeEach
    void setUp() {
        referenceHandler = new ReferenceHandler(resourceGroupValidator);
    }


    @Nested
    class CoreResource {
        @Test
        void handleReferenceGroupNotLoadableResource() {
            ResourceBundle coreBundle = new ResourceBundle();
            AnnotatedAttribute attribute1 = new AnnotatedAttribute("MedicationAdministration.medication", "MedicationAdministration.medication", "Condition.onset[x]", false);
            var references = List.of(new ReferenceWrapper(attribute1, List.of("Medication/UnknownResource"), "group1", "test"));
            coreBundle.put("Medication/UnknownResource");

            var result = referenceHandler.handleReferences(references, null, coreBundle, new HashMap<>());

            assertThat(result).isEmpty();
        }


        @Test
        void handleReferenceGroupLoadableResource() {
            ResourceGroup resourceGroupMedication123 = new ResourceGroup("Medication/123", "group1");
            ResourceBundle coreBundle = new ResourceBundle();
            AnnotatedAttribute attribute1 = new AnnotatedAttribute("MedicationAdministration.medication", "MedicationAdministration.medication", "MedicationAdministration.medication", false, List.of("group1"));
            var reference = new ReferenceWrapper(attribute1, List.of("Medication/123"), "MedicationAdministrationGroup", "test");
            Medication medication = new Medication();
            medication.setId("123");
            coreBundle.put(medication);
            when(resourceGroupValidator.collectValidGroups(
                    eq(reference), any(), eq(medication), eq(coreBundle))).thenReturn(List.of(resourceGroupMedication123));


            var result = referenceHandler.handleReferences(List.of(reference), null, coreBundle, new HashMap<>());

            assertThat(result).containsExactly(resourceGroupMedication123);

        }

        @Test
        void handleReferenceNewGroupInvalid() {
            ResourceBundle coreBundle = new ResourceBundle();
            AnnotatedAttribute attribute1 = new AnnotatedAttribute("MedicationAdministration.medication", "MedicationAdministration.medication", "MedicationAdministration.medication", true, List.of("group1"));
            var reference = new ReferenceWrapper(attribute1, List.of("Medication/123"), "MedicationAdministrationGroup", "MedicationAdministration/123");
            Medication medication = new Medication();
            medication.setId("123");
            coreBundle.put(medication);
            when(resourceGroupValidator.collectValidGroups(
                    eq(reference), any(), eq(medication), eq(coreBundle))).thenReturn(List.of());


            var result = referenceHandler.handleReferences(List.of(reference), null, coreBundle, new HashMap<>());

            assertThat(result).isEmpty();
            System.out.println(coreBundle.resourceGroupValidity());
            assertThat(coreBundle.isValidResourceGroup(new ResourceGroup("MedicationAdministration/123", "MedicationAdministrationGroup"))).isFalse();

        }

        @Test
        void handleReferenceGroupKnownInvalid() {

            ResourceGroup resourceGroupMedication123 = new ResourceGroup("Medication/123", "group1");
            ResourceBundle coreBundle = new ResourceBundle();
            AnnotatedAttribute attribute1 = new AnnotatedAttribute("MedicationAdministration.medication", "MedicationAdministration.medication", "MedicationAdministration.medication", true, List.of("group1"));
            var reference = new ReferenceWrapper(attribute1, List.of("Medication/123"), "MedicationAdministrationGroup", "test");
            Medication medication = new Medication();
            medication.setId("123");
            coreBundle.put(medication);
            coreBundle.addResourceGroupValidity(resourceGroupMedication123, false);
            when(resourceGroupValidator.collectValidGroups(
                    eq(reference), any(), eq(medication), eq(coreBundle))).thenReturn(List.of());


            assertThatThrownBy(() -> referenceHandler.handleReference(reference, null, coreBundle, new HashMap<>())).isInstanceOf(MustHaveViolatedException.class);

        }


    }

    @Nested
    class PatientResource {

        @Test
        void handleResourceAttributeKnownInvalid() {
            ResourceBundle coreBundle = new ResourceBundle();
            AnnotatedAttribute attribute1 = new AnnotatedAttribute("MedicationAdministration.medication", "MedicationAdministration.medication", "MedicationAdministration.medication", true, List.of("group1"));
            ResourceAttribute resourceAttribute = new ResourceAttribute("MedicationAdministration/123", attribute1);
            var reference = new ReferenceWrapper(attribute1, List.of("Medication/123"), "MedicationAdministrationGroup", "MedicationAdministration/123");

            PatientResourceBundle patientResourceBundle = new PatientResourceBundle("123");
            patientResourceBundle.bundle().setResourceAttributeInValid(resourceAttribute);

            assertThat(referenceHandler.handleReferences(List.of(reference), patientResourceBundle, coreBundle, new HashMap<>())).isEmpty();

        }


    }


}
