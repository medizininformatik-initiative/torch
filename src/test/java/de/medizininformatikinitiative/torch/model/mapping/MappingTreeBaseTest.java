package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.sq.ContextualTermCode;
import de.medizininformatikinitiative.torch.model.sq.TermCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingTreeBaseTest {

    static final TermCode CONTEXT_1 = TermCode.of("context1", "context1", "context1");
    static final TermCode CONTEXT_2 = TermCode.of("context2", "context2", "context2");
    static final String SYSTEM_1 = "sys1";
    static final String SYSTEM_2 = "sys2";
    static final String C1 = "c1";
    static final String C2 = "c2";
    static final String C3 = "c3";
    static final String C4 = "c4";
    static final String C5 = "c5";

    private static ContextualTermCode contextualTermCodeOf(TermCode context, String system, String code) {
        return new ContextualTermCode(context, new TermCode(system, code, "display"));
    }

    @Test
    void expand_empty() {
        var base = new MappingTreeBase(List.of(new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1, Map.of())));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1)).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void expand_noMatch_differentCode() {
        var base = new MappingTreeBase(List.of(new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                Map.of(C1, new MappingTreeModuleEntry(C1, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2)).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void expand_noMatch_differentContext() {
        var base = new MappingTreeBase(List.of(new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                Map.of(C1, new MappingTreeModuleEntry(C1, List.of())))));

        var result = base.expand(
                        contextualTermCodeOf(new TermCode("", "different-context", ""), SYSTEM_1, C2))
                .toList();

        assertThat(result).isEmpty();
    }

    @Test
    void expand_noMatch_differentSystem() {
        var base = new MappingTreeBase(List.of(new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                Map.of(C1, new MappingTreeModuleEntry(C1, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, "system2", C2)).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void expand_oneModule_twoEntries_withoutChildren() {
        var base = new MappingTreeBase(List.of(new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                Map.of(C1, new MappingTreeModuleEntry(C1, List.of()),
                        C2, new MappingTreeModuleEntry(C2, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1)).toList();

        assertThat(result).containsExactlyInAnyOrder(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1));
    }

    @Test
    void expand_twoModules_sameContext_differentSystem_withoutChildren() {
        var base = new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                        Map.of(C1, new MappingTreeModuleEntry(C1, List.of()),
                                C2, new MappingTreeModuleEntry(C2, List.of()))),
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_2,
                        Map.of(C3, new MappingTreeModuleEntry(C3, List.of()),
                                C4, new MappingTreeModuleEntry(C4, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2)).toList();

        assertThat(result).containsExactlyInAnyOrder(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2));
    }

    @Test
    void expand_twoModules_sameSystem_differentContext_withoutChildren() {
        var base = new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                        Map.of(C1, new MappingTreeModuleEntry(C1, List.of()),
                                C2, new MappingTreeModuleEntry(C2, List.of()))),
                new MappingTreeModuleRoot(CONTEXT_2, SYSTEM_1,
                        Map.of(C3, new MappingTreeModuleEntry(C3, List.of()),
                                C4, new MappingTreeModuleEntry(C4, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2)).toList();

        assertThat(result).containsExactlyInAnyOrder(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2));
    }

    @Test
    void expand_oneChild_withNoReference() {
        var base = new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                        Map.of(C1, new MappingTreeModuleEntry(C1, List.of(C2))))));

        assertThatThrownBy(() -> base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1)).toList())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void expand_oneChild_onFirstLayer() {
        var base = new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                        Map.of(C1, new MappingTreeModuleEntry(C1, List.of(C2)),
                                C2, new MappingTreeModuleEntry(C2, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1)).toList();

        assertThat(result).containsExactlyInAnyOrder(
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2));
    }

    @Test
    void expand_twoChildren_onFirstLayer() {
        var base = new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                        Map.of(C1, new MappingTreeModuleEntry(C1, List.of(C2, C3)),
                                C2, new MappingTreeModuleEntry(C2, List.of()),
                                C3, new MappingTreeModuleEntry(C3, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1)).toList();

        assertThat(result).containsExactlyInAnyOrder(
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C3));
    }

    @Test
    void expand_twoChildren_onFirstAndSecondLayer() {
        var base = new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                        Map.of(C1, new MappingTreeModuleEntry(C1, List.of(C2, C3)),
                                C2, new MappingTreeModuleEntry(C2, List.of(C4)),
                                C3, new MappingTreeModuleEntry(C3, List.of(C5)),
                                C4, new MappingTreeModuleEntry(C4, List.of()),
                                C5, new MappingTreeModuleEntry(C5, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1)).toList();

        assertThat(result).containsExactlyInAnyOrder(
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C3),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C4),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C5));
    }

    @Test
    void expand_oneChild_onThreeLayers() {
        var base = new MappingTreeBase(List.of(
                new MappingTreeModuleRoot(CONTEXT_1, SYSTEM_1,
                        Map.of(C1, new MappingTreeModuleEntry(C1, List.of(C2)),
                                C2, new MappingTreeModuleEntry(C2, List.of(C3)),
                                C3, new MappingTreeModuleEntry(C3, List.of(C4, C5)),
                                C4, new MappingTreeModuleEntry(C4, List.of()),
                                C5, new MappingTreeModuleEntry(C5, List.of())))));

        var result = base.expand(contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1)).toList();

        assertThat(result).containsExactlyInAnyOrder(
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C1),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C2),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C3),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C4),
                contextualTermCodeOf(CONTEXT_1, SYSTEM_1, C5));
    }

    @Test
    void fromJson() throws Exception {
        var base = parse("""
                [
                  {
                    "entries": [
                      {
                        "key": "C1",
                        "parents": [],
                        "children": []
                      }
                    ],
                    "context": {
                      "system": "sys1",
                      "code": "code1",
                      "display": "display"
                    },
                    "system": "module-system"
                  }
                ]
                """);

        assertThat(base.moduleRoots().get(0).context())
                .isEqualTo(new TermCode("sys1", "code1", "display"));
        assertThat(base.moduleRoots().get(0).system()).isEqualTo("module-system");
        assertThat(base.moduleRoots().get(0).entries().get("C1")).isNotNull();
    }

    @Test
    void fromJson_AdditionalPropertyIsIgnored() throws Exception {
        var base = parse("""
                [
                  {
                    "foo-133831": "bar-133841",
                    "entries": [
                      {
                        "key": "C1",
                        "parents": [],
                        "children": []
                      }
                    ],
                    "context": {
                      "system": "sys1",
                      "code": "code1",
                      "display": "display"
                    },
                    "system": "module-system"
                  }
                ]
                """);

        assertThat(base.moduleRoots().get(0).context())
                .isEqualTo(new TermCode("sys1", "code1", "display"));
        assertThat(base.moduleRoots().get(0).system()).isEqualTo("module-system");
        assertThat(base.moduleRoots().get(0).entries().get("C1")).isNotNull();
    }

    @Test
    void fromJson_withChildren() throws Exception {
        var base = parse("""
                [
                  {
                    "entries": [
                      {
                        "key": "C1",
                        "parents": [],
                        "children": ["C2"]
                      },
                      {
                        "key": "C2",
                        "parents": [],
                        "children": []
                      }
                    ],
                    "context": {
                      "system": "sys1",
                      "code": "code1",
                      "display": "display"
                    },
                    "system": "module-system"
                  }
                ]
                """);

        assertThat(base.moduleRoots().get(0).context())
                .isEqualTo(new TermCode("sys1", "code1", "display"));
        assertThat(base.moduleRoots().get(0).system()).isEqualTo("module-system");
        assertThat(base.moduleRoots().get(0).entries().get("C1").children()).containsExactly("C2");
        assertThat(base.moduleRoots().get(0).entries().get("C2")).isNotNull();
    }

    static MappingTreeBase parse(String s) throws JsonProcessingException {
        return new MappingTreeBase(Arrays.stream(new ObjectMapper().readValue(s, MappingTreeModuleRoot[].class)).toList());
    }
}
