package de.medizininformatikinitiative.torch.model.consent;

import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * Records that a provision's start was moved back to the start of an overlapping encounter.
 *
 * @param originalStart the provision start as fetched, before the shift
 * @param encounterId   the ID of the encounter whose start became the provision start
 */
public record EncounterShift(LocalDate originalStart, String encounterId) {

    public EncounterShift {
        requireNonNull(originalStart);
        requireNonNull(encounterId);
    }
}
