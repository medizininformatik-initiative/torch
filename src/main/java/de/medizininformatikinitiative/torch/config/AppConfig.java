package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.DataStore;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.rest.CapabilityStatementController;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    @Bean
    @Qualifier("fhirClient")
    public WebClient fhirWebClient(@Value("${torch.fhir.url}") String baseUrl) {
        ConnectionProvider provider = ConnectionProvider.builder("data-store")
                .maxConnections(4)
                .pendingAcquireMaxCount(500)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/fhir+json");

        return builder.build();
    }

    @Bean
    @Qualifier("flareClient")
    public WebClient flareWebClient(@Value("${torch.flare.url}") String baseUrl) {
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
    public DataStore dataStore(@Qualifier("fhirClient") WebClient client, FhirContext context) {
        return new DataStore(client, context);  // Or use your specific configuration to instantiate
    }


    @Bean
    public ElementCopier elementCopier(CdsStructureDefinitionHandler cds) {
        return new ElementCopier(cds);  // Or use your specific configuration to instantiate
    }

    @Bean
    public BundleBuilder bundleBuilder(FhirContext context) {
        return new BundleBuilder(context);
    }

    @Bean
    public Redaction redaction(CdsStructureDefinitionHandler cds) {
        return new Redaction(cds);  // Or use your specific configuration to instantiate
    }

    @Bean
    public ResourceTransformer resourceTransformer(DataStore dataStore,CdsStructureDefinitionHandler cds, ResultFileManager fileManager) {
        return new ResourceTransformer(dataStore, cds,fileManager);
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();  // Assuming R4 version, configure as needed
    }

    @Bean
    public CdsStructureDefinitionHandler cdsStructureDefinitionHandler(FhirContext fhirContext, @Value("${torch.profile.dir}") String dir){
        return new CdsStructureDefinitionHandler(fhirContext, dir);
    }

    @Bean
    public IParser parser(FhirContext fhirContext) {
        return fhirContext.newJsonParser();
    }

    @Bean
    public ResultFileManager resultFileManager(@Value("${torch.results.dir}") String resultsDir, @Value("${torch.results.persistence}") String duration, IParser parser){
        return new ResultFileManager(resultsDir,duration, parser);
    }


    @Bean
    public CapabilityStatementController capabilityStatementController() {
        return new CapabilityStatementController();
    }

    @Bean
    public BundleCreator bundleCreator(){
        return new BundleCreator();
    }

    @Bean
    public ObjectMapper objectMapper(){
        return new ObjectMapper();
    }

    @Bean
    ExecutorService executorService(){
        return Executors.newCachedThreadPool();
    }


}
