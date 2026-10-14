package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.management.OperationOutcomeCreator;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

/**
 * Fallback router that answers requests whose path matches a functional route, but whose HTTP method does not,
 * with {@code 405 Method Not Allowed}, an {@code Allow} header and an OperationOutcome. Without it, WebFlux
 * functional routing answers such requests with 404, indistinguishable from an unknown path.
 * <p>
 * The allowed methods are derived by re-evaluating all other {@link RouterFunction} beans with each candidate
 * method, so new routes are covered without registering them here.
 */
@Component
public class MethodNotAllowedRouter implements RouterFunction<ServerResponse> {

    private static final MediaType MEDIA_TYPE_FHIR_JSON = MediaType.valueOf("application/fhir+json");
    private static final List<HttpMethod> CANDIDATE_METHODS =
            List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private final FhirContext fhirContext;
    private final ObjectProvider<RouterFunction<?>> routers;

    public MethodNotAllowedRouter(FhirContext fhirContext, ObjectProvider<RouterFunction<?>> routers) {
        this.fhirContext = requireNonNull(fhirContext);
        this.routers = requireNonNull(routers);
    }

    /**
     * Completes empty if the request matches another route as-is or if its path matches no route under any
     * candidate method, leaving those requests to the regular handling. Independent of the router evaluation order.
     */
    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        List<RouterFunction<?>> others = routers.orderedStream().filter(router -> router != this).toList();
        return matches(others, request, request.method())
                .flatMap(matched -> matched ? Mono.<Set<HttpMethod>>empty() : allowedMethods(others, request))
                .filter(allowed -> !allowed.isEmpty())
                .map(allowed -> req -> methodNotAllowed(req, allowed));
    }

    private static Mono<Set<HttpMethod>> allowedMethods(List<RouterFunction<?>> routers, ServerRequest request) {
        return Flux.fromIterable(CANDIDATE_METHODS)
                .filterWhen(method -> matches(routers, request, method))
                .collect(toCollection(LinkedHashSet::new));
    }

    private static Mono<Boolean> matches(List<RouterFunction<?>> routers, ServerRequest request, HttpMethod method) {
        ServerRequest probe = ServerRequest.from(request).method(method).build();
        return Flux.fromIterable(routers)
                .concatMap(router -> router.route(probe))
                .hasElements();
    }

    private Mono<ServerResponse> methodNotAllowed(ServerRequest request, Set<HttpMethod> allowed) {
        String allowedNames = allowed.stream().map(HttpMethod::name).collect(Collectors.joining(", "));
        OperationOutcome outcome = OperationOutcomeCreator.simple(
                Severity.ERROR,
                OperationOutcome.IssueType.NOTSUPPORTED,
                "Method %s is not allowed on %s. Allowed: %s".formatted(request.method().name(), request.path(), allowedNames));
        return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(allowed)
                .contentType(MEDIA_TYPE_FHIR_JSON)
                .bodyValue(fhirContext.newJsonParser().encodeResourceToString(outcome));
    }
}
