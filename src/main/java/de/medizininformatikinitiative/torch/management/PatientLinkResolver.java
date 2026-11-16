package de.medizininformatikinitiative.torch.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Determines, per resource type of the Patient compartment, how a resource links to its patient.
 * <p>
 * For each type one search parameter is chosen: {@code _id} for {@code Patient}; {@code patient} where the
 * SearchParameter definitions define it for the type, even if the compartment definition doesn't list it (e.g. for
 * {@code Observation}); otherwise the first parameter of the compartment definition. Only {@code reference} parameters that can
 * target {@code Patient} and whose expression reduces to plain element paths are eligible. The chosen parameter is used
 * both to search the compartment and, through its expression, to find the patient a loaded resource belongs to,
 * e.g. {@code patient} with expression {@code Coverage.beneficiary} for {@code Coverage}.
 */
public class PatientLinkResolver {

    private static final Logger logger = LoggerFactory.getLogger(PatientLinkResolver.class);

    private static final String PATIENT = "Patient";
    private static final String PATIENT_REFERENCE_PREFIX = "Patient/";
    private static final String PATIENT_FILTER = ".where(resolve() is Patient)";
    private static final Pattern ELEMENT_PATH = Pattern.compile("[A-Z][A-Za-z]*(\\.[a-z][A-Za-z]*)+");

    private final Map<String, PatientLink> links;

    private record PatientLink(String searchParam, List<String> elementPaths) {
    }

    private record SearchParamDefinition(List<String> base, String expression) {
    }

    /**
     * @param compartmentManager   the Patient compartment definition
     * @param searchParametersFile classpath location of a FHIR Bundle of SearchParameter resources
     * @throws IllegalStateException if a type of the compartment has no usable search parameter
     */
    public PatientLinkResolver(CompartmentManager compartmentManager, String searchParametersFile) throws IOException {
        Map<String, SearchParamDefinition> patientReferenceParams = readPatientReferenceParams(searchParametersFile);

        Map<String, PatientLink> map = new HashMap<>();
        for (String resourceType : compartmentManager.getCompartment()) {
            if (PATIENT.equals(resourceType)) {
                map.put(resourceType, new PatientLink("_id", List.of()));
                continue;
            }
            Stream.concat(Stream.of("patient"), compartmentManager.getParams(resourceType).stream())
                    .flatMap(param -> Optional.ofNullable(patientReferenceParams.get(key(resourceType, param)))
                            .flatMap(definition -> elementPaths(resourceType, definition))
                            .map(paths -> new PatientLink(param, paths))
                            .stream())
                    .findFirst()
                    .ifPresent(link -> map.put(resourceType, link));
        }
        List<String> unmapped = compartmentManager.getCompartment().stream()
                .filter(resourceType -> !map.containsKey(resourceType))
                .sorted()
                .toList();
        if (!unmapped.isEmpty()) {
            throw new IllegalStateException(
                    "No usable search parameter links these Patient compartment types to a patient: " + unmapped);
        }
        new TreeMap<>(map).forEach((resourceType, link) ->
                logger.debug("{} -> {} -> {}", resourceType, link.searchParam(), link.elementPaths()));
        this.links = Map.copyOf(map);
    }

    private static Map<String, SearchParamDefinition> readPatientReferenceParams(String searchParametersFile)
            throws IOException {
        JsonNode bundle = new ObjectMapper().readTree(new ClassPathResource(searchParametersFile).getInputStream());

        Map<String, SearchParamDefinition> params = new HashMap<>();
        bundle.path("entry").forEach(entry -> {
            JsonNode searchParam = entry.path("resource");
            if ("reference".equals(searchParam.path("type").asText())
                    && texts(searchParam.path("target")).contains(PATIENT)) {
                List<String> base = texts(searchParam.path("base"));
                var definition = new SearchParamDefinition(base, searchParam.path("expression").asText());
                base.forEach(type -> params.put(key(type, searchParam.path("code").asText()), definition));
            }
        });
        return params;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(node -> texts.add(node.asText()));
        return texts;
    }

