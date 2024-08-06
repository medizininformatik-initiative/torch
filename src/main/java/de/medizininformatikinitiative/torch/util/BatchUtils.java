package de.medizininformatikinitiative.torch.util;

import java.util.ArrayList;
import java.util.List;

public class BatchUtils {

    public static List<String> splitListIntoBatches(List<String> originalList, int batchSize) {
        List<String> batches = new ArrayList<>();
        int totalSize = originalList.size();
        for (int i = 0; i < totalSize; i += batchSize) {
            int end = Math.min(totalSize, i + batchSize);
            batches.add(String.join(",", originalList.subList(i, end)));
        }
        return batches;
    }

}
