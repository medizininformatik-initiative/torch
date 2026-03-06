package de.medizininformatikinitiative.torch.diagnostics;

public record CriterionCounts(long patientsExcluded, long resourcesExcluded) {
    public static CriterionCounts empty() {
        return new CriterionCounts(0, 0);
    }

    public CriterionCounts add(CriterionCounts other) {
        return new CriterionCounts(
                this.patientsExcluded + other.patientsExcluded,
                this.resourcesExcluded + other.resourcesExcluded
        );
    }

    public CriterionCounts plusPatients(int delta) {
        if (delta == 0) return this;
        return new CriterionCounts(patientsExcluded + delta, resourcesExcluded);
    }

    public CriterionCounts plusResources(int delta) {
        if (delta == 0) return this;
        return new CriterionCounts(patientsExcluded, resourcesExcluded + delta);
    }

}
