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
 * queries. Non-token params (dates, quantities, ...) are never joined. For example,
 * <pre>
 * Condition?code=icd10|C50&amp;clinical-status=active
 * Condition?code=icd10|C51&amp;clinical-status=active
 * Observation?code=loinc|1234
 * </pre>
 * become
 * <pre>
 * Condition?code=icd10|C50,icd10|C51&amp;clinical-status=active
 * Observation?code=loinc|1234
 * </pre>
 * The Condition queries differ only in the {@code code} value and are joined; the Observation query differs in its
 * type and stays unchanged.
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
     *
     * @param queries  the queries to join
     * @param position the index of the param whose values are joined
     * @return the joined queries, in the order of their first occurrence
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

    /**
     * @param query    the query to compute the key for
     * @param position the index of the param whose values are joined
     * @return a key equal for all queries that can be joined at {@code position}, or the query itself if the param
     * there is missing or not a token
     */
    private static Object joinKey(Query query, int position) {
        var params = query.params().params();
        if (params.size() <= position || !QueryParams.isToken(params.get(position).value())) {
            return query;
        }
        var others = new ArrayList<>(params);
        others.remove(position);
        return new JoinKey(query.type(), params.get(position).name(), others);
    }

    /**
     * @param group    queries with equal keys at {@code position}
     * @param position the index of the param whose values are joined
     * @return the first query with the param at {@code position} replaced by the OR of all values of the group
     */
    private static Query joinValuesAt(List<Query> group, int position) {
        var params = new ArrayList<>(group.getFirst().params().params());
        var values = group.stream().map(query -> query.params().params().get(position).value()).toList();
        params.set(position, new Param(params.get(position).name(), QueryParams.orValue(values)));
        return Query.of(group.getFirst().type(), new QueryParams(params));
    }

    private record JoinKey(String type, String name, List<Param> others) {
    }
}