    /**
     * Reduces a search parameter expression to the element paths that apply to {@code resourceType}.
     * <p>
     * Union parts of other types are dropped, the {@code .where(resolve() is Patient)} filter is removed (it is applied
     * when reading the references) and expressions relative to a single base type are made absolute.
     *
     * @return the element paths, or empty if the expression has no part for this type or a part that is not a plain
     * element path
     */
    private static Optional<List<String>> elementPaths(String resourceType, SearchParamDefinition definition) {
        List<String> paths = new ArrayList<>();
        for (String part : definition.expression().split("\\|")) {
            String path = part.trim();
            if (path.endsWith(PATIENT_FILTER)) {
                path = path.substring(0, path.length() - PATIENT_FILTER.length());
            }
            if (path.startsWith(".") && definition.base().size() == 1) {
                path = resourceType + path;
            }
            if (!path.startsWith(resourceType + ".")) {
                continue;
            }
            if (!ELEMENT_PATH.matcher(path).matches()) {
                return Optional.empty();
            }
            paths.add(path);
        }
        return paths.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(paths));
    }

    private static String key(String resourceType, String code) {
        return resourceType + "." + code;
    }

    private PatientLink link(String resourceType) {
        PatientLink link = links.get(resourceType);
        if (link == null) {
            throw new IllegalArgumentException(
                    "Resource type " + resourceType + " is not in the Patient compartment");
        }
        return link;
    }

    /**
     * @param resourceType FHIR resource type to be searched for
     * @return the code of the search parameter selecting resources of that type by patient
     * @throws IllegalArgumentException if the type is not in the Patient compartment
     */
    public String searchParam(String resourceType) {
        return link(resourceType).searchParam();
    }

    /**
     * @param resourceType FHIR resource type of the Patient compartment
     * @return the element paths holding the patient reference, e.g. {@code Coverage.beneficiary}; empty for
     * {@code Patient}
     * @throws IllegalArgumentException if the type is not in the Patient compartment
     */
    public List<String> elementPaths(String resourceType) {
        return link(resourceType).elementPaths();
    }

    /**
     * @param resource the resource to inspect
     * Only references through the chosen search parameter's element paths count; patient references in other
     * elements (e.g. {@code Coverage.subscriber}) are ignored. Only relative references {@code Patient/<id>} count,
     * optionally versioned.
     *
     * @return the distinct IDs of the patients the resource references through its element paths, in document order;
     * the resource's own ID for a {@code Patient}; empty if it references no patient, is a {@code Patient} without an
     * ID or is not in the compartment
     */
    public List<String> patientIds(DomainResource resource) {
        if (resource instanceof Patient patient) {
            String id = patient.getIdPart();
            return id == null || id.isBlank() ? List.of() : List.of(id);
        }
        PatientLink link = links.get(resource.fhirType());
        if (link == null) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String path : link.elementPaths()) {
            for (Base element : elements(resource, path)) {
                if (element instanceof Reference reference && reference.hasReference()
                        && reference.getReference().startsWith(PATIENT_REFERENCE_PREFIX)) {
                    IdType id = new IdType(reference.getReference());
                    if (id.hasIdPart()) {
                        ids.add(id.getIdPart());
                    }
                }
            }
        }
        return List.copyOf(ids);
    }

    private static List<Base> elements(DomainResource resource, String path) {
        List<Base> current = List.of(resource);
        String[] names = path.split("\\.");
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            current = current.stream()
                    .flatMap(base -> base.listChildrenByName(name).stream())
                    .filter(base -> base != null && !base.isEmpty())
                    .toList();
        }
        return current;
    }

    /**
     * @return the single patient the resource references through its element paths (see {@link #patientIds})
     * @throws PatientIdNotFoundException if the resource references no patient or several distinct patients; a
     *                                    resource linking several patients is not attributed to any of them
     */
    public String patientId(DomainResource resource) throws PatientIdNotFoundException {
        List<String> ids = patientIds(resource);
        if (ids.size() > 1) {
            throw new PatientIdNotFoundException("Resource of Type " + resource.fhirType()
                    + " references several patients");
        }
        return ids.stream().findFirst().orElseThrow(() -> notFound(resource));
    }

    private static PatientIdNotFoundException notFound(DomainResource resource) {
        return new PatientIdNotFoundException("Patient Reference not found for Resource of Type " + resource.fhirType());
    }
}
