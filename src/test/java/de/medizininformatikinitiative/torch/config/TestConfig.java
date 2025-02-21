package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.DirectResourceLoader;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.cql.FhirHelper;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.model.mapping.DseTreeRoot;
import de.medizininformatikinitiative.torch.rest.CapabilityStatementController;
import de.medizininformatikinitiative.torch.service.*;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.testUtil.FhirTestHelper;
import de.medizininformatikinitiative.torch.util.*;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.Mapping;
import de.numcodex.sq2cql.model.MappingContext;
import de.numcodex.sq2cql.model.MappingTreeBase;
import de.numcodex.sq2cql.model.MappingTreeModuleRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;

@Configuration
@Profile("test")
public class TestConfig {
    private static final Logger logger = LoggerFactory.getLogger(TestConfig.class);

    @Value("${torch.mappingsFile}")
    private String mappingsFile;
    @Value("${torch.conceptTreeFile}")
    private String conceptTreeFile;
    @Value("${torch.dseMappingTreeFile}")
    private String dseMappingTreeFile;
    @Value("${torch.mapping.consent}")
    String consentFilePath;
    @Value("${torch.mapping.type_to_consent}")
    private String consentToProfileFilePath;

    @Value("compartmentdefinition-patient.json")
    private String compartmentPath;

    @Bean
    public CompartmentManager compartmentManager() throws IOException {
        return new CompartmentManager(compartmentPath);
    }

    @Bean
    public ProcessedGroupFactory attributeGroupProcessor(CompartmentManager manager) {
        return new ProcessedGroupFactory(manager);
    }

    @Bean
    ProfileMustHaveChecker mustHaveChecker(FhirContext ctx) {
        return new ProfileMustHaveChecker(ctx);
    }

    @Bean
    ReferenceResolver referenceResolver(FhirContext ctx, DataStore dataStore, ProfileMustHaveChecker mustHaveChecker, ProfileMustHaveChecker profileMustHaveChecker, CompartmentManager compartmentManager, ConsentHandler consentHandler) {
        return new ReferenceResolver(ctx, dataStore, profileMustHaveChecker, compartmentManager, consentHandler);
    }

    @Bean
    BatchReferenceProcessor batchReferenceProcessor(ReferenceResolver referenceResolver) {
        return new BatchReferenceProcessor(referenceResolver);
    }

    @Bean
    BatchCopierRedacter batchCopierRedacter(ElementCopier copier, Redaction redaction) {
        return new BatchCopierRedacter(copier, redaction);
    }


    @Bean
    BatchProcessingPipeline batchProcessingPipeline(DirectResourceLoader directLoader,
                                                    BatchReferenceProcessor batchReferenceProcessor,
                                                    BatchCopierRedacter batchCopierRedacter,
                                                    @Value("5") int maxConcurrency) {
        return new BatchProcessingPipeline(directLoader, batchReferenceProcessor, batchCopierRedacter, maxConcurrency);
    }

    @Bean
    public CrtdlProcessingService crtdlProcessingService(
            @Qualifier("flareClient") WebClient webClient,
            Translator cqlQueryTranslator,
            CqlClient cqlClient,
            ResultFileManager resultFileManager,
            ProcessedGroupFactory processedGroupFactory,
            @Value("${torch.batchsize:10}") int batchSize,
            @Value("${torch.useCql}") boolean useCql,
            BatchProcessingPipeline batchProcessingPipeline) {
        return new CrtdlProcessingService(webClient, cqlQueryTranslator, cqlClient, resultFileManager,
                processedGroupFactory, batchSize, useCql, batchProcessingPipeline);
    }


