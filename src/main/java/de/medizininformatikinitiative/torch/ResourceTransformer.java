package de.medizininformatikinitiative.torch;

import org.hl7.fhir.r4.model.Bundle;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.*;
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.util.BatchUtils.splitListIntoBatches;

@Component
public class ResourceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformer.class);
    private final DataStore dataStore;

    private final ElementCopier copier;
    private final Redaction redaction;

    private final ResultFileManager fileManager;

    @Autowired(required = false)
    int batchSize = 1;

    private final FhirSearchBuilder searchBuilder = new FhirSearchBuilder();


    private final BundleCreator creator=new BundleCreator();

    public ConcurrentMap<String, Set<String>> fulfilledGroupsPerPatient;

    @Autowired
    public ResourceTransformer(DataStore dataStore, CdsStructureDefinitionHandler cds, ResultFileManager fileManager) {
        this.dataStore = dataStore;
        this.copier = new ElementCopier(cds);
        this.redaction = new Redaction(cds);
        this.fulfilledGroupsPerPatient = new ConcurrentHashMap<>();
        this.fileManager=fileManager;
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



    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, List<String> patients, int batchSize) {
        logger.debug("Starting collectResourcesByPatientReference with batchSize: {}", batchSize);
        logger.debug("Patients Received: {}", patients);

        Set<String> safeSet = new HashSet<>(patients);
        List<List<String>> batches = splitListIntoBatches(patients, batchSize);

        return Mono.fromCallable(() -> {
            return Flux.fromIterable(batches)
                    .flatMap(batch -> {
                        Set<String> safeGroup = new HashSet<>();

                        return Flux.fromIterable(crtdl.getDataExtraction().getAttributeGroups())
                                .flatMap(group -> {
                                    Flux<Resource> resources = transformResources(searchBuilder.getSearchBatch(group, batch), group);

                                    if (group.hasMustHave()) {
                                        return resources.filter(resource -> {
                                            boolean isNotEmpty = !resource.isEmpty();
                                            if (isNotEmpty) {
                                                try {
                                                    String id = ResourceUtils.getPatientId((DomainResource) resource);
                                                    safeGroup.add(id);
                                                    logger.info("Resource is non-empty and has MustHave, adding id {} to safeGroup", id);
                                                } catch (PatientIdNotFoundException e) {
                                                    logger.error("PatientIdNotFoundException: {}", e.getMessage());
                                                }
                                            }
                                            return isNotEmpty;
                                        });
                                    } else {
                                        // If no MustHave, add the whole batch to safeGroup
                                        safeGroup.addAll(batch);
                                        return resources;
                                    }
                                }).filter(resource -> !resource.isEmpty())
                                .collectMultimap(resource -> {
                                    try {
                                        return ResourceUtils.getPatientId((DomainResource) resource);
                                    } catch (PatientIdNotFoundException e) {
                                        logger.error("PatientIdNotFoundException: {}", e.getMessage());
                                        throw new RuntimeException(e);
                                    }
                                })
                                .doOnNext(map -> {
                                    safeSet.retainAll(safeGroup);
                                    logger.info("SafeGroup after diff with SafeSet: {}", safeGroup);
                                });
                    })
                    .collectList()
                    .map(resourceLists -> {
                        logger.info("Combining resource lists into a single map. Number of maps: {}", resourceLists.size());

                        // Ensure the safeSet is not empty
                        if (safeSet.isEmpty()) {
                            logger.warn("SafeSet is empty, returning an empty map.");
                            return Collections.<String, Collection<Resource>>emptyMap();
                        }

                        return resourceLists.stream()
                                .flatMap(map -> {
                                    if (map == null) {
                                        logger.warn("Encountered a null map in resourceLists.");
                                        return Stream.<Map.Entry<String, Collection<Resource>>>empty();
                                    }
                                    return map.entrySet().stream();
                                })
                                .filter(entry -> {
                                    if (entry == null || entry.getKey() == null) {
                                        logger.warn("Encountered a null entry or key in map.");
                                        return false;
                                    }
                                    return safeSet.contains(entry.getKey());

                                })
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (existing, replacement) -> {
                                            if (existing == null) {
                                                logger.warn("Existing collection is null, using replacement.");
                                                return replacement;
                                            }
                                            if (replacement == null) {
                                                logger.warn("Replacement collection is null, using existing.");
                                                return existing;
                                            }
                                            existing.addAll(replacement);
                                            return existing;
                                        }
                                ));
                    })
                    .doOnSuccess(result -> {
                        if (result == null || result.isEmpty()) {
                            logger.warn("Resulting map is empty or null after combining resources.");
                        } else {

                            logger.info("Successfully combined resources into map. Map size: {}", result.size());
                        }
                    })
                    .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()))
                    .block(); // Blocking call to wait for the full processing to complete
        });
    }








    // Helper method to save resources to a file asynchronously with batch name
    private Mono<String> saveResourcesToFileAsync(Bundle bundle, String groupReference, String batchName, String JobID) {
        return Mono.fromCallable(() -> {

            String filename = JobID+"/"+ groupReference.hashCode() + "_" + batchName + "_" + System.currentTimeMillis() + ".json";
            logger.info("File Name to be saved to {}",filename);
            fileManager.saveBundleToFileSystem(filename,bundle);

            return filename;
        }).subscribeOn(Schedulers.boundedElastic());
    }






}


