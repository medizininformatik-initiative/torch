package de.medizininformatikinitiative.torch.model.mapping;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;

import static java.util.Objects.requireNonNull;

public enum ConsentKey {

    NO_NO_NO_NO("no-no-no-no"),
    NO_NO_NO_YES("no-no-no-yes"),
    NO_NO_YES_NO("no-no-yes-no"),
    NO_NO_YES_YES("no-no-yes-yes"),
    NO_YES_NO_NO("no-yes-no-no"),
    NO_YES_NO_YES("no-yes-no-yes"),
    NO_YES_YES_NO("no-yes-yes-no"),
    NO_YES_YES_YES("no-yes-yes-yes"),
    YES_NO_NO_NO("yes-no-no-no"),
    YES_NO_NO_YES("yes-no-no-yes"),
    YES_NO_YES_NO("yes-no-yes-no"),
    YES_NO_YES_YES("yes-no-yes-yes"),
    YES_YES_NO_NO("yes-yes-no-no"),
    YES_YES_NO_YES("yes-yes-no-yes"),
    YES_YES_YES_NO("yes-yes-yes-no"),
    YES_YES_YES_YES("yes-yes-yes-yes");

    private final String s;

    ConsentKey(String s) {
        this.s = requireNonNull(s);
    }

    @Override
    public String toString() {
        return s;
    }

    public static ConsentKey fromString(String value) throws ValidationException {
        for (ConsentKey key : values()) {
            if (key.toString().equalsIgnoreCase(value)) {
                return key;
            }
        }
        throw new ValidationException("Unknown consent key: " + value);
    }
}
