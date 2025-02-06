package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.BatchCopierRedacter;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Meta;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class BatchCopierRedactorTest {


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

    Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();


    AnnotatedAttribute effective = new AnnotatedAttribute("Observation.effective", "Observation.effective", "Observation.effective", false);
    AnnotatedAttribute metaAttribute = new AnnotatedAttribute("Observation.meta", "Observation.meta", "Observation.meta", false);
    AnnotatedAttribute id = new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", false);
    AnnotatedAttribute subject = new AnnotatedAttribute("Observation.subject", "Observation.subject", "Observation.subject", false);


    @InjectMocks
    BatchCopierRedacter transformer;
    private ResourceGroupWrapper wrapper;


    @Nested
    class Transform {
        @BeforeEach
        void setUp() {
            Observation src = new Observation();
            src.setId("123");
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(OBSERVATION)));
            src.setSubject(new Reference("Patient/123"));

            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Observation1", OBSERVATION, List.of(effective, metaAttribute, id, subject), List.of());
            groupMap.put(group.id(), group);

            wrapper = new ResourceGroupWrapper(src, Set.of(group.id()));
        }


        @Test
        void successAttributeCopy() throws Exception {


            ResourceGroupWrapper result = transformer.transform(wrapper, groupMap);

            Mockito.verify(copier).copy(eq(wrapper.resource()), any(), eq(effective), any());
            Mockito.verify(copier).copy(eq(wrapper.resource()), any(), eq(metaAttribute), any());
            Mockito.verify(copier).copy(eq(wrapper.resource()), any(), eq(id), any());
            Mockito.verify(copier).copy(eq(wrapper.resource()), any(), eq(subject), any());


        }


    }


}
