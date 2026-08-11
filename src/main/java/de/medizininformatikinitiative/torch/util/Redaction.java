package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.ElementContext;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.MultiElementContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;
import static java.util.Objects.requireNonNull;

/**
 * Redaction operations on copied Resources based on the StructureDefinition
 */
@Component
public class Redaction {

    private static final Logger logger = LoggerFactory.getLogger(Redaction.class);
    private static final String MASKED = "masked";
    private static final Extension ABSENT_REASON_EXTENSION = createAbsentReasonExtension(MASKED);
    private static final String EXTENSION = "extension";
    private static final String MODIFIER_EXTENSION = "modifierExtension";
    private static final String REFERENCE = "reference";

    private final StructureDefinitionHandler structureDefinitionHandler;

    /**
     * Constructor for Redaction
     *
     * @param structureDefinitionHandler StructureDefinitionHandler
     */
    public Redaction(StructureDefinitionHandler structureDefinitionHandler) {
        this.structureDefinitionHandler = requireNonNull(structureDefinitionHandler);
    }

    /**
     * Removes disallowed {@link Reference} values from the given property.
     * <p>
     * If a reference is not in the provided {@code references} set, it is removed entirely.
     * </p>
     *
     * @param child   the property containing reference values
     * @param allowed the set of allowed reference strings
     */
    private void handleReference(Property child, Set<ExtractionId> allowed) {
        child.getValues().forEach(referenceValue -> {
            if (!(referenceValue instanceof Reference reference) || !reference.hasReference()) {
                return;
            }

            String refString = reference.getReference();
            boolean isAllowed;
            try {
                ExtractionId id = ExtractionId.fromRelativeUrl(refString);
                isAllowed = allowed.contains(id);
            } catch (IllegalArgumentException ex) {
                isAllowed = false;
            }

            if (!isAllowed) {
                referenceValue.setProperty(REFERENCE, HapiFactory.create("string").addExtension(ABSENT_REASON_EXTENSION));
            }
        });
    }


    private static List<String> getTypes(Property child, List<String> collectedTypes) {
        return collectedTypes.isEmpty() ? List.of(child.getTypeCode().split("\\|")) : collectedTypes;
    }

    /**
     * Redacts a FHIR resource using structure definitions and allowed references.
     * <p>
     * Assumes {@code wrapper}'s resource-profile association was already validated when it was built
     * (see {@link ExtractionRedactionWrapper#of}). Resolves structure definitions for the requested
     * profiles and uses them to redact the resource, filling required fields with Data Absent Reasons
     * where necessary.
     * </p>
     *
     * @param wrapper the wrapper containing the resource, profiles, and allowed references
     * @return the redacted resource with required fields fulfilled
     * @throws RedactionException if the requested profiles are unknown
     */
    public DomainResource redact(ExtractionRedactionWrapper wrapper) throws RedactionException {
        DomainResource resource = wrapper.resource();
        Meta meta = resource.getMeta();
        List<CanonicalType> resourceProfiles;
        if (!resource.getResourceType().toString().equals("Patient")) {
            resourceProfiles = wrapper.matchedProfiles();
        } else {
            resourceProfiles = wrapper.profiles().stream().map(CanonicalType::new).toList();
        }
        List<CompiledStructureDefinition> definitions = structureDefinitionHandler.getDefinitions(wrapper.profiles());
        if (definitions.isEmpty()) {
            logger.error("REDACTION_02 Unknown Profile in Resource {} {}", resource.getResourceType(), resource.getId());
            throw new RedactionException("Trying to handle unknown profiles: " + wrapper.profiles());
        }
        meta.setProfile(resourceProfiles);
        this.redact(resource, new MultiElementContext(String.valueOf(resource.getResourceType()), definitions), wrapper.references());
        return resource;
    }

    /**
     * Handles redaction of extensions for the given FHIR element:
     * <ul>
     *   <li>Removes extensions not allowed by slicing rules</li>
     *   <li>Recursively redacts known extensions according to structure definitions</li>
     * </ul>
     *  @param base    the FHIR element whose extensions are to be validated and redacted
     *
     * @param context    the element context used to evaluate and process extensions
     * @param references Map of allowed references
     */
    private void redactExtensions(Base base, MultiElementContext context, Map<String, Set<ExtractionId>> references) {
        MultiElementContext extensionsContext = context.descend(EXTENSION);
        removeUnknownExtensions(base, extensionsContext);
        redactKnownExtensions(base, extensionsContext, references);
    }

