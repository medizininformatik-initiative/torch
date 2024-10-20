package de.medizininformatikinitiative.torch.cql;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;


@Component
public class FhirConnector {

    private static final Logger logger = LoggerFactory.getLogger(FhirConnector.class);
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
            //Post Bundle
            client.transaction().withBundle(bundle).execute();
        } catch (BaseServerResponseException e) {
            throw new IOException("An error occurred while trying to create measure and library", e);
        }
    }




    private Mono<Bundle> fetchInitialBundle(String id) {
        //get mit FHIR Search
        return Mono.fromCallable(() -> client.search()
                        .forResource("Patient")
                        .where(new StringClientParam("_list").matches().value(id))
                        .elementsSubset("id")
                        .returnBundle(Bundle.class)
                        .execute())
                .onErrorResume(e -> {
                    logger.debug("Failed to connect to the FHIR server: {}", e.getMessage());
                    return Mono.empty();
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
            //Get
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
