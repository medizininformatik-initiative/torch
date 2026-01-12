package de.medizininformatikinitiative.torch.model.consent;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public record Provisions(Map<String, NonContinuousPeriod> periods) {

    public Provisions {
        periods = Map.copyOf(periods);
    }

    public static Provisions of() {
        return new Provisions(Map.of());
    }

    public static Provisions merge(Collection<Provisions> provisions) {
        return new Provisions(provisions.stream().flatMap(map -> map.periods.entrySet().stream()).collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        NonContinuousPeriod::merge
                )
        ));
    }

    public boolean isEmpty() {
        return periods.isEmpty();
    }
}
