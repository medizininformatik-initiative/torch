package de.medizininformatikinitiative.flare.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.flare.model.sq.ContextualTermCode;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * @author Alexander Kiel
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface TermCodeNode permits TermCodeNode.Abstract, TermCodeNode.Normal {

    static TermCodeNode createAbstract(ContextualTermCode termCode, TermCodeNode... children) {
        return new TermCodeNode.Abstract(termCode, children == null ? List.of() : List.of(children));
    }

    static TermCodeNode createNormal(ContextualTermCode termCode, TermCodeNode... children) {
        return new TermCodeNode.Normal(termCode, children == null ? List.of() : List.of(children));
    }

    @JsonCreator
    static TermCodeNode fromJson(@JsonProperty("context") TermCode context,
                                 @JsonProperty("termCode") TermCode termCode,
                                 @JsonProperty("abstract") boolean abstractJson,
                                 @JsonProperty("children") TermCodeNode... children) {
        var contextualTermCode = ContextualTermCode.of(requireNonNull(context, "missing JSON property: context"),
                requireNonNull(termCode, "missing JSON property: termCode"));
        return abstractJson
                ? new Abstract(contextualTermCode, children == null ? List.of() : List.of(children))
                : new Normal(contextualTermCode, children == null ? List.of() : List.of(children));
    }

    Stream<ContextualTermCode> expand(ContextualTermCode termCode);

    Stream<ContextualTermCode> leafConcepts();

    ContextualTermCode contextualTermCode();

    List<TermCodeNode> children();

    record Abstract(ContextualTermCode contextualTermCode, List<TermCodeNode> children) implements TermCodeNode {

        public Abstract {
            requireNonNull(contextualTermCode);
            children = List.copyOf(children);
        }

        @Override
        public Stream<ContextualTermCode> expand(ContextualTermCode termCode) {
            if (requireNonNull(termCode).equals(this.contextualTermCode)) {
                return leafConcepts();
            } else if (children.isEmpty()) {
                return Stream.of();
            } else {
                return children.stream().flatMap(n -> n.expand(termCode));
            }
        }

        @Override
        public Stream<ContextualTermCode> leafConcepts() {
            return children.isEmpty() ? Stream.of() : children.stream().flatMap(TermCodeNode::leafConcepts);
        }
    }

    record Normal(ContextualTermCode contextualTermCode, List<TermCodeNode> children) implements TermCodeNode {

        public Normal {
            requireNonNull(contextualTermCode);
            children = List.copyOf(children);
        }

        @Override
        public Stream<ContextualTermCode> expand(ContextualTermCode termCode) {
            if (requireNonNull(termCode).equals(this.contextualTermCode)) {
                return leafConcepts();
            } else if (children.isEmpty()) {
                return Stream.of();
            } else {
                return children.stream().flatMap(n -> n.expand(termCode));
            }
        }

        @Override
        public Stream<ContextualTermCode> leafConcepts() {
            if (children.isEmpty()) {
                return Stream.of(contextualTermCode);
            } else {
                return Stream.concat(Stream.of(contextualTermCode), children.stream().flatMap(TermCodeNode::leafConcepts));
            }
        }
    }
}
