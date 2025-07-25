package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ch.qos.logback.classic.Level;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.jetbrains.annotations.NotNull;
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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.ResourceType.MeasureReport;
import static org.hl7.fhir.r4.model.ResourceType.Patient;

class DataStoreTest {

    private static final String INVALID_RESOURCE = """
            {
              "resourceType": "Invalid"
            }
            """;

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

        @ParameterizedTest
        @ValueSource(ints = {404, 500})
        @DisplayName("fails after 5 unsuccessful retries")
        void retryFails(int statusCode) {
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(200));

            var result = dataStore.search(Query.ofType("Observation"), Observation.class);

            StepVerifier.create(result).verifyErrorMessage("Retries exhausted: 5/5");
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
        void emptyResultAfterParsingError() {
            mockStore.enqueue(new MockResponse().setResponseCode(200).setBody(INVALID_RESOURCE));

            var result = dataStore.search(Query.ofType("Patient"), Patient.class);

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    class FetchReferencesByBatch {

        @BeforeEach
        void setUp() {
            dataStore = new DataStore(client, ctx, 1000, false);
        }

        @Test
        @DisplayName("Fetch with retry")
        void fetchWithRetry() {
            mockStore.enqueue(new MockResponse().setResponseCode(500));
            mockStore.enqueue(new MockResponse().setResponseCode(500));
            mockStore.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(BATCH_RESPONSE));


            var result = dataStore.executeSearchBatch(Map.of("Patient", Set.of("1", "2")));

            StepVerifier.create(result)
                    .expectNextMatches(resources ->
                            resources.size() == 1 && resources.getFirst().getResourceType() == Patient && resources.getFirst().getIdElement().getIdPart().equals("1"))
                    .expectComplete()
                    .verify();
        }

        @Test
        void emptyResultAfterParsingError() {
            mockStore.enqueue(new MockResponse().setResponseCode(200).setBody(INVALID_RESOURCE));

            var result = dataStore.executeSearchBatch(Map.of("Patient", Set.of("1", "2")));

            StepVerifier.create(result).verifyComplete();
        }

        @ParameterizedTest
        @ValueSource(ints = {404, 500})
        @DisplayName("fails after 5 unsuccessful retries")
        void retryFails(int statusCode) {
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(statusCode));
            mockStore.enqueue(new MockResponse().setResponseCode(200));

            var result = dataStore.executeSearchBatch(Map.of("Patient", Set.of("1", "2")));

            StepVerifier.create(result).verifyErrorMessage("Retries exhausted: 5/5");
        }

        @Test
        @DisplayName("doesn't retry a 400")
        void errorNoRetry() {
            mockStore.enqueue(new MockResponse().setResponseCode(400));

            var result = dataStore.executeSearchBatch(Map.of("Patient", Set.of("1", "2")));

            StepVerifier.create(result).verifyError(WebClientResponseException.BadRequest.class);
        }

    }

    @Nested
    class EvaluateMeasure {

        private String responseSuccess;
        private String batchResponseSuccess;
        private String batchResponseError;
        private String batchResponseErrorDiagnosis;

        private static Parameters params() {
            var parameters = new Parameters();
            parameters.setParameter("measure", "uri-152349");
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

                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
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

        private Dispatcher statusEndpointNotFoundDispatcher(String response) {
            return new Dispatcher() {

                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    return "/fhir/Measure/$evaluate-measure".equals(request.getPath())
                            ? evaluateMeasureResponse(request, response)
                            : new MockResponse().setResponseCode(404);
                }
            };
        }

        private Dispatcher statusEndpointOneInternalServerErrorDispatcher(String response, String batchResponse) {
            var asyncPollIndex = new AtomicInteger(0);
            return new Dispatcher() {

                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
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
        @DisplayName("with default enabled asynchronous requests")
        class Async {

            @BeforeEach
            void setUp() {
                dataStore = new DataStore(client, ctx, 1000, false);
            }

            @Test
            void failsOnOperationNotFound() {
                mockStore.enqueue(new MockResponse().setResponseCode(404));

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result).expectError(WebClientResponseException.NotFound.class).verify();
            }

            @Test
            void failsOnStatusEndpointNotFound() {
                mockStore.setDispatcher(statusEndpointNotFoundDispatcher(responseSuccess));

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result).expectError().verify();
            }

            @Test
            void failsOnErrorInResponseBundle() {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseError));

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result)
                        .expectErrorSatisfies(e -> assertThat(e)
                                .isInstanceOf(OutcomeException.class)
                                .hasMessage("unknown error"))
                        .verify();
            }

            @Test
            void failsOnErrorInResponseBundleWithDiagnosis() {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseErrorDiagnosis));

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result)
                        .expectErrorSatisfies(e -> assertThat(e)
                                .isInstanceOf(OutcomeException.class)
                                .hasMessage("msg-093723"))
                        .verify();
            }

            @Test
            void successOnStatusEndpointOneInternalServerError() {
                mockStore.setDispatcher(statusEndpointOneInternalServerErrorDispatcher(responseSuccess, batchResponseSuccess));

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result)
                        .expectNextMatches(resource -> resource.getResourceType() == MeasureReport)
                        .verifyComplete();
            }

            @ParameterizedTest()
            @ValueSource(ints = {0, 1, 2, 5})
            void success(int numRequiredAsyncPolls) {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseSuccess, new AtomicInteger(numRequiredAsyncPolls)));

                var result = dataStore.evaluateMeasure(params());

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

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result).expectError(WebClientResponseException.NotFound.class).verify();
            }

            @Test
            void failsOnOperationInternalServerError() {
                mockStore.enqueue(new MockResponse().setResponseCode(500));

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result).expectError(WebClientResponseException.InternalServerError.class).verify();
            }

            @Test
            void success() {
                mockStore.setDispatcher(dispatcher(responseSuccess, batchResponseSuccess));

                var result = dataStore.evaluateMeasure(params());

                StepVerifier.create(result)
                        .expectNextMatches(resource -> resource.getResourceType() == MeasureReport)
                        .verifyComplete();
            }
        }
    }
}
