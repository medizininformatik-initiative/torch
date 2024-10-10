package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.DateTimeType;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;

public class ConsentPeriod {

    private DateTimeType start;
    private DateTimeType end;
    private String code;

    // Default constructor
    public ConsentPeriod() {
    }

    // Parameterized constructor for convenience
    public ConsentPeriod(DateTimeType start, DateTimeType end) {
        this.start = start;
        this.end = end;
    }

    // Getter for start
    public DateTimeType getStart() {
        return start;
    }

    // Setter for start
    public void setStart(DateTimeType start) {
        this.start = start;
    }

    // Getter for end
    public DateTimeType getEnd() {
        return end;
    }

    // Setter for end
    public void setCode(String code) {
        this.code = code;
    }

    // Getter for end
    public String getCode() {
        return code;
    }

    // Setter for end
    public void setEnd(DateTimeType end) {
        this.end = end;
    }


    // Optionally, override toString() for easy debugging and logging
    @Override
    public String toString() {
        return "ConsentPeriod{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }

    // You can also add utility methods, such as:

    // Check if a given date falls within the consent period
    public boolean isWithinPeriod(DateTimeType date) {
        if (date == null) return false;
        return (start == null || !date.after(start)) && (end == null || !date.before(end));
    }

    // Check if the consent period is valid (start is before end)
    public boolean isValidPeriod() {
        return start != null && end != null && start.before(end);
    }
}
