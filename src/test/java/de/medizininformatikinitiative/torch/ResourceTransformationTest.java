package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class ResourceTransformationTest {


    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();
    public static final String OBSERVATION = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    @Mock
    DataStore dataStore;
    @Mock
    ConsentHandler handler;
    @Mock
    ElementCopier copier;
    @Mock
    Redaction redaction;
    @Mock
    FhirContext context;
    @Mock
    DseMappingTreeBase dseMappingTreeBase;


    @InjectMocks
    ResourceTransformer transformer;


    @Nested
    class FetchAndTransformResources {

        @Test
        void success() {

        }

        @Test
        void fail() {

        }

    }

    @Nested
    class Transform {
        @BeforeEach
        void setUp() {

        }


        @Test
        void successAttributeCopy() throws Exception {
            Observation src = new Observation();
            src.setSubject(new Reference("Patient/123"));
            AnnotatedAttribute effective = new AnnotatedAttribute("Observation.effective", "Observation.effective", "Observation.effective", false);
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", OBSERVATION, List.of(effective), List.of());

            Observation result = transformer.transform(src, group, Observation.class);

            Mockito.verify(copier).copy(src, result, effective, group.groupReference());
        }

        @Test
        void failWithMustHaveAttributeCopy() throws Exception {
            Observation src = new Observation();
            src.setSubject(new Reference("Patient/123"));
            AnnotatedAttribute id = new AnnotatedAttribute("id", "id", "id", true);
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", OBSERVATION, List.of(id), List.of());
            doThrow(MustHaveViolatedException.class).when(copier).copy(Mockito.eq(src), Mockito.any(), Mockito.eq(id), Mockito.eq(OBSERVATION));

            assertThatThrownBy(() -> transformer.transform(src, group, Observation.class)).isInstanceOf(MustHaveViolatedException.class);
        }

        @Test
        void failWithPatientIdException() throws Exception {
            Observation src = new Observation();
            AnnotatedAttribute id = new AnnotatedAttribute("id", "id", "id", true);
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Test", OBSERVATION, List.of(id), List.of());

            assertThatThrownBy(() -> transformer.transform(src, group, Observation.class)).isInstanceOf(PatientIdNotFoundException.class);
        }


    }


}
