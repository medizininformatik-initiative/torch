package de.medizininformatikinitiative.torch.cql;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;


@Component
public class FhirConnector {

    private final IGenericClient client;

    public FhirConnector(IGenericClient client) {
        this.client = client;
    }

    /**
     * Submit a {@link Bundle} to the FHIR server.
     *
     * @param bundle the {@link Bundle} to submit
     * @throws IOException if the communication with the FHIR server fails due to any client or server error
     */
    public void transmitBundle(Bundle bundle) throws IOException {
        try {
            client.transaction().withBundle(bundle).execute();
        } catch (BaseServerResponseException e) {
            throw new IOException("An error occurred while trying to create measure and library", e);
        }
    }


    public Mono<List<String>> searchAndExtractIds(String id) {
        return fetchInitialBundle(id)
                .flatMapMany(this::fetchAllPages)
                .map(entry -> entry.getResource().getIdElement().getIdPart())
                .collectList();
    }

    // Fetch the initial bundle asynchronously
    private Mono<Bundle> fetchInitialBundle(String id) {
        return Mono.fromCallable(() -> client.search()
                        .forResource("Patient")
                        .where(new StringClientParam("_list").matches().value(id))
                        .elementsSubset("id")
                        .returnBundle(Bundle.class)
                        .execute())
                .onErrorResume(e -> {
                    System.err.println("Failed to connect to the FHIR server: " + e.getMessage());
                    return Mono.empty();
                });
    }

    private Flux<Bundle.BundleEntryComponent> fetchAllPages(Bundle initialBundle) {
        return Flux.create(sink -> {
            Bundle bundle = initialBundle;

            try {
                do {
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        sink.next(entry);
                    }

                    if (bundle.getLink(Bundle.LINK_NEXT) != null) {
                        bundle = client.loadPage().next(bundle).execute();
                    } else {
                        bundle = null;
                    }
                } while (bundle != null);

                sink.complete();
            } catch (FhirClientConnectionException e) {
                sink.error(e);
            }
        });
    }


    /**
     * Get the {@link MeasureReport} for a previously transmitted {@link Measure}
     *
     * @param params the Parameters for the evaluation of the {@link Measure}
     * @return the retrieved {@link MeasureReport} from the server
     * @throws IOException if the communication with the FHIR server fails due to any client or server error
     */
    public MeasureReport evaluateMeasure(Parameters params) throws IOException {
        try {

            return client.operation().onType(Measure.class)
                    .named("evaluate-measure")
                    .withParameters(params)
                    .returnResourceType(MeasureReport.class)
                    .execute();
        } catch (BaseServerResponseException e) {
            throw new IOException("An error occurred while trying to evaluate a measure report", e);
        }


    }

}
