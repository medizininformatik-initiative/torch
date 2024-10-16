package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.cql.FhirConnector;
import de.medizininformatikinitiative.torch.cql.FhirHelper;
import de.medizininformatikinitiative.torch.*;
import de.medizininformatikinitiative.torch.rest.CapabilityStatementController;
import de.medizininformatikinitiative.torch.rest.FhirController;
import de.medizininformatikinitiative.torch.util.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
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
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    @Value("${torch.mappingsFile}")
    private String mappingsFile;
    @Value("${torch.conceptTreeFile}")
    private String conceptTreeFile;

    @Bean
    @Qualifier("fhirClient")
    public WebClient fhirWebClient(@Value("${torch.fhir.url}") String baseUrl,
                                   @Value("${torch.fhir.user}") String user,
                                   @Value("${torch.fhir.password}") String password,
                                   @Qualifier("oauth") ExchangeFilterFunction oauthExchangeFilterFunction) {
        logger.info("Initializing FHIR WebClient with URL: {}", baseUrl);

        ConnectionProvider provider = ConnectionProvider.builder("data-store")
                .maxConnections(4)
                .pendingAcquireMaxCount(500)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json");

        if (!user.isEmpty() && !password.isEmpty()) {
            builder = builder.filter(ExchangeFilterFunctions.basicAuthentication(user, password));
            logger.info("Added basic authentication for user: {}", user);
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
    public ConsentCodeMapper consentCodeMapper(  @Value("${torch.mapping.consent}") String consentFilePath) throws IOException {
        return new ConsentCodeMapper(consentFilePath);
    }

    @Bean
    public DataStore dataStore(@Qualifier("fhirClient") WebClient client, FhirContext context, @Qualifier("systemDefaultZone") Clock clock,
                               @Value("${torch.fhir.pageCount}") int pageCount) {
        return new DataStore(client, context, clock, pageCount);

    }

    @Lazy
    @Bean
    Translator createCqlTranslator(@Qualifier("translation") ObjectMapper jsonUtil) throws IOException {
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
            FhirConnector fhirConnector,
            FhirHelper fhirHelper,
            DataStore dataStore
    ) {

        return new CqlClient(fhirConnector, fhirHelper, dataStore);
    }

    @Qualifier("translation")
    @Bean
    ObjectMapper createTranslationObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ElementCopier elementCopier(CdsStructureDefinitionHandler cds) {
        return new ElementCopier(cds);
    }

    @Bean
    public BundleBuilder bundleBuilder(FhirContext context) {
        return new BundleBuilder(context);
    }

    @Bean
    public Redaction redaction(CdsStructureDefinitionHandler cds) {
        return new Redaction(cds);
    }

    @Bean
    public ResourceTransformer resourceTransformer(DataStore dataStore, CdsStructureDefinitionHandler cds, ConsentHandler handler) {
        return new ResourceTransformer(dataStore, cds, handler);
    }

    @Bean
    ConsentHandler handler(DataStore dataStore, CdsStructureDefinitionHandler cds, ConsentCodeMapper mapper, @Value("${torch.mapping.consent_to_profile}") String consentFilePath) throws IOException {
        return new ConsentHandler(dataStore, mapper,consentFilePath,cds);
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();  // Assuming R4 version, configure as needed
    }

    @Bean
    public CdsStructureDefinitionHandler cdsStructureDefinitionHandler(FhirContext fhirContext, @Value("${torch.profile.dir}") String dir) {
        return new CdsStructureDefinitionHandler(dir);
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
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
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
    @Qualifier("oauth")
    ExchangeFilterFunction oauthExchangeFilterFunction(
            @Value("${torch.fhir.oauth.issuer.uri:}") String issuerUri,
            @Value("${torch.fhir.oauth.client.id:}") String clientId,
            @Value("${torch.fhir.oauth.client.secret:}") String clientSecret) {
        if (!issuerUri.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty()) {
            logger.debug("Enabling OAuth2 authentication (issuer uri: '{}', client id: '{}').",
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
            logger.debug("Skipping OAuth2 authentication.");
            return (request, next) -> next.exchange(request);
        }
    }
}
