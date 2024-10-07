package de.medizininformatikinitiative.torch.model.ccdl;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@JsonSerialize(using = ValidationIssueSerializer.class)
public enum ValidationIssue {
    TERMCODE_CONTEXT_COMBINATION_INVALID(20001, "The combination of context and termcode(s) is not found.");

    private static final ValidationIssue[] VALUES;

    static {
        VALUES = values();
    }

    private final int code;
    private final String detail;

    ValidationIssue(int code, String detail) {
        this.code = code;
        this.detail = detail;
    }

    public static ValidationIssue valueOf(int validationIssueCode) {
        ValidationIssue validationIssue = resolve(validationIssueCode);
        if (validationIssue == null) {
            throw new IllegalArgumentException("No matching Validation issue for code " + validationIssueCode);
        }
        return validationIssue;
    }

    @Nullable
    public static ValidationIssue resolve(int validationIssueCode) {
        for (ValidationIssue validationIssue : VALUES) {
            if (validationIssue.code == validationIssueCode) {
                return validationIssue;
            }
        }
        return null;
    }

    public int code() {
        return this.code;
    }

    public String detail() {
        return this.detail;
    }
}