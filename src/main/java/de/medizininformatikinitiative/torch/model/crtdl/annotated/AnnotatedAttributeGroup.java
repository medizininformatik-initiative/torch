package de.medizininformatikinitiative.torch.model.crtdl.annotated;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.crtdl.FieldCondition;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.EMPTY;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

/**
 * Annotated representation of a CRTDL {@code AttributeGroup} used during extraction.
 *
 * <p>This record combines:
 * <ul>
 *   <li>Group identity and FHIR context ({@link #resourceType()}, {@link #groupReference()})</li>
 *   <li>The list of {@link AnnotatedAttribute}s to extract</li>
 *   <li>Declared {@link Filter}s and their derived query/search parameters</li>
 *   <li>Runtime-only helper {@link #copyTree()})</li>
 * </ul>
 *
 * <h2>Serialization caveat (important)</h2>
 * <p> {@link #copyTree()} are annotated with {@link JsonIgnore} because they are
 * runtime constructs:
 * <ul>
 *   <li>{@code copyTree} is a derived helper structure used to efficiently drive copy/redaction traversal.</li>
 * </ul>
 *
 * <p>That means: when an {@code AnnotatedAttributeGroup} is loaded from JSON, these fields will be {@code null}/empty
 * and must be rebuilt before use. Use  {@link #withTree()})
 * after deserialization.
 */
