package de.medizininformatikinitiative.torch;

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

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ResourceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformer.class);

    private final DataStore dataStore;
    private final ElementCopier copier;
    private final Redaction redaction;
    private final ConsentHandler handler;
    private final FhirSearchBuilder searchBuilder = new FhirSearchBuilder();
        
    @Autowired
    public ResourceTransformer(DataStore dataStore, CdsStructureDefinitionHandler cds, ConsentHandler handler) {
        this.dataStore = dataStore;
        this.copier = new ElementCopier(cds);
        this.redaction = new Redaction(cds);
        this.handler = handler;
    }

    public Flux<Resource> transformResources(String parameters, AttributeGroup group, Flux<Map<String, Map<String, List<ConsentPeriod>>>> consentmap) {
        String resourceType = group.getResourceType();

        // Offload the HTTP call to a bounded elastic scheduler to handle blocking I/O
        Flux<Resource> resources = dataStore.getResources(resourceType, parameters)
                .subscribeOn(Schedulers.boundedElastic())  // Ensure HTTP requests are offloaded
                // Error handling in case the HTTP request fails
                .onErrorResume(e -> {
                    logger.error("Error fetching resources for parameters: {}", parameters, e);
                    return Flux.empty();  // Return an empty Flux to continue processing without crashing the pipeline
                });

        return resources.map(resource -> {
            try {
                if(handler.checkConsent((DomainResource) resource,consentmap))
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

        try {
            logger.debug("Handling resource {}", ResourceUtils.getPatientId(resourcesrc));
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
            if (resourcesrc.getClass() != org.hl7.fhir.r4.model.Patient.class && resourcesrc.getClass() != org.hl7.fhir.r4.model.Consent.class) {
                copier.copy(resourcesrc, tgt, new Attribute("subject.reference", true));
            }
            if(resourcesrc.getClass() == org.hl7.fhir.r4.model.Consent.class){
                copier.copy(resourcesrc, tgt, new Attribute("patient.reference", true));
            }


            redaction.redact(tgt);
        } catch (PatientIdNotFoundException e) {
            throw new RuntimeException(e);
        }
        return tgt;
    }

    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, List<String> batch)  {
        //logger.debug("Starting collectResourcesByPatientReference");
        //logger.debug("Patients Received: {}", batch);
        Flux<Map<String, Map<String, List<ConsentPeriod>>>> consentmap=Flux.empty();
        //todo get consent key from crtdl, get all conset ressources and map all times where consent is valid to each code
        String key=crtdl.getConsentKey();
        if(key!=""){
            //Consent needed
            //Call maps
            consentmap = handler.buildingConsentInfo(key, batch);
        }

        // Set of Pat Ids that survived so far
        Set<String> safeSet = new HashSet<>(batch);

        // Mono.fromCallable is used to wrap the blocking code
        Flux<Map<String, Map<String, List<ConsentPeriod>>>> finalConsentmap = consentmap;
        return Mono.fromCallable(() -> {
                    // This part of the code involves blocking operations like creating lists
                    List<Mono<Map<String, Collection<Resource>>>> groupMonos = crtdl.getDataExtraction().getAttributeGroups().stream()
                            .map(group -> {
                                // Set of PatIds that survived for this group
                                Set<String> safeGroup = new HashSet<>();
                                if (!group.hasMustHave()) {
                                    safeGroup.addAll(batch);
                                }

                                // Handling the entire batch list as a batch
                                return transformResources(searchBuilder.getSearchParam(group, batch), group, finalConsentmap)
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
                                        .doOnNext(map -> {
                                            //logger.debug("Collected resources for group {} {}", group.getGroupReference(),map.size());
                                            safeSet.retainAll(safeGroup); // Retain only the batch that are present in both sets
                                            //logger.debug("SafeGroup after diff with SafeSet: {} {}", safeSet.size(), safeSet);
                                        });
                            })
                            .collect(Collectors.toList());

                    return Flux.concat(groupMonos)
                            .collectList()
                            .map(resourceLists -> {
                                //logger.debug("Combining resource lists into a single map");
                                return resourceLists.stream()
                                        .flatMap(map -> map.entrySet().stream())
                                        .filter(entry -> safeSet.contains(entry.getKey())) // Ensure the entry key is in the safe set
                                        .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                Map.Entry::getValue,
                                                (existing, replacement) -> {
                                                    existing.addAll(replacement);
                                                    return existing;
                                                }
                                        ));
                            })
                            .block(); // Blocking call within a Callable to get the final result
                })
                .doOnSuccess(result -> logger.debug("Successfully collected resources {}", result.entrySet()))
                .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()));
    }
}
