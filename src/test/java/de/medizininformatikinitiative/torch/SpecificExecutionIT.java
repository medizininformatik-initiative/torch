package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.assertions.Assertions;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static de.medizininformatikinitiative.torch.TestUtils.nodeFromValueString;
import static de.medizininformatikinitiative.torch.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
public class SpecificExecutionIT extends BaseExecutionIT {
    private static final Logger logger = LoggerFactory.getLogger(SpecificExecutionIT.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final FhirContext context = FhirContext.forR4();
    private static final IParser parser = context.newJsonParser();
    private static final ComposeContainer environment;

    private static final WebClient torchClient;
    private static final WebClient blazeClient;

    private static final String blazeHost;
    private static final int blazePort;
    private static final String torchHost;
    private static final int torchPort;
    private static final int nginxPort;
    private static final String torchOrigin;
    public static final int POLL_MAX_RETRIES = 10;
    public static final Duration POLL_RETRY_DELAY = Duration.ofSeconds(2);

    static {
        environment = new ComposeContainer(new File("src/test/resources/docker-compose.yml"))
                .withExposedService("blaze", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("blaze", new Slf4jLogConsumer(logger).withPrefix("blaze"))
                .withExposedService("torch", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("torch", new Slf4jLogConsumer(logger).withPrefix("torch"))
                .withExposedService("nginx", 8080, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                .withLogConsumer("nginx", new Slf4jLogConsumer(logger).withPrefix("nginx"));
        environment.start();
        blazeHost = environment.getServiceHost("blaze", 8080);
        blazePort = environment.getServicePort("blaze", 8080);
        torchHost = environment.getServiceHost("torch", 8080);
        torchPort = environment.getServicePort("torch", 8080);
        nginxPort = environment.getServicePort("nginx", 8080);

        torchOrigin = "%s:%d".formatted(torchHost, torchPort);
        ConnectionProvider provider = ConnectionProvider.builder("custom")
                .maxConnections(4)
                .build();
        HttpClient httpClient = HttpClient.create(provider);
        torchClient = WebClient.builder()
                .baseUrl("http://%s".formatted(torchOrigin))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/fhir+json")
                .defaultHeader("X-Forwarded-Host", torchOrigin)
                .build();

        var blazeUrl = "%s:%d".formatted(blazeHost, blazePort);
        ConnectionProvider blazeProvider = ConnectionProvider.builder("custom")
                .maxConnections(4)
                .build();
        HttpClient blazeHttpClient = HttpClient.create(blazeProvider);
        blazeClient = WebClient.builder()
                .baseUrl("http://%s/fhir".formatted(blazeUrl))
                .clientConnector(new ReactorClientHttpConnector(blazeHttpClient))
                .defaultHeader("Content-Type", "application/fhir+json")
                .defaultHeader("X-Forwarded-Host", blazeUrl)
                .build();
    }

    private static String slurp(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    static Set<String> uploadTestdata(String testName) throws IOException {
        logger.info("Uploading test data...for test {}", testName);
        var testdataFolder = "testdata/shorthand/fsh-generated/resources/";
        var testBundle = testdataFolder + "Bundle-test-" + testName + ".json";
        var bundleString = slurp(testBundle);
        blazeClient.post()
                .bodyValue(bundleString)
                .retrieve()
                .toBodilessEntity()
                .block();
        Bundle bundle = context.newJsonParser().parseResource(Bundle.class, bundleString);
        Set<String> resourceTypes = new HashSet<>();
        bundle.getEntry().forEach(entry -> resourceTypes.add(entry.getResource().getResourceType().name()));
        return resourceTypes;
    }

    private static TestUtils.TorchBundleUrls getOutputUrls(ResponseEntity<String> response) throws IOException {
        var body = mapper.readTree(response.getBody());
        var output = body.get("output");
        var coreBundle = "";
        var patientBundles = new LinkedList<String>();
        for (int i = 0; i < output.size(); i++) {
            var bundleUrl = URLDecoder.decode(mapper.writeValueAsString(output.get(i).get("url")), StandardCharsets.UTF_8);
            bundleUrl = bundleUrl.replace("8085", String.valueOf(nginxPort)).replace("\"", "");
            if (bundleUrl.contains("core.ndjson")) {
                coreBundle = bundleUrl;
            } else {
                patientBundles.add(bundleUrl);
            }
        }
        return new TestUtils.TorchBundleUrls(coreBundle, patientBundles);
    }

    private static String loadCrtdl(String testName) throws IOException {

        var crtdlTestFolder = "src/test/resources/CrtdlItTests/";
        var path = crtdlTestFolder + "CRTDL_test_" + testName + ".json";
        var crtdl = Base64.getEncoder().encodeToString(slurp(path).getBytes(StandardCharsets.UTF_8));

        var node = mapper.createObjectNode();
        var parameter = mapper.createObjectNode().put("name", "crtdl").put("valueBase64Binary", crtdl);

        node.put("resourceType", "Parameters");
        node.putArray("parameter").add(parameter);

        return mapper.writeValueAsString(node);
    }

    private static List<Bundle> fetchBundles(List<String> urls) {
        List<Bundle> bundles = new LinkedList<>();
        for (String url : urls) {
            var response = torchClient.get().uri(url).retrieve().toEntity(String.class).block();
            var scanner = new Scanner(response.getBody());
            while (scanner.hasNextLine()) {
                bundles.add((Bundle) parser.parseResource(scanner.nextLine()));
            }
        }
        return bundles;
    }

    private static HttpHeaders pollExtractRequest(String crtdlFile) throws IOException {
        var crtdl = loadCrtdl(crtdlFile);
        return torchClient.post()
                .uri("/fhir/$extract-data")
                .bodyValue(crtdl)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), ClientResponse::createException)
                .toEntity(String.class)
                .retryWhen(Retry.fixedDelay(POLL_MAX_RETRIES, POLL_RETRY_DELAY))
                .map(HttpEntity::getHeaders)
                .block();
    }

    private static TestUtils.TorchBundleUrls sendCrtdlAndGetOutputUrls(String crtdlFile) throws IOException {
        var statusUrl = pollExtractRequest(crtdlFile).get("Content-Location").getFirst();
        var bundleLocationResponse = torchClient.get().uri(statusUrl).retrieve().toEntity(String.class).block();
        return getOutputUrls(bundleLocationResponse);
    }


    public void executeStandardTests(List<Bundle> patientBundles, Bundle coreBundle) {

        // test if referenced IDs match the actual patient's IDs
        assertThat(patientBundles).allSatisfy(bundle -> Assertions.assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                Assertions.assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(condition ->
                        Assertions.assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));

        // test that IDs are properly formatted
        assertThat(patientBundles).allSatisfy(bundle ->
                assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(
                        r -> assertThat(r).extractChildrenStringsAt("subject.reference").satisfiesExactly(id -> assertThat(id).startsWith("Patient/"))
                ));
    }


    @Test
    public void testDoubleRefResolve() throws IOException {

        /*
        Diabetes Condition .encounter -> Encounter
        Diabetes Encounter .diagnosis -> Conditions not diabetes
        One diagnosis not referenced by encounter

        CRTDL to extract Diabetes Condition, the Encounter, the diagnoses referenced by the Diabetes encounter
        and all resources linked to Patient via .subject

        Should extract all resources except the not from ecnounter referenced condition "torch-test-diag-enc-diag-diag-3"
        All resources should link to the patient
         */

        var testName = "diag-enc-diag";
        var testData = uploadTestdata(testName);
        var bundleUrls = sendCrtdlAndGetOutputUrls(testName);
        var coreBundle = fetchBundles(List.of(bundleUrls.coreBundle())).getFirst();
        var patientBundles = fetchBundles(bundleUrls.patientBundles());

        var patJson = context.newJsonParser().encodeResourceToString(patientBundles.get(0));
        System.out.println(patJson);

        assertThat(coreBundle).containsNEntries(0);
        assertThat(patientBundles).hasSize(1);

        executeStandardTests(patientBundles, coreBundle);

        // all conditions in all patient bundles must have the given top fields
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(
                        r -> assertThat(r).extractTopElementNames()
                                .containsExactlyInAnyOrder("id", "resourceType", "meta", "clinicalStatus", "verificationStatus", "code", "subject", "recordedDate", "encounter")));

        // diab encounter included
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Encounter", "torch-test-diag-enc-diag-enc-1").isNotNull());

        // diab condition included
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "torch-test-diag-enc-diag-diag-1").isNotNull());

        // other linked condition included
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "torch-test-diag-enc-diag-diag-2").isNotNull());

        // other not linked condition not included
        assertThat(patientBundles).noneSatisfy(bundle ->
                assertThat(bundle).extractResourceById("Condition", "torch-test-diag-enc-diag-diag-3"));


        // condition with given id must have a data absent reason at its recordedDate (primitive type)
        /*assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-4-diagnose-1").isNotNull()
                        .hasDataAbsentReasonAt("recordedDate", "masked"));

         */

        cleanup(testData);
    }

    @Test
    public void testMustHaveResolve() throws IOException {

        /*

         */

        var testName = "diag-no-enc-diag";
        var testData = uploadTestdata(testName);
        var bundleUrls = sendCrtdlAndGetOutputUrls(testName);
        var coreBundle = fetchBundles(List.of(bundleUrls.coreBundle())).getFirst();
        var patientBundles = fetchBundles(bundleUrls.patientBundles());

        var patJson = context.newJsonParser().encodeResourceToString(patientBundles.get(0));

        System.out.println(patJson);
        System.out.println("Size: " + patientBundles.size());

        assertThat(coreBundle).containsNEntries(0);
        assertThat(patientBundles).hasSize(1);

        executeStandardTests(patientBundles, coreBundle);

        cleanup(testData);


    }

    @Test
    public void testExamples() throws IOException {
        /*
            In order for the tests to work locally, a torch image must be built:
            =>      mvn clean install -DskipTests && docker build -f Dockerfile -t torch:latest .
        */

        var bundleUrls = sendCrtdlAndGetOutputUrls("it-kds-crtdl");
        var coreBundle = fetchBundles(List.of(bundleUrls.coreBundle())).getFirst();
        var patientBundles = fetchBundles(bundleUrls.patientBundles());

        assertThat(coreBundle).containsNEntries(0);
        assertThat(patientBundles).hasSize(2);

        // test if referenced IDs match the actual patient's IDs
        assertThat(patientBundles).allSatisfy(bundle -> assertThat(bundle).extractOnlyPatient().satisfies(patient ->
                assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(condition ->
                        assertThat(condition).extractChildrenStringsAt("subject.reference").hasSize(1).first().isEqualTo(patient.getId()))));

        // this is equivalent to 'never exists' (and an example how to filter by resource type)
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(c -> assertThat(c).extractElementsAt("bodySite").isEmpty()));

        // opposite of 'never exists'
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(
                        r -> assertThat(r).extractElementsAt("code").isNotEmpty()
                ));

        // all conditions in all patient bundles must have the given top fields
        assertThat(patientBundles).allSatisfy(b -> assertThat(b).extractResourcesByType(ResourceType.Condition)
                .allSatisfy(
                        r -> assertThat(r).extractTopElementNames()
                                .containsExactlyInAnyOrder("resourceType", "subject", "meta", "_recordedDate", "code", "id")));

        // condition with given id must have a data absent reason at its recordedDate (primitive type)
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-4-diagnose-1").isNotNull()
                        .hasDataAbsentReasonAt("recordedDate", "masked"));

        // condition with given id must only have this one code
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-1-diagnose-2").isNotNull()
                        .extractElementsAt("code.coding.code").containsExactly(nodeFromValueString("H67.1")));

        // condition with given id must have the given codes (and not fewer or more codes)
        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-1-diagnose-1").isNotNull()
                        .extractElementsAt("code.coding.code")
                        .containsExactlyInAnyOrder(nodeFromValueString("B05.3"), nodeFromValueString("I29578"), nodeFromValueString("13420004")));

        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-3-diagnose-1").isNotNull()
                        .extractElementsAt("code.coding.code")
                        .containsExactlyInAnyOrder(nodeFromValueString("C21.8"), nodeFromValueString("447886005"), nodeFromValueString("I29975"), nodeFromValueString("8140/3")));

        assertThat(patientBundles).satisfiesOnlyOnce(bundle ->
                assertThat(bundle).extractResourceById("Condition", "mii-exa-test-data-patient-4-diagnose-1").isNotNull()
                        .extractElementsAt("code.coding.code")
                        .containsExactly(nodeFromValueString("C16.9")));

        // test that IDs are properly formatted
        assertThat(patientBundles).allSatisfy(bundle ->
                assertThat(bundle).extractResourcesByType(ResourceType.Condition).allSatisfy(
                        r -> assertThat(r).extractChildrenStringsAt("subject.reference").satisfiesExactly(id -> assertThat(id).startsWith("Patient/"))
                ));


    }

    static public void cleanup(Set<String> resourceTypes) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.setId(UUID.randomUUID().toString());

        logger.info(resourceTypes.toString());
        resourceTypes.forEach(resourceType -> {
            Bundle.BundleEntryComponent entryComponent = new Bundle.BundleEntryComponent();
            Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();

            request.setUrl("/" + resourceType + "?_lastUpdated=gt1900-01-01");
            request.setMethod(Bundle.HTTPVerb.DELETE);
            entryComponent.setRequest(request);
            bundle.addEntry(entryComponent);
        });
        var bundleString = context.newJsonParser().encodeToString(bundle);
        logger.info(bundleString);
        blazeClient.post()
                .bodyValue(bundleString)
                .retrieve()
                .toBodilessEntity()
                .block();

    }


    @AfterAll
    public static void cleanup() {
        environment.stop();
    }
}
