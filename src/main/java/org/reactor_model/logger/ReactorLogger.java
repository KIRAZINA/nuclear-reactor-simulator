package org.reactor_model.logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Unified logging interface for all reactor subsystems.
 * Ensures consistent formatting and timestamping.
 */
public interface ReactorLogger {

    DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Logs the full reactor state snapshot.
     */
    void logState(double power, double temperature, double coolantFlowRate,
                  double controlRodPosition, double reactivity);

    /**
     * Logs warnings about abnormal or dangerous conditions.
     */
    void logWarning(String message);

    /**
     * Logs decisions made by subsystems (regulator, cooling system, etc.).
     */
    void logDecision(String subsystem, String decision);

    /**
     * Provides a unified timestamp for all logs.
     */
    default String getTimestamp() {
        return LocalDateTime.now().format(FORMATTER);
    }
}

