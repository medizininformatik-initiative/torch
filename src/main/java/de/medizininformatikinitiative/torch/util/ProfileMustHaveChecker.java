package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;

public class ProfileMustHaveChecker {

    private final IFhirPath fhirPathEngine;

    public ProfileMustHaveChecker(FhirContext ctx) {
        this.fhirPathEngine = ctx.newFhirPath();
    }

    public boolean fulfilled(Resource src, AnnotatedAttributeGroup group) {
        if (group == null) {
            return false;
        }
        if (src == null) {
            return false;
        }
        DomainResource resource = (DomainResource) src;
        List<String> profiles = src.getMeta().getProfile().stream().map(CanonicalType::getValue).map(ResourceUtils::stripVersion).toList();

        if (resource.getResourceType().toString().equals("Patient") || profiles.contains(group.groupReference())) {
            if (group.hasMustHave()) {
                return group.attributes().stream()
                        .filter(AnnotatedAttribute::mustHave)
                        .allMatch(attribute -> Boolean.TRUE.equals(fulfilled(resource, attribute))); // Ensures true or false
            }
            return true;
        }
        return false;
    }


    public Boolean fulfilled(DomainResource src, AnnotatedAttribute attribute) {
        List<Base> elements;
        elements = fhirPathEngine.evaluate(src, attribute.fhirPath(), Base.class);
        return !elements.isEmpty();
    }
}