    /**
     * Removes extensions from the given FHIR element that are not allowed by slicing rules.
     * <p>
     * Skipped when {@code base} is itself an {@link Extension} whose own nested extension slot can't be
     * resolved: a composite extension's internal slicing (e.g. an extension nested inside another
     * extension) is defined in that extension's own StructureDefinition, not the containing profile's,
     * so Torch commonly has no information to judge such children and must not delete them.
     *
     * @param base    the FHIR element from which unknown extensions should be removed
     * @param context the context containing allowed extensions for validation
     */
    private void removeUnknownExtensions(Base base, MultiElementContext context) {
        if (base instanceof Extension && !context.isResolvable()) {
            return;
        }
        getExtensions(base).stream().filter(context::shouldRedactExtension).forEach(extension -> base.removeChild(EXTENSION, extension));
    }

    /**
     * Redacts known extensions of the given FHIR element using the provided structure definitions.
     * <p>
     * An extension left with neither a value nor nested extensions afterward is removed: {@link Base#isEmpty()}
     * treats a {@code url}-only extension as non-empty since {@code url} technically counts as a child per
     * FHIR's own model, but HAPI's serializers write nothing for such an extension anyway, so leaving it in
     * place produces a container that appears empty on the wire (violating {@code ele-1}) despite passing
     * {@code isEmpty()}.
     *
     * @param base       the FHIR element whose remaining extensions should be processed
     * @param context    the context for redacting extensions
     * @param references Map of allowed references
     */
    private void redactKnownExtensions(Base base, MultiElementContext context, Map<String, Set<ExtractionId>> references) {
        getExtensions(base).forEach(extension -> {
            redactChildren(extension, context, references);
            if (!extension.hasValue() && !extension.hasExtension()) {
                base.removeChild(EXTENSION, extension);
            }
        });
    }

    private List<Extension> getExtensions(Base base) {
        return switch (base) {
            case Element element when element.hasExtension() -> List.copyOf(element.getExtension());
            case DomainResource domainResource when domainResource.hasExtension() ->
                    List.copyOf(domainResource.getExtension());
            default -> List.of();
        };
    }

    /**
     * Recursively redacts the given FHIR element based on its structure definitions and allowed references.
     * <p>
     * Handles slicing logic, removes unknown or disallowed children, and sets Data Absent Reason extensions
     * for required elements when necessary.
     * </p>
     *
     * @param dataElement the FHIR {@link Base} element to redact
     * @param context     element ID and associated structure definitions
     * @param references  Map of allowed references
     */
    private void redact(Base dataElement, MultiElementContext context, Map<String, Set<ExtractionId>> references) {
        handleSlicing(dataElement, context).ifPresent(updatedContext -> {
            redactExtensions(dataElement, updatedContext, references);
            if (!dataElement.isPrimitive()) {
                redactChildren(dataElement, updatedContext, references);
            }
        });
    }

    /**
     * Handles slicing resolution for the given element and context.
     * If slicing is applicable and no match is found, redacts the element if required.
     * Unsliced contexts mixed with sliced contexts get passed through to preserve behaviour.
     *
     * @return updated ElementContexts if valid, otherwise Optional empty if element was removed due to slicing
     */
    private Optional<MultiElementContext> handleSlicing(Base dataElement, MultiElementContext context) {
        if (dataElement instanceof Extension extension) {
            // Extensions are never wiped here for failing to match a slice — that's already handled by
            // removeUnknownExtensions at the parent level. But the context must still be narrowed to the
            // matched slice (if any), so that checks against the extension's own nested content (e.g. a
            // composite extension's sub-extensions) resolve against the right element ID instead of an
            // unqualified path that can never resolve.
            return Optional.of(context.mergeWithSlices(context.matchingSlices(extension)));
        }
        if (!context.hasSlicing() || context.ignoreSlicingInRedaction()) {
            return Optional.of(context);
        }
        return context.resolveSlices(dataElement, slices -> {
            if (slices.isEmpty()) {
                removeAllChildren(dataElement);
                if (context.required()) {
                    dataElement.setProperty(EXTENSION, createAbsentReasonExtension(MASKED));
                }
                return true;
            }
            return false;
        });
    }

    private void removeAllChildren(Base base) {
        base.children().stream().flatMap(child -> child.getValues().stream().map(value -> Map.entry(child.getName(), value))).forEach(entry -> base.removeChild(entry.getKey(), entry.getValue()));
    }

