package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.FieldCondition;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyTreeNodeTest {

    @Test
    public void testMergedDoesNotMutateOriginal() {
        // Original tree a: identifier -> system
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("identifier", ""),
                List.of(new CopyTreeNode(new FieldCondition("system", ""))));

        // Original tree b: identifier -> where(type='official') -> value
        CopyTreeNode b = new CopyTreeNode(new FieldCondition("identifier", ""),
                List.of(new CopyTreeNode(new FieldCondition("value", ""))));

        CopyTreeNode merged = a.merged(b);

        assertThat(merged.children()).containsExactlyInAnyOrder(
                new CopyTreeNode(new FieldCondition("system", "")),
                new CopyTreeNode(new FieldCondition("value", ""))
        );

        // Original a should remain unchanged
        assertThat(a.children()).containsExactly(new CopyTreeNode(new FieldCondition("system", "")));

        // Original b should remain unchanged
        assertThat(b.children()).containsExactly(new CopyTreeNode(new FieldCondition("value", "")));
    }

    @Test
    public void testEncounterTypeSlice_isPreservedWhenMergingIntoEmptyRoot() {
        // This mirrors BatchCopierRedacter.createWrapper:
        // start with a fresh root and merge in the group's tree
        CopyTreeNode emptyRoot = new CopyTreeNode(new FieldCondition("Encounter", ""), List.of());

        CopyTreeNode groupTree = new CopyTreeNode(new FieldCondition("Encounter", ""), List.of(
                new CopyTreeNode(new FieldCondition("subject", ""), List.of()),
                new CopyTreeNode(new FieldCondition("meta", ""), List.of()),
                new CopyTreeNode(new FieldCondition("id", ""), List.of()),
                new CopyTreeNode(new FieldCondition(
                        "type",
                        ".where($this.coding.system='http://fhir.de/CodeSystem/Kontaktebene')"
                ), List.of())
        ));

        CopyTreeNode merged = emptyRoot.merged(groupTree);

        assertThat(merged.getChild(new FieldCondition("subject", ""))).isPresent();
        assertThat(merged.getChild(new FieldCondition("meta", ""))).isPresent();
        assertThat(merged.getChild(new FieldCondition("id", ""))).isPresent();


        assertThat(merged.getChild(new FieldCondition(
                "type",
                ".where($this.coding.system='http://fhir.de/CodeSystem/Kontaktebene')"
        ))).isPresent();
    }

    @Test
    public void testHierarchicalMerge() {
        // Unconditional path: patient.identifier.system + value
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""),
                        List.of(new CopyTreeNode(new FieldCondition("system", "")),
                                new CopyTreeNode(new FieldCondition("value", ""))))));

        // Conditional path: patient.identifier.where(type='official').value + displayName
        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ".where(type='official')"),
                        List.of(new CopyTreeNode(new FieldCondition("value", "")),
                                new CopyTreeNode(new FieldCondition("displayName", ""))))));

        CopyTreeNode merged = a.merged(b);

        // Expect unconditional children
        assertThat(merged.getChild(new FieldCondition("identifier", "")).get().children())
                .containsExactlyInAnyOrder(
                        new CopyTreeNode(new FieldCondition("system", "")),
                        new CopyTreeNode(new FieldCondition("value", ""))
                );

        // Expect conditional children preserved
        assertThat(merged.getChild(new FieldCondition("identifier", ".where(type='official')")).get().children())
                .containsExactlyInAnyOrder(
                        new CopyTreeNode(new FieldCondition("displayName", ""))
                );
    }

    @Test
    public void testHierarchicalMergeOverwritesConditionalBranch() {
        // Unconditional node with empty children
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""), List.of())));

        // Conditional node
        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ".where(type='official')"),
                        List.of(new CopyTreeNode(new FieldCondition("value", ""))))));

        CopyTreeNode merged = a.merged(b);
        // Unconditional node remains
        assertThat(merged.getChild(new FieldCondition("identifier", "")).get().children()).isEmpty();
    }

    @Test
    public void testMergeWithMultipleConditionsAndUnconditional() {
        // Unconditional path: patient.identifier.system + value
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""),
                        List.of(
                                new CopyTreeNode(new FieldCondition("system", "")),
                                new CopyTreeNode(new FieldCondition("value", ""))
                        ))));

        // Conditional path 1: patient.identifier.where(type='official').value
        // Conditional path 2: patient.identifier.where(type='secondary').value
        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ".where(type='official')"),
                                List.of(new CopyTreeNode(new FieldCondition("value", "")))),
                        new CopyTreeNode(new FieldCondition("identifier", ".where(type='secondary')"),
                                List.of(new CopyTreeNode(new FieldCondition("value", ""))))
                ));

        CopyTreeNode merged = a.merged(b);

        // Expect unconditional children preserved
        assertThat(merged.getChild(new FieldCondition("identifier", "")).get().children())
                .containsExactlyInAnyOrder(
                        new CopyTreeNode(new FieldCondition("system", "")),
                        new CopyTreeNode(new FieldCondition("value", ""))
                );

        // Expect conditional children skipped because unconditional exists
        assertThat(merged.getChild(new FieldCondition("identifier", ".where(type='official')"))).isEmpty();
        assertThat(merged.getChild(new FieldCondition("identifier", ".where(type='secondary')"))).isEmpty();
    }

    @Test
    public void testMergeWithUnmatchedUnconditionalChildren() {
        // a has patient.identifier, b has patient.name (no overlap)
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""),
                        List.of(new CopyTreeNode(new FieldCondition("system", ""))))));

        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("name", ""),
                        List.of(new CopyTreeNode(new FieldCondition("family", ""))))));

        CopyTreeNode merged = a.merged(b);

        // both unconditional children should appear
        assertThat(merged.children()).extracting(c -> c.fieldCondition().fieldName())
                .containsExactlyInAnyOrder("identifier", "name");

        assertThat(merged.getChild(new FieldCondition("identifier", "")).get().children())
                .containsExactly(new CopyTreeNode(new FieldCondition("system", "")));

        assertThat(merged.getChild(new FieldCondition("name", "")).get().children())
                .containsExactly(new CopyTreeNode(new FieldCondition("family", "")));
    }

    @Test
    public void testRecursiveUnconditionalMerge() {
        // both sides have the same unconditional 'identifier' field
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""),
                        List.of(new CopyTreeNode(new FieldCondition("system", ""))))));

        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""),
                        List.of(new CopyTreeNode(new FieldCondition("value", ""))))));

        CopyTreeNode merged = a.merged(b);

        // Expect identifier subtree merged recursively
        assertThat(merged.getChild(new FieldCondition("identifier", "")).get().children())
                .containsExactlyInAnyOrder(
                        new CopyTreeNode(new FieldCondition("system", "")),
                        new CopyTreeNode(new FieldCondition("value", ""))
                );
    }

    @Test
    public void testConditionalWithoutUnconditionalMatchKeepsChildren() {
        // No unconditional "identifier" in mergedUncond
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""), List.of());

        // Conditional "identifier.where(type='official')" with one child
        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ".where(type='official')"),
                        List.of(new CopyTreeNode(new FieldCondition("value", ""))))));

        CopyTreeNode merged = a.merged(b);

        // Expect the conditional branch to be preserved
        CopyTreeNode condNode = merged.getChild(new FieldCondition("identifier", ".where(type='official')")).orElseThrow();
        assertThat(condNode.children())
                .containsExactly(new CopyTreeNode(new FieldCondition("value", "")));
    }

    @Test
    public void testConditionalEmptyChildren_isKeptEvenIfUnconditionalPartiallyCoversField() {
        // Unconditional covers only part of identifier
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""),
                        List.of(new CopyTreeNode(new FieldCondition("system", ""))))));

        // Conditional slice on same fieldName, but with empty children => means "copy whole slice"
        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ".where(type='official')"),
                        List.of())));

        CopyTreeNode merged = a.merged(b);

        // Unconditional remains
        assertThat(merged.getChild(new FieldCondition("identifier", ""))).isPresent();
        assertThat(merged.getChild(new FieldCondition("identifier", "")).get().children())
                .containsExactly(new CopyTreeNode(new FieldCondition("system", "")));

        // Conditional must NOT be dropped (hits: uncondMatch != null && c.children().isEmpty() => keep)
        assertThat(merged.getChild(new FieldCondition("identifier", ".where(type='official')"))).isPresent();
        assertThat(merged.getChild(new FieldCondition("identifier", ".where(type='official')")).get().children())
                .isEmpty();
    }

    @Test
    public void testUnconditionalMerge_whenThisNodeHasNoChildren_takesOtherUnconditionalChildren() {
        // this copies identifier but has no children (i.e., nothing specified below it)
        CopyTreeNode a = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""), List.of())));

        // other specifies unconditional children under identifier
        CopyTreeNode b = new CopyTreeNode(new FieldCondition("patient", ""),
                List.of(new CopyTreeNode(new FieldCondition("identifier", ""),
                        List.of(
                                new CopyTreeNode(new FieldCondition("system", "")),
                                new CopyTreeNode(new FieldCondition("value", ""))
                        ))));

        CopyTreeNode merged = a.merged(b);

        // identifier subtree should contain other's unconditional children
        CopyTreeNode idNode = merged.getChild(new FieldCondition("identifier", "")).orElseThrow();
        assertThat(idNode.children()).containsExactlyInAnyOrder(
                new CopyTreeNode(new FieldCondition("system", "")),
                new CopyTreeNode(new FieldCondition("value", ""))
        );

        // sanity: root condition from "this" is used
        assertThat(merged.fieldCondition()).isEqualTo(new FieldCondition("patient", ""));
    }
}
