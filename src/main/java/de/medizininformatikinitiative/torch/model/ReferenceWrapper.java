package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.List;

public record ReferenceWrapper(String SourceId, AnnotatedAttribute refAttribute, List<String> references) {

}
