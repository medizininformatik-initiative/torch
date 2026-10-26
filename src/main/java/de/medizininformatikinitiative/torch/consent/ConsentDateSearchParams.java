package de.medizininformatikinitiative.torch.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The FHIR search param that searches the consent date of each resource type, derived from the date fields of
 * {@code type_to_consent.json}.
 *
 * <p>A search param is derived for a type if exactly one date search param of the FHIR R4 spec has a path equal to the
 * type's date field. Types whose date field no search param covers exactly cannot be checked against consent on the
 * FHIR server, e.g. {@code RiskAssessment.occurrence}, whose R4 search param {@code date} only covers
 * {@code occurrence as dateTime}. Types without a date field need no search param.
 */
public class ConsentDateSearchParams {

    private static final Logger logger = LoggerFactory.getLogger(ConsentDateSearchParams.class);

    private final Map<String, String> searchParams;

    /**
     * @param dateFields the consent date FHIRPath per resource type, {@code ""} for types without consent date
     * @throws IllegalArgumentException if more than one search param matches the date field of a type
     */
    public ConsentDateSearchParams(FhirContext ctx, Map<String, String> dateFields) {
        var params = new TreeMap<String, String>();
        var notCheckable = new ArrayList<String>();
        dateFields.forEach((type, dateField) -> {
            if (dateField.isEmpty()) {
                return;
            }
            var matches = matchingDateSearchParams(ctx, type, dateField);
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Consent date field %s of %s is searched by more than one search param: %s"
                        .formatted(dateField, type, matches));
            }
            if (matches.isEmpty()) {
                notCheckable.add(type + " (" + dateField + ")");
            } else {
                params.put(type, matches.getFirst());
            }
        });
        if (!notCheckable.isEmpty()) {
            logger.info("No search param covers the consent date of {}; inclusion criteria on these types are rejected",
                    notCheckable);
        }
        this.searchParams = Map.copyOf(params);
    }

    private static List<String> matchingDateSearchParams(FhirContext ctx, String type, String dateField) {
        return ctx.getResourceDefinition(type).getSearchParams().stream()
                .filter(param -> param.getParamType() == RestSearchParameterTypeEnum.DATE)
                .filter(param -> param.getPathsSplitForResourceType(type).contains(dateField))
                .map(RuntimeSearchParam::getName)
                .sorted()
                .toList();
    }

    /**
     * @return the derived search param per resource type with a date field
     */
    public Map<String, String> searchParams() {
        return searchParams;
    }

    /**
     * @param resourceType the FHIR resource type
     * @return the search param of the type's consent date, or empty if the type has no date field or no search param
     * covers it
     */
    public Optional<String> searchParam(String resourceType) {
        return Optional.ofNullable(searchParams.get(resourceType));
    }
}
