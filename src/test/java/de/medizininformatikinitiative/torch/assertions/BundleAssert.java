package de.medizininformatikinitiative.torch.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ListAssert;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class BundleAssert extends AbstractAssert<BundleAssert, Bundle> {

    protected BundleAssert(Bundle bundle) {
        super(bundle, BundleAssert.class);
    }

    public BundleAssert containsNEntries(int n) {
        var found = actual.getEntry().size();
        if (found != n) {
            failWithMessage("Expected bundle to contain %s entries, but found %s", n, found);
        }

        return myself;
    }

    private List<Resource> filterResources(Predicate<Resource> predicate) {
        return actual.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).filter(predicate).toList();
    }

    public ListAssert<Resource> extractResourcesByType(ResourceType type) {
        return new ListAssert<>(filterResources(r -> r.getResourceType().equals(type)));
    }

    public ListAssert<Resource> extractResources() {
        return new ListAssert<>(actual.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList());
    }

    /**
     * Extracts resources of the bundle by a filter.
     * @param predicate the filter to apply to each resource in the bundle
     * @return the filtered resources
     */
    public ListAssert<Resource> extractResources(Predicate<Resource> predicate) {
        return new ListAssert<>(filterResources(predicate));
    }

    /**
     * Extracts the only patient of the bundle.
     * <p>
     * This is a convenience method that works similar to {@link #extractResources(Predicate)} but additionally asserts
     * that there is only one patient allowed in a bundle.
     * @return the patient resource
     */
    public ResourceAssert extractOnlyPatient() {
        var found = filterResources(resource -> resource.getResourceType().equals(ResourceType.Patient));
        if (found.isEmpty()) {
            failWithMessage("Expected bundle to contain exactly one patient resource, but found none");
        }
        if (found.size() > 1) {
            failWithMessage("Expected bundle to contain exactly one patient resource, but found %s instead", found.size());
        }
        return new ResourceAssert(found.getFirst());
    }

    public ResourceAssert extractResourceById(String type, String id) {
        Optional<Resource> found = actual.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r.fhirType().equals(type) && r.getIdPart().equals(id)).findFirst();

        if (found.isEmpty()) {
            failWithMessage("Expected bundle to contain resource of type %s and with id %s, but it could not be found", type, id);
        }

        return new ResourceAssert(found.get());
    }
}