    /**
     * Redacts the children of a given FHIR element based on the provided structure definition.
     * <p>
     * Constructs elementids from {@code baseid} and child name.
     * Attempts to look up the corresponding {@link ElementDefinition} from the given {@code definition}.
     * If a definition exists, it sets the type and minimum cardinality with the ones defined there;
     * otherwise, it falls back to the values derived directly from the child element itself.
     *
     * @param baseElement element whose children should be redacted
     * @param contexts    element ID and associated structure definitions
     * @param references  Map of allowed references
     */
    private void redactChildren(Base baseElement, MultiElementContext contexts, Map<String, Set<ExtractionId>> references) {

        baseElement.children().forEach(child -> {
            MultiElementContext childContexts = contexts.descend(child.getName());
            List<String> types = getTypes(child, childContexts.workingCodes());

            if (child.hasValues()) {
                if (types.stream().anyMatch(type -> type.contains("Reference"))) {

                    handleReference(child, childContexts.allowedReferences(references));
                }
                boolean checkSlices = !EXTENSION.equals(child.getName()) && !MODIFIER_EXTENSION.equals(child.getName());
                Set<String> matchedSliceIds = checkSlices ? matchedSliceIds(child, childContexts) : Set.of();
                for (Base value : child.getValues()) {
                    redact(value, childContexts, references);
                }
                // Only flag missing required slices if at least one instance matched some slice; otherwise none of
                // the values addressed slicing at all, and the per-instance masking above already covers them.
                if (!matchedSliceIds.isEmpty()) {
                    childContexts.missingRequiredSlices(matchedSliceIds)
                            .forEach(slice -> addMissingSlice(baseElement, child, slice, childContexts, references));
                }
            } else if (child.getMinCardinality() > 0 || childContexts.required()) {
                addDataAbsentReason(baseElement, child, types.getFirst(), childContexts, references);
            }
        });
    }

    /**
     * Collects the element ids of slices matched by at least one existing value of {@code child}.
     * <p>
     * Must be evaluated before {@code child}'s values are redacted, since redaction wipes the children
     * of instances that match no slice, which would make them unmatchable afterwards.
     * </p>
     */
    private Set<String> matchedSliceIds(Property child, MultiElementContext childContexts) {
        return child.getValues().stream()
                .flatMap(value -> childContexts.matchingSlices(value).stream())
                .map(ElementContext::elementId)
                .collect(Collectors.toSet());
    }

    /**
     * Appends a masked stub value of the given slice's type, representing a required named slice with no
     * matching instance among the existing values of {@code child}.
     * <p>
     * Slices defined via {@code contentReference} rather than {@code type} carry no type of their own; such a
     * slice is logged and skipped, since there is no type to build a masked stub from.
     * </p>
     */
    private void addMissingSlice(Base base, Property child, ElementDefinition slice, MultiElementContext childContexts, Map<String, Set<ExtractionId>> references) {
        List<String> sliceTypes = slice.getType().stream().map(ElementDefinition.TypeRefComponent::getWorkingCode).toList();
        if (sliceTypes.isEmpty()) {
            logger.warn("Missing type for required slice {} in field {} of {}", slice.getId(), child.getName(), base.fhirType());
            return;
        }
        // Redact the stub against the missing slice's own element ID rather than the unsliced childContexts, so
        // requirements the slice adds beyond the base type (e.g. a child required only within this named slice)
        // are also honored when masking the stub's own children.
        MultiElementContext sliceContext = new MultiElementContext(slice.getId(),
                childContexts.contexts().stream().map(ElementContext::definition).toList());
        addDataAbsentReason(base, child, sliceTypes.getFirst(), sliceContext, references);
    }

    /**
     * Adds a DataAbsentReason for a child property of a base.
     * <p>
     * A {@code BackboneElement} stub is appended rather than replacing the property outright, so existing
     * sibling values (e.g. other matched slices already present on a repeating property) are preserved. Its
     * own required children are then recursively redacted, since a bare {@code data-absent-reason} extension
     * on the stub does not by itself satisfy the base FHIR cardinality of children such as {@code component.code}.
     *
     * @param base         the parent of the child
     * @param child        property without values to be checked
     * @param type         type of the child to be handled
     * @param childContexts context describing {@code child}, used to redact a BackboneElement stub's own children
     * @param references   Map of allowed references, forwarded when redacting a BackboneElement stub's children
     */
    private void addDataAbsentReason(Base base, Property child, String type, MultiElementContext childContexts, Map<String, Set<ExtractionId>> references) {
        type = type.replaceFirst("^[^(|]*[(|]", "");
        try {
            if ("BackboneElement".equals(type)) {
                Base stub = ResourceUtils.setField(base, child.getName(), createAbsentReasonExtension(MASKED));
                if (stub != null) {
                    redactChildren(stub, childContexts, references);
                }
            } else {
                Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension(MASKED));
                base.setProperty(child.getName(), element);
            }
        } catch (FHIRException e) {
            logger.warn("Unresolvable elementID {} in field {} Type {} ", base.fhirType(), child.getName(), type);
        }
    }
}
