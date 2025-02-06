package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BatchCopierRedacter {

    private static final Logger logger = LoggerFactory.getLogger(BatchCopierRedacter.class);
    private final ElementCopier copier;
    private final Redaction redaction;

    public BatchCopierRedacter(ElementCopier copier, Redaction redaction) {
        this.copier = copier;
        this.redaction = redaction;
    }

    /**
     * @param batches  List of PatientBatches to be handled
     * @param groupMap Immutable AttributeGroup Map shared between all Batches
     * @return Transformed list of batches
     */
    public List<PatientBatchWithConsent> transformBatch(List<PatientBatchWithConsent> batches, Map<String, AnnotatedAttributeGroup> groupMap) {
        batches.forEach(batch ->
                batch.bundles().values().forEach(bundle -> transform(bundle, groupMap))
        );
        return batches;
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
                logger.trace("Error transforming resource", e);
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
            throw new MustHaveViolatedException("No groups found for extraction");
        }
        for (String groupID : groups) {
            AnnotatedAttributeGroup group = groupMap.get(groupID);
            for (AnnotatedAttribute attribute : group.attributes()) {
                copier.copy(srcResource, tgt, attribute, group.groupReference());
            }
        }
        redaction.redact(tgt);
        return new ResourceGroupWrapper(tgt, resourceGroupWrapper.groupSet(), resourceGroupWrapper.referencedBy(), resourceGroupWrapper.referencing());
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
