package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.mapping.MappingException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The FHIR search param that searches the consent date field of each resource type, used to check inclusion
 * criteria against the consent window on the FHIR server.
 *
 * <p>Complements {@code type_to_consent.json}, which maps each resource type to its consent date field, with
 * {@code type_to_consent_search_param.json}, which maps each type with a date field to its search param.
 */
public final class ConsentSearchParams {

    private final Set<String> mappedTypes;
    private final Map<String, String> searchParams;

    private ConsentSearchParams(Set<String> mappedTypes, Map<String, String> searchParams) {
        this.mappedTypes = Set.copyOf(mappedTypes);
        this.searchParams = Map.copyOf(searchParams);
    }

    /**
     * @param dateFields   the consent date field per resource type, empty for types without consent date
     * @param searchParams the search param per resource type with a consent date field
     * @throws IllegalArgumentException if a type with a date field has no or a blank search param, or a type without
     *                                  date field has a search param
     */
    public static ConsentSearchParams of(Map<String, String> dateFields, Map<String, String> searchParams) {
        var missing = new TreeSet<String>();
        dateFields.forEach((type, dateField) -> {
            if (hasDate(dateField) && (!searchParams.containsKey(type) || searchParams.get(type).isBlank())) {
                missing.add(type);
            }
        });
        var superfluous = new TreeSet<String>();
        searchParams.keySet().forEach(type -> {
            if (!hasDate(dateFields.get(type))) {
                superfluous.add(type);
            }
        });
        if (!missing.isEmpty() || !superfluous.isEmpty()) {
            throw new IllegalArgumentException("Consent search params do not match the consent date fields: "
                    + "types with date field but without search param " + missing
                    + ", types with search param but without date field " + superfluous);
        }
        return new ConsentSearchParams(dateFields.keySet(), searchParams);
    }

    private static boolean hasDate(String dateField) {
        return dateField != null && !dateField.isEmpty();
    }

    /**
     * @param resourceType the FHIR resource type
     * @return the search param of the type's consent date, or empty if the type has no consent date
     * @throws MappingException if the resource type has no consent date mapping
     */
    public Optional<String> dateSearchParam(String resourceType) throws MappingException {
        if (!mappedTypes.contains(resourceType)) {
            throw new MappingException("No consent date mapping for resource type " + resourceType);
        }
        return Optional.ofNullable(searchParams.get(resourceType));
    }
}
