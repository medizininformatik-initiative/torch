package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ProfileAttributeCollection;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class BatchCopierRedacter {

    private static final Logger logger = LoggerFactory.getLogger(BatchCopierRedacter.class);

    private final ElementCopier copier;
    private final Redaction redaction;

    public BatchCopierRedacter(ElementCopier copier, Redaction redaction) {
        this.copier = requireNonNull(copier);
        this.redaction = requireNonNull(redaction);
    }

    /**
     * Transforms a batch of patients reactively.
     *
     * @param batch    Mono of PatientBatchWithConsent to be handled.
     * @param groupMap Immutable AttributeGroup Map shared between all Batches.
     * @return Mono of transformed batch.
     */
    public PatientBatchWithConsent transformBatch(PatientBatchWithConsent batch, Map<String, AnnotatedAttributeGroup> groupMap) {
        batch.bundles().values().parallelStream().forEach(
                bundle -> transform(bundle, groupMap)
        );
        return batch;
    }

    /**
     * Transforms a PatientResourceBundle using the given attribute group map.
     *
     * @param patientResourceBundle PatientResourceBundle to transform
     * @param groupMap              Immutable AttributeGroup Map shared between all Batches
     * @return Mono of Transformed PatientResourceBundle
     */
    public PatientResourceBundle transform(PatientResourceBundle patientResourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
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

        logger.trace("validResourceIds: {}", groupedResources.keySet());
        logger.trace("validResourceGroups: {}", groupedResources);
        logger.trace("validAttributes: {}", bundle.resourceAttributeValidity().entrySet());

        Map<String, Map<String, Set<String>>> attributeStringGroupedWithReferenceString = new HashMap<>();

        bundle.resourceAttributeValidity().entrySet().stream()
                .filter(Map.Entry::getValue) // Only valid attributes
                .map(Map.Entry::getKey) // Extract ResourceAttribute
                .forEach(resourceAttribute -> {
                    String resourceId = resourceAttribute.resourceId();
                    String attributeRef = resourceAttribute.annotatedAttribute().attributeRef();

                    // Get the linked groups from attribute validity
                    Set<ResourceGroup> validResourceGroups = bundle.resourceAttributeToChildResourceGroup().getOrDefault(resourceAttribute, Set.of());
                    logger.trace("Valid RG {} found for {} -> {}", validResourceGroups, resourceId, attributeRef);
                    // Filter valid resource groups
                    Set<String> validReferences = validResourceGroups.stream()
                            .filter(group -> Boolean.TRUE.equals(bundle.isValidResourceGroup(group)))
                            .map(ResourceGroup::resourceId)
                            .collect(Collectors.toSet());

                    if (!validReferences.isEmpty()) {// Merge into the result map
                        attributeStringGroupedWithReferenceString
                                .computeIfAbsent(resourceId, k -> new HashMap<>())
                                .computeIfAbsent(attributeRef, k -> new HashSet<>())
                                .addAll(validReferences);
                    }
                });

        logger.trace("attributeStringGroupedWithReferenceString: {}", attributeStringGroupedWithReferenceString);

        // Step 2: Process each resource asynchronously
        groupedResources.entrySet().parallelStream()
                .forEach(entry -> {
                    String resourceId = entry.getKey();
                    Resource resource = bundle.get(resourceId); // synchronous get
                    if (resource == null) {
                        return; // skip
                    }

                    try {
                        ProfileAttributeCollection profileAttributeCollection = collectHighestLevelAttributes(groupMap, entry.getValue());

                        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(
                                (DomainResource) resource,
                                profileAttributeCollection.profiles(),
                                attributeStringGroupedWithReferenceString.getOrDefault(resourceId, Map.of()),
                                profileAttributeCollection.attributes()
                        );

                        Resource transformed = transform(wrapper);

                        bundle.remove(ResourceUtils.getRelativeURL(transformed));
                        bundle.put(transformed);


                    } catch (MustHaveViolatedException | TargetClassCreationException e) {
                        logger.warn("Error transforming resource {}", resourceId, e);

                        bundle.remove(resourceId);

                    }
                });

        return patientResourceBundle;
    }

    /**
     * Transforms a ResourceGroupWrapper by copying attributes and applying redaction.
     *
     * @param extractionRedactionWrapper wrapper containing all the extraction relevant information
     * @return Transformed Resource
     * @throws MustHaveViolatedException    If required attributes are missing
     * @throws TargetClassCreationException If target class creation fails
     */
    public Resource transform(ExtractionRedactionWrapper extractionRedactionWrapper) throws MustHaveViolatedException, TargetClassCreationException {
        DomainResource tgt = ResourceUtils.createTargetResource(extractionRedactionWrapper.resource().getClass());

        // Step 4: Copy only the highest-level attributes
        for (AnnotatedAttribute attribute : extractionRedactionWrapper.attributes()) {
            copier.copy(extractionRedactionWrapper.resource(), tgt, attribute);
        }

        redaction.redact(extractionRedactionWrapper.updateWithResource(tgt));
        logger.trace("Transformed resource: {}", tgt);
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

    private boolean isSubPath(String path, Set<String> existingPaths) {
        for (String existing : existingPaths) {
            if (isParentPath(existing, path)) {
                return true;
            }
        }
        return false;
    }
}
