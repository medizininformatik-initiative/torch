package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.consent.PatientConsentInfo;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.numcodex.sq2cql.Translator;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentHandlerIT {


    public static final PatientBatch BATCH = PatientBatch.of("VHF00006");
    public static final PatientBatch BATCH_INVALID = PatientBatch.of("INVALID");
    public static final String OBSERVATION_PATH = "src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json";
    public static final String PATIENT_ID = "VHF00006";
    @Autowired
    ResourceReader resourceReader;


    @Autowired
    @Qualifier("fhirClient")
    protected WebClient webClient;

    ContainerManager manager;


    protected final ResourceTransformer transformer;
    protected final DataStore dataStore;
    protected final StructureDefinitionHandler cds;
    protected BundleCreator bundleCreator;
    protected ObjectMapper objectMapper;
    protected CqlClient cqlClient;
    protected Translator cqlQueryTranslator;
    @Value("${torch.fhir.testPopulation.path}")
    private String testPopulationPath;
    protected FhirContext fhirContext;


    protected final DseMappingTreeBase dseMappingTreeBase;


    private static final String RESOURCE_PATH_PREFIX = "src/test/resources/";
    @LocalServerPort
    private int port;


    @Autowired
    ConsentHandler consentHandler;

    @Autowired
    public ConsentHandlerIT(ResourceTransformer transformer, DataStore dataStore, StructureDefinitionHandler cds, FhirContext fhirContext, BundleCreator bundleCreator, ObjectMapper objectMapper, CqlClient cqlClient, Translator cqlQueryTranslator, DseMappingTreeBase dseMappingTreeBase) {
        this.transformer = transformer;
        this.dataStore = dataStore;
        this.cds = cds;
        this.fhirContext = fhirContext;
        this.bundleCreator = bundleCreator;
        this.objectMapper = objectMapper;
        this.cqlClient = cqlClient;
        this.cqlQueryTranslator = cqlQueryTranslator;
        this.dseMappingTreeBase = dseMappingTreeBase;
        this.manager = new ContainerManager();


    }

    @BeforeAll
    void init() throws IOException {
        manager.startContainers();
        webClient.post().bodyValue(Files.readString(Path.of(testPopulationPath))).header("Content-Type", "application/fhir+json").retrieve().toBodilessEntity().block();
    }

    /*
    @Test
    public void testHandlerWithUpdate() throws IOException {
        private Observation getObservationWithTime(String s) throws IOException {
            Observation observation = (Observation) resourceReader.readResource(OBSERVATION_PATH);
            DateTimeType time = new DateTimeType(s);
            observation.setEffective(time);
            return observation;
        }

        Flux<ConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH);
        consentInfoFlux = consentHandler.updateConsentPeriodsByPatientEncounters(consentInfoFlux, BATCH);


        List<ConsentInfo> consentInfoList = consentInfoFlux.collectList().block();
        Assertions.assertNotNull(consentInfoList);
        for (Map<String, Map<String, ConsentInfo.NonContinousPeriod>> consentInfo : consentInfoList) {
            Boolean consentInfoResult = consentHandler.checkConsent((Observation) observation, consentInfo);
            assertThat(consentInfoResult).isTrue();
        }
    }




    @Test
    public void testHandlerWithoutUpdate() {
        PatientBatch batch = PatientBatch.of("VHF00006");
        Resource observation;
        try {
            observation = resourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json");
            DateTimeType time = new DateTimeType("2022-01-01T00:00:00+01:00");
            ((Observation) observation).setEffective(time);
            Flux<ConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", batch);

            consentInfoFlux = consentHandler.updateConsentPeriodsByPatientEncounters(consentInfoFlux, batch);


            List<ConsentInfo> consentInfoList = consentInfoFlux.collectList().block();


            Assertions.assertNotNull(consentInfoList);
            for (ConsentInfo consentInfo : consentInfoList) {


                Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);

                System.out.println("consent Check Result: " + consentInfoResult);
                Assertions.assertTrue(consentInfoResult);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    @Test
    public void testHandlerWithUpdatingFail() {
        PatientBatch batch = PatientBatch.of("VHF00006");

        Resource observation;
        try {
            observation = resourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json");
            DateTimeType time = new DateTimeType("2026-01-01T00:00:00+01:00");
            ((Observation) observation).setEffective(time);

            Flux<ConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", batch);

            List<ConsentInfo> consentInfoList = consentInfoFlux.collectList().block();


            Assertions.assertTrue(consentInfoList != null && !consentInfoList.isEmpty());


            for (ConsentInfo consentInfo : consentInfoList) {
                System.out.println("Evaluating consentInfo: " + consentInfo);
                Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);
                Assertions.assertFalse(consentInfoResult);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


      @Test
    public void testHandlerWithoutUpdatingFail() throws IOException {
        Observation observation = getObservationWithTime("2020-01-01T00:00:00+01:00");


        Flux<ConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH);

        List<ConsentInfo> consentInfoList = consentInfoFlux.collectList().block();
        ConsentInfo consentInfo = consentInfoList.getFirst();

        assertThat(consentInfo.patientId()).isEqualTo(PATIENT_ID);
        //Boolean consentInfoResult = consentHandler.checkConsent(observation, consentInfo);
    }
     */

    @Nested
    class UpdatePatientConsentInfo {
        @Test
        public void success() throws IOException {
            Flux<PatientConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH);

            List<PatientConsentInfo> patientConsentInfoList = consentInfoFlux.collectList().block();
            assert patientConsentInfoList != null;
            PatientConsentInfo patientConsentInfo = patientConsentInfoList.getFirst();

            assertThat(patientConsentInfo.patientId()).isEqualTo(PATIENT_ID);
        }

        @Test
        public void invalidBatch() throws IOException {
            Flux<PatientConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH_INVALID);

            List<PatientConsentInfo> patientConsentInfoList = consentInfoFlux.collectList().block();
            assertThat(patientConsentInfoList).isEmpty();
        }


    }


    @Nested
    class BuildPatientConsentInfo {
        @Test
        public void success() throws IOException {
            Flux<PatientConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH);

            List<PatientConsentInfo> patientConsentInfoList = consentInfoFlux.collectList().block();
            assert patientConsentInfoList != null;
            PatientConsentInfo patientConsentInfo = patientConsentInfoList.getFirst();

            assertThat(patientConsentInfo.patientId()).isEqualTo(PATIENT_ID);
        }

        @Test
        public void invalidBatch() throws IOException {
            Flux<PatientConsentInfo> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH_INVALID);

            List<PatientConsentInfo> patientConsentInfoList = consentInfoFlux.collectList().block();
            assertThat(patientConsentInfoList).isEmpty();
        }

    }


    private Observation getObservationWithTime(String s) throws IOException {
        Observation observation = (Observation) resourceReader.readResource(OBSERVATION_PATH);
        DateTimeType time = new DateTimeType(s);
        observation.setEffective(time);
        return observation;
    }


}