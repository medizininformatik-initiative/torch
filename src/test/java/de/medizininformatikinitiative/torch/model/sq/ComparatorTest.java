package de.medizininformatikinitiative.torch.model.sq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparatorTest {

    @ParameterizedTest
    @EnumSource(Comparator.class)
    void fromJson_roundTripsToString(Comparator comparator) {
        assertThat(Comparator.fromJson(comparator.toString())).isEqualTo(comparator);
    }

    @Test
    void fromJson_unknown() {
        assertThatThrownBy(() -> Comparator.fromJson("ne"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown JSON comparator: ne");
    }
}
