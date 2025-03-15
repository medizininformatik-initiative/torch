package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.numcodex.sq2cql.Translator;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentHandlerIT {

    public static final String PATIENT_ID = "VHF00006";
    public static final String INVALID_PATIENT_ID = "InvalidPatientId";
    public static final PatientBatchWithConsent BATCH = PatientBatchWithConsent.fromBatch(PatientBatch.of(PATIENT_ID));
    public static final PatientBatchWithConsent BATCH_INVALID = PatientBatchWithConsent.fromBatch(PatientBatch.of(INVALID_PATIENT_ID));
    public static final String OBSERVATION_PATH = "src/test/resources/InputResources/Observation/Observation_lab_vhf_00006.json";

    @Autowired
    ResourceReader resourceReader;


    @Autowired
    @Qualifier("fhirClient")
    protected WebClient webClient;

    ContainerManager manager;


    protected final DirectResourceLoader transformer;
    protected final DataStore dataStore;
    protected final StructureDefinitionHandler cds;

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
    ConsentValidator consentValidator;

    @Autowired
    public ConsentHandlerIT(DirectResourceLoader transformer, DataStore dataStore, StructureDefinitionHandler cds, FhirContext fhirContext, ObjectMapper objectMapper, CqlClient cqlClient, Translator cqlQueryTranslator, DseMappingTreeBase dseMappingTreeBase) {
        this.transformer = transformer;
        this.dataStore = dataStore;
        this.cds = cds;
        this.fhirContext = fhirContext;

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


    @Test
    public void invalidConsentCode() throws IOException {
        // Execute the method and block for the result
        PatientBatchWithConsent resultBatch = consentHandler.buildingConsentInfo("yes-no-no-yes", BATCH).block();

        assertThat(resultBatch).isNotNull();
        assertThat(resultBatch.bundles()).isNotEmpty(); // Ensure there are patient bundles
        assertThat(resultBatch.keySet()).containsExactly(PATIENT_ID);

        PatientResourceBundle patientResourceBundle = resultBatch.bundles().get(PATIENT_ID);


        assertThat(patientResourceBundle).isNotNull();
        assertThat(patientResourceBundle.patientId()).isEqualTo(PATIENT_ID);
        assertThat(patientResourceBundle.provisions()).isNotNull();
        assertThat(patientResourceBundle.provisions().periods()).isEmpty(); // Ensure provisions are added
    }

    @Test
    public void success() throws IOException {
        // Execute the method and block for the result
        PatientBatchWithConsent resultBatch = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH).block();

        assertThat(resultBatch).isNotNull();
        assertThat(resultBatch.bundles()).isNotEmpty(); // Ensure there are patient bundles
        assertThat(resultBatch.keySet()).containsExactly(PATIENT_ID); // Ensure there are patient bundles

        // Pick a patient from the batch
        PatientResourceBundle patientResourceBundle = resultBatch.bundles().get(PATIENT_ID);

        assertThat(patientResourceBundle).isNotNull();
        assertThat(patientResourceBundle.patientId()).isEqualTo(PATIENT_ID);
        assertThat(patientResourceBundle.provisions()).isNotNull();
        assertThat(patientResourceBundle.provisions().periods()).isNotEmpty(); // Ensure provisions are added

        Observation observation = new Observation();
        observation.setId("12345");
        observation.setSubject(new Reference("Patient/" + PATIENT_ID));
        observation.setEffective(new DateTimeType("2021-01-02T00:00:00+01:00"));
        assertThat(consentValidator.checkConsent(observation, resultBatch)).isTrue();
        observation.setEffective(new DateTimeType("2020-01-01T00:00:00+01:00"));
        assertThat(consentValidator.checkConsent(observation, resultBatch)).isFalse();
    }


    @Test
    public void invalidBatch() throws IOException {
        // Execute the method and block for the result
        PatientBatchWithConsent resultBatch = consentHandler.buildingConsentInfo("yes-yes-yes-yes", BATCH_INVALID).block();
        assertThat(resultBatch.keySet()).containsExactly(INVALID_PATIENT_ID);

        // Pick a patient from the batch
        PatientResourceBundle patientResourceBundle = resultBatch.bundles().get(INVALID_PATIENT_ID);


        assertThat(patientResourceBundle).isNotNull();
        assertThat(patientResourceBundle.patientId()).isEqualTo(INVALID_PATIENT_ID);
        assertThat(patientResourceBundle.provisions()).isNotNull();
        assertThat(patientResourceBundle.provisions().isEmpty()).isTrue(); // Ensure provisions are added
    }


}