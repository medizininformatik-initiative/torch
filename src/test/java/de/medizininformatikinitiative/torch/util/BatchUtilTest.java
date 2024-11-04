package de.medizininformatikinitiative.torch.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchUtilTest {

    @Test
    void testSplitListIntoBatches_EmptyList() {
        List<String> originalList = Collections.emptyList();
        int batchSize = 5;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertTrue(batches.isEmpty(), "The batches list should be empty for an empty original list.");
    }

    @Test
    void testSplitListIntoBatches_ListSizeLessThanBatchSize() {
        List<String> originalList = Arrays.asList("A", "B", "C");
        int batchSize = 5;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertEquals(1, batches.size(), "There should be exactly one batch.");
        assertEquals(originalList, batches.get(0), "The single batch should contain all original elements.");
    }

    @Test
    void testSplitListIntoBatches_ListSizeEqualToBatchSize() {
        List<String> originalList = Arrays.asList("A", "B", "C", "D", "E");
        int batchSize = 5;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertEquals(1, batches.size(), "There should be exactly one batch.");
        assertEquals(originalList, batches.get(0), "The single batch should contain all original elements.");
    }

    @Test
    void testSplitListIntoBatches_ListSizeGreaterThanBatchSize_ExactDivision() {
        List<String> originalList = Arrays.asList("A", "B", "C", "D", "E", "F");
        int batchSize = 3;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertEquals(2, batches.size(), "There should be exactly two batches.");

        List<String> expectedBatch1 = Arrays.asList("A", "B", "C");
        List<String> expectedBatch2 = Arrays.asList("D", "E", "F");

        assertEquals(expectedBatch1, batches.get(0), "First batch does not match expected.");
        assertEquals(expectedBatch2, batches.get(1), "Second batch does not match expected.");
    }

    @Test
    void testSplitListIntoBatches_ListSizeGreaterThanBatchSize_NonExactDivision() {
        List<String> originalList = Arrays.asList("A", "B", "C", "D", "E", "F", "G");
        int batchSize = 3;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertEquals(3, batches.size(), "There should be exactly three batches.");

        List<String> expectedBatch1 = Arrays.asList("A", "B", "C");
        List<String> expectedBatch2 = Arrays.asList("D", "E", "F");
        List<String> expectedBatch3 = List.of("G");

        assertEquals(expectedBatch1, batches.get(0), "First batch does not match expected.");
        assertEquals(expectedBatch2, batches.get(1), "Second batch does not match expected.");
        assertEquals(expectedBatch3, batches.get(2), "Third batch does not match expected.");
    }

    @Test
    void testSplitListIntoBatches_BatchSizeOne() {
        List<String> originalList = Arrays.asList("A", "B", "C");
        int batchSize = 1;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertEquals(3, batches.size(), "There should be exactly three batches.");

        for (int i = 0; i < originalList.size(); i++) {
            assertEquals(Collections.singletonList(originalList.get(i)), batches.get(i),
                    "Batch " + (i + 1) + " does not match expected.");
        }
    }

    @Test
    void testSplitListIntoBatches_BatchSizeGreaterThanList() {
        List<String> originalList = Arrays.asList("A", "B");
        int batchSize = 10;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertEquals(1, batches.size(), "There should be exactly one batch.");
        assertEquals(originalList, batches.get(0), "The single batch should contain all original elements.");
    }

    @Test
    void testSplitListIntoBatches_SingleElementList() {
        List<String> originalList = Collections.singletonList("A");
        int batchSize = 3;

        List<List<String>> batches = BatchUtils.splitListIntoBatches(originalList, batchSize);

        assertNotNull(batches, "The returned batches list should not be null.");
        assertEquals(1, batches.size(), "There should be exactly one batch.");
        assertEquals(originalList, batches.get(0), "The single batch should contain the single element.");
    }
    
}