package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;

import java.util.List;

public record ReferenceWrapper(AnnotatedAttribute refAttribute,
                               List<ExtractionId> references, String groupId, ExtractionId resourceId) {

    /**
     * @return the ResourceAttribute from which the reference was extracted
     */
    public ResourceAttribute toResourceAttributeGroup() {
        return new ResourceAttribute(resourceId, refAttribute);
    }

    /**
     * @return the ResourceGroup fron which the reference was extracted
     */
    public ResourceGroup toResourceGroup() {
        return new ResourceGroup(resourceId, groupId);
    }
}
