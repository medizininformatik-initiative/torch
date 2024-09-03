package de.medizininformatikinitiative.torch.util;

import java.util.ArrayList;
import java.util.List;

public class BatchUtils {

    public static List<List<String>> splitListIntoBatches(List<String> originalList, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        int totalSize = originalList.size();
        for (int i = 0; i < totalSize; i += batchSize) {
            int end = Math.min(totalSize, i + batchSize);
            batches.add(originalList.subList(i, end));
        }
        return batches;
    }

}
