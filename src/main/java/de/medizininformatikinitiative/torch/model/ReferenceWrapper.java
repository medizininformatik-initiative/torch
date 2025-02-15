package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.List;

public record ReferenceWrapper(String ResourceID, String GroupId, AnnotatedAttribute refAttribute,
                               List<String> references) {



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
