package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.Optional;

public record MustHaveEvaluation(
        boolean applicable,
        boolean fulfilled,
        Optional<AnnotatedAttribute> firstViolated
) {
    public static MustHaveEvaluation notApplicable() {
        return new MustHaveEvaluation(false, false, Optional.empty());
    }

    public static MustHaveEvaluation ok() {
        return new MustHaveEvaluation(true, true, Optional.empty());
    }

    public static MustHaveEvaluation violated(AnnotatedAttribute attr) {
        return new MustHaveEvaluation(true, false, Optional.of(attr));
    }
}
