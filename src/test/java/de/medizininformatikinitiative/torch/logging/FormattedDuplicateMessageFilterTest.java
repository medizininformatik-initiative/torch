package de.medizininformatikinitiative.torch.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormattedDuplicateMessageFilterTest {

    private FormattedDuplicateMessageFilter filter;

    @BeforeEach
    void setUp() {
        filter = new FormattedDuplicateMessageFilter();
        filter.setAllowedRepetitions(5);
        filter.start();
    }

    @AfterEach
    void tearDown() {
        filter.stop();
    }

    @Test
    void suppressesLiteralMessageAfterAllowedRepetitions() {
        FilterReply[] replies = new FilterReply[10];
        for (int i = 0; i < replies.length; i++) {
            replies[i] = filter.decide(null, null, Level.WARN, "Some fixed warning", null, null);
        }

        assertThat(replies).startsWith(
                FilterReply.NEUTRAL, FilterReply.NEUTRAL, FilterReply.NEUTRAL,
                FilterReply.NEUTRAL, FilterReply.NEUTRAL, FilterReply.NEUTRAL);
        assertThat(replies).endsWith(FilterReply.DENY, FilterReply.DENY, FilterReply.DENY, FilterReply.DENY);
    }

    @Test
    void doesNotSuppressParameterizedCallsWithDifferentArguments() {
        for (int i = 0; i < 10; i++) {
            FilterReply reply = filter.decide(null, null, Level.WARN, "Skipping resource {}", new Object[]{"Patient/" + i}, null);
            assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
        }
    }

    @Test
    void suppressesParameterizedCallsWithTheSameArgumentAfterAllowedRepetitions() {
        FilterReply[] replies = new FilterReply[10];
        for (int i = 0; i < replies.length; i++) {
            replies[i] = filter.decide(null, null, Level.WARN, "Skipping resource {}", new Object[]{"Patient/1"}, null);
        }

        assertThat(replies).startsWith(
                FilterReply.NEUTRAL, FilterReply.NEUTRAL, FilterReply.NEUTRAL,
                FilterReply.NEUTRAL, FilterReply.NEUTRAL, FilterReply.NEUTRAL);
        assertThat(replies).endsWith(FilterReply.DENY, FilterReply.DENY, FilterReply.DENY, FilterReply.DENY);
    }

    @Test
    void recurringMessageSurvivesEvictionPressureFromUnrelatedOneOffMessages() {
        FormattedDuplicateMessageFilter smallCache = new FormattedDuplicateMessageFilter();
        smallCache.setAllowedRepetitions(3);
        smallCache.setCacheSize(10);
        smallCache.start();
        try {
            FilterReply lastReply = null;
            // Each burst touches the recurring message once, then floods fewer distinct one-off
            // messages than the cache holds. Cumulatively, the one-offs alone (5 bursts * 5 = 25) far
            // exceed the cache size (10), so under plain insertion-order LRU the recurring message
            // would eventually be evicted (and its count reset) purely because it was first inserted
            // a while ago, even though it keeps being touched. With access-order LRU it stays "fresh"
            // on every touch and its count keeps accumulating.
            for (int burst = 0; burst < 5; burst++) {
                lastReply = smallCache.decide(null, null, Level.WARN, "Recurring warning", null, null);
                for (int i = 0; i < 5; i++) {
                    smallCache.decide(null, null, Level.WARN, "One-off warning {}", new Object[]{burst + "-" + i}, null);
                }
            }

            assertThat(lastReply).isEqualTo(FilterReply.DENY);
        } finally {
            smallCache.stop();
        }
    }

    @Test
    void resetCountsGivesASuppressedMessageAFreshBudget() {
        for (int i = 0; i < 10; i++) {
            filter.decide(null, null, Level.WARN, "Some fixed warning", null, null);
        }
        assertThat(filter.decide(null, null, Level.WARN, "Some fixed warning", null, null)).isEqualTo(FilterReply.DENY);

        filter.resetCounts();

        assertThat(filter.decide(null, null, Level.WARN, "Some fixed warning", null, null)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void isNeutralWhenNotStarted() {
        FormattedDuplicateMessageFilter neverStarted = new FormattedDuplicateMessageFilter();

        assertThat(neverStarted.decide(null, null, Level.WARN, "Some warning", null, null)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void isNeutralForANullFormat() {
        assertThat(filter.decide(null, null, Level.WARN, null, null, null)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void treatsAnEmptyArgumentArrayLikeNoArguments() {
        FilterReply[] replies = new FilterReply[7];
        for (int i = 0; i < replies.length; i++) {
            replies[i] = filter.decide(null, null, Level.WARN, "Some fixed warning", new Object[0], null);
        }

        assertThat(replies).startsWith(
                FilterReply.NEUTRAL, FilterReply.NEUTRAL, FilterReply.NEUTRAL,
                FilterReply.NEUTRAL, FilterReply.NEUTRAL, FilterReply.NEUTRAL);
        assertThat(replies).endsWith(FilterReply.DENY);
    }

    @Test
    void stopIsSafeOnAFilterThatWasNeverStarted() {
        FormattedDuplicateMessageFilter neverStarted = new FormattedDuplicateMessageFilter();

        neverStarted.stop();
    }

    @Test
    void gettersReflectConfiguredValues() {
        filter.setAllowedRepetitions(7);
        filter.setCacheSize(42);
        filter.setResetIntervalMinutes(15);

        assertThat(filter.getAllowedRepetitions()).isEqualTo(7);
        assertThat(filter.getCacheSize()).isEqualTo(42);
        assertThat(filter.getResetIntervalMinutes()).isEqualTo(15);
    }
}
