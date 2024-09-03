package de.medizininformatikinitiative.torch.util;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Resource;

public class TransformedResource {
    private final String id;
    private final Resource resource;
    private final boolean mustHaveViolated;

    public TransformedResource(String id, Resource resource, boolean mustHaveViolated) {
        this.id = id;
        this.resource = resource;
        this.mustHaveViolated = mustHaveViolated;
    }

    public String getId() {
        return id;
    }

    public Resource getResource() {
        return resource;
    }

    public boolean isMustHaveViolated() {
        return mustHaveViolated;
    }
}
