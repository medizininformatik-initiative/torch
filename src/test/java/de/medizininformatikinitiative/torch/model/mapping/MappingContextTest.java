package de.medizininformatikinitiative.torch.model.mapping;

import de.medizininformatikinitiative.torch.model.sq.ContextualConcept;
import de.medizininformatikinitiative.torch.model.sq.ContextualTermCode;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingContextTest {

    static final TermCode CONTEXT = TermCode.of("context", "context", "context");
    static final String SYSTEM = "sys";
    static final String C1 = "c1";
    static final String C2 = "c2";
    static final String C3 = "c3";
    static final String C4 = "c4";

    private static ContextualTermCode contextualTermCodeOf(String code) {
        return new ContextualTermCode(MappingContextTest.CONTEXT, new TermCode(MappingContextTest.SYSTEM, code, "display"));
    }

    @Test
    void expandConcept_ConceptNotExpandable() throws Exception {
        var context = MappingContext.of(Map.of(), new MappingTreeBase(List.of(new MappingTreeModuleRoot(CONTEXT, SYSTEM, Map.of()))));

        assertThatThrownBy(() -> context.expandConcept(ContextualConcept.of(contextualTermCodeOf(C1))))
                .isInstanceOf(ContextualConceptNotExpandableException.class);
    }

    @Test
    void expandConcept_OneConcept() throws Exception {
        var context = MappingContext.of(Map.of(), new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT, SYSTEM, Map.of(C1, new MappingTreeModuleEntry(C1, List.of()))))));

        var result = context.expandConcept(ContextualConcept.of(contextualTermCodeOf(C1)));

        assertThat(result).containsExactly(contextualTermCodeOf(C1));
    }

    @Test
    void expandConcept_TwoConcepts() throws Exception {
        var context = MappingContext.of(Map.of(), new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT, SYSTEM, Map.of(C1, new MappingTreeModuleEntry(C1, List.of(C2)),
                        C2, new MappingTreeModuleEntry(C2, List.of()))))));

        var result = context.expandConcept(ContextualConcept.of(contextualTermCodeOf(C1)));

        assertThat(result).containsExactly(contextualTermCodeOf(C1), contextualTermCodeOf(C2));
    }

    @Test
    void expandConcept_SharedDescendantOnlyOnce() throws Exception {
        var context = MappingContext.of(Map.of(), new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT, SYSTEM, Map.of(C1, new MappingTreeModuleEntry(C1, List.of(C2, C3)),
                        C2, new MappingTreeModuleEntry(C2, List.of(C4)),
                        C3, new MappingTreeModuleEntry(C3, List.of(C4)),
                        C4, new MappingTreeModuleEntry(C4, List.of()))))));

        var result = context.expandConcept(ContextualConcept.of(contextualTermCodeOf(C1)));

        assertThat(result).containsExactly(contextualTermCodeOf(C1), contextualTermCodeOf(C2), contextualTermCodeOf(C4),
                contextualTermCodeOf(C3));
    }
}
