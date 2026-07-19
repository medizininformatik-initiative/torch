package de.medizininformatikinitiative.torch.consent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Narrow view over a CRTDL that {@link ConsentEvaluator} needs: the raw cohort-definition JSON.
 * Implemented by Torch's own {@code Crtdl} and {@code AnnotatedCrtdl} so those types can be passed
 * directly without conversion, while keeping this module free of any dependency on Torch's full
 * CRTDL/data-extraction domain model.
 */
public interface ConsentContext {

    JsonNode cohortDefinition();
}
