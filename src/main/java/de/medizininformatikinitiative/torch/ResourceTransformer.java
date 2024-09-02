package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.FhirSearchBuilder;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.util.BatchUtils.splitListIntoBatches;

@Component
public class ResourceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformer.class);
    private final DataStore dataStore;

    private final ElementCopier copier;
    private final Redaction redaction;

    @Autowired(required = false)
    int batchSize = 1;

    private final FhirSearchBuilder searchBuilder = new FhirSearchBuilder();


    public ConcurrentMap<String, Set<String>> fulfilledGroupsPerPatient;

    @Autowired
    public ResourceTransformer(DataStore dataStore, CdsStructureDefinitionHandler cds) {
        this.dataStore = dataStore;
        this.copier = new ElementCopier(cds);
        this.redaction = new Redaction(cds);
        this.fulfilledGroupsPerPatient = new ConcurrentHashMap<>();
    }


    public Flux<Resource> transformResources(String parameters, AttributeGroup group) {
        String resourceType = group.getResourceType();
        Flux<Resource> resources = dataStore.getResources(resourceType, parameters);
        return resources.map(resource -> {
            try {
                return transform((DomainResource) resource, group);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                     InstantiationException e) {
                logger.error("Transform error ", e);
                throw new RuntimeException(e);
            } catch (MustHaveViolatedException e) {
                Patient empty = new Patient();
                logger.error("Empty Transformation {}", empty.isEmpty());
                return empty;
            }

        });

    }


    public Resource transform(DomainResource resourcesrc, AttributeGroup group) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, MustHaveViolatedException {
        Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
        for (Attribute attribute : group.getAttributes()) {
            copier.copy(resourcesrc, tgt, attribute);
        }
        //TODO define technically required in all Ressources
        copier.copy(resourcesrc, tgt, new Attribute("meta.profile", true));
        copier.copy(resourcesrc, tgt, new Attribute("id", true));
        //TODO Handle Custom ENUM Types like Status, since it has its Error in the valuesystem.
        if (resourcesrc.getClass() == org.hl7.fhir.r4.model.Observation.class) {
            copier.copy(resourcesrc, tgt, new Attribute("status", true));
        }
        if (resourcesrc.getClass() != org.hl7.fhir.r4.model.Patient.class) {
            copier.copy(resourcesrc, tgt, new Attribute("subject.reference", true));
        }
        redaction.redact(tgt, "", 1);
        return tgt;
    }

    public Mono<List<String>> collectResourcesByPatientReference(Crtdl crtdl, List<String> patients, int batchSize) {
        logger.debug("Starting collectResourcesByPatientReference with batchSize: {}", batchSize);
        logger.debug("Patients Received: {}", patients);

        Set<String> safeSet = new HashSet<>(patients);

        return Mono.fromCallable(() -> {
                    List<Mono<String>> batchMonos = crtdl.getDataExtraction().getAttributeGroups().stream()
                            .flatMap(group -> {
                                Set<String> safeGroup = new HashSet<>();
                                List<String> batches = splitListIntoBatches(patients, batchSize);
                                logger.debug("FHIR search List size for group {}: {}", group, batches.size());

                                return batches.stream().map(batch -> {
                                    String batchName = "batch_" + batches.indexOf(batch); // Generate a batch name based on its index
                                    return transformResources(searchBuilder.getSearchBatch(group, batch), group)
                                            .filter(resource -> !resource.isEmpty())
                                            .collectMultimap(resource -> {
                                                try {
                                                    String id = ResourceUtils.getPatientId((DomainResource) resource);
                                                    safeGroup.add(id);
                                                    return id;
                                                } catch (PatientIdNotFoundException e) {
                                                    logger.error("PatientIdNotFoundException: {}", e.getMessage());
                                                    throw new RuntimeException(e);
                                                }
                                            })
                                            .flatMap(map -> {
                                                logger.debug("Collected resources for group {}", group.getGroupReference());

                                                safeSet.retainAll(safeGroup);
                                                logger.debug("SafeGroup after diff with SafeSet: {}", safeGroup);

                                                List<Resource> resources = map.values().stream()
                                                        .flatMap(Collection::stream)
                                                        .collect(Collectors.toList());

                                                return saveResourcesToFileAsync(resources, group.getGroupReference(), batchName);
                                            });
                                });
                            })
                            .collect(Collectors.toList());

                    return Flux.concat(batchMonos).collectList().block(); // Blocking call within a Callable to get the final result
                })
                .doOnSuccess(result -> logger.info("Successfully collected resources and saved to files"))
                .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()));
    }

    // Helper method to save resources to a file asynchronously with batch name
    private Mono<String> saveResourcesToFileAsync(List<Resource> resources, String groupReference, String batchName) {
        return Mono.fromCallable(() -> {
            String filename = "resources_" + groupReference + "_" + batchName + "_" + System.currentTimeMillis() + ".json";
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.writeValue(new File(filename), resources);
            } catch (IOException e) {
                logger.error("Error writing resources to file: {}", e.getMessage());
                throw new RuntimeException(e);
            }
            return filename;
        }).subscribeOn(Schedulers.boundedElastic());
    }






}


