package de.medizininformatikinitiative.torch.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for batch processing of lists.
 */
public class BatchUtils {

    /**
     * Splits a list of strings into smaller batches of a specified size.
     *
     * @param originalList the original list to be split into batches
     * @param batchSize    the maximum size of each batch
     * @return a list of lists, where each sublist is a batch of the original list
     */
    public static List<List<String>> splitListIntoBatches(List<String> originalList, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        int totalSize = originalList.size();
        for (int i = 0; i < totalSize; i += batchSize) {
            int end = Math.min(totalSize, i + batchSize);
            batches.add(originalList.subList(i, end));
        }
        return batches;
    }

    public static String queryElements(String type) {
        return switch (type) {
            case "Patient" -> "_id";
            case "Immunization", "Consent" -> "patient";
            default -> "subject";
        };
    }

}
