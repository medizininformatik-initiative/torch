package de.medizininformatikinitiative.torch.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Suppresses a log event once its fully rendered text (arguments substituted) has repeated more
 * than {@code allowedRepetitions} times.
 *
 * <p>Logback's built-in {@link ch.qos.logback.classic.turbo.DuplicateMessageFilter} keys on the
 * raw, unsubstituted format string instead. For a parameterized call such as
 * {@code logger.warn("Skipping resource {}", id)}, every {@code id} shares the same format string,
 * so the built-in filter starts suppressing after the first few occurrences regardless of which
 * resource is being reported. Keying on the rendered message instead means only genuinely
 * identical messages are collapsed; calls that differ only in their arguments are each counted
 * independently.
 *
 * <p>The count cache is cleared every {@code resetIntervalMinutes} so a message suppressed during
 * a burst doesn't stay silent for the remaining lifetime of the server; there's no notion of "job"
 * involved, since the causes behind repeated messages (e.g. an unchecked ValueSet binding in the
 * loaded ontology) are typically independent of which job or batch happens to trigger them.
 */
public class FormattedDuplicateMessageFilter extends TurboFilter {

    public static final int DEFAULT_CACHE_SIZE = 1000;
    public static final int DEFAULT_ALLOWED_REPETITIONS = 5;
    public static final int DEFAULT_RESET_INTERVAL_MINUTES = 60;

    private int allowedRepetitions = DEFAULT_ALLOWED_REPETITIONS;
    private int cacheSize = DEFAULT_CACHE_SIZE;
    private int resetIntervalMinutes = DEFAULT_RESET_INTERVAL_MINUTES;
    private Map<String, Integer> messageCounts;
    private ScheduledExecutorService resetScheduler;

    @Override
    public void start() {
        messageCounts = newBoundedCache(cacheSize);
        resetScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "duplicate-message-filter-reset");
            thread.setDaemon(true);
            return thread;
        });
        resetScheduler.scheduleAtFixedRate(this::resetCounts, resetIntervalMinutes, resetIntervalMinutes, TimeUnit.MINUTES);
        super.start();
    }

    @Override
    public void stop() {
        if (resetScheduler != null) {
            resetScheduler.shutdownNow();
            resetScheduler = null;
        }
        messageCounts = null;
        super.stop();
    }

    /**
     * Clears all tracked message counts, giving every message a fresh repetition budget.
     * Called periodically by the reset scheduler; exposed for testing.
     */
    void resetCounts() {
        synchronized (messageCounts) {
            messageCounts.clear();
        }
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (!isStarted() || format == null) {
            return FilterReply.NEUTRAL;
        }
        String rendered = (params == null || params.length == 0) ? format : MessageFormatter.arrayFormat(format, params).getMessage();

        int countBeforeThisOccurrence;
        synchronized (messageCounts) {
            countBeforeThisOccurrence = messageCounts.getOrDefault(rendered, 0);
            messageCounts.put(rendered, countBeforeThisOccurrence + 1);
        }
        return countBeforeThisOccurrence <= allowedRepetitions ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    private static Map<String, Integer> newBoundedCache(int maxSize) {
        // accessOrder=true: a key is moved to the front on every touch, not just on first insertion, so a
        // message that keeps recurring is protected from eviction by unrelated one-off messages in between.
        // Initial capacity sized so the map never needs to resize while filling up to maxSize entries.
        int initialCapacity = (int) (maxSize / 0.75f) + 1;
        return new LinkedHashMap<>(initialCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                return size() > maxSize;
            }
        };
    }

    public int getAllowedRepetitions() {
        return allowedRepetitions;
    }

    public void setAllowedRepetitions(int allowedRepetitions) {
        this.allowedRepetitions = allowedRepetitions;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public int getResetIntervalMinutes() {
        return resetIntervalMinutes;
    }

    public void setResetIntervalMinutes(int resetIntervalMinutes) {
        this.resetIntervalMinutes = resetIntervalMinutes;
    }
}
