package de.medizininformatikinitiative.torch.model.mapping;

import de.medizininformatikinitiative.torch.model.sq.ContextualConcept;
import de.medizininformatikinitiative.torch.model.sq.ContextualTermCode;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A context holding information to facilitate the mapping process.
 * <p>
 * Uses the default system clock to private the current instant for date/time calculations.
 *
 * @author Alexander Kiel
 */
public class MappingContext {

    private final Map<ContextualTermCode, Mapping> mappings;
    private final MappingTreeBase conceptTree;
    private final Clock clock;

    private MappingContext(Map<ContextualTermCode, Mapping> mappings, MappingTreeBase conceptTree, Clock clock) {
        this.mappings = Map.copyOf(mappings);
        this.conceptTree = requireNonNull(conceptTree);
        this.clock = requireNonNull(clock);
    }

    /**
     * Returns a mapping context.
     *
     * @param mappings    the mappings keyed by their term code
     * @param conceptTree a tree of concepts to expand (can be null)
     * @return the mapping context
     */
    public static MappingContext of(Map<ContextualTermCode, Mapping> mappings, MappingTreeBase conceptTree) {
        return new MappingContext(mappings, conceptTree, Clock.systemDefaultZone());
    }

    /**
     * Returns a mapping context.
     *
     * @param mappings    the mappings keyed by their term code
     * @param conceptTree a tree of concepts to expand (can be null)
     * @param clock       a clock that is used for time-related calculations
     * @return the mapping context
     */
    public static MappingContext of(Map<ContextualTermCode, Mapping> mappings, MappingTreeBase conceptTree, Clock clock) {
        return new MappingContext(mappings, conceptTree, clock);
    }

    /**
     * Tries to find the {@link Mapping} with the given {@code key}.
     *
     * @param key the TermCode of the mapping
     * @return the Mapping
     * @throws MappingNotFoundException if there is no mapping for {@code key}
     */
    public Mapping findMapping(ContextualTermCode key) throws MappingNotFoundException {
        var mapping = mappings.get(requireNonNull(key));
        if (mapping == null) {
            throw new MappingNotFoundException(key);
        }
        return mapping;
    }

    /**
     * Expands {@code contextualConcept} into a list of {@link ContextualTermCode contextual term codes}.
     *
     * @param contextualConcept the contextualConcept to expand
     * @return a non-empty list of distinct contextual term codes; codes reachable over several paths of the concept
     * tree are listed only once
     * @throws ContextualConceptNotExpandableException if none of the concept's term codes is in the concept tree
     */
    public List<ContextualTermCode> expandConcept(ContextualConcept contextualConcept)
            throws ContextualConceptNotExpandableException {
        var contextualTermCodes = contextualConcept.contextualTermCodes().stream().flatMap(conceptTree::expand).distinct().toList();
        if (contextualTermCodes.isEmpty()) {
            throw new ContextualConceptNotExpandableException(contextualConcept);
        }
        return contextualTermCodes;
    }

    /**
     * Returns the current day according to the clock of this mapping context.
     *
     * @return the current day
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
