package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * A Service that handles filter preprocessing.
 */
@Service
public class FilterService {

    private static final Logger logger = LoggerFactory.getLogger(FilterService.class);

    private final FhirContext fhirContext;
    private final IFhirPath fhirPathEngine;
    private final String searchParametersFile;

    /**
     * Maps filter code, filter type and resource type (in this exact order) to an expression.
     */
    private Map<List<String>, String> searchParameters;

    public FilterService(FhirContext fhirContext, String searchParametersFile) {
        this.searchParametersFile = searchParametersFile;
        this.fhirContext = fhirContext;
        this.fhirPathEngine = fhirContext.newFhirPath();
    }

    @PostConstruct
    public void init() {
        IParser parser = fhirContext.newJsonParser();
        Bundle searchParams = parser.parseResource(Bundle.class, readSearchParams());

        this.searchParameters = searchParams.getEntry().stream().map(e -> (SearchParameter) e.getResource())
                .filter(FilterService::isValidSearchParam)
                .flatMap(searchParam -> searchParam.getBase().stream().map(base ->
                                Map.entry(
                                        List.of(searchParam.getCode(), searchParam.getType().toCode(), base.getCode()),
                                        buildExpression(searchParam.getExpression(), base.getCode())
                                )
                        )
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Preprocesses multiple filters to be ready for evaluation on a specific resource type.
     * The predicate can be evaluated on as many resources as needed.
     *
     * @param attributeGroupFilters     the filters to be preprocessed
     * @param resourceType              the resource type of the resource that the filters should later be applied on
     * @return                          the compiled filters containing parsed FHIRPath expressions that are ready to be evaluated
     * @throws IllegalArgumentException if attributeGroupFilters is null or empty
     */
    public Predicate<Resource> compileFilter(List<Filter> attributeGroupFilters, String resourceType) {
        if (attributeGroupFilters == null || attributeGroupFilters.isEmpty()) {
            throw new IllegalArgumentException("An empty list of filters can't be compiled.");
        }
        var parsedFilters = attributeGroupFilters.stream()
                .map(filter ->
                        new ParsedFilter(
                                filter,
                                parseFhirPath(searchParameters.get(List.of(filter.name(), filter.type(), resourceType)))))
                .toList();

        return new CompiledFilter(parsedFilters, fhirPathEngine);
    }

    private IFhirPath.IParsedExpression parseFhirPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Could not find any matching search parameter for filter. This can later " +
                    "result in unexpected false negative results of the 'test' method of 'CompiledFilter'.");
        }

        try {
            return fhirPathEngine.parse(path);
        } catch (Exception e) {
            // FHIRPath expressions from FHIR search parameters should usually never fail parsing
            throw new RuntimeException("Unexpected parsing error occurred", e);
        }
    }

    private String readSearchParams() {
        try {
            ClassPathResource resource = new ClassPathResource(searchParametersFile);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String buildExpression(String expressions, String resourceType) {
        return Arrays.stream(expressions.split("\\|"))
                .filter(e -> e.startsWith(resourceType, e.indexOf('.') - resourceType.length()))
                .collect(Collectors.joining(" | "));
    }

    private static boolean isValidSearchParam(SearchParameter searchParam) {
        return searchParam.hasCode() &&
                searchParam.hasType() &&
                searchParam.hasBase() &&
                searchParam.hasExpression() &&
                (searchParam.getType().equals(Enumerations.SearchParamType.TOKEN) ||
                        searchParam.getType().equals(Enumerations.SearchParamType.DATE));
    }

    private record CompiledFilter(List<ParsedFilter> filters,
                                  IFhirPath fhirPathEngine) implements Predicate<Resource> {

        public CompiledFilter {
            if (filters.isEmpty()) {
                throw new IllegalArgumentException("CompiledFilter must have at least one parsed filter, but got none.");
            }
            requireNonNull(fhirPathEngine);
        }

        @Override
        public boolean test(Resource resource) {


            for (ParsedFilter parsedFilter : filters) {
                var foundElements = fhirPathEngine.evaluate(resource, parsedFilter.parsedExpression, Base.class);

                var isFilterSatisfied = foundElements.stream().anyMatch(found -> switch (parsedFilter.filter.type()) {
                    case "token" -> evaluateToken(found, parsedFilter.filter);
                    case "date" -> evaluateDate(found, parsedFilter.filter);
                    default -> false;
                });

                if (!isFilterSatisfied)
                    return false;
            }
            return true;
        }

        private boolean evaluateToken(Base found, Filter filter) {
            return filter.codes().stream().anyMatch(filterCode -> resourceContainsFilterCode(found, filterCode));
        }

        private boolean resourceContainsFilterCode(Base found, Code filterCode) {
            if (found instanceof CodeableConcept codeableConcept) {
                return codeableConcept.getCoding().stream()
                        .anyMatch(resourceCode -> (codingMatchesFilterCode(resourceCode, filterCode)));
            }

            if (found instanceof Coding coding) {
                return codingMatchesFilterCode(coding, filterCode);
            }

            logger.warn("Expected Code filter to be of type 'CodeableConcept' or 'Coding', but was {}.", found.fhirType());
            return false;
        }

        private boolean codingMatchesFilterCode(Coding a, Code b) {
            return a.getSystem().equals(b.system()) && a.getCode().equals(b.code());
        }

        private boolean greaterEquals(LocalDate resourceStartDate, LocalDate resourceEndDate, LocalDate filterDate) {
            return resourceStartDate.isAfter(filterDate) || resourceStartDate.isEqual(filterDate) ||
                    resourceEndDate.isAfter(filterDate) || resourceEndDate.isEqual(filterDate);
        }

        private boolean lessEquals(LocalDate resourceStartDate, LocalDate resourceEndDate, LocalDate filterDate) {
            return resourceStartDate.isBefore(filterDate) || resourceStartDate.isEqual(filterDate) ||
                    resourceEndDate.isBefore(filterDate) || resourceEndDate.isEqual(filterDate);
        }

        private boolean evaluatePeriod(Date periodStart, Date periodEnd, Filter filter) {
            var resourceStartDate = periodStart.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            var resourceEndDate = periodEnd.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            if (filter.start() == null && filter.end() == null) {
                logger.warn("Both start and end of filter are null.");
                return false;
            }

            if (filter.start() != null && filter.end() == null) {
                return greaterEquals(resourceStartDate, resourceEndDate, filter.start());
            }

            if (filter.start() == null && filter.end() != null) {
                return lessEquals(resourceStartDate, resourceEndDate, filter.end());
            }

            return greaterEquals(resourceStartDate, resourceEndDate, filter.start()) &&
                    lessEquals(resourceStartDate, resourceEndDate, filter.end());
        }

        private boolean evaluateDate(Base found, Filter filter) {
            if (found instanceof DateTimeType date) {
                return evaluatePeriod(date.getValue(), date.getValue(), filter);
            }

            if (found instanceof DateType date) {
                return evaluatePeriod(date.getValue(), date.getValue(), filter);
            }

            if (found instanceof Period period) {
                return evaluatePeriod(period.getStart(), period.getEnd(), filter);
            }

            logger.warn("Date filter was not of type 'DateTimeType' or 'Period'.");
            return false;
        }
    }

    private record ParsedFilter(Filter filter, IFhirPath.IParsedExpression parsedExpression) {
        public ParsedFilter {
            requireNonNull(filter);
            requireNonNull(parsedExpression);
        }
    }
}
