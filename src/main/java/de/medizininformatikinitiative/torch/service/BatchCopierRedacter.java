package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.*;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BatchCopierRedacter {

    private static final Logger logger = LoggerFactory.getLogger(BatchCopierRedacter.class);
    private final ElementCopier copier;
    private final Redaction redaction;

    static final Factory FACTORY = new Factory();

    public BatchCopierRedacter(ElementCopier copier, Redaction redaction) {
        this.copier = copier;
        this.redaction = redaction;
    }

    /**
     * Transforms a batch of patients reactively.
     *
     * @param batchMono Mono of PatientBatchWithConsent to be handled.
     * @param groupMap  Immutable AttributeGroup Map shared between all Batches.
     * @return Mono of transformed batch.
     */
    public Mono<PatientBatchWithConsent> transformBatch(Mono<PatientBatchWithConsent> batchMono, Map<String, AnnotatedAttributeGroup> groupMap) {
        return batchMono.flatMap(batch ->
                Flux.fromIterable(batch.bundles().values()) // Convert bundles into a Flux
                        .flatMap(bundle -> transform(bundle, groupMap)) // Transform each bundle asynchronously
                        .collectList() // Collect transformed bundles
                        .map(transformedBundles -> batch) // Map back to the original batch
        );
    }


    /**
     * Transforms a PatientResourceBundle using the given attribute group map.
     *
     * @param bundle   PatientResourceBundle to transform
     * @param groupMap Immutable AttributeGroup Map shared between all Batches
     * @return Mono of Transformed PatientResourceBundle
     */
    public Mono<PatientResourceBundle> transform(PatientResourceBundle patientResourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        ResourceBundle bundle = patientResourceBundle.bundle();

        // Step 1: Group valid resource groups by resourceId
        HashMap<String, Set<String>> groupedResources = new HashMap<>();

        logger.trace("ResourceGroups validity: {}", bundle.resourceGroupValidity().entrySet());

        bundle.resourceGroupValidity().entrySet().stream()
                .filter(Map.Entry::getValue)
                .forEach(entry -> {
                    ResourceGroup group = entry.getKey();
                    groupedResources.computeIfAbsent(group.resourceId(), k -> new HashSet<>()).add(group.groupId());
                });

        logger.debug("validResourceIds: {}", groupedResources.keySet());
        logger.debug("validResourceGroups: {}", groupedResources);
        logger.debug("validAttributes: {}", bundle.resourceAttributeValidity().entrySet());


        Map<String, Map<String, Set<String>>> attributeStringGroupedWithReferenceString = new HashMap<>();

        bundle.resourceAttributeValidity().entrySet().stream()
                .filter(Map.Entry::getValue) // Only valid attributes
                .map(Map.Entry::getKey) // Extract ResourceAttribute
                .forEach(resourceAttribute -> {
                    String resourceId = resourceAttribute.resourceId();
                    String attributeRef = resourceAttribute.annotatedAttribute().attributeRef();

                    // Get the linked groups from attribute validity
                    Set<ResourceGroup> validResourceGroups = bundle.resourceAttributeToChildResourceGroup().getOrDefault(resourceAttribute, Set.of());
                    logger.debug("Valid RG {} found for {} -> {}", validResourceGroups, resourceId, attributeRef);
                    // Filter valid resource groups
                    Set<String> validReferences = validResourceGroups.stream()
                            .filter(bundle::isValidResourceGroup)
                            .map(ResourceGroup::resourceId)
                            .collect(Collectors.toSet());

                    if (!validReferences.isEmpty()) {// Merge into the result map
                        attributeStringGroupedWithReferenceString
                                .computeIfAbsent(resourceId, k -> new HashMap<>())
                                .computeIfAbsent(attributeRef, k -> new HashSet<>())
                                .addAll(validReferences);
                    }
                });


        // Step 2: Process each resource asynchronously
        return Flux.fromIterable(groupedResources.entrySet()) // Convert to Flux for async processing
                .flatMap(entry -> {
                    String resourceId = entry.getKey();
                    Set<String> groupIds = entry.getValue();

                    return bundle.get(resourceId)
                            .flatMap(resource -> Mono.fromCallable(() -> {
                                                ProfileAttributeCollection profilesHighestLevelAttributes = collectHighestLevelAttributes(groupMap, groupedResources.get(resourceId));
                                                ExtractionRedactionWrapper processingWrapper = new ExtractionRedactionWrapper(
                                                        (DomainResource) resource, profilesHighestLevelAttributes.profiles(),
                                                        attributeStringGroupedWithReferenceString.getOrDefault(resourceId, Map.of())
                                                        , profilesHighestLevelAttributes.attributes()
                                                );
                                                return transform(processingWrapper);
                                            })
                                            .flatMap(Mono::just) // Ensure the result is a Mono
                                            .onErrorResume(MustHaveViolatedException.class, e -> {
                                                logger.warn("Error transforming resource", e);
                                                bundle.remove(resourceId); // Remove on failure
                                                return Mono.empty(); // Skip failed resource
                                            })
                                            .onErrorResume(TargetClassCreationException.class, e -> {
                                                logger.warn("Error transforming resource", e);
                                                bundle.remove(resourceId);
                                                return Mono.empty();
                                            })
                            );

                })
                .flatMap(transformed -> { // Use flatMap to ensure update completes
                    bundle.remove(ResourceUtils.getRelativeURL(transformed));
                    bundle.put(transformed);
                    return Mono.just(transformed);
                })
                .collectList()
                .then(Mono.just(patientResourceBundle)); // Return the modified bundle once all resources are processed
    }

    /**
     * Transforms a ResourceGroupWrapper by copying attributes and applying redaction.
     *
     * @param resource                   Resource wrapper containing the original FHIR resource
     * @param extractionRedactionWrapper
     * @return Transformed ResourceGroupWrapper
     * @throws MustHaveViolatedException    If required attributes are missing
     * @throws TargetClassCreationException If target class creation fails
     */
    public Resource transform(ExtractionRedactionWrapper extractionRedactionWrapper) throws MustHaveViolatedException, TargetClassCreationException {
        DomainResource tgt = createTargetResource(extractionRedactionWrapper.resource().getClass());

        // Step 4: Copy only the highest-level attributes
        for (AnnotatedAttribute attribute : extractionRedactionWrapper.attributes()) {
            copier.copy(extractionRedactionWrapper.resource(), tgt, attribute);
        }

        redaction.redact(extractionRedactionWrapper.updateWithResource(tgt));
        logger.debug("Transformed resource: {}", tgt);
        return tgt;
    }

    public ProfileAttributeCollection collectHighestLevelAttributes(Map<String, AnnotatedAttributeGroup> groupMap, Set<String> groups) {
        Map<String, AnnotatedAttribute> highestLevelAttributes = new HashMap<>();
        Set<String> groupProfiles = new HashSet<>();
        for (String groupID : groups) {
            AnnotatedAttributeGroup group = groupMap.get(groupID);
            groupProfiles.add(group.groupReference());
            for (AnnotatedAttribute attribute : group.attributes()) {
                String attrPath = attribute.attributeRef();

                if (isSubPath(attrPath, highestLevelAttributes.keySet())) {
                    continue;
                }
                Set<String> children = findChildren(attrPath, highestLevelAttributes.keySet());
                for (String child : children) {
                    highestLevelAttributes.remove(child);
                }

                // Step 3: Add the current highest-level attribute
                highestLevelAttributes.put(attrPath, attribute);
            }
        }
        return new ProfileAttributeCollection(groupProfiles, new HashSet<>(highestLevelAttributes.values()));
    }


    /**
     * @param parentCandidate potential parent Element Id string
     * @param path            Element Id string to be tested against.
     * @return true if parent is a substring of path and either the end is defined by a separating dot . or colon :
     * or a camelcase like e.g. value vs. valueQuantity
     * choice operators at the end are ignored.
     */
    public boolean isParentPath(String parentCandidate, String path) {
        if (!path.startsWith(parentCandidate)) {
            return false;
        }

        String remainder = path.substring(parentCandidate.length());

        return remainder.startsWith(".") || remainder.startsWith(":");
    }

    public Set<String> findChildren(String parent, Set<String> existingPaths) {
        Set<String> children = new HashSet<>();
        for (String existing : existingPaths) {
            if (isParentPath(parent, existing)) {
                children.add(existing);
            }
        }
        return children;
    }


    public boolean isSubPath(String path, Set<String> existingPaths) {
        for (String existing : existingPaths) {
            if (isParentPath(existing, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a new instance of a FHIR DomainResource subclass.
     *
     * @param resourceClass Class of the FHIR resource
     * @return New instance of the specified FHIR resource class
     * @throws TargetClassCreationException If instantiation fails
     */
    private static <T extends DomainResource> T createTargetResource(Class<T> resourceClass) throws TargetClassCreationException {
        try {
            return resourceClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new TargetClassCreationException(resourceClass);
        }
    }

}