    // Bean for the FHIR WebClient initialized with the dynamically determined URL
    @Bean
    @Qualifier("fhirClient")
    public WebClient fhirWebClient(ContainerManager containerManager) {
        String blazeBaseUrl = containerManager.getBlazeBaseUrl();
        logger.info("Initializing FHIR WebClient with URL: {}", blazeBaseUrl);

        ConnectionProvider provider = ConnectionProvider.builder("data-store")
                .maxConnections(4)
                .pendingAcquireMaxCount(500)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        return WebClient.builder()
                .baseUrl(blazeBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json")
                .build();
    }


    // Bean for the Flare WebClient initialized with the dynamically determined URL
    @Bean
    @Qualifier("flareClient")
    public WebClient flareWebClient(ContainerManager containerManager) {
        String flareBaseUrl = containerManager.getFlareBaseUrl();
        logger.info("Initializing Flare WebClient with URL: {}", flareBaseUrl);

        ConnectionProvider provider = ConnectionProvider.builder("flare-store")
                .maxConnections(4)
                .pendingAcquireMaxCount(500)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        return WebClient.builder()
                .baseUrl(flareBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }


    @Bean
    FhirTestHelper testHelper(FhirContext context, ResourceReader resourceReader) {
        return new FhirTestHelper(context, resourceReader);
    }

    @Bean
    public CrtdlValidatorService crtdlValidatorService(StructureDefinitionHandler structureDefinitionHandler, CompartmentManager compartmentManager) throws IOException {
        return new CrtdlValidatorService(structureDefinitionHandler, compartmentManager);
    }


    @Bean
    public ContainerManager containerManager() {
        return new ContainerManager();
    }

    @Bean
    ResourceReader resourceReader(FhirContext ctx) {
        return new ResourceReader(ctx);
    }


    @Bean
    public ObjectMapper objectMapper() {

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }


    @Bean
    public ConsentCodeMapper consentCodeMapper(ObjectMapper objectMapper) throws IOException {
        return new ConsentCodeMapper(consentFilePath, objectMapper);
    }

    @Bean
    public DataStore dataStore(@Qualifier("fhirClient") WebClient client, FhirContext context, @Qualifier("systemDefaultZone") Clock clock,
                               @Value("${torch.fhir.pageCount}") int pageCount) {
        return new DataStore(client, context, pageCount);
    }

    @Bean
    public DseMappingTreeBase dseMappingTreeBase(ObjectMapper jsonUtil) throws IOException {
        return new DseMappingTreeBase(Arrays.stream(jsonUtil.readValue(new File(dseMappingTreeFile), DseTreeRoot[].class)).toList());
    }

    @Lazy
    @Bean
    Translator createCqlTranslator(ObjectMapper jsonUtil) throws IOException {
        var mappings = jsonUtil.readValue(new File(mappingsFile), Mapping[].class);
        var mappingTreeBase = new MappingTreeBase(Arrays.stream(jsonUtil.readValue(new File(conceptTreeFile), MappingTreeModuleRoot[].class)).toList());

        return Translator.of(MappingContext.of(
                Stream.of(mappings)
                        .collect(Collectors.toMap(Mapping::key, Function.identity(), (a, b) -> a)),
                mappingTreeBase,
                Map.ofEntries(entry("http://fhir.de/CodeSystem/bfarm/icd-10-gm", "icd10"),
                        entry("mii.abide", "abide"),
                        entry("http://fhir.de/CodeSystem/bfarm/ops", "ops"),
                        entry("http://dicom.nema.org/resources/ontology/DCM", "dcm"),
                        entry("https://www.medizininformatik-initiative.de/fhir/core/modul-person/CodeSystem/Vitalstatus", "vitalstatus"),
                        entry("http://loinc.org", "loinc"),
                        entry("https://fhir.bbmri.de/CodeSystem/SampleMaterialType", "sample"),
                        entry("http://fhir.de/CodeSystem/bfarm/atc", "atc"),
                        entry("http://snomed.info/sct", "snomed"),
                        entry("http://terminology.hl7.org/CodeSystem/condition-ver-status", "cvs"),
                        entry("http://hl7.org/fhir/administrative-gender", "gender"),
                        entry("urn:oid:1.2.276.0.76.5.409", "urn409"),
                        entry(
                                "https://www.netzwerk-universitaetsmedizin.de/fhir/CodeSystem/ecrf-parameter-codes",
                                "numecrf"),
                        entry("urn:iso:std:iso:3166", "iso3166"),
                        entry("https://www.netzwerk-universitaetsmedizin.de/fhir/CodeSystem/frailty-score",
                                "frailtyscore"),
                        entry("http://terminology.hl7.org/CodeSystem/consentcategorycodes", "consentcategory"),
                        entry("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "consent"),
                        entry("http://hl7.org/fhir/sid/icd-o-3", "icdo3"),
                        entry("http://hl7.org/fhir/consent-provision-type", "provisiontype"))));
    }


    @Bean
    FhirHelper createFhirHelper(FhirContext fhirContext) {
        return new FhirHelper(fhirContext);
    }

    @Bean
    CqlClient createCqlQueryClient(
            FhirHelper fhirHelper,
            DataStore dataStore) {

        return new CqlClient(fhirHelper, dataStore);
    }

    @Bean
    public ElementCopier elementCopier(StructureDefinitionHandler handler, FhirContext ctx) {
        return new ElementCopier(handler, ctx);
    }

    @Bean
    public BundleBuilder bundleBuilder(FhirContext context) {
        return new BundleBuilder(context);
    }

    @Bean
    public Redaction redaction(StructureDefinitionHandler cds) {
        return new Redaction(cds);
    }

    @Bean
    public DirectResourceLoader resourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, DseMappingTreeBase dseMappingTreeBase, StructureDefinitionHandler structureDefinitionHandler, ProfileMustHaveChecker profileMustHaveChecker) {

        return new DirectResourceLoader(dataStore, handler, dseMappingTreeBase, structureDefinitionHandler, profileMustHaveChecker);
    }

    @Bean
    ConsentHandler handler(DataStore dataStore, ConsentCodeMapper mapper, StructureDefinitionHandler cds, FhirContext ctx, ObjectMapper objectMapper) throws IOException {
        return new ConsentHandler(dataStore, mapper, consentToProfileFilePath, cds, ctx, objectMapper);
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();  // Assuming R4 version, configure as needed
    }


    @Bean
    public StructureDefinitionHandler cdsStructureDefinitionHandler(@Value("${torch.profile.dir}") String dir, ResourceReader reader) {
        return new StructureDefinitionHandler(dir, reader);
    }

    @Bean
    public ResultFileManager resultFileManager(@Value("${torch.results.dir}") String resultsDir, @Value("${torch.results.persistence}") String duration, FhirContext fhirContext, @Value("${nginx.servername}") String hostname, @Value("${nginx.filelocation}") String fileserverName) {
        return new ResultFileManager(resultsDir, duration, fhirContext, hostname, fileserverName);
    }

    @Bean
    public CapabilityStatementController capabilityStatementController() {
        return new CapabilityStatementController();
    }


    @Bean
    ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public Clock systemDefaultZone() {
        return Clock.systemDefaultZone();
    }


}
