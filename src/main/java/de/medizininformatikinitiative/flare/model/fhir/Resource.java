package de.medizininformatikinitiative.flare.model.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Resource(String id, Reference patient, Reference subject) {

    public Optional<String> patientId() {
        return patient != null ? patient.id() : subject != null ? subject.id() : Optional.ofNullable(id);
    }

}
