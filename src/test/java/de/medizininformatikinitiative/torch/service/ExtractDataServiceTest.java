package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractDataServiceTest {

    private ResultFileManager resultFileManager;
    private CrtdlProcessingService processingService;
    private ExtractDataService service;
    private AnnotatedCrtdl annotatedCrtdl;

    @BeforeEach
    void setUp() {
        resultFileManager = mock(ResultFileManager.class);
        processingService = mock(CrtdlProcessingService.class);
        annotatedCrtdl = mock(AnnotatedCrtdl.class);
        service = new ExtractDataService(resultFileManager, processingService);

        when(resultFileManager.initJobDir(anyString())).thenReturn(Mono.empty());
        when(resultFileManager.saveErrorToJson(anyString(), any(OperationOutcome.class), any(HttpStatus.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    void startJob_success() {
        when(processingService.process(any(), anyString(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(service.startJob(annotatedCrtdl, List.of("p1"), "job-ok"))
                .verifyComplete();

        verify(resultFileManager).setStatus("job-ok", HttpStatus.ACCEPTED);
        verify(resultFileManager).initJobDir("job-ok");
        verify(processingService).process(eq(annotatedCrtdl), eq("job-ok"), eq(List.of("p1")));
        verify(resultFileManager).setStatus("job-ok", HttpStatus.OK);
    }

    @Test
    void startJob_illegalArgumentError() {
        when(processingService.process(any(), anyString(), anyList()))
                .thenReturn(Mono.error(new IllegalArgumentException("bad arg")));

        StepVerifier.create(service.startJob(annotatedCrtdl, List.of("p1"), "job-bad-arg"))
                .verifyComplete();

        verify(resultFileManager).saveErrorToJson(eq("job-bad-arg"), any(OperationOutcome.class), eq(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJob_validationExceptionError() {
        when(processingService.process(any(), anyString(), anyList()))
                .thenReturn(Mono.error(new ValidationException("validation failed")));

        StepVerifier.create(service.startJob(annotatedCrtdl, List.of("p1"), "job-validation-fail"))
                .verifyComplete();

        verify(resultFileManager).saveErrorToJson(eq("job-validation-fail"), any(OperationOutcome.class), eq(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJob_runtimeError() {
        when(processingService.process(any(), anyString(), anyList()))
                .thenReturn(Mono.error(new RuntimeException("unexpected")));

        StepVerifier.create(service.startJob(annotatedCrtdl, List.of("p1"), "job-runtime-fail"))
                .verifyComplete();

        verify(resultFileManager).saveErrorToJson(eq("job-runtime-fail"), any(OperationOutcome.class), eq(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
