package de.medizininformatikinitiative.torch.model.sq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeRestrictionTest {

    @Test
    void fromJson_withInvalidAfterDate() {
        assertThatThrownBy(() -> parse("""
                {
                  "afterDate": "foo",
                  "beforeDate": "2021-10-09"
                }
                """))
                .hasRootCauseMessage("Invalid value `foo` in time restriction property `afterDate`.");
    }

    @Test
    void fromJson_withInvalidBeforeDate() {
        assertThatThrownBy(() -> parse("""
                {
                  "afterDate": "2021-10-09",
                  "beforeDate": "bar"
                }
                """))
                .hasRootCauseMessage("Invalid value `bar` in time restriction property `beforeDate`.");
    }

    @Test
    void fromJson_withBothDatesMissing() {
        assertThatThrownBy(() -> parse("{}"))
                .hasRootCauseMessage("Missing properties expect at least one of `afterDate` or `beforeDate`.");
    }

    @Test
    void fromJson_withoutAfterDate() throws Exception {
        var result = parse("""
                {
                  "beforeDate": "2021-10-09"
                }
                """);

        assertThat(result).isEqualTo(new TimeRestriction.OpenStart(LocalDate.of(2021, 10, 9)));
    }

    @Test
    void fromJson_withoutBeforeDate() throws Exception {
        var result = parse("""
                {
                  "afterDate": "2023-03-16"
                }
                """);

        assertThat(result).isEqualTo(new TimeRestriction.OpenEnd(LocalDate.of(2023, 3, 16)));
    }

    static TimeRestriction parse(String s) throws JsonProcessingException {
        return new ObjectMapper().readValue(s, TimeRestriction.class);
    }
}
