package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.DirectResourceLoader;
import de.medizininformatikinitiative.torch.model.management.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
public class BatchProcessingPipeline {

    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingPipeline.class);

    private final DirectResourceLoader directLoader;
    private final int maxConcurrency;
    private final BatchReferenceProcessor batchReferenceProcessor;
    private final BatchCopierRedacter batchCopierRedacter;


    public BatchProcessingPipeline(DirectResourceLoader directLoader,
                                   BatchReferenceProcessor batchReferenceProcessor,
                                   BatchCopierRedacter batchCopierRedacter,
                                   @Value("5") int maxConcurrency) {
        this.directLoader = directLoader;
        this.maxConcurrency = maxConcurrency;
        this.batchReferenceProcessor = batchReferenceProcessor;
        this.batchCopierRedacter = batchCopierRedacter;
    }


    /**
     * @param batches         Queried PatientBatch from the cohort
     * @param groupsToProcess AttributeGroups extracted from the CRTDL
     * @param consentKey      consent key from the crtdl (optional)
     * @return Mono<List < PatientBatchWithConsent>> with the processed resources in form of batches
     */
    public Mono<List<PatientBatchWithConsent>> execute(Flux<PatientBatch> batches, GroupsToProcess groupsToProcess, Optional<String> consentKey) {
        Mono<ResourceBundle> coreResourceBundle = directLoader.proccessCoreAttributeGroups(groupsToProcess.directNoPatientGroups());
        return batches
                .flatMap(batch -> directLoader.directLoadPatientCompartment(groupsToProcess.directPatientCompartmentGroups(), batch, consentKey), maxConcurrency)
                .collectList()
                .flatMap(directlyLoadedPatients -> batchReferenceProcessor.processBatches(directlyLoadedPatients, coreResourceBundle, groupsToProcess.allGroups()))
                //TODO: Cascading delete
                .map(referencedBatch -> batchCopierRedacter.transformBatch(referencedBatch, groupsToProcess.allGroups()));
    }


}