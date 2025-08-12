package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
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
    private CrtdlValidatorService validatorService;
    private CrtdlProcessingService processingService;
    private ExtractDataService service;
    private Crtdl crtdl; // mocked
    private AnnotatedCrtdl annotatedCrtdl;

    @BeforeEach
    void setUp() {
        resultFileManager = mock(ResultFileManager.class);
        validatorService = mock(CrtdlValidatorService.class);
        processingService = mock(CrtdlProcessingService.class);
        crtdl = mock(Crtdl.class);
        annotatedCrtdl = mock(AnnotatedCrtdl.class);
        service = new ExtractDataService(resultFileManager, validatorService, processingService);

        when(resultFileManager.initJobDir(anyString())).thenReturn(Mono.empty());
        when(resultFileManager.saveErrorToJson(anyString(), any(OperationOutcome.class), any(HttpStatus.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    void startJob_success() throws ValidationException {
        when(validatorService.validate(crtdl)).thenReturn(annotatedCrtdl);
        when(processingService.process(any(), anyString(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(service.startJob(crtdl, List.of("p1"), "job-ok"))
                .verifyComplete();

        verify(resultFileManager).setStatus("job-ok", HttpStatus.ACCEPTED);
        verify(resultFileManager).initJobDir("job-ok");
        verify(validatorService).validate(crtdl);
        verify(processingService).process(eq(annotatedCrtdl), eq("job-ok"), eq(List.of("p1")));
        verify(resultFileManager).setStatus("job-ok", HttpStatus.OK);
    }

    @Test
    void startJob_illegalArgumentError() throws ValidationException {
        when(validatorService.validate(crtdl)).thenReturn(annotatedCrtdl);
        when(processingService.process(any(), anyString(), anyList()))
                .thenReturn(Mono.error(new IllegalArgumentException("bad arg")));

        StepVerifier.create(service.startJob(crtdl, List.of("p1"), "job-bad-arg"))
                .verifyComplete();

        verify(resultFileManager).saveErrorToJson(eq("job-bad-arg"), any(OperationOutcome.class), eq(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJob_validationExceptionError() throws ValidationException {
        when(validatorService.validate(crtdl)).thenReturn(annotatedCrtdl);
        when(processingService.process(any(), anyString(), anyList()))
                .thenReturn(Mono.error(new ValidationException("validation failed")));

        StepVerifier.create(service.startJob(crtdl, List.of("p1"), "job-validation-fail"))
                .verifyComplete();

        verify(resultFileManager).saveErrorToJson(eq("job-validation-fail"), any(OperationOutcome.class), eq(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJob_runtimeError() throws ValidationException {
        when(validatorService.validate(crtdl)).thenReturn(annotatedCrtdl);
        when(processingService.process(any(), anyString(), anyList()))
                .thenReturn(Mono.error(new RuntimeException("unexpected")));

        StepVerifier.create(service.startJob(crtdl, List.of("p1"), "job-runtime-fail"))
                .verifyComplete();

        verify(resultFileManager).saveErrorToJson(eq("job-runtime-fail"), any(OperationOutcome.class), eq(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void startJob_validatorThrows() throws ValidationException {
        when(validatorService.validate(crtdl)).thenThrow(new ValidationException("invalid"));

        StepVerifier.create(service.startJob(crtdl, List.of("p1"), "job-validator-throw"))
                .verifyComplete();

        verify(resultFileManager).saveErrorToJson(eq("job-validator-throw"), any(OperationOutcome.class), eq(HttpStatus.BAD_REQUEST));
    }
}
