package de.medizininformatikinitiative.flare.model.sq;

import java.math.BigDecimal;

/**
 * A quantity with or without unit.
 */
public sealed interface Quantity permits Quantity.Unitless, Quantity.WithUnit {

    /**
     * Creates a unitless quantity.
     *
     * @param value the value of the quantity
     * @return a unitless quantity
     */
    static Quantity.Unitless of(BigDecimal value) {
        return new Unitless(value);
    }

    /**
     * Creates a quantity with unit.
     *
     * @param value the value of the quantity
     * @param unit  the unit of the quantity
     * @return a quantity with unit
     */
    static Quantity.WithUnit of(BigDecimal value, TermCode unit) {
        return new WithUnit(value, unit);
    }

    /**
     * Returns a string that can be used as FHIR search parameter value.
     *
     * @return a string that can be used as FHIR search parameter value
     */
    String searchValue();

    record Unitless(BigDecimal value) implements Quantity {

        @Override
        public String searchValue() {
            return value.toString();
        }
    }

    record WithUnit(BigDecimal value, TermCode unit) implements Quantity {

        public Quantity.WithUnit add(Quantity.WithUnit augend) {
            if (!unit.equals(augend.unit)) {
                throw new IllegalArgumentException("Incompatible units `%s` and `%s` in addition.".formatted(unit,
                        augend.unit));
            }
            return new WithUnit(value.add(augend.value), unit);
        }

        @Override
        public String searchValue() {
            return value + "|" + unit.searchValue();
        }
    }
}
