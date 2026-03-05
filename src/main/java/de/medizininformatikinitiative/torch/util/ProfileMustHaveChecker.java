package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfileMustHaveChecker {

    private final IFhirPath fhirPathEngine;

    public ProfileMustHaveChecker(FhirContext ctx) {
        this.fhirPathEngine = ctx.newFhirPath();
    }

    /**
     * Checks whether a FHIR resource satisfies the must-have constraints of an annotated attribute group.
     *
     * <p>A resource is considered fulfilled if:
     * <ul>
     *   <li>it is a {@code Patient} (profile check is skipped), or</li>
     *   <li>its metadata contains the profile referenced by the group.</li>
     * </ul>
     * If the group defines must-have attributes, all of them must be present and not carry a
     * Data Absent Reason extension.
     *
     * @param src   the FHIR resource to check, may be {@code null}
     * @param group the annotated attribute group defining the constraints, may be {@code null}
     * @return {@code true} if the resource fulfills the group constraints, {@code false} otherwise
     */
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

    /**
     * Checks whether a specific must-have attribute is present in a FHIR resource.
     *
     * <p>The attribute's FHIRPath expression is evaluated against the resource. An element is
     * considered absent if it carries a Data Absent Reason (DAR) extension, regardless of whether
     * it is otherwise populated. Elements that are not instances of {@link Element} cannot carry
     * extensions and are always treated as present.
     *
     * @param src       the domain resource to evaluate against
     * @param attribute the annotated attribute containing the FHIRPath expression to evaluate
     * @return {@code true} if at least one non-DAR element is found, {@code false} otherwise
     */
    public Boolean fulfilled(DomainResource src, AnnotatedAttribute attribute) {
        List<Base> elements = fhirPathEngine.evaluate(src, attribute.fhirPath(), Base.class);
        return elements.stream().anyMatch(e -> {
            if (e instanceof Element element) {
                return element.getExtension().stream().noneMatch(ResourceUtils::isDataAbsentReason);
            }
            // Base instances that are not Element (e.g. raw Resource) cannot carry extensions,
            // so we treat them as present
            return true;
        });
    }
}
