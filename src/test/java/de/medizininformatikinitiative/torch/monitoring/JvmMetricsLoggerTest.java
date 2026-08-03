package de.medizininformatikinitiative.torch.monitoring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.medizininformatikinitiative.torch.config.JvmMetricsLoggerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JvmMetricsLoggerTest {

    @Mock
    MemoryMXBean memoryMXBean;
    @Mock
    ScheduledExecutorService scheduler;

    private ch.qos.logback.classic.Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(JvmMetricsLogger.class);
        logbackLogger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
    }

    private static MemoryUsage memoryUsage(long used, long committed, long max) {
        return new MemoryUsage(0, used, committed, max);
    }

    private static GarbageCollectorMXBean gcBean(String name, long count, long timeMs) {
        return new GarbageCollectorMXBean() {
            @Override
            public long getCollectionCount() {
                return count;
            }

            @Override
            public long getCollectionTime() {
                return timeMs;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public String[] getMemoryPoolNames() {
                return new String[0];
            }

            @Override
            public javax.management.ObjectName getObjectName() {
                return null;
            }
        };
    }

    @Test
    void heapUsagePercentComputesRoundedDownPercentage() {
        assertThat(JvmMetricsLogger.heapUsagePercent(memoryUsage(500, 1000, 1000))).isEqualTo(50);
    }

    @Test
    void heapUsagePercentReturnsZeroWhenMaxUndefined() {
        assertThat(JvmMetricsLogger.heapUsagePercent(memoryUsage(500, 1000, -1))).isZero();
    }

    @Test
    void usageLineFormatsMegabytesAndSingleCollection() {
        MemoryUsage heap = memoryUsage(512 * 1024 * 1024L, 1024 * 1024 * 1024L, 2048 * 1024 * 1024L);
        MemoryUsage nonHeap = memoryUsage(100 * 1024 * 1024L, 200 * 1024 * 1024L, -1);
        GarbageCollectorMXBean gc = gcBean("G1 Young Generation", 1, 500);

        String line = JvmMetricsLogger.usageLine(heap, nonHeap, List.of(gc), 25);

        assertThat(line).isEqualTo("Heap: 512 MB used / 1.0 GB committed / 2.0 GB max (25%), " +
                "Non-Heap: 100 MB used / 200 MB committed, GC: G1 Young Generation 1 collection 0.5s");
    }

    @Test
    void usageLineFormatsGigabytesAndMultipleCollectionsAndIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            MemoryUsage heap = memoryUsage(1024 * 1024 * 1024L, 2048 * 1024 * 1024L, 2048 * 1024 * 1024L);
            MemoryUsage nonHeap = memoryUsage(0, 0, -1);
            GarbageCollectorMXBean gc = gcBean("G1 Old Generation", 5, 2000);

            String line = JvmMetricsLogger.usageLine(heap, nonHeap, List.of(gc), 50);

            assertThat(line).isEqualTo("Heap: 1.0 GB used / 2.0 GB committed / 2.0 GB max (50%), " +
                    "Non-Heap: 0 MB used / 0 MB committed, GC: G1 Old Generation 5 collections 2.0s");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void usageLineOmitsGcPartWhenNoBeans() {
        MemoryUsage heap = memoryUsage(0, 0, 1000);
        MemoryUsage nonHeap = memoryUsage(0, 0, -1);

        String line = JvmMetricsLogger.usageLine(heap, nonHeap, List.of(), 0);

        assertThat(line).isEqualTo("Heap: 0 MB used / 0 MB committed / 0 MB max (0%), Non-Heap: 0 MB used / 0 MB committed");
    }

    @Test
    void logsWarnWhenHeapUsageAtOrAboveThreshold() {
        when(memoryMXBean.getHeapMemoryUsage()).thenReturn(memoryUsage(900, 1000, 1000));
        when(memoryMXBean.getNonHeapMemoryUsage()).thenReturn(memoryUsage(10, 20, -1));
        var properties = new JvmMetricsLoggerProperties(Duration.ofMinutes(5), 5, 80);
        var jvmMetricsLogger = new JvmMetricsLogger(properties, memoryMXBean, scheduler);

        jvmMetricsLogger.logMetrics();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).startsWith("High heap usage - Heap:");
    }

    @Test
    void logsDebugOnlyOnEveryWarnFactorTickWhenBelowThreshold() {
        when(memoryMXBean.getHeapMemoryUsage()).thenReturn(memoryUsage(100, 1000, 1000));
        when(memoryMXBean.getNonHeapMemoryUsage()).thenReturn(memoryUsage(10, 20, -1));
        var properties = new JvmMetricsLoggerProperties(Duration.ofMinutes(5), 2, 80);
        var jvmMetricsLogger = new JvmMetricsLogger(properties, memoryMXBean, scheduler);

        jvmMetricsLogger.logMetrics();
        assertThat(appender.list).isEmpty();

        jvmMetricsLogger.logMetrics();
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
        assertThat(appender.list.get(0).getFormattedMessage()).startsWith("Heap:");
    }

    @Test
    void tickCountAdvancesDuringWarnTicksSoDebugCadenceIsNotDelayed() {
        var properties = new JvmMetricsLoggerProperties(Duration.ofMinutes(5), 3, 50);
        var jvmMetricsLogger = new JvmMetricsLogger(properties, memoryMXBean, scheduler);

        when(memoryMXBean.getNonHeapMemoryUsage()).thenReturn(memoryUsage(10, 20, -1));

        when(memoryMXBean.getHeapMemoryUsage()).thenReturn(memoryUsage(100, 1000, 1000)); // 10%, tick 1
        jvmMetricsLogger.logMetrics();
        when(memoryMXBean.getHeapMemoryUsage()).thenReturn(memoryUsage(900, 1000, 1000)); // 90%, tick 2 -> WARN
        jvmMetricsLogger.logMetrics();
        when(memoryMXBean.getHeapMemoryUsage()).thenReturn(memoryUsage(100, 1000, 1000)); // 10%, tick 3 -> DEBUG
        jvmMetricsLogger.logMetrics();

        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.DEBUG);
    }

    @Test
    void startSchedulesAtTickIntervalDerivedFromIntervalAndWarnFactor() {
        var properties = new JvmMetricsLoggerProperties(Duration.ofMinutes(5), 5, 80);
        var jvmMetricsLogger = new JvmMetricsLogger(properties, memoryMXBean, scheduler);

        jvmMetricsLogger.start();

        long expectedTickMillis = Duration.ofMinutes(1).toMillis();
        verify(scheduler).scheduleAtFixedRate(any(), eq(expectedTickMillis), eq(expectedTickMillis), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void stopShutsDownScheduler() {
        var properties = new JvmMetricsLoggerProperties(Duration.ofMinutes(5), 5, 80);
        var jvmMetricsLogger = new JvmMetricsLogger(properties, memoryMXBean, scheduler);

        jvmMetricsLogger.stop();

        verify(scheduler).shutdownNow();
    }
}
