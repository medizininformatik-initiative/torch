package de.medizininformatikinitiative.torch.consent;

import java.util.List;

/**
 * Narrow view over a patient batch that {@link ConsentEvaluator} needs: the patient IDs.
 * Implemented by Torch's own {@code PatientBatch} so it can be passed directly without conversion.
 */
public interface PatientSet {

    List<String> ids();
}
