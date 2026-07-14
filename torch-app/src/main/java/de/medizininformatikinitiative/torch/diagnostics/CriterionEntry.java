package de.medizininformatikinitiative.torch.diagnostics;

/**
 * A single entry in a diagnostics report, pairing an exclusion criterion with its counts.
 *
 * @param key    the criterion that caused exclusions
 * @param counts the associated patient, resource, and timing counts
 */
public record CriterionEntry(CriterionKey key, CriterionCounts counts) {
}
