package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.cql.FhirConnector;
import de.medizininformatikinitiative.torch.cql.FhirHelper;
import de.medizininformatikinitiative.torch.*;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.model.mapping.DseTreeRoot;
import de.medizininformatikinitiative.torch.rest.CapabilityStatementController;
import de.medizininformatikinitiative.torch.service.CrtdlProcessingService;
import de.medizininformatikinitiative.torch.setup.ContainerManager;
import de.medizininformatikinitiative.torch.testUtil.FhirTestHelper;
import de.medizininformatikinitiative.torch.util.*;
import de.medizininformatikinitiative.torch.service.DataStore;
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


    @Bean
    public CrtdlProcessingService crtdlProcessingService(
            @Qualifier("flareClient") WebClient webClient,
            Translator cqlQueryTranslator,
            CqlClient cqlClient,
            ResultFileManager resultFileManager,
            ResourceTransformer transformer,
            BundleCreator bundleCreator,
            @Value("${torch.batchsize:10}") int batchSize,
            @Value("5") int maxConcurrency,
            @Value("${torch.useCql}") boolean useCql) {

        return new CrtdlProcessingService(webClient, cqlQueryTranslator, cqlClient, resultFileManager,
                transformer, bundleCreator,
                batchSize, maxConcurrency, useCql);
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
        return new DataStore(client, context, clock, pageCount);
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
    FhirConnector createFhirConnector(@Value("${torch.fhir.url}") String fhirUrl) {
        return new FhirConnector(fhirContext().newRestfulGenericClient(fhirUrl));
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
    FhirPathBuilder fhirPathBuilder(Slicing slicing) {
        return new FhirPathBuilder(slicing);
    }

    @Bean
    Slicing slicing(CdsStructureDefinitionHandler cds, FhirContext ctx) {
        return new Slicing(cds, ctx);
    }


    @Bean
    public ElementCopier elementCopier(CdsStructureDefinitionHandler handler, FhirContext ctx, FhirPathBuilder fhirPathBuilder) {
        return new ElementCopier(handler, ctx, fhirPathBuilder);
    }

    @Bean
    public BundleBuilder bundleBuilder(FhirContext context) {
        return new BundleBuilder(context);
    }

    @Bean
    public Redaction redaction(CdsStructureDefinitionHandler cds, Slicing slicing) {
        return new Redaction(cds, slicing);
    }

    @Bean
    public ResourceTransformer resourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, FhirContext context) {

        return new ResourceTransformer(dataStore, handler, copier, redaction, context);
    }

    @Bean
    ConsentHandler handler(DataStore dataStore, ConsentCodeMapper mapper, @Value("${torch.mapping.consent_to_profile}") String consentFilePath, CdsStructureDefinitionHandler cds, FhirContext ctx, ObjectMapper objectMapper) throws IOException {
        return new ConsentHandler(dataStore, mapper, consentFilePath, cds, ctx,objectMapper );
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();  // Assuming R4 version, configure as needed
    }


    @Bean
    public CdsStructureDefinitionHandler cdsStructureDefinitionHandler(@Value("${torch.profile.dir}") String dir, ResourceReader reader) {
        return new CdsStructureDefinitionHandler(dir, reader);
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
    public BundleCreator bundleCreator() {
        return new BundleCreator();
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
