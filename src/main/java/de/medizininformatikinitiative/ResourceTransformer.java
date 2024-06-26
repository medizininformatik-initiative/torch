package de.medizininformatikinitiative;

import de.medizininformatikinitiative.util.ElementCopier;
import de.medizininformatikinitiative.util.FhirSearchBuilder;
import de.medizininformatikinitiative.util.Redaction;
import de.medizininformatikinitiative.model.*;
import de.medizininformatikinitiative.util.ResourceUtils;
import de.medizininformatikinitiative.exceptions.*;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ResourceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformer.class);
    private final DataStore dataStore;

    private final ElementCopier copier;
    private final Redaction redaction;

    private final FhirSearchBuilder searchBuilder = new FhirSearchBuilder();


    @Autowired
    public ResourceTransformer(DataStore dataStore, CdsStructureDefinitionHandler cds) {
        this.dataStore = dataStore;
        this.copier = new ElementCopier(cds);
        this.redaction = new Redaction(cds);
    }


    public Flux<Resource> transformResources(String parameters, AttributeGroup group) {
        String resourceType= group.getResourceType();
        Flux<Resource> resources = dataStore.getResources(resourceType, parameters);
        return resources.map(resource -> {

            try {
                return transform((DomainResource) resource, group);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException |
                     de.medizininformatikinitiative.util.Exceptions.MustHaveViolatedException e) {
                throw new RuntimeException(e);
            }
        });

    }



    public Resource transform(DomainResource resourcesrc, AttributeGroup group) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, de.medizininformatikinitiative.util.Exceptions.MustHaveViolatedException {
        Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
        group.getAttributes().forEach(attribute -> {
            try {
                copier.copy(resourcesrc, tgt, attribute);
            } catch (de.medizininformatikinitiative.util.Exceptions.MustHaveViolatedException e) {
                throw new RuntimeException(e);
            }
        });
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

    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, List<String> patients,int batchSize) {
        return Mono.fromCallable(() -> {

                List<Mono<Map<String, Collection<Resource>>>> groupMonos = crtdl.getCohortDefinition().getDataExtraction().getAttributeGroups().stream()
                        .map(group -> {
                            List<String> fhirSearchList = searchBuilder.getSearchBatches(group, patients, batchSize);
                            logger.debug("FHIR search List size: " + fhirSearchList.size());

                            return Flux.fromIterable(fhirSearchList)
                                    .flatMap(parameters -> transformResources(parameters, group))
                                    .collectMultimap(resource -> {
                                        try {
                                            return ResourceUtils.getPatientId((DomainResource) resource);
                                        } catch (PatientIdNotFoundException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                        })
                        .collect(Collectors.toList());

                return Flux.concat(groupMonos)
                        .collectList()
                        .map(resourceLists -> resourceLists.stream()
                                .flatMap(map -> map.entrySet().stream())
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (existing, replacement) -> {
                                            existing.addAll(replacement);
                                            return existing;
                                        }
                                )))
                        .block();
        });
    }



}
