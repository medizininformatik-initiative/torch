package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import org.hl7.fhir.r4.model.Resource;

import java.util.Optional;
import java.util.function.Function;

/**
 * Bundles the lookups {@link de.medizininformatikinitiative.torch.util.DiscriminatorResolver} needs to evaluate
 * discriminator paths that resolve an external {@link org.hl7.fhir.r4.model.Reference} (e.g. {@code $this.resolve()}).
 *
 * @param referenceResolver looks up an already-resolved resource by its {@link ExtractionId}; empty if unresolved
 * @param profileResolver   looks up the {@link CompiledStructureDefinition} for a profile canonical URL; empty if
 *                          the profile is not locally known (e.g. a base HL7 canonical URL, which isn't loaded as
 *                          an MII ontology profile)
 */
public record ReferenceResolutionContext(Function<ExtractionId, Optional<Resource>> referenceResolver,
                                          Function<String, Optional<CompiledStructureDefinition>> profileResolver) {

    public static final ReferenceResolutionContext EMPTY =
            new ReferenceResolutionContext(id -> Optional.empty(), url -> Optional.empty());
}
