package de.medizininformatikinitiative.torch.jobhandling;

public enum JobPriority {
    NORMAL(0),
    HIGH(1);

    private final int value;

    JobPriority(int value) {
        this.value = value;
    }

    public static JobPriority fromValue(int val) {
        for (JobPriority p : values()) {
            if (p.value == val) return p;
        }
        throw new IllegalArgumentException("Unknown job priority: " + val);
    }

    public int value() {
        return value;
    }
}
