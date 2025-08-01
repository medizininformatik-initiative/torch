package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.consent.ConsentFetcher;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.cql.FhirHelper;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.model.mapping.DseTreeRoot;
import de.medizininformatikinitiative.torch.rest.CapabilityStatementController;
import de.medizininformatikinitiative.torch.service.BatchCopierRedacter;
import de.medizininformatikinitiative.torch.service.CascadingDelete;
import de.medizininformatikinitiative.torch.service.CrtdlProcessingService;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.service.DirectResourceLoader;
import de.medizininformatikinitiative.torch.service.FilterService;
import de.medizininformatikinitiative.torch.service.PatientBatchToCoreBundleWriter;
import de.medizininformatikinitiative.torch.service.ReferenceBundleLoader;
import de.medizininformatikinitiative.torch.service.ReferenceResolver;
import de.medizininformatikinitiative.torch.service.StandardAttributeGenerator;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
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
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.REGISTRATION_ID;

@Configuration
@Profile("active")
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);


    @Value("compartmentdefinition-patient.json")
    private String compartmentPath;


    private final TorchProperties torchProperties;

    public AppConfig(TorchProperties torchProperties) {
        this.torchProperties = torchProperties;
    }


    @Bean
    public String searchParametersFile(@Value("${torch.search_parameters_file}") String searchParametersFile) {
        return searchParametersFile;
    }

    @Bean
    public FilterService filterService(FhirContext ctx, String searchParametersFile) {
        return new FilterService(ctx, searchParametersFile);
    }

    @Bean
    public CascadingDelete cascadingDelete() {
        return new CascadingDelete();
    }

    @Bean
    public CompartmentManager compartmentManager() throws IOException {
        return new CompartmentManager(compartmentPath);
    }

    @Bean
    ProfileMustHaveChecker mustHaveChecker(FhirContext ctx) {
        return new ProfileMustHaveChecker(ctx);
    }


    @Bean
    public ProcessedGroupFactory attributeGroupProcessor(CompartmentManager manager) {
        return new ProcessedGroupFactory(manager);
    }


    @Bean
    ReferenceHandler referenceHandler(DataStore dataStore, ProfileMustHaveChecker mustHaveChecker, CompartmentManager compartmentManager, ConsentValidator consentValidator) {
        return new ReferenceHandler(mustHaveChecker);
    }

    @Bean
    ReferenceExtractor referenceExtractor(FhirContext ctx) {
        return new ReferenceExtractor(ctx);
    }

    @Bean
    ReferenceResolver referenceResolver(CompartmentManager compartmentManager, ReferenceHandler referenceHandler, ReferenceExtractor referenceExtractor, ReferenceBundleLoader referenceBundleLoader) {
        return new ReferenceResolver(compartmentManager, referenceHandler, referenceExtractor, referenceBundleLoader);
    }

    @Bean
    public ReferenceBundleLoader referenceBundleLoader(CompartmentManager compartmentManager,
                                                       DataStore dataStore, ConsentValidator consentValidator) {
        return new ReferenceBundleLoader(compartmentManager, dataStore, consentValidator, torchProperties.fhir().page().count());

    }

    @Bean
    BatchCopierRedacter batchCopierRedacter(ElementCopier copier, Redaction redaction) {
        return new BatchCopierRedacter(copier, redaction);
    }

    @Bean
    @Qualifier("fhirClient")
    public WebClient fhirWebClient(@Value("${torch.fhir.url}") String baseUrl,
                                   ExchangeFilterFunction oauthExchangeFilterFunction,
                                   @Value("${torch.fhir.user}") String user,
                                   @Value("${torch.fhir.password}") String password,
                                   @Value("${torch.fhir.max.connections}") int maxConnections) {
        logger.info("Initializing FHIR WebClient with URL {} and a maximum number of {} concurrent connections", baseUrl, maxConnections);

        // Configure buffer size to 10MB
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(1024 * 1024 * torchProperties.bufferSize()))
                .build();

        ConnectionProvider provider = ConnectionProvider.builder("data-store")
                .maxConnections(torchProperties.fhir().max().connections())
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json");

        if (!user.isEmpty() && !password.isEmpty()) {
            builder = builder.filter(ExchangeFilterFunctions.basicAuthentication(user, password));
            logger.info("Added basic authentication for user: {}", user);
        } else {
            logger.info("Using OAuth");
        }

        return builder.filter(oauthExchangeFilterFunction).build();
    }

    @Bean
    @Qualifier("flareClient")
    public WebClient flareWebClient(@Value("${torch.flare.url}") String baseUrl) {
        logger.info("Initializing Flare WebClient with URL: {}", baseUrl);

        ConnectionProvider provider = ConnectionProvider.builder("data-store")
                .maxConnections(4)
                .pendingAcquireMaxCount(500)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/sq+json");

        return builder.build();
    }

    @Bean
    StandardAttributeGenerator standardAttributeGenerator(CompartmentManager compartmentManager, StructureDefinitionHandler structureDefinitionHandler) {
        return new StandardAttributeGenerator(compartmentManager, structureDefinitionHandler);

    }

    @Bean
    public CrtdlValidatorService crtdlValidatorService(StructureDefinitionHandler structureDefinitionHandler, StandardAttributeGenerator standardAttributeGenerator, FilterService filterService) throws IOException {
        return new CrtdlValidatorService(structureDefinitionHandler, standardAttributeGenerator, filterService);
    }


    @Bean
    public CrtdlProcessingService crtdlProcessingService(
            @Qualifier("flareClient") WebClient webClient,
            Translator cqlQueryTranslator,
            CqlClient cqlClient,
            ResultFileManager resultFileManager,
            ProcessedGroupFactory processedGroupFactory,
            DirectResourceLoader directResourceLoader,
            ReferenceResolver referenceResolver,
            BatchCopierRedacter batchCopierRedacter,
            CascadingDelete cascadingDelete,
            PatientBatchToCoreBundleWriter writer,
            ConsentHandler consentHandler
    ) {

        return new CrtdlProcessingService(webClient, cqlQueryTranslator, cqlClient, resultFileManager,
                processedGroupFactory, torchProperties.batchsize(), torchProperties.useCql(), directResourceLoader,
                referenceResolver, batchCopierRedacter, torchProperties.maxConcurrency(), cascadingDelete, writer, consentHandler);
    }

    @Bean
    PatientBatchToCoreBundleWriter patientBatchToCoreBundleWriter(CompartmentManager compartmentManager) {
        return new PatientBatchToCoreBundleWriter(compartmentManager);
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
        return new ConsentCodeMapper(torchProperties.mapping().consent(), objectMapper);
    }

    @Bean
    public DseMappingTreeBase dseMappingTreeBase(ObjectMapper jsonUtil) throws IOException {
        return new DseMappingTreeBase(Arrays.stream(jsonUtil.readValue(new File(torchProperties.dseMappingTreeFile()), DseTreeRoot[].class)).toList());
    }

    @Lazy
    @Bean
    Translator createCqlTranslator(ObjectMapper jsonUtil) throws IOException {
        var mappings = jsonUtil.readValue(new File(torchProperties.mappingsFile()), Mapping[].class);
        var mappingTreeBase = new MappingTreeBase(Arrays.stream(jsonUtil.readValue(new File(torchProperties.conceptTreeFile()), MappingTreeModuleRoot[].class)).toList());

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
                        entry("fdpg.mii.cds", "fdpgmiicds"),
                        entry("http://fhir.de/CodeSystem/bfarm/alpha-id", "alphaid"),
                        entry("urn:iso:std:iso:11073:10101", "ISO11073"),
                        entry("http://terminology.hl7.org/CodeSystem/icd-o-3", "icdo3"),
                        entry("http://fhir.de/CodeSystem/dkgev/Fachabteilungsschluessel", "fachabteilungsschluessel"),
                        entry("http://terminology.hl7.org/CodeSystem/v3-ActCode", "v3actcode"),
                        entry("http://fhir.de/CodeSystem/dkgev/Fachabteilungsschluessel-erweitert", "fachabteilungsschluesselerweitert"),
                        entry("http://fhir.de/CodeSystem/kontaktart-de", "kontaktart"),
                        entry("http://hl7.org/fhir/sid/icd-10", "sidicd10"),
                        entry("http://fhir.de/CodeSystem/Kontaktebene", "kontaktebene"),
                        entry("http://www.orpha.net", "orphanet"),
                        entry("fdpg.consent.combined", "fdpgcombinedconsent"),
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
    public ElementCopier elementCopier(FhirContext ctx) {
        return new ElementCopier(ctx);
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
    ConsentHandler handler(DataStore dataStore, ConsentFetcher consentFetcherBuilder) {
        return new ConsentHandler(dataStore, consentFetcherBuilder);
    }

    @Bean
    ConsentFetcher consentFetcherBuilder(DataStore dataStore, ConsentCodeMapper mapper, FhirContext ctx) {
        return new ConsentFetcher(dataStore, mapper, ctx);
    }

    @Bean
    ConsentValidator consentValidator(FhirContext ctx, ObjectMapper mapper, String consentToProfileFilePath) throws IOException {
        JsonNode resourcetoField = mapper.readTree(new File(consentToProfileFilePath).getAbsoluteFile());
        return new ConsentValidator(ctx, resourcetoField);
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();  // Assuming R4 version, configure as needed
    }

    @Bean
    public StructureDefinitionHandler cdsStructureDefinitionHandler(ResourceReader resourceReader) {
        return new StructureDefinitionHandler(new File(torchProperties.profile().dir()), resourceReader);
    }

    @Bean
    public ResultFileManager resultFileManager(FhirContext fhirContext) {
        return new ResultFileManager(torchProperties.results().dir(), torchProperties.results().persistence(), fhirContext, torchProperties.base().url(), torchProperties.output().file().server().url());
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


    @Bean
    ExchangeFilterFunction oauthExchangeFilterFunction(TorchProperties torchProperties) {
        String issuerUri = torchProperties.fhir().oauth().issuer().uri();
        String clientId = torchProperties.fhir().oauth().client().id();
        String clientSecret = torchProperties.fhir().oauth().client().secret();

        if (!issuerUri.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty()) {
            logger.info("Enabling OAuth2 authentication (issuer uri: '{}', client id: '{}').",
                    issuerUri, clientId);
            var clientRegistration = ClientRegistrations.fromIssuerLocation(issuerUri)
                    .registrationId(REGISTRATION_ID)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .authorizationGrantType(CLIENT_CREDENTIALS)
                    .build();
            var registrations = new InMemoryReactiveClientRegistrationRepository(clientRegistration);
            var clientService = new InMemoryReactiveOAuth2AuthorizedClientService(registrations);
            var authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    registrations, clientService);
            var oAuthExchangeFilterFunction = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
                    authorizedClientManager);
            oAuthExchangeFilterFunction.setDefaultClientRegistrationId(REGISTRATION_ID);

            return oAuthExchangeFilterFunction;
        } else {
            logger.info("Skipping OAuth2 authentication.");
            return (request, next) -> next.exchange(request);
        }
    }
}
