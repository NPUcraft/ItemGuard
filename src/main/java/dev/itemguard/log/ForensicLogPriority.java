package dev.itemguard.log;

public enum ForensicLogPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW;

    public boolean urgent() {
        return this == CRITICAL || this == HIGH;
    }
}
