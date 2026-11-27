package de.medizininformatikinitiative.torch.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.management.PatientLinkResolver;
import de.medizininformatikinitiative.torch.model.consent.ConsentSearchParams;
import de.medizininformatikinitiative.torch.model.mapping.Mapping;
import de.medizininformatikinitiative.torch.model.mapping.MappingContext;
import de.medizininformatikinitiative.torch.model.mapping.MappingTreeBase;
import de.medizininformatikinitiative.torch.model.mapping.MappingTreeModuleRoot;
import de.medizininformatikinitiative.torch.service.CriterionQueryTranslator;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.service.InclusionConsentChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Beans for checking inclusion criteria against consent windows, using Flare's FHIR search translation.
 *
 * <p>Only active if {@code torch.inclusion-consent-check.enabled} is set, since loading the FHIR search mapping
 * takes several seconds and hundreds of megabytes of heap.
 */
@Configuration
@ConditionalOnProperty(prefix = "torch.inclusion-consent-check", name = "enabled", havingValue = "true")
public class InclusionConsentCheckConfig {

    @Bean
    public CriterionQueryTranslator criterionQueryTranslator(ObjectMapper mapper,
                                                             TorchProperties torchProperties,
                                                             InclusionConsentCheckProperties properties) throws IOException {
        var mappings = Arrays.stream(mapper.readValue(new File(properties.fhirMappingsFile()), Mapping[].class))
                .collect(Collectors.toMap(Mapping::key, Function.identity(), (a, b) -> a));
        var conceptTree = new MappingTreeBase(Arrays.asList(
                mapper.readValue(new File(torchProperties.conceptTreeFile()), MappingTreeModuleRoot[].class)));
        return new CriterionQueryTranslator(MappingContext.of(mappings, conceptTree));
    }

    /**
     * @throws IOException if a file cannot be read or the search params do not match the consent date fields
     */
    @Bean
    public ConsentSearchParams consentSearchParams(ObjectMapper mapper,
                                                   TorchProperties torchProperties,
                                                   InclusionConsentCheckProperties properties) throws IOException {
        var type = new TypeReference<Map<String, String>>() {
        };
        var file = new File(properties.consentSearchParamsFile());
        try {
            return ConsentSearchParams.of(mapper.readValue(new File(torchProperties.mapping().typeToConsent()), type),
                    mapper.readValue(file, type));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid consent search params %s: %s".formatted(file, e.getMessage()), e);
        }
    }

    @Bean
    public InclusionConsentChecker inclusionConsentChecker(CriterionQueryTranslator translator,
                                                           DataStore dataStore,
                                                           ConsentSearchParams consentSearchParams,
                                                           PatientLinkResolver patientLinkResolver,
                                                           ObjectMapper mapper,
                                                           InclusionConsentCheckProperties properties) {
        return new InclusionConsentChecker(translator, dataStore, consentSearchParams, patientLinkResolver, mapper,
                properties.concurrency(), properties.capabilityStatementRefresh(), Clock.systemUTC());
    }
}
