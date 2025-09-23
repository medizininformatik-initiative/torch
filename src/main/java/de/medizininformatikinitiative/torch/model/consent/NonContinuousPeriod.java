package de.medizininformatikinitiative.torch.model.consent;

import com.google.common.collect.Streams;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record NonContinuousPeriod(List<Period> periods) {

    public NonContinuousPeriod {
        periods = List.copyOf(periods);
    }

    public static NonContinuousPeriod of(Period period) {
        return new NonContinuousPeriod(List.of(period));
    }

    public static NonContinuousPeriod of() {
        return new NonContinuousPeriod(List.of());
    }

    public NonContinuousPeriod merge(NonContinuousPeriod other) {
        List<Period> merged = Streams.concat(periods.stream(), other.periods.stream())
                .sorted(Comparator.comparing(p -> p.start()))
                .collect(ArrayList::new, (acc, next) -> {
                    if (acc.isEmpty()) {
                        acc.add(next);
                    } else {
                        Period last = acc.get(acc.size() - 1);
                        if (!last.end().isBefore(next.start().minusDays(1))) {
                            acc.set(acc.size() - 1, new Period(
                                    last.start(),
                                    last.end().isAfter(next.end()) ? last.end() : next.end()
                            ));
                        } else {
                            acc.add(next);
                        }
                    }
                }, ArrayList::addAll);


        return new NonContinuousPeriod(merged);
    }

    public NonContinuousPeriod update(Period encounterPeriod) {
        return new NonContinuousPeriod(
                periods.stream()
                        .map(consentPeriod -> {
                            if (consentPeriod.isStartBetween(encounterPeriod)) {
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


    public NonContinuousPeriod intersect(NonContinuousPeriod other) {
        return new NonContinuousPeriod(
                periods.stream()
                        .flatMap(p1 -> other.periods.stream()
                                .map(p1::intersect)
                                .filter(Objects::nonNull))
                        .toList()
        );
    }

    /**
     * returns a new NonContinuousPeriod which is the result of subtracting the periods in 'other' from this.
     *
     * @param subtrahend the Period to subtract
     * @return a new NonContinuousPeriod after subtraction unchanged if there is no intersection.
     */
    public NonContinuousPeriod substract(Period subtrahend) {
        List<Period> temp = new ArrayList<>();
        for (Period allow : periods) {
            // No overlap → keep original
            Period intersection = allow.intersect(subtrahend);
            if (intersection == null) {
                temp.add(allow);
            } else {
                // Left part (before deny)
                if (subtrahend.start().isAfter(allow.start())) {
                    temp.add(new Period(allow.start(), subtrahend.start().minusDays(1)));
                }
                // Right part (after deny)
                if (subtrahend.end().isBefore(allow.end())) {
                    temp.add(new Period(subtrahend.end().plusDays(1), allow.end()));
                }
            }
        }

        return new NonContinuousPeriod(temp);
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
