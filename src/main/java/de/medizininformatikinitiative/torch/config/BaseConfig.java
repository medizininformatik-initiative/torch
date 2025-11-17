package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.consent.ConsentHandler;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.cql.CqlClient;
import de.medizininformatikinitiative.torch.cql.FhirHelper;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ProcessedGroupFactory;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.model.mapping.DseTreeRoot;
import de.medizininformatikinitiative.torch.service.BatchCopierRedacter;
import de.medizininformatikinitiative.torch.service.CascadingDelete;
import de.medizininformatikinitiative.torch.service.CrtdlProcessingService;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.service.DirectResourceLoader;
import de.medizininformatikinitiative.torch.service.FilterService;
import de.medizininformatikinitiative.torch.service.PatientBatchToCoreBundleWriter;
import de.medizininformatikinitiative.torch.service.ReferenceBundleLoader;
import de.medizininformatikinitiative.torch.service.ReferenceResolver;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import de.medizininformatikinitiative.torch.util.ResultFileManager;
import de.numcodex.sq2cql.Translator;
import de.numcodex.sq2cql.model.Mapping;
import de.numcodex.sq2cql.model.MappingContext;
import de.numcodex.sq2cql.model.MappingTreeBase;
import de.numcodex.sq2cql.model.MappingTreeModuleRoot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;

@Configuration
public class BaseConfig {

