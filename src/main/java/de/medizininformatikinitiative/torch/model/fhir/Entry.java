package de.medizininformatikinitiative.torch.model.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Entry(Resource resource) {

}
