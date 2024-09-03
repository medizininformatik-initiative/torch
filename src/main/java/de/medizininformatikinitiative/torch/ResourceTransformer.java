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


    public Flux<TransformedResource> transformResources(String parameters, AttributeGroup group) {
        String resourceType = group.getResourceType();
        Flux<Resource> resources = dataStore.getResources(resourceType, parameters);

        return resources.map(resource -> {
            String id = null;
            try {

                id = ResourceUtils.getPatientId((DomainResource) resource);
                logger.debug("Got Resource {}",id);
                Resource transformedResource = transform((DomainResource) resource, group);
                return new TransformedResource(id, transformedResource, false);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                     InstantiationException e) {
                logger.error("Transform error ", e);
                throw new RuntimeException(e);
            } catch (MustHaveViolatedException e) {
                if (id == null) {
                    try {
                        id = ResourceUtils.getPatientId((DomainResource) resource);
                    } catch (PatientIdNotFoundException ex) {
                        logger.error("PatientIdNotFoundException: {}", ex.getMessage());
                    }
                }
                logger.warn("MustHave violated for resource with ID: {}", id);
                return new TransformedResource(id, resource, true);
            } catch (PatientIdNotFoundException e) {
                logger.error("PatientIdNotFoundException during transform: {}", e.getMessage());
                return new TransformedResource(null, resource, true); // Handle case where ID couldn't be determined
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




    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, List<String> patientBatch) {
        logger.debug("Starting collectResourcesByPatientReference with patient batch size: {}", patientBatch.size());
        logger.debug("Patients Received: {}", patientBatch);

        Set<String> toBeDeleted = new HashSet<>();

        return Flux.fromIterable(crtdl.getDataExtraction().getAttributeGroups())
                .flatMap(group -> {
                    Flux<TransformedResource> resources = transformResources(searchBuilder.getSearchBatch(group, patientBatch), group);

                    if (group.hasMustHave()) {
                        return resources.filter(resource -> {
                            if (resource.isMustHaveViolated()) {
                                toBeDeleted.add(resource.getId());
                                return false;
                            }
                            return true;
                        });
                    } else {
                        return resources;
                    }
                })
                .collect(Collectors.toMap(
                        TransformedResource::getId, // Key: getID from TransformedResource
                        resource -> {
                            // Value: Collection of Resources (initially a singleton list)
                            Collection<Resource> resources = new ArrayList<>();
                            resources.add(resource.getResource());
                            return resources;
                        },
                        (existing, replacement) -> {
                            // Merge function to handle duplicate keys
                            existing.addAll(replacement);
                            return existing;
                        }
                ))
                .map(result -> {
                    // Final filtering based on `toBeDeleted`
                    return result.entrySet().stream()
                            .filter(entry -> !toBeDeleted.contains(entry.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                })
                .doOnSuccess(result -> {
                    if (result == null || result.isEmpty()) {
                        logger.warn("Resulting map is empty or null after combining resources. {}",patientBatch);
                    } else {
                        logger.info("Successfully combined resources into map. Map size: {}", result.size());
                    }
                })
                .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()));
    }


    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, List<String> patients, int batchSize) {
        logger.debug("Starting collectResourcesByPatientReference with batchSize: {}", batchSize);
        logger.debug("Patients Received: {}", patients);

        Set<String> toBeDeleted = new HashSet<>();
        List<List<String>> batches = splitListIntoBatches(patients, batchSize);

        return Mono.fromCallable(() -> {
            return Flux.fromIterable(batches)
                    .flatMap(batch -> Flux.fromIterable(crtdl.getDataExtraction().getAttributeGroups())
                            .flatMap(group -> {

                                Flux<TransformedResource> resources = transformResources(searchBuilder.getSearchBatch(group, batch), group);

                                if (group.hasMustHave()) {
                                    return resources.filter(resource -> {
                                        if (resource.isMustHaveViolated()) {
                                            toBeDeleted.add(resource.getId());
                                            return false;
                                        }
                                        return !resource.isMustHaveViolated();
                                    });
                                } else {
                                    return resources;
                                }
                            })
                    )
                    .collectList()
                    .map(transformedResources -> {
                        logger.info("Combining transformed resources into a multimap. Number of resources: {}", transformedResources.size());

                        // Create a multimap where the key is the resource ID and the value is a collection of resources.
                        return transformedResources.stream()
                                .filter(resource -> resource != null && resource.getId() != null && !toBeDeleted.contains(resource.getId()))
                                .collect(Collectors.toMap(
                                        TransformedResource::getId, // Key: getID from TransformedResource
                                        resource -> {
                                            // Value: Collection of Resources (initially a singleton list)
                                            Collection<Resource> resources = new ArrayList<>();
                                            resources.add(resource.getResource());
                                            return resources;
                                        },
                                        (existing, replacement) -> {
                                            // Merge function to handle duplicate keys
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













}


