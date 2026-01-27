package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static de.medizininformatikinitiative.torch.management.OperationOutcomeCreator.createOperationOutcome;
import static java.util.Objects.requireNonNull;

@Service
public class ExtractDataService {

    private final ResultFileManager resultFileManager;
    private final CrtdlProcessingService processingService;
    private static final Logger logger = LoggerFactory.getLogger(ExtractDataService.class);

    public ExtractDataService(ResultFileManager resultFileManager,
                              CrtdlProcessingService processingService) {
        this.resultFileManager = requireNonNull(resultFileManager);
        this.processingService = requireNonNull(processingService);
    }

    public Mono<Void> startJob(AnnotatedCrtdl crtdl, List<String> patientIds, String jobId) {
        resultFileManager.setStatus(jobId, HttpStatus.ACCEPTED);

        return resultFileManager.initJobDir(jobId)
                .then(processingService.process(crtdl, jobId, patientIds))
                .doOnSuccess(v -> resultFileManager.setStatus(jobId, HttpStatus.OK))
                .doOnError(e -> handleJobError(jobId, e))
                .onErrorResume(e -> Mono.empty());
    }

    private void handleJobError(String jobId, Throwable e) {
        logger.debug("Error occurred while processing job {} in {} (more information is logged on log level 'trace')",
                jobId, e.getStackTrace()[0]);
        logger.trace("Full error:", e);
        HttpStatus status = (e instanceof IllegalArgumentException || e instanceof ValidationException)
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;
        OperationOutcome outcome = createOperationOutcome(jobId, e);
        resultFileManager.saveErrorToJson(jobId, outcome, status)
                .doOnError(err -> resultFileManager.setStatus(jobId, HttpStatus.INTERNAL_SERVER_ERROR))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}

