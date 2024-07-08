package de.medizininformatikinitiative.flare.model.sq;

import java.util.List;

public record ContextualConcept(TermCode context, Concept concept) {

    public static ContextualConcept of(TermCode context, Concept concept) {
        return new ContextualConcept(context, concept);
    }

    public static ContextualConcept of(ContextualTermCode contextualTermCode) {
        return new ContextualConcept(contextualTermCode.context(), Concept.of(contextualTermCode.termCode()));
    }

    public List<ContextualTermCode> contextualTermCodes() {
        return concept.termCodes().stream().map(termCode -> ContextualTermCode.of(context, termCode)).toList();
    }
}
