package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceHandlerTest {

    @Mock
    private ProfileMustHaveChecker profileMustHaveChecker;

    private ReferenceHandler referenceHandler;

    static final ExtractionId OBS_ID = ExtractionId.fromRelativeUrl("Observation/obs1");
    static final ExtractionId MED_ID = ExtractionId.fromRelativeUrl("Medication/m1");

    @BeforeEach
    void setUp() {
        referenceHandler = new ReferenceHandler(profileMustHaveChecker);
    }

    @Nested
    class HandleReferenceAttribute {

        @Test
        void refNotInAnyBundle_mustHaveFalse_returnsEmptyList() {
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();

            StepVerifier.create(referenceHandler.handleReferenceAttribute(wrapper, null, coreBundle, Map.of()))
                    .assertNext(list -> assertThat(list).isEmpty())
                    .verifyComplete();
        }

        @Test
        void refNotInAnyBundle_mustHaveTrue_errorsWithMustHaveViolated() {
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", true, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();

            StepVerifier.create(referenceHandler.handleReferenceAttribute(wrapper, null, coreBundle, Map.of()))
                    .expectError(MustHaveViolatedException.class)
                    .verify();
        }

        @Test
        void refInCoreBundle_profileFulfilled_returnsResourceGroup() {
            var med = new Medication();
            med.setId("m1");
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.put(med);
            var group = new AnnotatedAttributeGroup("grp", "Medication", "http://profile", List.of(), List.of());
            when(profileMustHaveChecker.fulfilled(med, group)).thenReturn(true);

            StepVerifier.create(referenceHandler.handleReferenceAttribute(wrapper, null, coreBundle, Map.of("grp", group)))
                    .assertNext(list -> assertThat(list).hasSize(1))
                    .verifyComplete();
        }

        @Test
        void refInPatientBundle_profileFulfilled_returnsResourceGroup() {
            var obs = new Observation();
            obs.setId("obs1");
            var patRef = ExtractionId.fromRelativeUrl("Observation/obs1");
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(patRef), List.of(), "grp", OBS_ID);
            var patientBundle = new PatientResourceBundle("p1");
            patientBundle.put(obs);
            var coreBundle = new ResourceBundle();
            var group = new AnnotatedAttributeGroup("grp", "Observation", "http://profile", List.of(), List.of());
            when(profileMustHaveChecker.fulfilled(obs, group)).thenReturn(true);

            StepVerifier.create(referenceHandler.handleReferenceAttribute(wrapper, patientBundle, coreBundle, Map.of("grp", group)))
                    .assertNext(list -> assertThat(list).hasSize(1))
                    .verifyComplete();
        }

        @Test
        void profileNotFulfilled_returnsEmptyListNoError() {
            var med = new Medication();
            med.setId("m1");
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.put(med);
            var group = new AnnotatedAttributeGroup("grp", "Medication", "http://profile", List.of(), List.of());
            when(profileMustHaveChecker.fulfilled(med, group)).thenReturn(false);

            StepVerifier.create(referenceHandler.handleReferenceAttribute(wrapper, null, coreBundle, Map.of("grp", group)))
                    .assertNext(list -> assertThat(list).isEmpty())
                    .verifyComplete();
        }

        @Test
        void cachedValidityReused_doesNotCallChecker() {
            var med = new Medication();
            med.setId("m1");
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.put(med);
            var group = new AnnotatedAttributeGroup("grp", "Medication", "http://profile", List.of(), List.of());
            var rg = new ResourceGroup(MED_ID, "grp");
            coreBundle.addResourceGroupValidity(rg, true);

            StepVerifier.create(referenceHandler.handleReferenceAttribute(wrapper, null, coreBundle, Map.of("grp", group)))
                    .assertNext(list -> assertThat(list).hasSize(1))
                    .verifyComplete();
        }
    }

    @Nested
    class HandleReferences {

        @Test
        void unprocessedRef_notInBundle_mustHaveFalse_completesEmpty() {
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();

            StepVerifier.create(referenceHandler.handleReferences(List.of(wrapper), null, coreBundle, Map.of(), Set.of()))
                    .verifyComplete();
        }

        @Test
        void alreadyValidAttribute_skipsReprocessing_completesEmpty() {
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.setResourceAttributeValid(wrapper.toResourceAttributeGroup());

            StepVerifier.create(referenceHandler.handleReferences(List.of(wrapper), null, coreBundle, Map.of(), Set.of()))
                    .verifyComplete();
        }

        @Test
        void invalidAttribute_mustHaveFalse_completesEmpty() {
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.setResourceAttributeInValid(wrapper.toResourceAttributeGroup());

            StepVerifier.create(referenceHandler.handleReferences(List.of(wrapper), null, coreBundle, Map.of(), Set.of()))
                    .verifyComplete();
        }

        @Test
        void refInCoreBundle_profileFulfilled_emitsNewResourceGroup() {
            var med = new Medication();
            med.setId("m1");
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.put(med);
            var group = new AnnotatedAttributeGroup("grp", "Medication", "http://profile", List.of(), List.of());
            when(profileMustHaveChecker.fulfilled(med, group)).thenReturn(true);

            StepVerifier.create(referenceHandler.handleReferences(
                            List.of(wrapper), null, coreBundle, Map.of("grp", group), Set.of()))
                    .assertNext(rg -> assertThat(rg.resourceId()).isEqualTo(MED_ID))
                    .verifyComplete();
        }

        @Test
        void alreadyKnownGroup_filteredOut() {
            var med = new Medication();
            med.setId("m1");
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.put(med);
            var group = new AnnotatedAttributeGroup("grp", "Medication", "http://profile", List.of(), List.of());
            when(profileMustHaveChecker.fulfilled(med, group)).thenReturn(true);
            var knownGroup = new ResourceGroup(MED_ID, "grp");

            StepVerifier.create(referenceHandler.handleReferences(
                            List.of(wrapper), null, coreBundle, Map.of("grp", group), Set.of(knownGroup)))
                    .verifyComplete();
        }

        @Test
        void invalidAttribute_mustHaveTrue_errorsAndMarksParentInvalid() {
            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", true, List.of("grp"));
            var wrapper = new ReferenceWrapper(attr, List.of(MED_ID), List.of(), "grp", OBS_ID);
            var coreBundle = new ResourceBundle();
            coreBundle.setResourceAttributeInValid(wrapper.toResourceAttributeGroup());

            StepVerifier.create(referenceHandler.handleReferences(List.of(wrapper), null, coreBundle, Map.of(), Set.of()))
                    .expectError(MustHaveViolatedException.class)
                    .verify();

            assertThat(coreBundle.isValidResourceGroup(wrapper.toResourceGroup())).isFalse();
        }
    }
}
