package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MustHaveChecker {

    private static final Logger logger = LoggerFactory.getLogger(MustHaveChecker.class);

    private final IFhirPath fhirPathEngine;

    public MustHaveChecker(FhirContext ctx) {
        this.fhirPathEngine = ctx.newFhirPath();
    }

    public Boolean fulfilled(DomainResource src, AnnotatedAttributeGroup group) {
        if (group.hasMustHave()) {
            return group.attributes().stream().filter(AnnotatedAttribute::mustHave).allMatch(attribute ->
                    fulfilled(src, attribute));
        }
        return true;
    }

    public Boolean fulfilled(DomainResource src, AnnotatedAttribute attribute) {
        List<Base> elements;
        elements = fhirPathEngine.evaluate(src, attribute.fhirPath(), Base.class);
        return !elements.isEmpty();
    }
}
