package de.medizininformatikinitiative.flare.service;

import de.medizininformatikinitiative.flare.model.Population;
import de.medizininformatikinitiative.flare.model.fhir.Query;
import reactor.core.publisher.Mono;

public interface FhirQueryService {

    Mono<Population> execute(Query query, boolean ignoreCache);

    default Mono<Population> execute(Query query) {
        return execute(query, false);
    }
}
