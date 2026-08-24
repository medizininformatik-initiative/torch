package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contains all information needed for the extraction and redaction operations, and pairs a resource
 * with the profiles it is required to satisfy.
 *
 * @param resource          Resource to be processed
 * @param profiles          profiles of structure definitions of the applied groups
 * @param references        map from elementid to reference string
 * @param copyTree          merged attribute copy tree for the extraction
 * @param referenceResolver looks up an already-resolved resource by {@link ExtractionId}, used to evaluate a
 *                          {@code resolve()} step in a slicing discriminator path; empty if unresolved
 */
public record ExtractionRedactionWrapper(DomainResource resource, Set<String> profiles,
                                         Map<String, Set<ExtractionId>> references,
                                         CopyTreeNode copyTree,
                                         Function<ExtractionId, Optional<Resource>> referenceResolver) {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionRedactionWrapper.class);

    public ExtractionRedactionWrapper {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(copyTree);
        Objects.requireNonNull(referenceResolver);
        profiles = Set.copyOf(profiles);
        references = Map.copyOf(references);
    }

    public ExtractionRedactionWrapper(DomainResource resource, Set<String> profiles,
                                      Map<String, Set<ExtractionId>> references, CopyTreeNode copyTree) {
        this(resource, profiles, references, copyTree, id -> Optional.empty());
    }

    /**
     * Builds a wrapper, validating that {@code resource} actually carries every profile in {@code profiles}.
     * <p>
     * {@code Patient} resources are exempt, since their profiles are assigned by the caller rather than
     * read off the resource.
     *
     * @throws RedactionException if a non-{@code Patient} resource is missing one or more required profiles
     */
    public static ExtractionRedactionWrapper of(DomainResource resource, Set<String> profiles,
                                                 Map<String, Set<ExtractionId>> references,
                                                 CopyTreeNode copyTree) throws RedactionException {
        return of(resource, profiles, references, copyTree, id -> Optional.empty());
    }

    /**
     * Builds a wrapper with an explicit {@code referenceResolver}, validating that {@code resource} actually
     * carries every profile in {@code profiles}.
     * <p>
     * {@code Patient} resources are exempt, since their profiles are assigned by the caller rather than
     * read off the resource.
     *
     * @throws RedactionException if a non-{@code Patient} resource is missing one or more required profiles
     */
    public static ExtractionRedactionWrapper of(DomainResource resource, Set<String> profiles,
                                                 Map<String, Set<ExtractionId>> references,
                                                 CopyTreeNode copyTree,
                                                 Function<ExtractionId, Optional<Resource>> referenceResolver) throws RedactionException {
        ExtractionRedactionWrapper wrapper = new ExtractionRedactionWrapper(resource, profiles, references, copyTree, referenceResolver);

        if (!resource.getResourceType().toString().equals("Patient")) {
            List<CanonicalType> resourceProfiles = wrapper.matchedProfiles();
            Set<String> validProfiles = profiles.stream()
                    .filter(profile -> resourceProfiles.stream().anyMatch(resourceProfile -> resourceProfile.toString().contains(profile)))
                    .collect(Collectors.toSet());

            if (!validProfiles.equals(profiles)) {
                logger.error("REDACTION_01 Missing Profiles in Resource {} {}: {} for requested profiles {}", resource.getResourceType(), resource.getId(), resourceProfiles, profiles);
                throw new RedactionException("Resource" + resource.getResourceType() + " " + resource.getId() + " is missing required profiles: " + resourceProfiles);
            }
        }

        return wrapper;
    }

    /**
     * Returns the subset of {@link #resource()}'s declared {@code meta.profile} entries that satisfy one of
     * {@link #profiles()}.
     */
    public List<CanonicalType> matchedProfiles() {
        return resource.getMeta().getProfile().stream()
                .filter(profile -> profiles.stream().anyMatch(wrapperProfile -> profile.toString().contains(wrapperProfile)))
                .toList();
    }

    public ExtractionRedactionWrapper updateWithResource(DomainResource resource) throws RedactionException {
        return ExtractionRedactionWrapper.of(resource, profiles, references, copyTree, referenceResolver);
    }

}
