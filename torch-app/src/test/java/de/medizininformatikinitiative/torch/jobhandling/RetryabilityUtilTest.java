package de.medizininformatikinitiative.torch.jobhandling;

import de.medizininformatikinitiative.torch.jobhandling.failure.RetryabilityUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class RetryabilityUtilTest {

    /**
     * Tries to create a reactor.netty.http.client.PrematureCloseException instance without
     * referencing the type at compile time.
     * <p>
     * Returns null if the class isn't present or can't be instantiated reflectively.
     */
    private static Throwable newPrematureCloseExceptionOrNull() {
        try {
            Class<?> c = Class.forName("reactor.netty.http.client.PrematureCloseException");

            // Try no-arg constructor first
            try {
                var ctor = c.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (Throwable) ctor.newInstance();
            } catch (NoSuchMethodException ignored) {
                // Try String constructor
            }

            try {
                var ctor = c.getDeclaredConstructor(String.class);
                ctor.setAccessible(true);
                return (Throwable) ctor.newInstance("premature close");
            } catch (NoSuchMethodException ignored) {
                // Try Throwable constructor (some variants have it)
            }

            try {
                var ctor = c.getDeclaredConstructor(Throwable.class);
                ctor.setAccessible(true);
                return (Throwable) ctor.newInstance(new IOException("premature close"));
            } catch (NoSuchMethodException ignored) {
                // give up
            }

            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Test
    void rootCause_walksToDeepest() {
        var root = new IllegalStateException("root");
        var mid = new RuntimeException("mid", root);
        var top = new RuntimeException("top", mid);

        assertThat(RetryabilityUtil.rootCause(top)).isSameAs(root);
    }

    @Test
    void rootCauseMessage_formatsWithAndWithoutMessage() {
        var withMsg = new IllegalArgumentException("nope");
        assertThat(RetryabilityUtil.rootCauseMessage(withMsg))
                .isEqualTo("IllegalArgumentException: nope");

        var noMsg = new IllegalArgumentException((String) null);
        assertThat(RetryabilityUtil.rootCauseMessage(noMsg))
                .isEqualTo("IllegalArgumentException");
    }

    @Test
    void isRetryable_trueForIOException() {
        assertThat(RetryabilityUtil.isRetryable(new IOException("anything"))).isTrue();

        // Also cover "root cause is IOException" path
        var wrapped = new RuntimeException("wrapper", new IOException("io"));
        assertThat(RetryabilityUtil.isRetryable(wrapped)).isTrue();
    }

    @Test
    void isRetryable_trueForMessageMatches_whenNotIOException() {
        assertThat(RetryabilityUtil.isRetryable(new Throwable("premature"))).isTrue();
        assertThat(RetryabilityUtil.isRetryable(new Throwable("connection reset by peer"))).isTrue();
        assertThat(RetryabilityUtil.isRetryable(new Throwable("broken pipe"))).isTrue();
        assertThat(RetryabilityUtil.isRetryable(new Throwable("closed channel"))).isTrue();
    }

    @Test
    void isRetryable_falseWhenNoMatchAndNoIOException() {
        assertThat(RetryabilityUtil.isRetryable(new Throwable("something else"))).isFalse();
        assertThat(RetryabilityUtil.isRetryable(new Throwable((String) null))).isFalse();
    }

    @Test
    void isRetryable_trueForPrematureCloseException_ifAvailable() {
        Throwable maybe = newPrematureCloseExceptionOrNull();
        if (maybe == null) {
            return;
        }
        assertThat(RetryabilityUtil.isRetryable(maybe)).isTrue();
    }
}
