package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Period;

import java.util.Date;

/**
 * Represents a consent period with start and end dates and an associated code.
 */
public class ConsentPeriod {

    private DateTimeType start;
    private DateTimeType end;
    private String code;

    /**
     * Default constructor.
     */
    public ConsentPeriod() {
    }

    /**
     * Constructs a {@code ConsentPeriod} with specified start and end dates.
     *
     * @param start the start date of the consent period
     * @param end   the end date of the consent period
     */
    public ConsentPeriod(DateTimeType start, DateTimeType end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Gets the start date of the consent period.
     *
     * @return the start date
     */
    public DateTimeType getStart() {
        return start;
    }

    /**
     * Sets the start date of the consent period.
     *
     * @param start the start date to set
     */
    public void setStart(DateTimeType start) {
        this.start = start;
    }

    /**
     * Gets the end date of the consent period.
     *
     * @return the end date
     */
    public DateTimeType getEnd() {
        return end;
    }

    /**
     * Sets the end date of the consent period.
     *
     * @param end the end date to set
     */
    public void setEnd(DateTimeType end) {
        this.end = end;
    }

    /**
     * Gets the code associated with the consent period.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the code associated with the consent period.
     *
     * @param code the code to set
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Returns a string representation of the {@code ConsentPeriod}.
     *
     * @return a string representation of the consent period
     */
    @Override
    public String toString() {
        return "ConsentPeriod{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }

    /**
     * Checks if a given date falls within the consent period.
     *
     * @param date the date to check
     * @return {@code true} if the date is within the consent period; {@code false} otherwise
     */
    public boolean isWithinPeriod(DateTimeType date) {
        if (date == null) return false;
        return (start == null || !date.after(start)) && (end == null || !date.before(end));
    }
    /**
     * Checks if the Consent start is wihtin a given Period.
     *
     * @param date the date to check
     * @return {@code true} if the date is within the consent period; {@code false} otherwise
     */
    // Helper method to check if the consent period start overlaps with the encounter period
    public boolean isWithinEncounterPeriod(Period encounterPeriod) {

        DateTimeType encounterStart = encounterPeriod.getStartElement();
        DateTimeType encounterEnd = encounterPeriod.getEndElement();

        // Adjust the logic based on your specific requirements
        return (start.before(encounterEnd) || start.equals(encounterEnd)) &&
                (start.after(encounterStart) || start.equals(encounterStart));
    }

    /**
     * Checks if the consent period is valid (start is before end).
     *
     * @return {@code true} if the period is valid; {@code false} otherwise
     */
    public boolean isValidPeriod() {
        return start != null && end != null && start.before(end);
    }
}
