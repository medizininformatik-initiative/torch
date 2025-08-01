package de.medizininformatikinitiative.torch.model.crtdl.annotated;


import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.EMPTY;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

public record AnnotatedAttributeGroup(
        String name,
        String id,
        String resourceType, String groupReference,
        List<AnnotatedAttribute> attributes,
        List<Filter> filter,
        Predicate<Resource> compiledFilter,
        boolean includeReferenceOnly) {


    public static final String PATIENT = "Patient";

    public AnnotatedAttributeGroup(String id, String resourceType,
                                   String groupReference,
                                   List<AnnotatedAttribute> attributes,
                                   List<Filter> filter,
                                   Predicate<Resource> compiledFilter) {
        this("", id, resourceType, groupReference, attributes, filter, compiledFilter, false); // Default value for includeReferenceOnly
    }


    /**
     * Canonical Constructor with validation for filter duplicates and UUID generation
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


    public AnnotatedAttributeGroup addAttributes(List<AnnotatedAttribute> newAttributes) {

        List<AnnotatedAttribute> tempAttributes = new ArrayList<>(attributes);
        tempAttributes.addAll(newAttributes);
        return new AnnotatedAttributeGroup(name, id, resourceType, groupReference, tempAttributes, filter, compiledFilter, includeReferenceOnly);
    }

    public AnnotatedAttributeGroup setCompiledFilter(Predicate<Resource> compiledFilter) {
        return new AnnotatedAttributeGroup(name, id, resourceType, groupReference, attributes, filter, compiledFilter, includeReferenceOnly);
    }

    public boolean hasMustHave() {
        return attributes.stream().anyMatch(AnnotatedAttribute::mustHave);
    }


    public List<AnnotatedAttribute> refAttributes() {
        return attributes.stream().filter(annotatedAttribute -> !annotatedAttribute.linkedGroups().isEmpty()).toList();
    }

}
