package de.medizininformatikinitiative.torch.model.consent;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodTest {

    @Test
    void testIntersect_noOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period p2 = Period.of(LocalDate.of(2025, 1, 11), LocalDate.of(2025, 1, 20));

        assertThat(p1.intersect(p2)).isNull();
        assertThat(p2.intersect(p1)).isNull();
    }

    @Test
    void testIntersect_partialOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period p2 = Period.of(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 15));

        Period intersection = p1.intersect(p2);
        assertThat(intersection.start()).isEqualTo(LocalDate.of(2025, 1, 5));
        assertThat(intersection.end()).isEqualTo(LocalDate.of(2025, 1, 10));
    }

    @Test
    void testIntersect_fullOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period p2 = Period.of(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 9));

        Period intersection = p1.intersect(p2);
        assertThat(intersection.start()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(intersection.end()).isEqualTo(LocalDate.of(2025, 1, 9));
    }

    @Test
    void testSubtract_noOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 11), LocalDate.of(2025, 1, 15));

        List<Period> result = p1.subtract(deny);
        assertThat(result).hasSize(1).containsExactly(p1);
    }

    @Test
    void testSubtract_partialLeft() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 7));

        List<Period> result = p1.subtract(deny);
        assertThat(result).containsExactly(
                Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 4)),
                Period.of(LocalDate.of(2025, 1, 8), LocalDate.of(2025, 1, 10))
        );
    }

    @Test
    void testSubtract_leftEdge() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5));

        List<Period> result = p1.subtract(deny);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(Period.of(LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10)));
    }

    @Test
    void testSubtract_rightEdge() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 10));

        List<Period> result = p1.subtract(deny);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 4)));
    }

    @Test
    void testSubtract_fullCover() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));

        List<Period> result = p1.subtract(deny);
        assertThat(result).isEmpty();
    }
}
