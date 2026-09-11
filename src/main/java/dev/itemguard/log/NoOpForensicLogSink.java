package dev.itemguard.log;

import dev.itemguard.config.LoggingSettings;

import java.time.Duration;

public final class NoOpForensicLogSink implements ForensicLogSink {

    public static final NoOpForensicLogSink INSTANCE = new NoOpForensicLogSink();

    private final ForensicLogMetrics metrics = new ForensicLogMetrics();

    private NoOpForensicLogSink() {
        metrics.queueCapacity = 0;
    }

    @Override
    public void submit(ForensicLogRecord record) {
        // intentionally empty
    }

    @Override
    public ForensicLogMetrics metrics() {
        return metrics;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public ForensicLogSink applySettings(LoggingSettings settings) {
        return this;
    }

    @Override
    public void shutdown(Duration timeout) {
        // no-op
    }
}
