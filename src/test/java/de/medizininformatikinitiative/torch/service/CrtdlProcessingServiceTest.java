package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import de.numcodex.sq2cql.Translator;
import org.junit.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CrtdlProcessingServiceTest {


    @Test
    public void testSplitIntoBatches() {
        CrtdlProcessingService service = new CrtdlProcessingService(
                mock(WebClient.class),
                mock(Translator.class),
                mock(CqlClient.class),
                mock(ResultFileManager.class),
                mock(ProcessedGroupFactory.class),
                3, // batchSize
                false,
                mock(DirectResourceLoader.class),
                mock(ReferenceResolver.class),
                mock(BatchCopierRedacter.class),
                5,
                mock(CascadingDelete.class),
                mock(PatientBatchToCoreBundleWriter.class),
                mock(ConsentHandler.class)
        );

        List<String> patientIds = List.of("a", "b", "c", "d", "e", "f", "g");

        List<PatientBatch> batches = service.splitIntoBatches(patientIds);

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0).ids()).containsExactly("a", "b", "c");
        assertThat(batches.get(1).ids()).containsExactly("d", "e", "f");
        assertThat(batches.get(2).ids()).containsExactly("g");
    }

}
