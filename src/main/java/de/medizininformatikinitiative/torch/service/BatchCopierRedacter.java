package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
     * <p>
     * A patient resource may hold an identifier-only reference to a resource cached in the batch's shared
     * {@code coreBundle} rather than in that patient's own bundle (e.g. a reference to a core resource). The
     * identifier index is therefore built once from every patient bundle plus the shared core bundle, so such a
     * reference resolves the same way a literal one already does via the (separately maintained) allowed-reference
     * bookkeeping.
     *
     * @param batch    Mono of PatientBatchWithConsent to be handled.
     * @param groupMap Immutable AttributeGroup Map shared between all Batches.
     * @return Mono of transformed batch.
     */
    public ExtractionPatientBatch transformBatch(ExtractionPatientBatch batch, Map<String, AnnotatedAttributeGroup> groupMap) {
        Map<IdentifierReference, Set<ExtractionId>> identifierIndex = buildIdentifierIndex(batch);

        batch.bundles().values().parallelStream().forEach(
                bundle -> transformBundle(bundle, groupMap, identifierIndex)
        );
        return batch;
    }

    /**
     * Indexes every resource of the batch by identifier: each patient bundle plus the batch's shared core bundle.
     */
    Map<IdentifierReference, Set<ExtractionId>> buildIdentifierIndex(ExtractionPatientBatch batch) {
        return ResourceUtils.indexByIdentifier(
                Stream.concat(
                        batch.bundles().values().stream().flatMap(bundle -> bundle.cache().values().stream()),
                        batch.coreBundle().cache().values().stream()
                ).flatMap(Optional::stream).toList());
    }

    /**
     * Transforms a PatientResourceBundle using the given attribute group map by redacting and copying from the extracted resources.
     * <p>
     * Groups Attribute Group by Resource to which they should be applied, builds all information needed for extraction and
     * redaction and then applies it to the resources.
     * <p>
     * A resource that fails with {@link TargetClassCreationException}, {@link ReflectiveOperationException}, or
     * {@link RedactionException} is isolated: it is dropped from the bundle and a warning is logged, without failing
     * the rest of the batch. Any other exception is treated as a programming error and propagates.
     *
     * @param extractionBundle PatientResourceBundle to transform
     * @param groupMap         Immutable AttributeGroup Map shared between all Batches
     * @return Mono of Transformed PatientResourceBundle
     */
    public ExtractionResourceBundle transformBundle(ExtractionResourceBundle extractionBundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        Map<IdentifierReference, Set<ExtractionId>> identifierIndex = ResourceUtils.indexByIdentifier(
                extractionBundle.cache().values().stream().flatMap(Optional::stream).toList());
        return transformBundle(extractionBundle, groupMap, identifierIndex);
    }

    private ExtractionResourceBundle transformBundle(
            ExtractionResourceBundle extractionBundle,
            Map<String, AnnotatedAttributeGroup> groupMap,
            Map<IdentifierReference, Set<ExtractionId>> identifierIndex) {
        Map<ExtractionId, ResourceExtractionInfo> infoMap = extractionBundle.extractionInfoMap();

        infoMap.keySet().parallelStream().forEach(resourceId -> {
            Optional<Resource> opt = extractionBundle.getResource(resourceId);
            if (opt.isEmpty()) {
                return;
            }

            Resource resource = opt.get();
            ResourceExtractionInfo info = infoMap.get(resourceId);

            try {
                ExtractionRedactionWrapper wrapper =
                        createWrapper(resource, info, groupMap, identifierIndex);

                Resource transformed = transformResource(wrapper);

                extractionBundle.put(transformed);

            } catch (TargetClassCreationException | ReflectiveOperationException | RedactionException e) {
                logger.warn("BatchCopierRedacter001: Error transforming resource {}: {}", resourceId, e.getMessage());
                extractionBundle.put(resourceId);
            }
        });

        return extractionBundle;
    }

    ExtractionRedactionWrapper createWrapper(
            Resource resource,
            ResourceExtractionInfo info,
            Map<String, AnnotatedAttributeGroup> groupMap,
            Map<IdentifierReference, Set<ExtractionId>> identifierIndex
    ) {
        CopyTreeNode copyTree = new CopyTreeNode(resource.getClass().getSimpleName());
        Set<String> groupProfiles = new HashSet<>();

        for (String groupId : info.groups()) {
            AnnotatedAttributeGroup group = groupMap.get(groupId);
            if (group != null) {
                groupProfiles.add(group.groupReference());
                copyTree = copyTree.merged(group.copyTree().get());
            }
        }

        return new ExtractionRedactionWrapper(
                (DomainResource) resource,
                groupProfiles,
                info.attributeToReferences(),
                copyTree,
                identifierIndex
        );
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
        return tgt;
    }

}
