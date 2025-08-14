package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static de.medizininformatikinitiative.torch.management.OperationOutcomeCreator.createOperationOutcome;

@Service
public class ExtractDataService {

    private final ResultFileManager resultFileManager;
    private final CrtdlValidatorService validatorService;
    private final CrtdlProcessingService processingService;

    public ExtractDataService(ResultFileManager resultFileManager,
                              CrtdlValidatorService validatorService,
                              CrtdlProcessingService processingService) {
        this.resultFileManager = resultFileManager;
        this.validatorService = validatorService;
        this.processingService = processingService;
    }

    public Mono<Void> startJob(Crtdl crtdl, List<String> patientIds, String jobId) {
        resultFileManager.setStatus(jobId, HttpStatus.ACCEPTED);

        Mono.defer(() ->
                resultFileManager.initJobDir(jobId)
                        .then(Mono.fromCallable(() -> validatorService.validate(crtdl))
                                .subscribeOn(Schedulers.boundedElastic()))
                        .flatMap(validated -> processingService.process(validated, jobId, patientIds))
                        .doOnSuccess(v -> resultFileManager.setStatus(jobId, HttpStatus.OK))
                        .doOnError(e -> handleJobError(jobId, e))
                        .onErrorResume(e -> Mono.empty())
        ).subscribeOn(Schedulers.boundedElastic()).subscribe();

        return Mono.empty();
    }

    private void handleJobError(String jobId, Throwable e) {
        HttpStatus status = (e instanceof IllegalArgumentException || e instanceof ValidationException)
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;
        OperationOutcome outcome = createOperationOutcome(jobId, e);
        resultFileManager.saveErrorToJSON(jobId, outcome, status)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}

