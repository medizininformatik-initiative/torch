package de.medizininformatikinitiative.torch.model.sq;

import java.util.List;

public interface TestUtil {

    TermCode CONTEXT = TermCode.of("context-system", "context-code", "context-display");

    static TermCode tc(int number) {
        return tc("foo", number);
    }

    static TermCode tc(String kind) {
        return new TermCode(kind, "code", "display");
    }

    static TermCode tc(String kind, int number) {
        return new TermCode(kind, "code-%d".formatted(number), "display");
    }

    static ContextualTermCode ctc(int number) {
        return ctc("foo", number);
    }

    static ContextualTermCode ctc(String kind) {
        return ContextualTermCode.of(CONTEXT, tc(kind));
    }

    static ContextualTermCode ctc(String kind, int number) {
        return ContextualTermCode.of(CONTEXT, tc(kind, number));
    }

    static List<ContextualTermCode> ctcl(String kind) {
        return List.of(ctc(kind));
    }

    static List<ContextualTermCode> ctcl(String kind, int number) {
        return List.of(ctc(kind, number));
    }

    static List<ContextualTermCode> ctcl(String kind, int n1, int n2) {
        return List.of(ctc(kind, n1), ctc(kind, n2));
    }

    static ContextualConcept cc(int number) {
        return cc("foo", number);
    }

    static ContextualConcept cc(String kind) {
        return ContextualConcept.of(CONTEXT, Concept.of(tc(kind)));
    }

    static ContextualConcept cc(String kind, int number) {
        return ContextualConcept.of(CONTEXT, Concept.of(tc(kind, number)));
    }
}
