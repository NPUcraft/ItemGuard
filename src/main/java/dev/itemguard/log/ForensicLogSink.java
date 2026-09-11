package dev.itemguard.log;

import dev.itemguard.config.LoggingSettings;

import java.time.Duration;

/**
 * Non-blocking forensic sink. Implementations must never perform disk IO on the caller thread.
 */
public interface ForensicLogSink {

    void submit(ForensicLogRecord record);

    ForensicLogMetrics metrics();

    boolean enabled();

    ForensicLogSink applySettings(LoggingSettings settings);

    void shutdown(Duration timeout);
}
