package de.medizininformatikinitiative.torch.util;

import java.time.LocalDateTime;

public class ConsentPeriod {

    private LocalDateTime start;
    private LocalDateTime end;
    private String code;

    // Default constructor
    public ConsentPeriod() {
    }

    // Parameterized constructor for convenience
    public ConsentPeriod(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    // Getter for start
    public LocalDateTime getStart() {
        return start;
    }

    // Setter for start
    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    // Getter for end
    public LocalDateTime getEnd() {
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
    public void setEnd(LocalDateTime end) {
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
    public boolean isWithinPeriod(LocalDateTime date) {
        if (date == null) return false;
        return (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end));
    }

    // Check if the consent period is valid (start is before end)
    public boolean isValidPeriod() {
        return start != null && end != null && start.isBefore(end);
    }
}
