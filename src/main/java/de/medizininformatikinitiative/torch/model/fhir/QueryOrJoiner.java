package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.model.fhir.QueryParams.Param;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reduces a union of FHIR search queries by joining queries that differ only in the value of one token param into a
 * single query with a comma-separated (OR) value.
 *
 * <p>The resources matched by the joined queries are exactly the union of the resources matched by the original
 * queries. Non-token params (dates, quantities, ...) are never joined.
 */
public final class QueryOrJoiner {

    private QueryOrJoiner() {
    }

    /**
     * @param queries queries whose matches are combined by union
     * @return queries with the same union of matches, in the order of their first occurrence
     */
    public static List<Query> join(List<Query> queries) {
        List<Query> result = List.copyOf(new LinkedHashSet<>(queries));
        int maxParams = result.stream().mapToInt(query -> query.params().params().size()).max().orElse(0);
        for (int position = 0; position < maxParams; position++) {
            result = joinAt(result, position);
        }
        return result;
    }

    /**
     * Joins the queries that are equal in everything except the token value of the param at {@code position}.
     */
    private static List<Query> joinAt(List<Query> queries, int position) {
        var groups = new LinkedHashMap<Object, List<Query>>();
        for (Query query : queries) {
            groups.computeIfAbsent(joinKey(query, position), key -> new ArrayList<>()).add(query);
        }
        return groups.values().stream()
                .map(group -> group.size() == 1 ? group.getFirst() : joinValuesAt(group, position))
                .toList();
    }

    private static Object joinKey(Query query, int position) {
        var params = query.params().params();
        if (params.size() <= position || !QueryParams.isToken(params.get(position).value())) {
            return query;
        }
        var others = new ArrayList<>(params);
        others.remove(position);
        return new JoinKey(query.type(), params.get(position).name(), others);
    }

    private static Query joinValuesAt(List<Query> group, int position) {
        var params = new ArrayList<>(group.getFirst().params().params());
        var values = group.stream().map(query -> query.params().params().get(position).value()).toList();
        params.set(position, new Param(params.get(position).name(), QueryParams.orValue(values)));
        return Query.of(group.getFirst().type(), new QueryParams(params));
    }

    private record JoinKey(String type, String name, List<Param> others) {
    }
}
