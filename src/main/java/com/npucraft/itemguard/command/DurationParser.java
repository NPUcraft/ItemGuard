package com.npucraft.itemguard.command;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("^(?<value>\\d+)(?<unit>ms|s|m|h|d)?$", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Optional<Duration> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long value = Long.parseLong(matcher.group("value"));
        String unit = matcher.group("unit");
        if (unit == null) {
            unit = "s";
        }
        Duration duration = switch (unit) {
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> null;
        };
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return Optional.empty();
        }
        return Optional.of(duration);
    }

    public static String format(Duration duration) {
        if (duration.toHours() > 0 && duration.toHours() * 60 == duration.toMinutes()) {
            return duration.toHours() + "h";
        }
        if (duration.toMinutes() > 0 && duration.toMinutes() * 60 == duration.getSeconds()) {
            return duration.toMinutes() + "m";
        }
        if (duration.toSeconds() > 0 && duration.getNano() == 0) {
            return duration.toSeconds() + "s";
        }
        return duration.toMillis() + "ms";
    }
}
