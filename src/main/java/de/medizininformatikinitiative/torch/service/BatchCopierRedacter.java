package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        return batchMono.map(batch -> {
            batch.bundles().values().forEach(bundle -> transform(bundle, groupMap));
            return batch;
        });
    }


    /**
     * Transforms a PatientResourceBundle using the given attribute group map.
     *
     * @param bundle   PatientResourceBundle to transform
     * @param groupMap Immutable AttributeGroup Map shared between all Batches
     * @return Transformed PatientResourceBundle
     */
    public PatientResourceBundle transform(PatientResourceBundle bundle, Map<String, AnnotatedAttributeGroup> groupMap) {
        bundle.values().forEach(wrapper -> {
            try {
                ResourceGroupWrapper transformedWrapper = transform(wrapper, groupMap);
                bundle.put(transformedWrapper);
            } catch (MustHaveViolatedException | TargetClassCreationException e) {
                logger.warn("Error transforming resource", e);
                bundle.remove(wrapper.resource().getIdBase());
            }
        });
        return bundle;
    }

    /**
     * Transforms a ResourceGroupWrapper by copying attributes and applying redaction.
     *
     * @param resourceGroupWrapper Resource wrapper containing the original FHIR resource
     * @param groupMap             Map of attribute groups for transformation
     * @return Transformed ResourceGroupWrapper
     * @throws MustHaveViolatedException    If required attributes are missing
     * @throws TargetClassCreationException If target class creation fails
     */
    public ResourceGroupWrapper transform(ResourceGroupWrapper resourceGroupWrapper, Map<String, AnnotatedAttributeGroup> groupMap) throws MustHaveViolatedException, TargetClassCreationException {
        DomainResource srcResource = resourceGroupWrapper.resource();
        DomainResource tgt = createTargetResource(srcResource.getClass());

        Set<String> groups = resourceGroupWrapper.groupSet();
        if (groups.isEmpty()) {
            throw new MustHaveViolatedException("No groups found");
        }

        Map<String, AnnotatedAttribute> highestLevelAttributes = collectHighestLevelAttributes(groupMap, groups);

        // Step 4: Copy only the highest-level attributes
        for (AnnotatedAttribute attribute : highestLevelAttributes.values()) {
            copier.copy(srcResource, tgt, attribute);
        }

        redaction.redact(tgt);
        return new ResourceGroupWrapper(tgt, resourceGroupWrapper.groupSet());
    }

    public Map<String, AnnotatedAttribute> collectHighestLevelAttributes(Map<String, AnnotatedAttributeGroup> groupMap, Set<String> groups) {
        Map<String, AnnotatedAttribute> highestLevelAttributes = new HashMap<>();

        for (String groupID : groups) {
            AnnotatedAttributeGroup group = groupMap.get(groupID);
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
        return highestLevelAttributes;
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
