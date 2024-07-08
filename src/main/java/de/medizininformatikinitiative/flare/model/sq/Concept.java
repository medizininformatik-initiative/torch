package de.medizininformatikinitiative.flare.model.sq;

import java.util.Formattable;
import java.util.Formatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A concept represented by one or more term codes.
 */
public record Concept(List<TermCode> termCodes) implements Formattable {

    public Concept {
        termCodes = List.copyOf(termCodes);
    }

    public static Concept of(TermCode... termCode) {
        return new Concept(List.of(termCode));
    }

    public static Concept of(List<TermCode> termCodes) {
        return new Concept(termCodes);
    }

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        formatter.format(termCodes.stream().map(termCode -> "(system: %s, code: %s, display: %s)".formatted(
                termCode.system(), termCode.code(), termCode.display()
        )).collect(Collectors.joining(", ")));
    }
}
