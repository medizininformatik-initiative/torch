package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ch.qos.logback.classic.Level;
import de.medizininformatikinitiative.torch.exceptions.DataStoreException;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static de.medizininformatikinitiative.torch.service.DataStoreIT.createBundleFromQuery;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.ResourceType.MeasureReport;
import static org.hl7.fhir.r4.model.ResourceType.Patient;

class DataStoreTest {

    private static final String PATIENT_BUNDLE = """
            {
              "resourceType": "Bundle",
              "type": "searchset",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "123"
                  }
                }
              ]
            }
            """;


    private static final String BATCH_RESPONSE = """
            {                "resourceType": "Bundle",
                             "type": "batch-response",
                             "entry": [
                               {
                                 "response": {
                                   "status": "200"
                                 },
                                 "resource": {
                                  "resourceType": "Bundle",
                                   "type": "searchset",
                                   "entry": [
                                     {
                                       "resource": {
                                         "resourceType": "Patient",
                                         "id": "1",
                                         "gender": "male"
                                       },
                                       "search": {
                                         "mode": "match"
                                       }
                                     }
                                   ]
                                 }
                               },
                               {
                                 "response": {
                                   "status": "200"
                                 },
                                 "resource": {
                                   "resourceType": "Bundle",
                                   "type": "searchset"
            
                                 }
                               }
                             ]
                           }
            """;


    private static final String OPERATION_OUTCOME = """
            {
                           "resourceType": "OperationOutcome",
                           "issue": [
                             {
                               "severity": "error",
                               "code": "not-found",
                               "diagnostics": "The search-param with code `test` and type `Observation` was not found."
                             }
                           ]
                         }
            """;

    private static final String UNSUPPORTED_RESOURCE_TYPE = """
            {
              "resourceType" : "Condition",
              "id" : "example",
              "text" : {
                "status" : "generated",
                "div" : "<div xmlns=\\"http://www.w3.org/1999/xhtml\\">Severe burn of left ear (Date: 24-May 2012)</div>"
              }
            }
            """;

    private static final String PATIENT_BUNDLE_WITH_NEXT = """
            {
              "resourceType": "Bundle",
              "type": "searchset",
              "link": [
                {
                  "relation": "next",
                  "url": "%s"
                }
              ],
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "p1"
                  }
                }
              ]
            }
            """;

    private static final String PATIENT_BUNDLE_LAST_PAGE = """
            {
              "resourceType": "Bundle",
              "type": "searchset",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "p2"
                  }
                }
              ]
            }
            """;

    private MockWebServer mockStore;
    private String baseUrl;
    private WebClient client;
    private FhirContext ctx;
    private DataStore dataStore;

    private static String slurp(String name) throws IOException {
        try (InputStream stream = DataStoreTest.class.getResourceAsStream(name)) {
            return new String(requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DataStore.class)).setLevel(Level.TRACE);
        mockStore = new MockWebServer();
        mockStore.start();
        ctx = FhirContext.forR4();
        baseUrl = "http://localhost:%d/fhir".formatted(mockStore.getPort());
        client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/fhir+json")
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockStore.shutdown();
    }

    @Nested
    class Search {

        @BeforeEach
        void setUp() {
            dataStore = new DataStore(client, ctx, 1000, false);
        }

        @Test
        @DisplayName("doesn't retry a 400")
        void errorNoRetry() {
            mockStore.enqueue(new MockResponse().setResponseCode(400));

            var result = dataStore.search(Query.ofType("Observation"), Observation.class);

            StepVerifier.create(result).verifyError(WebClientResponseException.BadRequest.class);
        }

        @Test
        void emptyResult() {
            mockStore.enqueue(new MockResponse().setResponseCode(200));

            var result = dataStore.search(Query.ofType("Observation"), Observation.class);

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        void fullResult() {
            mockStore.enqueue(new MockResponse().setResponseCode(200).setBody(PATIENT_BUNDLE));

            var result = dataStore.search(Query.ofType("Patient"), Patient.class);

            StepVerifier.create(result).expectNextMatches(resource -> resource.getResourceType() == Patient).verifyComplete();
        }

        @Test
        void operationOutcomeReturnsError() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody(OPERATION_OUTCOME));

