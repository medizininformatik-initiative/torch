package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.List;

public record ReferenceWrapper(AnnotatedAttribute refAttribute,
                               List<String> references, String groupId, String resourceId) {

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


    /*
    Check for all references must have

    Parent:
    Resource1 with [G1,G2]
    Case Asserter Reference not resolvable
    Group 1 attribute asserter must have -> remove group from applicable groups
    Group 2 attribute asserter no must have

    Resourcen:
    Parents i.e. who loaded me with which AG  
    Children i.e. I referenced with AG





    R1-/>R2-/>R3-/>R4

    Directly loaded:
    Seed for graph building


     */


}
