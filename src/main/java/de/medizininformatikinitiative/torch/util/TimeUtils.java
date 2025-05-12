package de.medizininformatikinitiative.torch.util;

public interface TimeUtils {

    static double durationSecondsSince(long startNanoTime) {
        return (double) (System.nanoTime() - startNanoTime) / 1e9;
    }
}
