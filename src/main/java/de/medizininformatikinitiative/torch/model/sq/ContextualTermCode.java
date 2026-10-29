package de.medizininformatikinitiative.torch.model.sq;

public record ContextualTermCode(TermCode context, TermCode termCode) {

    public static ContextualTermCode of(TermCode context, TermCode termCode) {
        return new ContextualTermCode(context, termCode);
    }
}
