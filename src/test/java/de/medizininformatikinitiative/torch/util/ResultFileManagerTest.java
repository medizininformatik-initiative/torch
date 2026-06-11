package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.jobhandling.DefaultFileIO;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveCoreBundleToNDJSON_writesWhateverBundleWrites() throws IOException {
        FhirContext ctx = FhirContext.forR4();
        ResultFileManager manager = new ResultFileManager(tempDir.toString(), ctx, new DefaultFileIO());

        ExtractionResourceBundle bundle = mock(ExtractionResourceBundle.class);
        when(bundle.isEmpty()).thenReturn(false);

        // Write one JSON line into the Writer that ResultFileManager provides
        doAnswer(inv -> {
            Writer out = inv.getArgument(1, Writer.class);
            out.write("{\"resourceType\":\"Bundle\"}\n");
            return null;
        }).when(bundle).writeToFhirBundle(eq(ctx), any(Writer.class), eq("jobX"));

        manager.saveCoreBundleToNDJSON("jobX", bundle);

        Path ndjson = tempDir.resolve("jobX").resolve("core.ndjson");
        assertThat(ndjson).exists();

        var lines = Files.readAllLines(ndjson);
        assertThat(lines).containsExactly("{\"resourceType\":\"Bundle\"}");
        verify(bundle, times(1)).writeToFhirBundle(eq(ctx), any(Writer.class), eq("jobX"));
    }

    @Test
    void saveCoreBundleToNDJSON_skipsWhenBundleEmpty() throws IOException {
        FhirContext ctx = FhirContext.forR4();
        ResultFileManager manager = new ResultFileManager(tempDir.toString(), ctx, new DefaultFileIO());

        ExtractionResourceBundle bundle = mock(ExtractionResourceBundle.class);
        when(bundle.isEmpty()).thenReturn(true);

        manager.saveCoreBundleToNDJSON("jobX", bundle);

        Path ndjson = tempDir.resolve("jobX").resolve("core.ndjson");
        assertThat(ndjson).doesNotExist();
        verify(bundle, never()).writeToFhirBundle(any(), any(), any());
    }

    @Test
    void saveBatchToNDJSON_skipsWhenBatchEmpty() throws IOException {
        FhirContext ctx = FhirContext.forR4();
        ResultFileManager manager = new ResultFileManager(tempDir.toString(), ctx, new DefaultFileIO());

        ExtractionPatientBatch batch = mock(ExtractionPatientBatch.class);
        when(batch.isEmpty()).thenReturn(true);

        manager.saveBatchToNDJSON("jobY", batch);

        Path batchDir = tempDir.resolve("jobY");
        assertThat(batchDir).doesNotExist();
        verify(batch, never()).writeToFhirBundles(any(), any(), any());
    }

    @Test
    void saveBatchToNDJSON_writesFileWhenBatchNonEmpty() throws IOException {
        FhirContext ctx = FhirContext.forR4();
        ResultFileManager manager = new ResultFileManager(tempDir.toString(), ctx, new DefaultFileIO());

        UUID batchId = UUID.randomUUID();
        ExtractionPatientBatch batch = mock(ExtractionPatientBatch.class);
        when(batch.isEmpty()).thenReturn(false);
        when(batch.id()).thenReturn(batchId);

        doAnswer(inv -> {
            Writer out = inv.getArgument(1, Writer.class);
            out.write("{\"resourceType\":\"Bundle\"}\n");
            return null;
        }).when(batch).writeToFhirBundles(eq(ctx), any(Writer.class), eq("jobY"));

        manager.saveBatchToNDJSON("jobY", batch);

        Path ndjson = tempDir.resolve("jobY").resolve(batchId + ".ndjson");
        assertThat(ndjson).exists();
        assertThat(Files.readAllLines(ndjson)).containsExactly("{\"resourceType\":\"Bundle\"}");
    }
}