public record AnnotatedAttributeGroup(
        @JsonProperty String name,
        @JsonProperty String id,
        @JsonProperty String resourceType,
        @JsonProperty String groupReference,
        @JsonProperty List<AnnotatedAttribute> attributes,
        @JsonProperty List<Filter> filter,
        @JsonProperty boolean includeReferenceOnly,
        @JsonIgnore Optional<CopyTreeNode> copyTree) {


    /**
     * FHIR resource type literal for Patient.
     *
     * <p>Patient is treated specially: queries for the Patient group are not filtered by date/code filters
     * in {@link #queries(DseMappingTreeBase, String)} / {@link #queryParams(DseMappingTreeBase)}.
     */
    public static final String PATIENT = "Patient";

    /**
     * Convenience constructor without {@code name} and without copy-tree.
     *
     * <p>Useful for tests or internal creation where the caller already has a compiled filter.</p>
     *
     * @param id             group id (stable identifier in CRTDL)
     * @param resourceType   FHIR resource type of the group (e.g. "Observation")
     * @param groupReference canonical group/profile reference (used for {@code _profile:below})
     * @param attributes     attributes to extract
     * @param filter         declared filters (may be null → treated as empty)
     */
    public AnnotatedAttributeGroup(String id,
                                   String resourceType,
                                   String groupReference,
                                   List<AnnotatedAttribute> attributes,
                                   List<Filter> filter) {
        this("", id, resourceType, groupReference, attributes, filter, false, Optional.of(buildTree(attributes, resourceType)));
    }

    public AnnotatedAttributeGroup(String name, String id,
                                   String resourceType,
                                   String groupReference,
                                   List<AnnotatedAttribute> attributes,
                                   List<Filter> filter,
                                   boolean includeReferenceOnly) {
        this(name, id, resourceType, groupReference, attributes, filter, includeReferenceOnly, Optional.of(buildTree(attributes, resourceType)));
    }


    /**
     * Canonical constructor.
     *
     * <p>Performs basic validation and normalization:
     * <ul>
     *   <li>{@code id} and {@code groupReference} must be non-null</li>
     *   <li>{@code attributes} and {@code filter} are defensively copied and made unmodifiable</li>
     *   <li>At most one {@code date} filter is allowed</li>
     * </ul>
     *
     * @throws NullPointerException     if {@code id} or {@code groupReference} is null
     * @throws IllegalArgumentException if multiple date filters are present
     */
    public AnnotatedAttributeGroup {
        requireNonNull(id);
        requireNonNull(groupReference);
        attributes = List.copyOf(attributes);
        filter = filter == null ? List.of() : List.copyOf(filter);
        if (containsDuplicateDateFilters(filter)) {
            throw new IllegalArgumentException("Duplicate date type filter found");
        }
    }

    /**
     * Returns the derived copy-tree.
     *
     * <p>This value is ignored during JSON (de)serialization. After reading an instance from JSON,
     * the tree must be rebuilt via {@link #withTree()}.</p>
     */
    @JsonIgnore
    public Optional<CopyTreeNode> copyTree() {
        return copyTree;
    }

    private static boolean containsDuplicateDateFilters(List<Filter> filters) {
        return filters.stream().filter(filter -> "date".equals(filter.type())).count() > 1;
    }

    public List<Query> queries(DseMappingTreeBase mappingTreeBase, String resourceType) {
        if (resourceType.equals(PATIENT)) {
            return List.of(Query.ofType(PATIENT));
        }
        return queryParams(mappingTreeBase).stream()
                .map(params -> Query.of(resourceType, params))
                .toList();
    }

    private static boolean sameAttributeIdentity(AnnotatedAttribute a, AnnotatedAttribute b) {
        return a.attributeRef().equals(b.attributeRef())
                && new LinkedHashSet<>(a.linkedGroups())
                .equals(new LinkedHashSet<>(b.linkedGroups()));
    }

    public static CopyTreeNode buildTree(List<AnnotatedAttribute> attributes, String resourceType) {
        CopyTreeNode root = new CopyTreeNode(resourceType);

        for (AnnotatedAttribute attr : attributes) {
            List<FieldCondition> parts = FieldCondition.splitFhirPath(attr);
            CopyTreeNode current = root;
            for (int i = 1; i < parts.size(); i++) {
                current = current.getOrCreateChild(parts.get(i));
            }
        }
        return root;
    }

    private List<QueryParams> queryParams(DseMappingTreeBase mappingTreeBase) {
        List<QueryParams> codeParams = filter.stream()
                .filter(f -> "token".equals(f.type()))
                .flatMap(f -> f.codeFilter(mappingTreeBase).split())
                .toList();

        QueryParams dateParams = PATIENT.equals(resourceType()) ? EMPTY : filter.stream()
                                                                          .filter(f -> "date".equals(f.type()))
                                                                          .findFirst()
                                                                          .map(Filter::dateFilter)
                                                                          .orElse(EMPTY);

        if (codeParams.isEmpty()) {
            // Add a single QueryParams with the date filter (if available) and profile parameter
            return List.of(dateParams.appendParam("_profile:below", stringValue(groupReference)));
        } else {
            return codeParams.stream()
                    .map(p -> p.appendParams(dateParams).appendParam("_profile:below", stringValue(groupReference)))
                    .toList();
        }
    }

    /**
     * Returns a new group whose attribute list is the merge of this group's existing attributes
     * and {@code newAttributes}.
     *
     * <h2>Merge semantics</h2>
     * <ul>
     *   <li>Two attributes are considered identical if both their
     *       {@link AnnotatedAttribute#attributeRef()} and their {@link AnnotatedAttribute#linkedGroups()}
     *       (as sets) are equal.</li>
     *   <li>Only identical attributes are merged.</li>
     *   <li>Attributes with the same {@code attributeRef} but different {@code linkedGroups}
     *       are treated as distinct entries and are <b>not merged</b>.</li>
     * </ul>
     *
     * <h2>Merge behavior</h2>
     * <ul>
     *   <li>{@code mustHave} — combined using logical OR
     *       ({@code existing.mustHave() || incoming.mustHave()})</li>
     *   <li>{@code fhirPath} — taken from the incoming attribute</li>
     *   <li>{@code linkedGroups} — unchanged (no union is performed)</li>
     * </ul>
     *
     * <h2>Rationale</h2>
     * <p>{@code linkedGroups} represent reference contexts and must not be merged implicitly.
     * This avoids mixing distinct reference semantics. The method therefore minimizes duplicates
     * while preserving context separation.</p>
     *
     * <h2>Examples</h2>
     * <pre>
     * Same attributeRef + same linkedGroups:
     *   A([], mustHave=true) + A([], mustHave=false)
     *   → A([], mustHave=true)
     *
     * Same attributeRef + different linkedGroups:
     *   A([], mustHave=true) + A(["X"], mustHave=false)
     *   → two separate entries
     * </pre>
     *
     * @param newAttributes attributes to add or merge into this group
     * @return a new {@code AnnotatedAttributeGroup} with the merged attribute list
     */
    public AnnotatedAttributeGroup addAttributes(List<AnnotatedAttribute> newAttributes) {
        List<AnnotatedAttribute> result = new ArrayList<>(attributes);

        for (AnnotatedAttribute incoming : newAttributes) {
            int matchIndex = -1;

            for (int i = 0; i < result.size(); i++) {
                AnnotatedAttribute candidate = result.get(i);

                if (sameAttributeIdentity(candidate, incoming)) {
                    matchIndex = i;
                    break;
                }
            }

            if (matchIndex < 0) {
                result.add(incoming);
            } else {
                AnnotatedAttribute existing = result.get(matchIndex);
                result.set(matchIndex, new AnnotatedAttribute(
                        existing.attributeRef(),
                        incoming.fhirPath(),
                        existing.mustHave() || incoming.mustHave(),
                        existing.linkedGroups()
                ));
            }
        }

        return new AnnotatedAttributeGroup(
                name, id, resourceType, groupReference, result, filter, includeReferenceOnly
        );
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(AnnotatedAttribute::mustHave);
    }


    public List<AnnotatedAttribute> refAttributes() {
        return attributes.stream().filter(annotatedAttribute -> !annotatedAttribute.linkedGroups().isEmpty()).toList();
    }


    public AnnotatedAttributeGroup withTree() {
        return new AnnotatedAttributeGroup(
                name, id, resourceType, groupReference, attributes, filter, includeReferenceOnly, Optional.of(buildTree(attributes, resourceType)));
    }

}
