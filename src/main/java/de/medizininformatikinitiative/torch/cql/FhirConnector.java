package de.medizininformatikinitiative.torch.cql;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
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


    public List<String> searchAndExtractIds(String id) {
        Bundle bundle = null;

        var patientIds = new ArrayList<String>();

        try {
            do {
                if (bundle == null) {

                    bundle = client.search()
                            .forResource("Patient")
                            .where(new StringClientParam("_list").matches().value(id))
                            .elementsSubset("id")
                            .returnBundle(Bundle.class)
                            .execute();
                } else {
                    bundle = client.loadPage().next(bundle).execute();
                }

                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    String patId = entry.getResource().getIdElement().getIdPart();
                    patientIds.add(patId);
                }

            } while (bundle.getLink(Bundle.LINK_NEXT) != null);

        } catch (FhirClientConnectionException e) {
            System.err.println("Failed to connect to the FHIR server: " + e.getMessage());
        }

        return patientIds;
    }


    /**
     * Get the {@link MeasureReport} for a previously transmitted {@link Measure}
     *
     * @param measureUri the identifier of the {@link Measure}
     * @return the retrieved {@link MeasureReport} from the server
     * @throws IOException if the communication with the FHIR server fails due to any client or server error
     */
    public MeasureReport evaluateMeasure(String measureUri, Parameters params) throws IOException {
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
