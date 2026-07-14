package de.medizininformatikinitiative.torch.model.sq;

import static java.util.Objects.requireNonNull;

/**
 * Comparator constants used in Structured Queries.
 */
public enum Comparator {

    EQUAL("eq"),
    LESS_EQUAL("le"),
    LESS_THAN("lt"),
    GREATER_EQUAL("ge"),
    GREATER_THAN("gt");

    private final String s;

    Comparator(String s) {
        this.s = requireNonNull(s);
    }

    public Comparator reverse() {
        return switch (this) {
            case GREATER_THAN -> LESS_THAN;
            case LESS_THAN -> GREATER_THAN;
            case GREATER_EQUAL -> LESS_EQUAL;
            case LESS_EQUAL -> GREATER_EQUAL;
            default -> EQUAL;
        };
    }

    @Override
    public String toString() {
        return s;
    }
}
