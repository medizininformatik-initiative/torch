package de.medizininformatikinitiative.torch.monitoring;

import de.medizininformatikinitiative.torch.config.JvmMetricsLoggerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Periodically logs JVM heap, non-heap, and GC usage so that heap pressure is visible in the
 * application log even without a Prometheus setup.
 *
 * <p>Runs on its own single-thread scheduler at a fixed tick of {@code interval / warnFactor}, so
 * that the more frequent, high-priority check for elevated heap usage is never delayed by unrelated
 * blocking work on Spring's shared task scheduler. On each tick, if heap usage is at or above
 * {@code warnThreshold}, a line is logged at {@code WARN}. Otherwise, a line is logged at
 * {@code DEBUG} every {@code warnFactor}-th tick, i.e. roughly every {@code interval}. The two cases
 * are mutually exclusive so elevated usage does not also produce a redundant {@code DEBUG} line.</p>
 */
@Service
public class JvmMetricsLogger {

    private static final Logger logger = LoggerFactory.getLogger(JvmMetricsLogger.class);

    private static final long BYTES_PER_GB = 1024L * 1024 * 1024;
    private static final long BYTES_PER_MB = 1024L * 1024;

    private final JvmMetricsLoggerProperties properties;
    private final MemoryMXBean memoryMXBean;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong tickCount = new AtomicLong();

    @Autowired
    public JvmMetricsLogger(JvmMetricsLoggerProperties properties) {
        this(properties, ManagementFactory.getMemoryMXBean(),
                Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "jvm-metrics-logger")));
    }

    JvmMetricsLogger(JvmMetricsLoggerProperties properties, MemoryMXBean memoryMXBean, ScheduledExecutorService scheduler) {
        this.properties = properties;
        this.memoryMXBean = memoryMXBean;
        this.scheduler = scheduler;
    }

    @PostConstruct
    void start() {
        Duration tickInterval = properties.interval().dividedBy(properties.warnFactor());
        logger.info("Start JVM metrics logger with an interval of {}, a warn factor of {} and a warn threshold of {}%",
                properties.interval(), properties.warnFactor(), properties.warnThreshold());
        scheduler.scheduleAtFixedRate(this::logMetrics, tickInterval.toMillis(), tickInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    void logMetrics() {
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        int pct = heapUsagePercent(heap);
        long tick = tickCount.incrementAndGet();

        if (pct >= properties.warnThreshold()) {
            logger.warn("High heap usage - {}", usageLine(heap, nonHeap, gcBeans, pct));
        } else if (tick % properties.warnFactor() == 0) {
            logger.debug(usageLine(heap, nonHeap, gcBeans, pct));
        }
    }

    static int heapUsagePercent(MemoryUsage usage) {
        long max = usage.getMax();
        return max > 0 ? (int) (100L * usage.getUsed() / max) : 0;
    }

    static String usageLine(MemoryUsage heap, MemoryUsage nonHeap, List<GarbageCollectorMXBean> gcBeans, int pct) {
        String gcPart = gcBeans.stream().map(JvmMetricsLogger::formatGc).collect(Collectors.joining(", "));
        return String.format(Locale.ROOT, "Heap: %s used / %s committed / %s max (%d%%), Non-Heap: %s used / %s committed%s",
                formatBytes(heap.getUsed()), formatBytes(heap.getCommitted()), formatBytes(heap.getMax()), pct,
                formatBytes(nonHeap.getUsed()), formatBytes(nonHeap.getCommitted()),
                gcPart.isEmpty() ? "" : ", GC: " + gcPart);
    }

    private static String formatGc(GarbageCollectorMXBean gc) {
        long count = gc.getCollectionCount();
        double seconds = gc.getCollectionTime() / 1000.0;
        return String.format(Locale.ROOT, "%s %d %s %.1fs", gc.getName(), count, count == 1 ? "collection" : "collections", seconds);
    }

    private static String formatBytes(long bytes) {
        return bytes >= BYTES_PER_GB
                ? String.format(Locale.ROOT, "%.1f GB", bytes / (double) BYTES_PER_GB)
                : String.format(Locale.ROOT, "%.0f MB", bytes / (double) BYTES_PER_MB);
    }
}
