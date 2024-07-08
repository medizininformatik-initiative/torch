package de.medizininformatikinitiative.flare;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.flare.model.mapping.Mapping;
import de.medizininformatikinitiative.flare.model.mapping.MappingContext;
import de.medizininformatikinitiative.flare.model.mapping.TermCodeNode;
import de.medizininformatikinitiative.flare.model.sq.ContextualTermCode;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.function.Function.identity;

public interface Util {

    static <T, U extends T> List<T> add(List<T> xs, U x) {
        return Stream.concat(xs.stream(), Stream.of(x)).toList();
    }

    /**
     * In Clojure this is:
     * <pre>
     * (reduce
     *   (fn [result rows]
     *     (reduce
     *       (fn [new-result result-rows]
     *         (reduce
     *           (fn [new-result-row x]
     *             (conj new-result-row (conj result-rows x)))
     *           new-result
     *           rows))
     *       []
     *       result))
     *   [[]]
     *   matrix)
     * </pre>
     */
    static <T> List<List<T>> cartesianProduct(List<List<T>> matrix) {
        var result = List.of(List.<T>of());
        for (var rows : matrix) {
            result = cartesianProductHelper1(result, rows);
        }
        return result;
    }

    private static <T> List<List<T>> cartesianProductHelper1(List<List<T>> result, List<T> rows) {
        var newResult = List.<List<T>>of();
        for (var resultRows : result) {
            newResult = cartesianProductHelper2(newResult, resultRows, rows);
        }
        return newResult;
    }

    private static <T> List<List<T>> cartesianProductHelper2(List<List<T>> newResult, List<T> resultRows, List<T> rows) {
        for (var x : rows) {
            newResult = add(newResult, add(resultRows, x));
        }
        return newResult;
    }

    static <T> List<T> concat(List<T> as, List<? extends T> bs) {
        return Stream.concat(as.stream(), bs.stream()).toList();
    }

    static double durationSecondsSince(long startNanoTime) {
        return ((double) (System.nanoTime() - startNanoTime)) / 1e9;
    }

    static MappingContext flareMappingContext(Clock clock) throws Exception {
        var mapper = new ObjectMapper();
        String ontologyZipFile = "ontology/mapping.zip";
        String mappingFile = "ontology/mapping/mapping_fhir.json";
        String conceptTreeFile = "ontology/mapping/mapping_tree.json";

        Map<ContextualTermCode, Mapping> mappings = null;
        TermCodeNode conceptTree = null;

        try (FileInputStream fis = new FileInputStream(ontologyZipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(mappingFile)) {
                    mappings = Arrays.stream(mapper.readValue(readZipEntryContent(zis), Mapping[].class))
                            .collect(Collectors.toMap(Mapping::key, identity()));
                } else if (ze.getName().equals(conceptTreeFile)) {
                    String treeString = readZipEntryContent(zis);
                    conceptTree = mapper.readValue(treeString, TermCodeNode.class);
                }
            }
        }
        return MappingContext.of(mappings, conceptTree, clock);
    }

    private static String readZipEntryContent(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
