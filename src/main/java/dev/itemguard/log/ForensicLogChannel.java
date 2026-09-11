package dev.itemguard.log;

public enum ForensicLogChannel {
    ALERTS("alerts"),
    EVENTS("events"),
    ADMIN("admin");

    private final String directory;

    ForensicLogChannel(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }

    public static ForensicLogChannel of(ForensicLogType type) {
        return switch (type) {
            case ALERT -> ALERTS;
            case ADMIN_ACTION -> ADMIN;
            default -> EVENTS;
        };
    }
}