            var result = dataStore.search(Query.ofType("Patient"), Patient.class);

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("OperationOutcome")).verify();
        }

        @Test
        void unsupportedResourceType() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody(UNSUPPORTED_RESOURCE_TYPE));

            var result = dataStore.search(Query.ofType("Patient"), Patient.class);

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("Unexpected resource type")).verify();
        }

        @Test
        @DisplayName("retries on prematurely closed connection for the first page")
        void retriesOnPrematureClose_firstPage() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody(PATIENT_BUNDLE)); // body won't fully arrive due to disconnect
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody(PATIENT_BUNDLE));

            var result = dataStore.search(Query.ofType("Patient"), Patient.class);

            StepVerifier.create(result)
                    .expectNextMatches(p -> p.getIdElement().getIdPart().equals("123"))
                    .verifyComplete();
        }

        @ParameterizedTest
        @ValueSource(ints = {404, 500})
        @DisplayName("retries at least 3 unsuccessful calls")
        void retrySuccess(int statusCode) {
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(200));

            var result = dataStore.search(Query.ofType("Patient"), Patient.class);

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("paging: retries only the failing next-page request (does not redo first page)")
        void pagingRetriesOnlyNextPage() throws InterruptedException {
            // next-link must be an absolute URL because fetchPage(url) uses .uri(url)
            String nextUrl = baseUrl + "/Patient?page=2";

            String page1 = PATIENT_BUNDLE_WITH_NEXT.formatted(nextUrl);
            String page2 = PATIENT_BUNDLE_LAST_PAGE;

            var nextPageAttempts = new AtomicInteger(0);

            mockStore.setDispatcher(new Dispatcher() {
                @Override
                public @NonNull MockResponse dispatch(@NonNull RecordedRequest request) {
                    assert request.getPath() != null;

                    // First page: POST /fhir/Patient/_search

                    if ("POST".equals(request.getMethod()) && request.getPath().equals("/fhir/Patient/_search")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/fhir+json")
                                .setBody(page1);
                    }

                    // Next page: GET /fhir/Patient?page=2  (first attempt disconnects)
                    if ("GET".equals(request.getMethod()) && request.getPath().equals("/fhir/Patient?page=2")) {
                        if (nextPageAttempts.getAndIncrement() == 0) {
                            return new MockResponse()
                                    .setResponseCode(200)
                                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                                    .setHeader("Content-Type", "application/fhir+json")
                                    .setBody(page2);
                        }
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/fhir+json")
                                .setBody(page2);
                    }

                    return new MockResponse().setResponseCode(404);
                }
            });

            var result = dataStore.search(Query.ofType("Patient"), Patient.class);

            StepVerifier.create(result)
                    .expectNextMatches(p -> p.getIdElement().getIdPart().equals("p1"))
                    .expectNextMatches(p -> p.getIdElement().getIdPart().equals("p2"))
                    .verifyComplete();

            // Assert request pattern: 1x POST first page, 2x GET next page (disconnect + retry)
            assertThat(mockStore.getRequestCount()).isEqualTo(3);

            RecordedRequest r1 = mockStore.takeRequest();
            RecordedRequest r2 = mockStore.takeRequest();
            RecordedRequest r3 = mockStore.takeRequest();

            assertThat(r1.getMethod()).isEqualTo("POST");
            assertThat(r1.getPath()).isEqualTo("/fhir/Patient/_search");

            assertThat(r2.getMethod()).isEqualTo("GET");
            assertThat(r2.getPath()).isEqualTo("/fhir/Patient?page=2");

            assertThat(r3.getMethod()).isEqualTo("GET");
            assertThat(r3.getPath()).isEqualTo("/fhir/Patient?page=2");
        }

    }

    @Nested
    class FetchReferencesByBatch {

        @BeforeEach
        void setUp() {
            dataStore = new DataStore(client, ctx, 1000, false);
        }

        @Test
        @DisplayName("executeSearchBatch retries on prematurely closed connection")
        void batchRetriesOnPrematureClose() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody(BATCH_RESPONSE)); // body won't fully arrive due to disconnect

            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody(BATCH_RESPONSE));

            var result = dataStore.executeBundle(createBundleFromQuery("Patient?_id=1,2"));

            StepVerifier.create(result)
                    .expectNextMatches(resources ->
                            resources.size() == 1
                                    && resources.getFirst().getResourceType() == Patient
                                    && resources.getFirst().getIdElement().getIdPart().equals("1"))
                    .verifyComplete();
        }

        @ParameterizedTest
        @ValueSource(ints = {404, 500})
        @DisplayName("retries at least 3 unsuccessful calls")
        void retrySuccess(int statusCode) {
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(200));

            var result = dataStore.executeBundle(createBundleFromQuery("Patient?_id=1,2"));

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("doesn't retry a 400")
        void errorNoRetry() {
            mockStore.enqueue(new MockResponse().setResponseCode(400));

            var result = dataStore.executeBundle(createBundleFromQuery("Patient?_id=1,2"));

            StepVerifier.create(result).verifyError(WebClientResponseException.BadRequest.class);
        }


    }

    @Nested
    class Transact {

        @BeforeEach
        void setUp() {
            dataStore = new DataStore(client, ctx, 1000, false);
        }

        private static Bundle transactionBundle() {
            var bundle = new Bundle();
            bundle.setType(Bundle.BundleType.TRANSACTION);
            return bundle;
        }

        @Test
        @DisplayName("doesn't retry a 400")
        void errorNoRetry() {
            mockStore.enqueue(new MockResponse().setResponseCode(400));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result).verifyError(WebClientResponseException.BadRequest.class);
        }

        @Test
        @DisplayName("shortens an overly long response body in the error message")
        void shortensLongResponseBody() {
            String longBody = "x".repeat(600);
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/plain")
                    .setBody(longBody));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> {
                        assertThat(e).isInstanceOf(DataStoreException.class);
                        assertThat(e.getMessage()).contains("...").doesNotContain(longBody);
                    })
                    .verify();
        }

        @Test
        @DisplayName("fails when an authenticating proxy returns a non-FHIR 200 response")
        void failsOnNonFhirResponse() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"type":"redirect","status":307,"location":"/fhir/__sign-in?redirect=..."}
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("HTTP status 200")
                            .hasMessageContaining("application/json"))
                    .verify();
        }

        @Test
        @DisplayName("fails when an authenticating proxy returns an HTML sign-in page as a 200 response")
        void failsOnHtmlRedirectResponse() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><head><title>Sign in</title></head>"
                            + "<body>Please <a href=\"/fhir/__sign-in?redirect=...\">sign in</a></body></html>"));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("HTTP status 200")
                            .hasMessageContaining("text/html"))
                    .verify();
        }

        @Test
        @DisplayName("fails when the response body is empty")
        void failsOnEmptyResponse() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json"));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("HTTP status 200"))
                    .verify();
        }

        @Test
        @DisplayName("fails when the response bundle is not a transaction-response")
        void failsOnWrongBundleType() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {"resourceType":"Bundle","type":"searchset"}
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("transaction-response"))
                    .verify();
        }

        @Test
        @DisplayName("fails when the response body is a valid FHIR resource that is not a Bundle")
        void failsOnNonBundleResource() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {"resourceType":"Patient","id":"1"}
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("transaction-response"))
                    .verify();
        }

        @Test
        @DisplayName("fails when the transaction-response has no entries")
        void failsOnEmptyEntries() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {"resourceType":"Bundle","type":"transaction-response","entry":[]}
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("no entries"))
                    .verify();
        }

        @Test
        @DisplayName("fails when a transaction entry did not succeed")
        void failsOnFailedEntry() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {
                              "resourceType": "Bundle",
                              "type": "transaction-response",
                              "entry": [
                                { "response": { "status": "400 Bad Request" } }
                              ]
                            }
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("400"))
                    .verify();
        }

        @Test
        @DisplayName("fails when a transaction entry has no response element")
        void failsOnEntryMissingResponse() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {
                              "resourceType": "Bundle",
                              "type": "transaction-response",
                              "entry": [
                                { "resource": { "resourceType": "Library", "id": "1" } }
                              ]
                            }
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("missing"))
                    .verify();
        }

        @Test
        @DisplayName("fails when a transaction entry's response has no status")
        void failsOnEntryResponseMissingStatus() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {
                              "resourceType": "Bundle",
                              "type": "transaction-response",
                              "entry": [
                                { "response": { "location": "Library/1" } }
                              ]
                            }
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(DataStoreException.class))
                    .verify();
        }

        @Test
        @DisplayName("fails when a transaction entry status is below 200")
        void failsOnEntryStatusBelow200() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {
                              "resourceType": "Bundle",
                              "type": "transaction-response",
                              "entry": [
                                { "response": { "status": "100 Continue" } }
                              ]
                            }
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("100"))
                    .verify();
        }

        @Test
        @DisplayName("fails when a transaction entry status is not numeric")
        void failsOnNonNumericEntryStatus() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {
                              "resourceType": "Bundle",
                              "type": "transaction-response",
                              "entry": [
                                { "response": { "status": "unknown" } }
                              ]
                            }
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result)
                    .expectErrorSatisfies(e -> assertThat(e)
                            .isInstanceOf(DataStoreException.class)
                            .hasMessageContaining("unknown"))
                    .verify();
        }

        @Test
        @DisplayName("succeeds on a valid transaction-response with successful entries")
        void success() {
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/fhir+json")
                    .setBody("""
                            {
                              "resourceType": "Bundle",
                              "type": "transaction-response",
                              "entry": [
                                { "response": { "status": "201 Created" } },
                                { "response": { "status": "201 Created" } }
                              ]
                            }
                            """));

            var result = dataStore.transact(transactionBundle());

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    class EvaluateMeasure {

        private String responseSuccess;
        private String batchResponseSuccess;
        private String batchResponseError;
        private String batchResponseErrorDiagnosis;

        private static Parameters params(String measureUri) {
            var parameters = new Parameters();
            parameters.setParameter("measure", measureUri);
            return parameters;
        }

        @BeforeEach
        void setUp() throws IOException {
            responseSuccess = slurp("evaluate-measure-response-success.json");
            batchResponseSuccess = slurp("evaluate-measure-batch-response-success.json");
            batchResponseError = slurp("evaluate-measure-batch-response-error.json");
            batchResponseErrorDiagnosis = slurp("evaluate-measure-batch-response-error-diagnosis.json");
        }

        private Dispatcher dispatcher(String response, String batchResponse) {
            return dispatcher(response, batchResponse, new AtomicInteger(0));
        }

        private Dispatcher dispatcher(String response, String batchResponse, AtomicInteger numRequiredAsyncPolls) {
            return new Dispatcher() {

                @Override
                public @NonNull MockResponse dispatch(@NonNull RecordedRequest request) {
                    assert request.getPath() != null;
                    return switch (request.getPath()) {
                        case "/fhir/Measure/$evaluate-measure" -> evaluateMeasureResponse(request, response);
                        case "/fhir/__async-status/152858" -> numRequiredAsyncPolls.getAndDecrement() == 0
                                ? new MockResponse().setResponseCode(200).setBody(batchResponse)
                                : new MockResponse().setResponseCode(202);
                        default -> new MockResponse().setResponseCode(404);
                    };
                }
            };
        }

        private Dispatcher statusEndpointGoneDispatcher(String response) {
            return new Dispatcher() {

                @Override
                public @NonNull MockResponse dispatch(@NonNull RecordedRequest request) {
                    return "/fhir/Measure/$evaluate-measure".equals(request.getPath())
                            ? evaluateMeasureResponse(request, response)
                            : new MockResponse().setResponseCode(410);
                }
            };
        }

        private Dispatcher statusEndpointOneInternalServerErrorDispatcher(String response, String batchResponse) {
            var asyncPollIndex = new AtomicInteger(0);
            return new Dispatcher() {

                @Override
                public @NonNull MockResponse dispatch(@NonNull RecordedRequest request) {
                    assert request.getPath() != null;
                    return switch (request.getPath()) {
                        case "/fhir/Measure/$evaluate-measure" -> evaluateMeasureResponse(request, response);
                        case "/fhir/__async-status/152858" -> switch (asyncPollIndex.getAndIncrement()) {
                            case 0, 2 -> new MockResponse().setResponseCode(202);
                            case 1 -> new MockResponse().setResponseCode(500);
                            default -> new MockResponse().setResponseCode(200).setBody(batchResponse);
                        };
                        default -> new MockResponse().setResponseCode(404);
                    };
                }
            };
        }

        private MockResponse evaluateMeasureResponse(RecordedRequest request, String response) {
            return "respond-async,return=representation".equals(request.getHeader("Prefer"))
                    ? new MockResponse().setResponseCode(202).setHeader("Content-Location", baseUrl + "/__async-status/152858")
                    : new MockResponse().setResponseCode(200).setBody(response);
        }

        @Nested
        @DisplayName("GroupReferencesByTypeInChunks")
        class GroupReferencesByTypeInChunks {

            private DataStore dataStore;

            @BeforeEach
            void setUp() {
                // fresh instance, owned by THIS nested class
                dataStore = new DataStore(
                        client,             // same MockWebServer client
                        ctx,                // same FhirContext
                        4,                  // pageCount under test
                        true                // disableAsync → stable, no Prefer header
                );
            }

            @Test
            void withoutChunking() {
                var chunks = dataStore.groupReferencesByTypeInChunks(ExtractionId.of(
                        "Observation/123",
                        "Patient/2",
                        "Patient/1",
                        "Medication/123"
                ));

                assertThat(chunks).containsExactly(
                        Map.of(
                                "Observation", Set.of("123"),
                                "Patient", Set.of("2", "1"),
                                "Medication", Set.of("123")
                        )
                );
            }

            @Test
            void withChunking() {
                var chunks = dataStore.groupReferencesByTypeInChunks(ExtractionId.of(
                        "Observation/123",
                        "Patient/2",
                        "Patient/1",
                        "Medication/123",
                        "MedicationAdministration/4"
                ));

                assertThat(chunks).containsExactly(
                        Map.of(
                                "Observation", Set.of("123"),
                                "MedicationAdministration", Set.of("4"),
                                "Medication", Set.of("123"),
                                "Patient", Set.of("1")
                        ),
                        Map.of(
                                "Patient", Set.of("2")
                        )
                );
            }
        }

        @Nested
        @DisplayName("with default enabled asynchronous requests")
        class Async {

            @BeforeEach
            void setUp() {
                dataStore = new DataStore(client, ctx, 1000, false);
            }

            @Test
            void failsOnOperationNotFound() {
                mockStore.enqueue(new MockResponse().setResponseCode(404));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result).expectError(WebClientResponseException.NotFound.class).verify();
            }

            @Test
            void failsOnStatusEndpointGone() {
                mockStore.setDispatcher(statusEndpointGoneDispatcher(responseSuccess));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result).expectError().verify();
            }

            @Test
            void failsOnErrorInResponseBundle() {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseError));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result)
                        .expectErrorSatisfies(e -> assertThat(e)
                                .isInstanceOf(OutcomeException.class)
                                .hasMessage("unknown error"))
                        .verify();
            }

            @Test
            void failsOnErrorInResponseBundleWithDiagnosis() {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseErrorDiagnosis));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result)
                        .expectErrorSatisfies(e -> assertThat(e)
                                .isInstanceOf(OutcomeException.class)
                                .hasMessage("msg-093723"))
                        .verify();
            }

            @Test
            void successOnStatusEndpointOneInternalServerError() {
                mockStore.setDispatcher(statusEndpointOneInternalServerErrorDispatcher(responseSuccess, batchResponseSuccess));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result)
                        .expectNextMatches(resource -> resource.getResourceType() == MeasureReport)
                        .verifyComplete();
            }

            @ParameterizedTest()
            @ValueSource(ints = {0, 1, 2, 5})
            void success(int numRequiredAsyncPolls) {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseSuccess, new AtomicInteger(numRequiredAsyncPolls)));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result)
                        .expectNextMatches(resource -> resource.getResourceType() == MeasureReport)
                        .verifyComplete();
            }
        }

        @Nested
        @DisplayName("with disabled asynchronous requests")
        class DisabledAsync {

            @BeforeEach
            void setUp() {
                dataStore = new DataStore(client, ctx, 1000, true);
            }

            @Test
            void failsOnOperationNotFound() {
                mockStore.enqueue(new MockResponse().setResponseCode(404));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result).expectError(WebClientResponseException.NotFound.class).verify();
            }

            @Test
            void failsOnOperationInternalServerError() {
                mockStore.enqueue(new MockResponse().setResponseCode(500));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result).expectError(WebClientResponseException.InternalServerError.class).verify();
            }

            @Test
            void success() {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseSuccess));

                var result = dataStore.evaluateMeasure(params("uri-152349"));

                StepVerifier.create(result)
                        .expectNextMatches(resource -> resource.getResourceType() == MeasureReport)
                        .verifyComplete();
            }
        }
    }
}
