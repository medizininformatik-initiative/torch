package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class ResourceTransformationTest {


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
        void successAttributeCopyStandardFields() throws Exception {
            Observation src = new Observation();
            src.setSubject(new Reference("Patient/123"));
            src.setMeta(new Meta().addProfile("Test"));
            Attribute effective = new Attribute("Observation.effective", false);
            AttributeGroup group = new AttributeGroup("GroupRef", List.of(effective), List.of());
            group.addStandardAttributes(Observation.class);

            Observation result = transformer.transform(src, group, Observation.class);

            Mockito.verify(copier).copy(src, result, effective);
        }


        @Test
        void successAttributeCopy() throws Exception {
            Observation src = new Observation();
            src.setSubject(new Reference("Patient/123"));
            Attribute effective = new Attribute("Observation.effective", false);
            AttributeGroup group = new AttributeGroup("GroupRef", List.of(effective), List.of());

            Observation result = transformer.transform(src, group, Observation.class);

            Mockito.verify(copier).copy(src, result, effective);
        }

        @Test
        void failWithMustHaveAttributeCopy() throws Exception {
            Observation src = new Observation();
            src.setSubject(new Reference("Patient/123"));
            Attribute id = new Attribute("id", true);
            AttributeGroup group = new AttributeGroup("GroupRef", List.of(id), List.of());
            doThrow(MustHaveViolatedException.class).when(copier).copy(Mockito.eq(src), Mockito.any(), Mockito.eq(id));

            assertThatThrownBy(() -> transformer.transform(src, group, Observation.class)).isInstanceOf(MustHaveViolatedException.class);
        }

        @Test
        void failWithPatientIdException() throws Exception {
            Observation src = new Observation();
            Attribute id = new Attribute("id", true);
            AttributeGroup group = new AttributeGroup("GroupRef", List.of(id), List.of());

            assertThatThrownBy(() -> transformer.transform(src, group, Observation.class)).isInstanceOf(PatientIdNotFoundException.class);
        }


    }

    @Nested
    class CollectResourcesByPatId {

    }


    /* TODO extract to integration test
    @Test
    public void testObservation() {

        try {
            FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json");
            Crtdl crtdl = INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class);

            DomainResource resourcesrc = INTEGRATION_TEST_SETUP.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            DomainResource resourceexpected = INTEGRATION_TEST_SETUP.readResource("src/test/resources/ResourceTransformationTest/ExpectedOutput/Observation_lab.json");

            DomainResource tgt = (DomainResource) transformer.transform(resourcesrc, crtdl.dataExtraction().attributeGroups().getFirst());

            logger.info("Result: {}", INTEGRATION_TEST_SETUP.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt));
            assertNotNull(tgt);
            assertEquals(
                    INTEGRATION_TEST_SETUP.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceexpected),
                    INTEGRATION_TEST_SETUP.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt),
                    "Expected not equal to actual output"
            );
            fis.close();
        } catch (Exception e) {
            logger.error("", e);
            fail("Deserialization failed: " + e.getMessage(), e);
        }
    }


    @Test
    public void collectPatientsbyResource() {

        try {
            FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields.json");
            Crtdl crtdl = INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class);
            fis.close();

            Mono<Map<String, Collection<Resource>>> result = transformer.collectResourcesByPatientReference(crtdl, new PatientBatch(List.of("1", "2", "4", "VHF00006")));

            StepVerifier.create(result)
                    .expectNextMatches(map -> map.containsKey("1")) // Patient1 is in consent info
                    .verifyComplete();

        } catch (Exception e) {
            logger.error("", e);
            fail("Deserialization failed: " + e.getMessage(), e);
        }
    }

    @Test
    void testExecuteQueryWithBatchAllPatients() {
        PatientBatch batch = PatientBatch.of("1", "2");
        Query query = new Query("Patient", EMPTY); // Basic query setup

        Flux<Resource> result = transformer.executeQueryWithBatch(batch, query);

        StepVerifier.create(result)
                .expectNextMatches(resource -> resource instanceof Patient)
                .expectNextMatches(resource -> resource instanceof Patient)
                .verifyComplete();

    }

    @Test
    void testExecuteQueryWithBatch_Success() throws IOException {
        FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation_all_fields.json");
        Crtdl crtdl = INTEGRATION_TEST_SETUP.objectMapper().readValue(fis, Crtdl.class);
        fis.close();


        PatientBatch batch = PatientBatch.of("1", "2");


        logger.info("Attribute Groups {}", crtdl.dataExtraction().attributeGroups().size());
        logger.info("Attribute Groups {}", crtdl.dataExtraction().attributeGroups().getFirst().attributes().size());
        List<Query> queries = crtdl.dataExtraction().attributeGroups().getFirst().queries(base);
        logger.info("Queries size {}", queries.size());
        List<QueryParams> params = crtdl.dataExtraction().attributeGroups().getFirst().queries(base).stream().map(Query::params).toList();
        queries.forEach(x -> logger.info("Query: {}", x.toString())
        );
        logger.info("Queries size {}", params.size());
        params.forEach(x -> logger.info("params: {}", x.toString())
        );


        StepVerifier.create(transformer.executeQueryWithBatch(batch, queries.getFirst()))
                .expectNextMatches(resource -> resource instanceof Observation)
                .expectNextMatches(resource -> resource instanceof Observation)
                .verifyComplete();

    }


     */


}
