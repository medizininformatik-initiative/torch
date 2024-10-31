package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
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

import static de.medizininformatikinitiative.torch.util.BatchUtils.queryElements;

@Component
public class ResourceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTransformer.class);

    private final DataStore dataStore;
    private final ElementCopier copier;
    private final Redaction redaction;
    private final ConsentHandler handler;
    private final FhirContext context;
    private final DseMappingTreeBase dseMappingTreeBase;

    @Autowired
    public ResourceTransformer(DataStore dataStore, ConsentHandler handler, ElementCopier copier, Redaction redaction, FhirContext context, DseMappingTreeBase dseMappingTreeBase) {
        this.dataStore = dataStore;
        this.copier = copier;
        this.redaction = redaction;
        this.handler = handler;
        this.context = context;
        this.dseMappingTreeBase = dseMappingTreeBase;
    }

    public Flux<Resource> transformResources(String batch, AttributeGroup group, Map<String, Map<String, List<Period>>> consentmap) {
        List<Query> queryList = group.queries(dseMappingTreeBase);  // Assuming 'queries()' method returns a list of queries.

        // Create a Flux from the list of queries and flatMap to handle each one
        return Flux.fromIterable(queryList)
                .flatMap(query -> {
                    // Offload the HTTP call to a bounded elastic scheduler to handle blocking I/O
                    Query finalQuery = new Query(query.type(), query.params().appendParam(queryElements(query.type()), QueryParams.stringValue(batch)));
                    Flux<Resource> resources = dataStore.getResources(query)
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                logger.error("Error fetching resources for parameters: {}", finalQuery, e);
                                return Flux.empty();
                            });

                    // Transform resources based on consentmap availability
                    return resources.flatMap(resource -> {
                        try {
                            if (consentmap.isEmpty() || handler.checkConsent((DomainResource) resource, consentmap)) {
                                return Mono.just(transform((DomainResource) resource, group));
                            } else {
                                logger.warn("Consent Violated for Resource {} {}", resource.getResourceType(), resource.getId());
                                return Mono.just(new Patient());  // Return empty patient if consent violated
                            }
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                                 InstantiationException e) {
                            logger.error("Transform error: ", e);
                            return Mono.error(new RuntimeException(e));
                        } catch (MustHaveViolatedException e) {
                            Patient empty = new Patient();
                            logger.error("Must Have Violated resulting in dropped Resource {} {}", resource.getResourceType(), resource.getId());
                            return Mono.just(empty);
                        }
                    });
                });
    }


    public Resource transform(DomainResource resourcesrc, AttributeGroup group) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, MustHaveViolatedException {
        Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

        try {
            logger.trace("Handling resource {} for patient {} and attributegroup {}", resourcesrc.getId(), ResourceUtils.getPatientId(resourcesrc), group.groupReference());
            for (Attribute attribute : group.attributes()) {

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
            if (resourcesrc.getClass() == org.hl7.fhir.r4.model.Consent.class) {
                copier.copy(resourcesrc, tgt, new Attribute("patient.reference", true));
            }
            logger.trace("Resource after Copy {}", this.context.newJsonParser().encodeResourceToString(tgt));

            redaction.redact(tgt);
            logger.trace("Resource after Redact {}", this.context.newJsonParser().encodeResourceToString(tgt));

            logger.debug("Sucessfully transformed and redacted {}", resourcesrc.getId());
        } catch (PatientIdNotFoundException e) {
            throw new RuntimeException(e);
        }
        return tgt;
    }

    public Mono<Map<String, Collection<Resource>>> collectResourcesByPatientReference(Crtdl crtdl, List<String> batch) {
        logger.trace("Starting collectResourcesByPatientReference");
        logger.trace("Patients Received: {}", batch);

        // Fetch consent key
        String key = crtdl.consentKey();


        // Initialize consentmap: fetch consent information reactively or return empty if no consent is needed
        Flux<Map<String, Map<String, List<Period>>>> consentmap = key.isEmpty() ?
                Flux.empty() : handler.updateConsentPeriodsByPatientEncounters(handler.buildingConsentInfo(key, batch), batch);

        // Set of patient IDs that survived so far
        Set<String> safeSet = new HashSet<>(batch);

        return consentmap.switchIfEmpty(Flux.just(Collections.emptyMap())) // Handle case where no consent is required
                .flatMap(finalConsentmap -> {

                    // Collect the attribute groups for each batch
                    return Flux.fromIterable(crtdl.dataExtraction().attributeGroups())
                            .flatMap(group -> {
                                // Set of patient IDs that survived for this group
                                Set<String> safeGroup = new HashSet<>();
                                List<QueryParams> params = group.queryParams(dseMappingTreeBase);
                                if (!group.hasMustHave()) {
                                    safeGroup.addAll(batch); // No constraints, all patients are initially safe
                                }

                                // Handle each group reactively
                                return transformResources(String.join(",", batch), group, finalConsentmap)
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
                                            safeSet.retainAll(safeGroup); // Retain only the patients present in both sets
                                            logger.trace("Retained {}", safeSet);
                                        });
                            });
                })
                .collectList()
                .map(resourceLists -> resourceLists.stream()
                        .flatMap(map -> map.entrySet().stream())
                        .filter(entry -> safeSet.contains(entry.getKey())) // Filter by the safe set
                        .peek(entry -> logger.debug("Filtering entry with key: {} and value: {}", entry.getKey(), entry.getValue())) // Log each filtered entry
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (existing, replacement) -> {
                                    existing.addAll(replacement);
                                    return existing;
                                }
                        ))
                )
                .doOnSuccess(result -> logger.debug("Successfully collected resources {}", result))
                .doOnError(error -> logger.error("Error collecting resources: {}", error.getMessage()));

    }


}
