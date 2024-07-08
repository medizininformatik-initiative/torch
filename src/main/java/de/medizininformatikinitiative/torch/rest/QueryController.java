package de.medizininformatikinitiative.torch.rest;

import de.medizininformatikinitiative.torch.BundleCreator;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import de.medizininformatikinitiative.torch.model.Crtdl;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;


public class QueryController {

    private static final MediaType MEDIA_TYPE_CRTDL = MediaType.valueOf("application/crtdl+json");

    private static final MediaType MEDIA_TYPE_FHIR = MediaType.valueOf("application/fhir+json");

    private static final Logger logger = LoggerFactory.getLogger(QueryController.class);


    private WebClient webClient;


    private int batchSize;


    BundleCreator bundleCreator;


    private ResourceTransformer transformer;

    public RouterFunction<ServerResponse> queryRouter() {
        return route(POST("/query/execute").and(accept(MEDIA_TYPE_CRTDL)), this::execute);
    }

    public Mono<ServerResponse> execute(ServerRequest request) {
        var startNanoTime = System.nanoTime();
        logger.debug("Execute CRTDL query");

        return request.bodyToMono(Crtdl.class)
                .flatMap(crtdl -> {
                    // Perform any necessary transformation or processing
                    logger.debug("Received CRTDL object: {}", crtdl);

                    //TODO: Get CRTDL and CCDL query
                    //TODO: Call Cohort Endpoint with CCDL to get patients
                    List<String> patients = List.of(); // Initialize appropriately

                    Mono<Map<String, Collection<Resource>>> collectedResourcesMono = transformer.collectResourcesByPatientReference(crtdl, patients, batchSize);

                    return collectedResourcesMono.flatMap(collectedResources -> {
                        //TODO: Collect Resources Bundles by Patient

                        Map<String, Bundle> bundles = bundleCreator.createBundles(collectedResources);


                        // Return a response, e.g., the transformed object as JSON
                        return ServerResponse.ok().contentType(MEDIA_TYPE_FHIR).bodyValue(bundles);
                    }).doOnError(error -> logger.error("Error collecting resources by patient", error));
                })
                .doOnError(error -> logger.error("Error processing CRTDL query", error))
                .onErrorResume(e -> {
                    logger.error("Exception occurred: ", e);
                    return ServerResponse.status(500).bodyValue("An error occurred processing the request.");
                });
    }
}
