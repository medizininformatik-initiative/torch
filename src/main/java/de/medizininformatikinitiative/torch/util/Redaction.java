package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionCause;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionIssues;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.ElementContext;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.MultiElementContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.BackboneElement;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
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
    private static final String DIV = "div";
    private static final String BACKBONE_ELEMENT = "BackboneElement";
    private static final String MASKED_DIV = "<div xmlns=\"http://www.w3.org/1999/xhtml\">masked</div>";

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
     * @param child    the property containing reference values
     * @param allowed  the set of allowed reference strings
     * @param resource the resource {@code child} belongs to, used only for warning context
     */
    private void handleReference(Property child, Set<ExtractionId> allowed, DomainResource resource) {
        child.getValues().forEach(referenceValue -> {
            if (!(referenceValue instanceof Reference reference) || !reference.hasReference()) {
                return;
            }

            String refString = reference.getReference();
            if (refString == null) {
                // hasReference() is true for a valueless reference StringType carrying only extensions
                // (e.g. an already-masked data-absent-reason reference); nothing to check against allowed.
                logger.warn("REDACTION_03 Reference without a value (data-absent-reason only) in field {} of Resource {} {}",
                        child.getName(), resource.getResourceType(), resource.getId());
                return;
            }
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
        if (!collectedTypes.isEmpty()) {
            return collectedTypes;
        }
        // HAPI's generated model gives BackboneElement children an empty type code
        return child.getTypeCode().isEmpty() ? List.of(BACKBONE_ELEMENT) : List.of(child.getTypeCode().split("\\|"));
    }

    /**
     * Redacts like {@link #redact(ExtractionRedactionWrapper, RedactionIssues)}, discarding the recorded issues.
     */
    DomainResource redact(ExtractionRedactionWrapper wrapper) throws RedactionException {
        return redact(wrapper, new RedactionIssues());
    }

    /**
     * Redacts a FHIR resource using structure definitions and allowed references.
     * <p>
     * Assumes {@code wrapper}'s resource-profile association was already validated when it was built
     * (see {@link ExtractionRedactionWrapper#of}). Resolves structure definitions for the requested
     * profiles and uses them to redact the resource, filling required fields with Data Absent Reasons
     * where necessary. Every required element that could not be masked, and is therefore left absent or invalid
     * in the returned resource, is recorded in {@code issues}.
     * </p>
     *
     * @param wrapper the wrapper containing the resource, profiles, and allowed references
     * @param issues  thread-safe collector, may be shared by resources redacted in parallel
     * @return the redacted resource with required fields fulfilled
     * @throws RedactionException if the requested profiles are unknown
     */
    public DomainResource redact(ExtractionRedactionWrapper wrapper, RedactionIssues issues) throws RedactionException {
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
        this.redact(resource, new MultiElementContext(String.valueOf(resource.getResourceType()), definitions), wrapper.references(), resource, issues);
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
     * @param resource   the resource {@code base} belongs to, used for warning context and in {@code issues}
     * @param issues     collects required elements left absent or invalid
     */
    private void redactExtensions(Base base, MultiElementContext context, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
        MultiElementContext extensionsContext = context.descend(EXTENSION);
        removeUnknownExtensions(base, extensionsContext);
        redactKnownExtensions(base, extensionsContext, references, resource, issues);
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
     * @param resource   the resource {@code base} belongs to, used for warning context and in {@code issues}
     * @param issues     collects required elements left absent or invalid
     */
    private void redactKnownExtensions(Base base, MultiElementContext context, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
        getExtensions(base).forEach(extension -> {
            redactChildren(extension, context, references, resource, issues);
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
     * @param resource    the resource {@code dataElement} belongs to, used for warning context and in {@code issues}
     * @param issues      collects required elements left absent or invalid
     */
    private void redact(Base dataElement, MultiElementContext context, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
        handleSlicing(dataElement, context, references, resource, issues).ifPresent(updatedContext -> {
            redactExtensions(dataElement, updatedContext, references, resource, issues);
            if (!dataElement.isPrimitive()) {
                redactChildren(dataElement, updatedContext, references, resource, issues);
            }
        });
    }

    /**
     * Handles slicing resolution for the given element and context.
     * If slicing is applicable and no match is found, the element's children are wiped; a required element is
     * then masked like a stub (see {@link #addRequiredChildren}).
     * Unsliced contexts mixed with sliced contexts get passed through to preserve behaviour.
     *
     * @return updated ElementContexts if valid, otherwise Optional empty if element was removed due to slicing
     */
    private Optional<MultiElementContext> handleSlicing(Base dataElement, MultiElementContext context, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
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
                    // dataElement is already attached here, so a failed child cannot detach it
                    addRequiredChildren(dataElement, context, references, resource, issues).ifPresent(cause -> {
                        logger.warn("Unmaskable elementID {} Type {}: a base-required child cannot be masked", elementId(context), dataElement.fhirType());
                        recordViolation(issues, resource, context, typeOf(dataElement), MaskFailure.childFailed(cause));
                    });
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Strips the reference targets that HAPI type codes name, e.g. {@code Reference(Practitioner} or
     * {@code canonical(Questionnaire)}.
     */
    private static String withoutTargets(String type) {
        return type.replaceFirst("\\(.*", "");
    }

    /**
     * HAPI's {@code fhirType()} names a BackboneElement by its path (e.g. {@code Observation.component}).
     */
    private static String typeOf(Base element) {
        return element instanceof BackboneElement ? BACKBONE_ELEMENT : element.fhirType();
    }

    private static String elementId(MultiElementContext context) {
        return context.contexts().getFirst().elementId();
    }

    private static void recordViolation(RedactionIssues issues, DomainResource resource, MultiElementContext context, String type,
                                        MaskFailure failure) {
        issues.record(resource, elementId(context), withoutTargets(type), failure.reason(), failure.cause());
    }

    /**
     * Why no masked placeholder could be built for an element.
     *
     * @param cause for {@link RedactionViolationReason#UNMASKABLE_REQUIRED_CHILD}, the innermost child that failed;
     *              {@code null} if the element failed itself
     */
    private record MaskFailure(RedactionViolationReason reason, RedactionCause cause) {

        static MaskFailure of(RedactionViolationReason reason) {
            return new MaskFailure(reason, null);
        }

        static MaskFailure childFailed(RedactionCause cause) {
            return new MaskFailure(RedactionViolationReason.UNMASKABLE_REQUIRED_CHILD, cause);
        }

        /**
         * @return the innermost failed element, given that this failure happened at {@code context} with {@code type}
         */
        RedactionCause innermost(MultiElementContext context, String type) {
            return cause != null ? cause : new RedactionCause(elementId(context), withoutTargets(type), reason);
        }
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
     * @param resource    the resource {@code baseElement} belongs to, used for warning context and in {@code issues}
     * @param issues      collects required elements left absent or invalid
     */
    private void redactChildren(Base baseElement, MultiElementContext contexts, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {

        baseElement.children().forEach(child -> {
            MultiElementContext childContexts = contexts.descend(child.getName());
            List<String> types = getTypes(child, childContexts.workingCodes());

            boolean isExtensionSlot = EXTENSION.equals(child.getName()) || MODIFIER_EXTENSION.equals(child.getName());
            if (child.hasValues()) {
                if (types.stream().anyMatch(type -> type.contains("Reference"))) {

                    handleReference(child, childContexts.allowedReferences(references), resource);
                }
                boolean checkSlices = !isExtensionSlot;
                Set<String> matchedSliceIds = checkSlices ? matchedSliceIds(child, childContexts) : Set.of();
                for (Base value : child.getValues()) {
                    redact(value, childContexts, references, resource, issues);
                }
                // Only flag missing required slices if at least one instance matched some slice; otherwise none of
                // the values addressed slicing at all, and the per-instance masking above already covers them.
                if (!matchedSliceIds.isEmpty()) {
                    childContexts.missingRequiredSlices(matchedSliceIds)
                            .forEach(slice -> addMissingSlice(baseElement, child, slice, childContexts, references, resource, issues));
                }
            // extension/modifierExtension are handled by the URL-aware pipeline (redactExtensions), not the
            // generic slice/DAR machinery here: an anonymous Extension has no url of its own and would
            // serialize as {"url": null, ...} (#1230), which no masked stub can represent.
            } else if (!isExtensionSlot && (HapiFactory.isRequiredChild(baseElement, child.getName()) || childContexts.required())) {
                addDataAbsentReason(baseElement, child, types.getFirst(), childContexts, references, resource, issues)
                        .ifPresent(failure -> recordViolation(issues, resource, childContexts, types.getFirst(), failure));
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
    private void addMissingSlice(Base base, Property child, ElementDefinition slice, MultiElementContext childContexts, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
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
        addDataAbsentReason(base, child, sliceTypes.getFirst(), sliceContext, references, resource, issues)
                .ifPresent(failure -> recordViolation(issues, resource, sliceContext, sliceTypes.getFirst(), failure));
    }

    /**
     * Adds a DataAbsentReason for a child property of a base.
     * <p>
     * A {@code BackboneElement} stub is appended rather than replacing the property outright, so existing
     * sibling values (e.g. other matched slices already present on a repeating property) are preserved. Since a
     * bare {@code data-absent-reason} extension does not by itself satisfy the cardinality of required children such
     * as {@code component.code}, every stub gets its required children via {@link #addRequiredChildren}; a stub of
     * any other type than {@code BackboneElement} is only attached if all of its base-required children could be filled.
     * The element itself is always masked, never filled with a profile {@code fixed[x]}/{@code pattern[x]} value.
     * <p>
     * A failure is not recorded here but returned, since only the outermost caller knows whether the element ends
     * up absent. Two cases are recorded here because the stub stays in the output: a {@code BackboneElement} stub
     * missing a base-required child, and the issues of a non-Backbone stub's children once that stub is attached.
     *
     * @param base         the parent of the child
     * @param child        property without values to be checked
     * @param type         type of the child to be handled
     * @param childContexts context describing {@code child}, used to fill the stub's own required children
     * @param references   Map of allowed references, forwarded when filling the stub's required children
     * @param resource     the resource {@code base} belongs to, used for warning context and in {@code issues}
     * @param issues       collects required elements left absent or invalid inside the attached stub
     * @return empty if a stub was attached, otherwise why none could be built
     */
    private Optional<MaskFailure> addDataAbsentReason(Base base, Property child, String type, MultiElementContext childContexts, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
        type = withoutTargets(type);
        try {
            if (BACKBONE_ELEMENT.equals(type)) {
                Base stub = ResourceUtils.setField(base, child.getName(), createAbsentReasonExtension(MASKED));
                if (stub == null) {
                    return Optional.of(MaskFailure.of(RedactionViolationReason.NO_SETTER));
                }
                String stubType = type;
                // the stub is already attached here, so a failed child cannot detach it
                addRequiredChildren(stub, childContexts, references, resource, issues).ifPresent(cause -> {
                    logger.warn("Unmaskable elementID {} in field {} Type {}: a base-required child cannot be masked", base.fhirType(), child.getName(), stubType);
                    recordViolation(issues, resource, childContexts, stubType, MaskFailure.childFailed(cause));
                });
                return Optional.empty();
            }
            Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension(MASKED));
            // the stub's children are only reported if the stub is attached
            RedactionIssues stubIssues = new RedactionIssues();
            Optional<RedactionCause> failedChild = addRequiredChildren(element, childContexts, references, resource, stubIssues);
            if (failedChild.isPresent()) {
                logger.warn("Unmaskable elementID {} in field {} Type {}: a base-required child cannot be masked", base.fhirType(), child.getName(), type);
                return Optional.of(MaskFailure.childFailed(failedChild.get()));
            }
            base.setProperty(child.getName(), element);
            issues.addAll(stubIssues);
            return Optional.empty();
        } catch (FHIRException e) {
            logger.warn("Unresolvable elementID {} in field {} Type {} ", base.fhirType(), child.getName(), type);
            return Optional.of(MaskFailure.of(RedactionViolationReason.UNRESOLVABLE_TYPE));
        }
    }

    /**
     * Completes a masked element whose own children are absent, so that it still satisfies the cardinality of its
     * required children (#1378).
     * <p>
     * A child is required if the base FHIR specification or any profile in {@code context} demands it. Each required
     * child is filled via {@link #fillRequiredChild}, which recurses into its own required children. The
     * {@code Narrative.div} of a masked Narrative gets a placeholder, since xhtml cannot carry a {@code data-absent-reason}.
     * {@code modifierExtension} stays absent.
     *
     * @param element masked element to complete; children that already have values are left untouched
     * @param context context describing {@code element}
     * @param issues  receives a child only the profile requires that could not be filled; a failed
     *                base-required child is left for the caller to report
     * @return the innermost failed element if a child required by the base specification could not be filled, leaving
     * {@code element} invalid; empty otherwise, since a failure on a child only the profile requires does not count
     */
    private Optional<RedactionCause> addRequiredChildren(Base element, MultiElementContext context, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
        for (Property child : element.children()) {
            // HAPI always reports Narrative.div as having a value, so it is checked before hasValues()
            if (element instanceof Narrative narrative && DIV.equals(child.getName())) {
                narrative.setDivAsString(MASKED_DIV);
                continue;
            }
            if (child.hasValues() || MODIFIER_EXTENSION.equals(child.getName())) {
                continue;
            }
            MultiElementContext childContexts = context.descend(child.getName());
            boolean baseRequired = HapiFactory.isRequiredChild(element, child.getName());
            if (!baseRequired && !childContexts.required()) {
                continue;
            }
            String type = getTypes(child, childContexts.workingCodes()).getFirst();
            Optional<MaskFailure> failure = fillRequiredChild(element, child, type, childContexts, references, resource, issues);
            if (failure.isPresent()) {
                if (baseRequired) {
                    return Optional.of(failure.get().innermost(childContexts, type));
                }
                recordViolation(issues, resource, childContexts, type, failure.get());
            }
        }
        return Optional.empty();
    }

    /**
     * Fills an absent required child with a copy of its profile {@code fixed[x]} or {@code pattern[x]} value, or
     * masks it via {@link #addDataAbsentReason} if no profile gives one. If several profiles give a value, the
     * first profile in {@code childContexts} wins; a resource cannot conform to profiles with conflicting values.
     *
     * @return empty if the child was filled, otherwise why no value could be built for it
     */
    private Optional<MaskFailure> fillRequiredChild(Base base, Property child, String type, MultiElementContext childContexts, Map<String, Set<ExtractionId>> references, DomainResource resource, RedactionIssues issues) {
        Optional<Type> fixedValue = childContexts.elementDefinitions()
                .filter(definition -> definition.hasFixed() || definition.hasPattern())
                .map(definition -> definition.hasFixed() ? definition.getFixed() : definition.getPattern())
                .findFirst();
        if (fixedValue.isPresent()) {
            base.setProperty(child.getName(), fixedValue.get().copy());
            return Optional.empty();
        }
        return addDataAbsentReason(base, child, type, childContexts, references, resource, issues);
    }
}
