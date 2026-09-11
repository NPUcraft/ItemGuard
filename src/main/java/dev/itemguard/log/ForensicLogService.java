package dev.itemguard.log;

import dev.itemguard.config.LoggingSettings;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded dual-queue forensic logger. {@link #submit} never blocks and never writes files.
 */
public final class ForensicLogService implements ForensicLogSink {

    private final Path dataFolder;
    private final Logger logger;
    private final Clock clock;
    private final ArrayBlockingQueue<ForensicLogRecord> urgent;
    private final ArrayBlockingQueue<ForensicLogRecord> bulk;
    private final ForensicLogMetrics metrics = new ForensicLogMetrics();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final RateLimiter dropWarnings = new RateLimiter();
    private final RateLimiter errorWarnings = new RateLimiter();
    private final JsonlLogWriter writer;
    private volatile LoggingSettings settings;
    private final Thread worker;

    public ForensicLogService(Path dataFolder, LoggingSettings settings, Logger logger) {
        this(dataFolder, settings, logger, Clock.systemDefaultZone());
    }

    public ForensicLogService(Path dataFolder, LoggingSettings settings, Logger logger, Clock clock) {
        this.dataFolder = dataFolder;
        this.settings = settings;
        this.logger = logger;
        this.clock = clock;
        this.urgent = new ArrayBlockingQueue<>(settings.urgentCapacity());
        this.bulk = new ArrayBlockingQueue<>(settings.bulkCapacity());
        this.writer = new JsonlLogWriter(settings.logsRoot(), settings, logger, clock);
        this.metrics.queueCapacity = settings.queueCapacity();
        this.worker = new Thread(this::run, "ItemGuard-ForensicLogWriter");
        this.worker.setDaemon(false);
        try {
            this.writer.ensureDirectories();
            this.writer.cleanupNow();
        } catch (IOException exception) {
            logger.log(Level.WARNING, "[ItemGuard] Forensic log directories could not be created; logging is degraded", exception);
            metrics.unhealthy = true;
        }
    }

    public void start() {
        worker.start();
    }

    public static ForensicLogSink create(Path dataFolder, LoggingSettings settings, Logger logger) {
        if (settings == null || !settings.enabled()) {
            return NoOpForensicLogSink.INSTANCE;
        }
        ForensicLogService service = new ForensicLogService(dataFolder, settings, logger);
        service.start();
        return service;
    }

    @Override
    public void submit(ForensicLogRecord record) {
        if (record == null || !accepting.get()) {
            return;
        }
        ArrayBlockingQueue<ForensicLogRecord> target = record.priority().urgent() ? urgent : bulk;
        if (target.offer(record)) {
            refreshQueueSize();
            return;
        }
        long dropped = metrics.dropped.incrementAndGet();
        if (record.priority() == ForensicLogPriority.CRITICAL) {
            metrics.droppedCritical.incrementAndGet();
            if (errorWarnings.shouldLog(settings.dropWarningInterval().toMillis(), clock.millis())) {
                logger.warning("[ItemGuard] Forensic log could not enqueue a CRITICAL record; core detection continues. "
                        + dropped + " total drops.");
            }
            return;
        }
        if (dropWarnings.shouldLog(settings.dropWarningInterval().toMillis(), clock.millis())) {
            logger.warning("[ItemGuard] Forensic log queue full; "
                    + dropWarnings.take() + " records were dropped in the last interval ("
                    + dropped + " total).");
        }
        refreshQueueSize();
    }

    @Override
    public ForensicLogMetrics metrics() {
        refreshQueueSize();
        metrics.unhealthy = writer.unhealthy();
        metrics.currentFile = writer.lastRelativePath();
        return metrics;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public ForensicLogSink applySettings(LoggingSettings next) {
        if (next == null || !next.enabled()) {
            shutdown(settings.shutdownTimeout());
            return NoOpForensicLogSink.INSTANCE;
        }
        if (settings.needsWriterRestart(next)) {
            shutdown(settings.shutdownTimeout());
            ForensicLogService replacement = new ForensicLogService(dataFolder, next, logger, clock);
            replacement.start();
            return replacement;
        }
        this.settings = next;
        this.writer.updateSettings(next);
        return this;
    }

    @Override
    public void shutdown(Duration timeout) {
        accepting.set(false);
        running.set(false);
        worker.interrupt();
        long wait = timeout == null ? 5_000L : Math.max(250L, timeout.toMillis());
        try {
            worker.join(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        int remaining = urgent.size() + bulk.size();
        if (worker.isAlive() || remaining > 0) {
            logger.warning("[ItemGuard] Forensic log writer stopped with "
                    + remaining + " records that could not be flushed before shutdown.");
        }
        writer.close();
    }

    private void run() {
        long lastFlush = clock.millis();
        try {
            while (running.get() || !queuesEmpty()) {
                List<ForensicLogRecord> batch = drain(running.get() ? 200L : 10L);
                if (!batch.isEmpty()) {
                    persist(batch);
                    lastFlush = maybeFlush(lastFlush, containsCritical(batch));
                } else {
                    lastFlush = maybeFlush(lastFlush, false);
                    writer.maybeReopen();
                }
            }
            persist(drain(0L));
            try {
                writer.flush();
            } catch (IOException exception) {
                logger.log(Level.WARNING, "[ItemGuard] Forensic log final flush failed", exception);
            }
        } finally {
            writer.close();
        }
    }

    private List<ForensicLogRecord> drain(long waitMs) {
        List<ForensicLogRecord> batch = new ArrayList<>(64);
        ForensicLogRecord first = urgent.poll();
        if (first == null) {
            try {
                first = waitMs <= 0 ? bulk.poll() : bulk.poll(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                first = bulk.poll();
            }
        }
        if (first == null) {
            first = urgent.poll();
        }
        if (first == null) {
            return batch;
        }
        batch.add(first);
        urgent.drainTo(batch, 48);
        if (batch.size() < 64) {
            bulk.drainTo(batch, 64 - batch.size());
        }
        refreshQueueSize();
        return batch;
    }

    private void persist(List<ForensicLogRecord> batch) {
        if (batch.isEmpty()) {
            return;
        }
        if (writer.unhealthy()) {
            writer.maybeReopen();
            if (writer.unhealthy()) {
                dropBatch(batch);
                return;
            }
        }
        try {
            writer.write(batch);
            metrics.written.addAndGet(batch.size());
            metrics.lastWriteEpochMillis.set(clock.millis());
            metrics.currentFile = writer.lastRelativePath();
            metrics.unhealthy = false;
        } catch (IOException exception) {
            metrics.lastErrorEpochMillis.set(clock.millis());
            metrics.unhealthy = true;
            dropBatch(batch);
            if (errorWarnings.shouldLog(settings.dropWarningInterval().toMillis(), clock.millis())) {
                logger.log(Level.WARNING, "[ItemGuard] Forensic log write failed; ItemGuard detection continues", exception);
            }
        }
    }

    private void dropBatch(List<ForensicLogRecord> batch) {
        metrics.dropped.addAndGet(batch.size());
        for (ForensicLogRecord record : batch) {
            if (record.priority() == ForensicLogPriority.CRITICAL) {
                metrics.droppedCritical.incrementAndGet();
            }
        }
    }

    private long maybeFlush(long lastFlush, boolean critical) {
        long now = clock.millis();
        if (critical || now - lastFlush >= settings.flushInterval().toMillis()) {
            try {
                writer.flush();
            } catch (IOException exception) {
                metrics.unhealthy = true;
                metrics.lastErrorEpochMillis.set(now);
            }
            return now;
        }
        return lastFlush;
    }

    private boolean queuesEmpty() {
        return urgent.isEmpty() && bulk.isEmpty();
    }

    private void refreshQueueSize() {
        metrics.queueSize = urgent.size() + bulk.size();
        metrics.queueCapacity = settings.queueCapacity();
        metrics.currentFile = writer.lastRelativePath();
    }

    private static boolean containsCritical(List<ForensicLogRecord> batch) {
        for (ForensicLogRecord record : batch) {
            if (record.priority() == ForensicLogPriority.CRITICAL) {
                return true;
            }
        }
        return false;
    }

    static final class RateLimiter {
        private final AtomicLong lastLog = new AtomicLong();
        private final AtomicLong suppressed = new AtomicLong();

        boolean shouldLog(long intervalMs, long now) {
            long previous = lastLog.get();
            if (now - previous >= intervalMs && lastLog.compareAndSet(previous, now)) {
                return true;
            }
            suppressed.incrementAndGet();
            return false;
        }

        long take() {
            return suppressed.getAndSet(0) + 1;
        }
    }
}
