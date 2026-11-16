package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

public class HapiFactory {

    private static final FhirContext CONTEXT = FhirContext.forR4Cached();

    /**
     * Creates an empty instance of the R4 data type with the given name.
     * <p>
     * Any data type known to HAPI's R4 type registry is supported, including Quantity specialisations such as
     * {@code Age} or {@code Duration}. The lookup ignores case; use {@link Type#fhirType()} on the result to get the
     * canonical type name (e.g. {@code dateTime}, {@code Age}).
     *
     * @throws FHIRException if {@code name} is not an R4 data type
     */
    public static Type create(String name) throws FHIRException {
        return switch (name) {
            case "*" ->
                //Any type allowed
                    new StringType();
            default -> {
                BaseRuntimeElementDefinition<?> definition = CONTEXT.getElementDefinition(name);
                if (definition != null && definition.newInstance() instanceof Type type) {
                    yield type;
                }
                throw new FHIRException("Unknown data type name " + name);
            }
        };
    }

    /**
     * Checks whether the base FHIR specification requires the named child ({@code min > 0}) of the given element,
     * e.g. {@code SampledData.origin} or {@code Observation.component.code}. Choice children are named with their
     * {@code [x]} suffix, as in {@link Base#children()}.
     *
     * @return {@code false} for primitives and unknown child names
     */
    public static boolean isRequiredChild(Base element, String childName) {
        return CONTEXT.getElementDefinition(element.getClass()) instanceof BaseRuntimeElementCompositeDefinition<?> composite
                && composite.getChildByName(childName) instanceof BaseRuntimeChildDefinition child
                && child.getMin() > 0;
    }
}
