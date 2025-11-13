package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Component
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
                bundle -> transformBundle(bundle, groupMap)
        );
        return batch;
    }

    /**
     * Loads a {@link Resource} from the {@link ResourceBundle} cache in a null-safe way.
     * <p>
     * <p>
     * This wrapper ensures that a ConcurrentHashMap#get.(Object) returning null
     * is converted into an {@link Optional}, avoiding direct null handling.
     *
     * @param bundle     the {@link ResourceBundle} containing cached resources
     * @param resourceId the ID of the resource to load
     * @return an {@link Optional} containing the {@link Resource} if present,
     * or {@link Optional#empty()} if the resource is missing or the cache
     * contains an empty optional for this ID
     */
    static Optional<Resource> getResource(ResourceBundle bundle, String resourceId) {
        return Optional.ofNullable(bundle.get(resourceId)).flatMap(Function.identity());
    }

    /**
     * Creates an {@link ExtractionRedactionWrapper} for a resource, merging
     * all relevant attribute groups and building the copy tree.
     *
     * @param groups                                    map of attribute groups
     * @param resource                                  the resource to transform
     * @param attributeStringGroupedWithReferenceString map of attribute references to resource references
     * @param resourceId                                the resource ID
     * @return a fully configured {@link ExtractionRedactionWrapper}
     */
    ExtractionRedactionWrapper createExtractionWrapper(Set<AnnotatedAttributeGroup> groups, Resource resource, Map<String, Map<String, Set<String>>> attributeStringGroupedWithReferenceString, String resourceId) {
        CopyTreeNode copyTreeNode = new CopyTreeNode(resource.getClass().getSimpleName());
        Set<String> groupProfiles = new HashSet<>();
        for (AnnotatedAttributeGroup group : groups.stream().toList()) {
            groupProfiles.add(group.groupReference());
            copyTreeNode = copyTreeNode.merged(group.copyTree());
        }

        return new ExtractionRedactionWrapper(
                (DomainResource) resource,
                groupProfiles,
                attributeStringGroupedWithReferenceString.getOrDefault(resourceId, Map.of()),
                copyTreeNode
        );
    }

    private Map<String, Map<String, Set<String>>> groupReferenceStringByAttributeGroup(ResourceBundle bundle) {
        Map<String, Map<String, Set<String>>> attributeStringGroupedWithReferenceString = new HashMap<>();

        bundle.resourceAttributeValidity().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .forEach(resourceAttribute -> {
                    String resourceId = resourceAttribute.resourceId();
                    String attributeRef = resourceAttribute.annotatedAttribute().attributeRef();
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
        return attributeStringGroupedWithReferenceString;
    }

    private HashMap<String, Set<String>> groupAttributeGroupsByResourceId(ResourceBundle bundle) {
        HashMap<String, Set<String>> groupedResources = new HashMap<>();

        logger.trace("ResourceGroups validity: {}", bundle.resourceGroupValidity().entrySet());

        bundle.resourceGroupValidity().entrySet().stream()
                .filter(Map.Entry::getValue)
                .forEach(entry -> {
                    ResourceGroup group = entry.getKey();
                    groupedResources.computeIfAbsent(group.resourceId(), k -> new HashSet<>()).add(group.groupId());
                });
        return groupedResources;
    }

    /**
     * Transforms a PatientResourceBundle using the given attribute group map by redacting and copying from the extracted resources.
     * <p>
     * Groups Attribute Group by Resource to which they should be applied, builds all information needed for extraction and
     * redaction and then applies it to the resources.
     *
     * @param patientResourceBundle PatientResourceBundle to transform
     * @param groupMap              Immutable AttributeGroup Map shared between all Batches
     * @return Mono of Transformed PatientResourceBundle
     */
    public PatientResourceBundle transformBundle(PatientResourceBundle patientResourceBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        ResourceBundle bundle = patientResourceBundle.bundle();

        HashMap<String, Set<String>> groupedResources = groupAttributeGroupsByResourceId(bundle);

        Map<String, Map<String, Set<String>>> attributeStringGroupedWithReferenceString = groupReferenceStringByAttributeGroup(bundle);
        groupedResources.entrySet().parallelStream()
                .forEach(entry -> {
                    String resourceId = entry.getKey();
                    Optional<Resource> optionalResource = getResource(bundle, resourceId);
                    if (optionalResource.isEmpty()) {
                        return;
                    }
                    Resource resource = optionalResource.get();

                    try {
                        Set<AnnotatedAttributeGroup> groups = entry.getValue().stream().map(groupMap::get).collect(Collectors.toSet());
                        ExtractionRedactionWrapper wrapper = createExtractionWrapper(groups, resource, attributeStringGroupedWithReferenceString, resourceId);

                        Resource transformed = transformResource(wrapper);
                        bundle.remove(resourceId);
                        bundle.put(transformed);

                    } catch (TargetClassCreationException | ReflectiveOperationException | RedactionException e) {
                        logger.warn("Error transforming resource {}", resourceId, e);

                        bundle.remove(resourceId);

                    }
                });

        return patientResourceBundle;
    }

    /**
     * Transforms a ResourceGroupWrapper by copying attributes and applying redaction rules.
     *
     * @param extractionRedactionWrapper wrapper containing all the extraction relevant information
     * @return Transformed Resource
     * @throws TargetClassCreationException if target resource instantiation fails
     * @throws ReflectiveOperationException if reflective copying fails
     */
    public Resource transformResource(ExtractionRedactionWrapper extractionRedactionWrapper) throws TargetClassCreationException, ReflectiveOperationException, RedactionException {
        DomainResource tgt = ResourceUtils.createTargetResource(extractionRedactionWrapper.resource().getClass());

        copier.copy(extractionRedactionWrapper.resource(), tgt, extractionRedactionWrapper.copyTree());
        redaction.redact(extractionRedactionWrapper.updateWithResource(tgt));
        logger.trace("Transformed resource: {}", tgt);
        return tgt;
    }

}
