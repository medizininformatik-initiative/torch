package de.medizininformatikinitiative.torch.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListUtilsTest {

    /**
     * [[a], [b]] -> [[a, b]]
     */
    @Test
    void cartesianProduct_one_one() {
        var m = ListUtils.cartesianProduct(List.of(List.of("a"), List.of("b")));

        assertThat(m).containsExactly(List.of("a", "b"));
    }

    /**
     * [[a, b], [c]] -> [[a, c], [b, c]]
     */
    @Test
    void cartesianProduct_two_one() {
        var m = ListUtils.cartesianProduct(List.of(List.of("a", "b"), List.of("c")));

        assertThat(m).containsExactly(List.of("a", "c"), List.of("b", "c"));
    }

    /**
     * [[a], [b, c]] -> [[a, b], [a, c]]
     */
    @Test
    void cartesianProduct_one_two() {
        var m = ListUtils.cartesianProduct(List.of(List.of("a"), List.of("b", "c")));

        assertThat(m).containsExactly(List.of("a", "b"), List.of("a", "c"));
    }

    /**
     * [[a, b], [c], [d]] -> [[a, c, d], [b, c, d]]
     */
    @Test
    void cartesianProduct_two_one_one() {
        var m = ListUtils.cartesianProduct(List.of(List.of("a", "b"), List.of("c"), List.of("d")));

        assertThat(m).containsExactly(List.of("a", "c", "d"), List.of("b", "c", "d"));
    }

    /**
     * [[a, b], [c], [d, e]] -> [[a, c, d], [a, c, e], [b, c, d], [b, c, e]]
     */
    @Test
    void cartesianProduct_two_one_two() {
        var m = ListUtils.cartesianProduct(List.of(List.of("a", "b"), List.of("c"), List.of("d", "e")));

        assertThat(m).containsExactly(
                List.of("a", "c", "d"),
                List.of("a", "c", "e"),
                List.of("b", "c", "d"),
                List.of("b", "c", "e"));
    }
}
