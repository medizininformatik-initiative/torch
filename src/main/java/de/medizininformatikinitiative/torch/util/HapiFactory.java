package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.exceptions.FHIRException;
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
     * Checks whether the named R4 data type is a composite with at least one child that is required by the base
     * FHIR specification ({@code min > 0}), e.g. {@code UsageContext.code} or {@code SampledData.origin}.
     *
     * @return {@code false} for primitives, composites without required children and unknown type names
     */
    public static boolean hasRequiredChildren(String name) {
        return CONTEXT.getElementDefinition(name) instanceof BaseRuntimeElementCompositeDefinition<?> composite
                && composite.getChildren().stream().anyMatch(child -> child.getMin() > 0);
    }
}
