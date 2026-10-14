package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.PUT;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

class MethodNotAllowedRouterTest {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");

    FhirContext fhirContext = FhirContext.forR4();
    WebTestClient client;

    @BeforeEach
    void setUp() {
        RouterFunction<ServerResponse> taskRoutes = route(GET("/fhir/Task/{id}"), request -> ServerResponse.ok().build())
                .andRoute(PUT("/fhir/Task/{id}"), request -> ServerResponse.ok().build())
                .andRoute(DELETE("/fhir/Task/{id}"), request -> ServerResponse.noContent().build())
                .andRoute(POST("/fhir/Task/{id}/$pause"), request -> ServerResponse.ok().build());
        RouterFunction<ServerResponse> extractRoutes = route(POST("/fhir/$extract-data").and(accept(MEDIA_TYPE_FHIR_JSON)),
                request -> ServerResponse.accepted().build());

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("taskRoutes", taskRoutes);
        beanFactory.addBean("extractRoutes", extractRoutes);
        ObjectProvider<RouterFunction<?>> routers = beanFactory.getBeanProvider(ResolvableType.forClass(RouterFunction.class));
        MethodNotAllowedRouter fallback = new MethodNotAllowedRouter(fhirContext, routers);
        beanFactory.addBean("methodNotAllowedRouter", fallback);

        // fallback first to show the result does not depend on router evaluation order
        client = WebTestClient.bindToRouterFunction(fallback.and(taskRoutes).and(extractRoutes)).build();
    }

    private OperationOutcome parseOutcome(byte[] body) {
        return fhirContext.newJsonParser().parseResource(OperationOutcome.class, new String(body));
    }

    @Test
    void wrongMethodOnOperationReturns405WithAllowAndOutcome() {
        byte[] body = client.get().uri("/fhir/Task/{id}/$pause", "123")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().valueEquals("Allow", "POST")
                .expectHeader().contentType(MEDIA_TYPE_FHIR_JSON)
                .expectBody().returnResult().getResponseBody();

        OperationOutcome outcome = parseOutcome(body);
        assertThat(outcome.getIssueFirstRep().getCode()).isEqualTo(OperationOutcome.IssueType.NOTSUPPORTED);
        assertThat(outcome.getIssueFirstRep().getDiagnostics())
                .isEqualTo("Method GET is not allowed on /fhir/Task/123/$pause. Allowed: POST");
    }

    @Test
    void allowListsAllMethodsOfPath() {
        client.patch().uri("/fhir/Task/{id}", "123")
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().valueEquals("Allow", "GET,PUT,DELETE");
    }

    @Test
    void wrongMethodOnRouteWithFurtherPredicatesReturns405() {
        client.get().uri("/fhir/$extract-data")
                .accept(MEDIA_TYPE_FHIR_JSON)
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().valueEquals("Allow", "POST");
    }

    @Test
    void unknownPathStillReturns404() {
        client.get().uri("/fhir/Unknown")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void matchingRequestIsHandledByItsRoute() {
        client.post().uri("/fhir/Task/{id}/$pause", "123")
                .exchange()
                .expectStatus().isOk();
        client.delete().uri("/fhir/Task/{id}", "123")
                .exchange()
                .expectStatus().isNoContent();
    }
}
