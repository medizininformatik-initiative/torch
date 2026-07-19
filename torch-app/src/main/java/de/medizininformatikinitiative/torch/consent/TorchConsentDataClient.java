package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Encounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.multiStringValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

/**
 * Adapts Torch's {@link DataStore} to the narrow {@link ConsentDataClient} port a {@link ConsentEvaluator}
 * implementation depends on. Patient-ID resolution from a fetched resource (following {@code subject}/
 * {@code patient} references) happens here, on the host side, so consent implementations don't need
 * Torch's general-purpose {@link ResourceUtils}.
 */
@Component
public class TorchConsentDataClient implements ConsentDataClient {

    private static final Logger logger = LoggerFactory.getLogger(TorchConsentDataClient.class);

    private final DataStore dataStore;

    public TorchConsentDataClient(DataStore dataStore) {
        this.dataStore = requireNonNull(dataStore);
    }

    @Override
    public Flux<PatientResource<Consent>> searchActiveConsentsByProfile(List<String> patientIds, String profileUrl) {
        QueryParams params = patientCompartmentParams(patientIds)
                .appendParam("status", stringValue("active"))
                .appendParam("_profile:below", stringValue(profileUrl));
        return dataStore.search(Query.of("Consent", params), Consent.class)
                .flatMap(consent -> resolvePatientId(consent, consent.getId()).map(id -> new PatientResource<>(id, consent)));
    }

    @Override
    public Flux<PatientResource<Encounter>> searchEncountersByProfile(List<String> patientIds, String profileUrl) {
        QueryParams params = patientCompartmentParams(patientIds)
                .appendParam("_profile:below", stringValue(profileUrl));
        return dataStore.search(Query.of("Encounter", params), Encounter.class)
                .flatMap(encounter -> resolvePatientId(encounter, encounter.getId()).map(id -> new PatientResource<>(id, encounter)));
    }

    private static QueryParams patientCompartmentParams(List<String> patientIds) {
        return QueryParams.of("patient", multiStringValue(patientIds.stream().map(id -> "Patient/" + id).toList()));
    }

    private <T extends org.hl7.fhir.r4.model.DomainResource> Mono<String> resolvePatientId(T resource, String resourceId) {
        try {
            return Mono.just(ResourceUtils.patientId(resource));
        } catch (PatientIdNotFoundException e) {
            logger.warn("Skipping resource {} due to patient not found: {}", resourceId, e.getMessage());
            return Mono.empty();
        }
    }
}
