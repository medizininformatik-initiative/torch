package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.SearchParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

        this.searchParameters = searchParams.getEntry().stream().map(e -> (SearchParameter)e.getResource())
                .filter(this::isValidSearchParam)
                .flatMap(searchParam -> searchParam.getBase().stream().map(base ->
                            Map.entry(
                                    List.of(searchParam.getCode(), searchParam.getType().toCode(), base.getCode()),
                                    getExpression(searchParam.getExpression(), base.getCode())
                            )
                        )
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Preprocesses multiple filters to be ready for evaluation on a specific resource type.
     * The predicate can be evaluated on as many resources as needed.
     *
     * @param attributeGroupFilters the filters to be preprocessed
     * @param resourceType the resource type of the resource that the filters should later be applied on
     * @return the compiled filters containing parsed FHIRPath expressions that are ready to be evaluated
     */
    public Predicate<Resource> compileFilter(List<Filter> attributeGroupFilters, String resourceType) {
        var parsedFhirPaths = attributeGroupFilters.stream()
                .map(f -> searchParameters.get(List.of(f.name(), f.type(), resourceType)))
                .map(this::parseFhirPath).toList();

        return new CompiledFilter(parsedFhirPaths, attributeGroupFilters, fhirPathEngine);
    }

    private IFhirPath.IParsedExpression parseFhirPath(String path) {
        try {
            return fhirPathEngine.parse(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String readSearchParams() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        try {
            return resourceLoader.getResource("classpath:"+searchParametersFile).getContentAsString(Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getExpression(String expressions, String resourceType) {
        return Arrays.stream(expressions.split("\\|"))
                .filter(e -> e.startsWith(resourceType, e.indexOf('.') - resourceType.length()))
                .collect(Collectors.joining(" | "));
    }

    private boolean isValidSearchParam(SearchParameter searchParam) {
        return  searchParam.hasCode() &&
                searchParam.hasType() &&
                searchParam.hasBase() &&
                searchParam.hasExpression() &&
                (   searchParam.getType().equals(Enumerations.SearchParamType.TOKEN) ||
                    searchParam.getType().equals(Enumerations.SearchParamType.DATE));
    }

    private record CompiledFilter(List<IFhirPath. IParsedExpression> parsedFhirPaths,
                                  List<Filter> attributeGroupFilters,
                                  IFhirPath fhirPathEngine) implements Predicate<Resource> {

        @Override
        public boolean test(Resource resource) {
            if (parsedFhirPaths.isEmpty()) {
                logger.warn("Could not find any matching search parameter for filter.");
                return false;
            }

            for(int i = 0; i < attributeGroupFilters.size(); i++) {
                var foundElements = fhirPathEngine.evaluate(resource, parsedFhirPaths.get(i), Base.class);

                int finalI = i;
                var isFilterSatisfied =  foundElements.stream().anyMatch( found-> {
                    var filter = attributeGroupFilters.get(finalI);
                    return switch (filter.type()) {
                        case "token" -> evaluateToken(found, filter);
                        case "date" -> evaluateDate(found, filter);
                        default -> false;
                    };
                });

                if(isFilterSatisfied)
                    return true;
            }
            return false;
        }

        private boolean evaluateToken(Base found, Filter filter) {
            return filter.codes().stream().anyMatch( filterCode -> resourceContainsFilterCode(found, filterCode));
        }

        private boolean resourceContainsFilterCode(Base found, Code filterCode) {
            if (found instanceof CodeableConcept codeableConcept) {
                return codeableConcept.getCoding().stream()
                        .anyMatch(resourceCode->(codingMatchesFilterCode(resourceCode, filterCode)));
            }

            if (found instanceof Coding coding) {
                return codingMatchesFilterCode(coding, filterCode);
            }

            logger.warn("Code filter was not of type 'CodeableConcept' or 'Coding'.");
            return false;
        }

        private boolean codingMatchesFilterCode(Coding a, Code b) {
            return a.getSystem().equals(b.system()) && a.getCode().equals(b.code());
        }

        private boolean greaterEquals(LocalDate resourceStartDate, LocalDate resourceEndDate, LocalDate filterDate) {
            return  resourceStartDate.isAfter(filterDate) || resourceStartDate.isEqual(filterDate) ||
                    resourceEndDate.isAfter(filterDate) || resourceEndDate.isEqual(filterDate);
        }

        private boolean lessEquals(LocalDate resourceStartDate, LocalDate resourceEndDate, LocalDate filterDate) {
            return  resourceStartDate.isBefore(filterDate) || resourceStartDate.isEqual(filterDate) ||
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
                return  greaterEquals(resourceStartDate, resourceEndDate, filter.start());
            }

            if (filter.start() == null && filter.end() != null) {
                return lessEquals(resourceStartDate, resourceEndDate, filter.end());
            }

            return  greaterEquals(resourceStartDate, resourceEndDate, filter.start()) &&
                    lessEquals(resourceStartDate, resourceEndDate, filter.end());
        }

        private boolean evaluateDate(Base found, Filter filter) {
            if (found instanceof DateTimeType date) {
                return evaluatePeriod(date.getValue(), date.getValue(), filter);
            }

            if (found instanceof Period period) {
                return evaluatePeriod(period.getStart(), period.getEnd(), filter);
            }

            logger.warn("Date filter was not of type 'DateTimeType' or 'Period'.");
            return false;
        }

    }
}
