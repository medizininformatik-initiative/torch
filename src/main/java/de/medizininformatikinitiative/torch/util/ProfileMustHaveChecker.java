package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.diagnostics.MustHaveEvaluation;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfileMustHaveChecker {

    private final IFhirPath fhirPathEngine;

    public ProfileMustHaveChecker(FhirContext ctx) {
        this.fhirPathEngine = ctx.newFhirPath();
    }

    public boolean fulfilled(Resource src, AnnotatedAttributeGroup group) {
        return evaluateFirst(src, group).fulfilled();
    }

    public MustHaveEvaluation evaluateFirst(Resource src, AnnotatedAttributeGroup group) {
        if (group == null || src == null) return MustHaveEvaluation.notApplicable();
        if (!(src instanceof DomainResource resource)) return MustHaveEvaluation.notApplicable();

        List<String> profiles = src.getMeta().getProfile().stream()
                .map(CanonicalType::getValue)
                .map(ResourceUtils::stripVersion)
                .toList();

        boolean inScope =
                resource.getResourceType().toString().equals("Patient")
                        || profiles.contains(group.groupReference());

        if (!inScope) return MustHaveEvaluation.notApplicable();
        if (!group.hasMustHave()) return MustHaveEvaluation.ok();

        for (AnnotatedAttribute attr : group.attributes()) {
            if (!attr.mustHave()) continue;

            if (!Boolean.TRUE.equals(fulfilled(resource, attr))) {
                return MustHaveEvaluation.violated(attr); // <-- short-circuit
            }
        }

        return MustHaveEvaluation.ok();
    }


    public Boolean fulfilled(DomainResource src, AnnotatedAttribute attribute) {
        List<Base> elements;
        elements = fhirPathEngine.evaluate(src, attribute.fhirPath(), Base.class);
        return !elements.isEmpty();
    }
}
