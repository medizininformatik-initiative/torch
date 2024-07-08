package de.medizininformatikinitiative.flare.model.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Bundle(List<Entry> entry, List<Link> link) {

    public Bundle {
        entry = entry == null ? List.of() : List.copyOf(entry);
        link = link == null ? List.of() : List.copyOf(link);
    }

    public Optional<Link> linkWithRel(String rel) {
        return link.stream().filter(l -> l.relation().equals(rel)).findFirst();
    }
}
