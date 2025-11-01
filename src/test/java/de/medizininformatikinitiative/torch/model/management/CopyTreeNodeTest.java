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
}
