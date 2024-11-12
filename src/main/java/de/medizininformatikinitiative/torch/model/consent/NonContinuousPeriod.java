package de.medizininformatikinitiative.torch.model.consent;

import com.google.common.collect.Streams;

import java.util.List;

public record NonContinuousPeriod(
        List<Period> periods
) {
    public NonContinuousPeriod {
        periods = List.copyOf(periods);
    }

    public NonContinuousPeriod merge(NonContinuousPeriod other) {
        return new NonContinuousPeriod(Streams.concat(periods.stream(), other.periods.stream()).toList());
    }

    public NonContinuousPeriod update(Period encounterPeriod) {
        return new NonContinuousPeriod(
                periods.stream()
                        .map(consentPeriod -> {
                            if (encounterPeriod.isStartBetween(consentPeriod)) {
                                return new Period(encounterPeriod.start(), consentPeriod.end());
                            }
                            return consentPeriod;
                        }).toList()
        );
    }

    public boolean within(Period resourcePeriod) {
        return periods.stream().anyMatch(period ->
                resourcePeriod.start().isAfter(period.start()) && resourcePeriod.end().isBefore(period.end()));
    }

    public boolean isEmpty() {
        return periods.isEmpty();
    }

    public int size() {
        return periods.size();
    }

    public Period get(int index) {
        return periods.get(index);
    }

}