    // ----------------------------------------------------------------------
    // CORE SHARED BEANS
    // ----------------------------------------------------------------------

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }


    @Bean
    public ResourceReader resourceReader(FhirContext ctx) {
        return new ResourceReader(ctx);
    }

    @Bean
    public CompartmentManager compartmentManager() throws IOException {
        return new CompartmentManager("compartmentdefinition-patient.json");
    }

    @Bean
    public FilterService filterService(FhirContext ctx, TorchProperties torchProperties) {
        return new FilterService(ctx, torchProperties.searchParametersFile());
    }

    // ----------------------------------------------------------------------
    // CONSENT
    // ----------------------------------------------------------------------

    @Bean
    public ConsentCodeMapper consentCodeMapper(ObjectMapper objectMapper, TorchProperties torchProperties) throws IOException {
        return new ConsentCodeMapper(torchProperties.mapping().consent(), objectMapper);
    }

    @Bean
    public ConsentValidator consentValidator(FhirContext ctx,
                                             ObjectMapper mapper,
                                             TorchProperties torchProperties)
            throws IOException {
        return new ConsentValidator(ctx, mapper.readTree(new File(torchProperties.mapping().typeToConsent())));
    }

    // ----------------------------------------------------------------------
    // DSE TREE + MAPPINGS
    // ----------------------------------------------------------------------

    @Bean
    public DseMappingTreeBase dseMappingTreeBase(ObjectMapper mapper,
                                                 TorchProperties torchProperties) throws IOException {
        return new DseMappingTreeBase(Arrays.stream(
                mapper.readValue(new File(torchProperties.dseMappingTreeFile()), DseTreeRoot[].class)
        ).toList());
    }

    @Lazy
    @Bean
    Translator createCqlTranslator(ObjectMapper jsonUtil, TorchProperties torchProperties) throws IOException {
        var mappings = jsonUtil.readValue(new File(torchProperties.mappingsFile()), Mapping[].class);
        var mappingTreeBase = new MappingTreeBase(Arrays.stream(jsonUtil.readValue(new File(torchProperties.conceptTreeFile()), MappingTreeModuleRoot[].class)).toList());

        return Translator.of(MappingContext.of(
                Stream.of(mappings)
                        .collect(Collectors.toMap(Mapping::key, Function.identity(), (a, b) -> a)),
                mappingTreeBase,
                Map.ofEntries(entry("http://fhir.de/CodeSystem/bfarm/icd-10-gm", "icd10"),
                        entry("mii.abide", "abide"),
                        entry("http://fhir.de/CodeSystem/bfarm/ops", "ops"),
                        entry("http://dicom.nema.org/resources/ontology/DCM", "dcm"),
                        entry("https://www.medizininformatik-initiative.de/fhir/core/modul-person/CodeSystem/Vitalstatus", "vitalstatus"),
                        entry("http://loinc.org", "loinc"),
                        entry("https://fhir.bbmri.de/CodeSystem/SampleMaterialType", "sample"),
                        entry("http://fhir.de/CodeSystem/bfarm/atc", "atc"),
                        entry("http://snomed.info/sct", "snomed"),
                        entry("http://terminology.hl7.org/CodeSystem/condition-ver-status", "cvs"),
                        entry("http://hl7.org/fhir/administrative-gender", "gender"),
                        entry("urn:oid:1.2.276.0.76.5.409", "urn409"),
                        entry(
                                "https://www.netzwerk-universitaetsmedizin.de/fhir/CodeSystem/ecrf-parameter-codes",
                                "numecrf"),
                        entry("urn:iso:std:iso:3166", "iso3166"),
                        entry("https://www.netzwerk-universitaetsmedizin.de/fhir/CodeSystem/frailty-score",
                                "frailtyscore"),
                        entry("http://terminology.hl7.org/CodeSystem/consentcategorycodes", "consentcategory"),
                        entry("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "consent"),
                        entry("http://hl7.org/fhir/sid/icd-o-3", "icdo3"),
                        entry("fdpg.mii.cds", "fdpgmiicds"),
                        entry("http://fhir.de/CodeSystem/bfarm/alpha-id", "alphaid"),
                        entry("urn:iso:std:iso:11073:10101", "ISO11073"),
                        entry("http://terminology.hl7.org/CodeSystem/icd-o-3", "icdo3"),
                        entry("http://fhir.de/CodeSystem/dkgev/Fachabteilungsschluessel", "fachabteilungsschluessel"),
                        entry("http://terminology.hl7.org/CodeSystem/v3-ActCode", "v3actcode"),
                        entry("http://fhir.de/CodeSystem/dkgev/Fachabteilungsschluessel-erweitert", "fachabteilungsschluesselerweitert"),
                        entry("http://fhir.de/CodeSystem/kontaktart-de", "kontaktart"),
                        entry("http://hl7.org/fhir/sid/icd-10", "sidicd10"),
                        entry("http://fhir.de/CodeSystem/Kontaktebene", "kontaktebene"),
                        entry("http://www.orpha.net", "orphanet"),
                        entry("fdpg.consent.combined", "fdpgcombinedconsent"),
                        entry("http://hl7.org/fhir/consent-provision-type", "provisiontype"))));
    }

    // ----------------------------------------------------------------------
    // CQL + HELPERS
    // ----------------------------------------------------------------------

    @Bean
    public FhirHelper fhirHelper(FhirContext ctx) {
        return new FhirHelper(ctx);
    }

    @Bean
    public CqlClient cqlClient(FhirHelper helper, DataStore dataStore) {
        return new CqlClient(helper, dataStore);
    }

    // ----------------------------------------------------------------------
    // STRUCTURES / REDACTION / RESULT STORAGE
    // ----------------------------------------------------------------------

    @Bean
    public StructureDefinitionHandler structureDefinitionHandler(ResourceReader reader,
                                                                 TorchProperties torchProperties) {
        return new StructureDefinitionHandler(new File(torchProperties.profile().dir()), reader);
    }

    @Bean
    public ResultFileManager resultFileManager(FhirContext fhirContext,
                                               TorchProperties torchProperties) {
        return new ResultFileManager(
                torchProperties.results().dir(),
                torchProperties.results().persistence(),
                fhirContext,
                torchProperties.base().url(),
                torchProperties.output().file().server().url()
        );
    }

    // ----------------------------------------------------------------------
    // BUNDLE + PATIENT WRITER
    // ----------------------------------------------------------------------

    @Bean
    public BundleBuilder bundleBuilder(FhirContext ctx) {
        return new BundleBuilder(ctx);
    }

    @Bean
    public PatientBatchToCoreBundleWriter patientBatchToCoreBundleWriter(CompartmentManager manager) {
        return new PatientBatchToCoreBundleWriter(manager);
    }

    // ----------------------------------------------------------------------
    // REFERENCE LOADER / RESOLVER
    // ----------------------------------------------------------------------

    @Bean
    public ReferenceBundleLoader referenceBundleLoader(CompartmentManager manager,
                                                       DataStore dataStore,
                                                       ConsentValidator validator,
                                                       FhirProperties torchProperties) {
        return new ReferenceBundleLoader(manager, dataStore, validator, torchProperties.page().count());
    }

    // ----------------------------------------------------------------------
    // CRTDL PROCESSING PIPELINE
    // ----------------------------------------------------------------------

    @Bean
    public CrtdlProcessingService crtdlProcessingService(
            @Qualifier("flareClient") WebClient flareClient,
            Translator cqlQueryTranslator,
            CqlClient cqlClient,
            ResultFileManager resultFileManager,
            ProcessedGroupFactory processedGroupFactory,
            DirectResourceLoader directResourceLoader,
            ReferenceResolver referenceResolver,
            BatchCopierRedacter batchCopierRedacter,
            CascadingDelete cascadingDelete,
            PatientBatchToCoreBundleWriter writer,
            ConsentHandler consentHandler,
            TorchProperties torchProperties
    ) {
        return new CrtdlProcessingService(
                flareClient,
                cqlQueryTranslator,
                cqlClient,
                resultFileManager,
                processedGroupFactory,
                torchProperties.batchsize(),
                torchProperties.useCql(),
                directResourceLoader,
                referenceResolver,
                batchCopierRedacter,
                torchProperties.maxConcurrency(),
                cascadingDelete,
                writer,
                consentHandler
        );
    }

    // ----------------------------------------------------------------------
    // UTIL
    // ----------------------------------------------------------------------

    @Bean
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
