package org.reactor_model.logger;

// ReactorLogger.java
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Interface for logging reactor states and events.
 * Ensures all logs are timestamped and human-readable.
 */
public interface ReactorLogger {
    void logState(double power, double temperature, double coolantFlowRate, double controlRodPosition, double reactivity);
    void logWarning(String message);
    void logDecision(String subsystem, String decision);

    default String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

