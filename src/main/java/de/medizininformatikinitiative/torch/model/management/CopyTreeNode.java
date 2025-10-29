package de.medizininformatikinitiative.torch.model.management;


import de.medizininformatikinitiative.torch.model.crtdl.FieldCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public record CopyTreeNode(FieldCondition fieldCondition, List<CopyTreeNode> children) {

    public CopyTreeNode(String fieldName) {
        this(new FieldCondition(fieldName, ""), new ArrayList<>());
    }

    public CopyTreeNode(String fieldName, String condition, List<CopyTreeNode> children) {
        this(new FieldCondition(fieldName, condition), children);
    }

    public CopyTreeNode(FieldCondition fieldCondition) {
        this(fieldCondition, new ArrayList<>());
    }

    public CopyTreeNode {
        Objects.requireNonNull(fieldCondition);
        children = new ArrayList<>(children); // mutable copy
    }


    public String fhirPath() {
        return fieldCondition.fhirPath();
    }

    public String fieldName() {
        return fieldCondition.fieldName();
    }

    public CopyTreeNode getOrCreateChild(FieldCondition fieldCondition) {
        return getChild(fieldCondition).orElseGet(() -> {
            CopyTreeNode child = new CopyTreeNode(fieldCondition);
            children.add(child);
            return child;
        });
    }

    public Optional<CopyTreeNode> getChild(FieldCondition fieldCondition) {
        return children.stream().filter(c -> c.fieldCondition.equals(fieldCondition)).findFirst();
    }

    /**
     * Merges this {@code CopyTreeNode} with another node, combining children while respecting
     * unconditional and conditional branches.
     * <p>
     * Merge rules:
     * <ul>
     *     <li>Unconditional children (condition == "") are merged recursively.</li>
     *     <li>Conditional children (condition != "") are kept unless fully covered by an
     *         unconditional child with empty children.</li>
     *     <li>Conditional children that are partially covered by an unconditional child
     *         will keep only the remaining, uncovered children.</li>
     *     <li>This merge is applied recursively for all child nodes.</li>
     * </ul>
     *
     * <p>Example:</p>
     * <pre>
     * Node a: identifier -> []            (unconditional, empty children)
     * Node b: identifier.where(type='official') -> [value]
     * Merged result: identifier -> []     (conditional branch removed)
     * </pre>
     *
     * @param other the other {@code CopyTreeNode} to merge with
     * @return a new {@code CopyTreeNode} containing the merged structure
     */
    public CopyTreeNode merged(CopyTreeNode other) {
        // Step 0: combine unconditional children first

        // Collect all unconditional children from both trees
        List<CopyTreeNode> uncondThis = this.children().stream().filter(c -> c.fieldCondition.condition().isEmpty()).toList();
        List<CopyTreeNode> uncondOther = other.children().stream().filter(c -> c.fieldCondition.condition().isEmpty()).toList();

        // Merge unconditional children by fieldName recursively
        List<CopyTreeNode> mergedUncond = new ArrayList<>();
        for (CopyTreeNode c : uncondThis) {
            CopyTreeNode match = uncondOther.stream().filter(o -> o.fieldCondition.fieldName().equals(c.fieldCondition.fieldName())).findFirst().orElse(null);

            if (match != null) {
                mergedUncond.add(c.merged(match));
            } else {
                mergedUncond.add(c);
            }
        }

        // Add unconditional children from other that were not in this
        uncondOther.stream().filter(o -> mergedUncond.stream().noneMatch(m -> m.fieldCondition.fieldName().equals(o.fieldCondition.fieldName()))).forEach(mergedUncond::add);

        List<CopyTreeNode> allChildren = new ArrayList<>(mergedUncond);

        // Step 1: handle conditional children
        List<CopyTreeNode> condChildren = Stream.concat(this.children().stream().filter(c -> !c.fieldCondition.condition().isEmpty()), other.children().stream().filter(c -> !c.fieldCondition.condition().isEmpty())).toList();

        for (CopyTreeNode c : condChildren) {
            // Find corresponding unconditional child with same fieldName
            CopyTreeNode uncondMatch = mergedUncond.stream().filter(u -> u.fieldCondition.fieldName().equals(c.fieldCondition.fieldName())).findFirst().orElse(null);

            List<CopyTreeNode> remainingChildren;
            if (uncondMatch != null) {
                // Remove any children that are already in the unconditional branch
                remainingChildren = c.children().stream().filter(ch -> uncondMatch.children().stream().noneMatch(uCh -> uCh.equals(ch))).toList();
            } else {
                remainingChildren = new ArrayList<>(c.children());
            }

            if (!remainingChildren.isEmpty()) {
                allChildren.add(new CopyTreeNode(c.fieldCondition, remainingChildren));
            }
        }

        return new CopyTreeNode(this.fieldCondition, allChildren);
    }


}
