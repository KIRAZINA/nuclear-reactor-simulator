package org.reactor_model.ui;

import org.reactor_model.logger.ReactorLogger;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ReactorLogger implementation that enqueues formatted messages for the UI event log.
 * The Swing EDT drains the queue on a timer.
 */
public class UiReactorLogger implements ReactorLogger {

    public enum Level { INFO, WARNING, CRITICAL }

    public static final class LogEntry {
        public final Level  level;
        public final String text;

        LogEntry(Level level, String text) {
            this.level = level;
            this.text  = text;
        }
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BlockingQueue<LogEntry> queue = new LinkedBlockingQueue<>(2000);

    // ---- ReactorLogger implementation ----------------------------------

    @Override
    public void logState(double power, double temperature,
                         double coolantFlowRate, double rodPosition,
                         double reactivity) {
        enqueue(Level.INFO, String.format(
                "State | Power: %.1f MW | Temp: %.1f °C | "
                + "Coolant: %.2f | Rod: %.2f | React: %.5f",
                power, temperature, coolantFlowRate, rodPosition, reactivity));
    }

    @Override
    public void logWarning(String message) {
        Level lvl = message.contains("CRITICAL") || message.contains("SCRAM")
                ? Level.CRITICAL
                : Level.WARNING;
        enqueue(lvl, "⚠ " + message);
    }

    @Override
    public void logDecision(String source, String decision) {
        enqueue(Level.INFO, "[" + source + "] " + decision);
    }

    // ---- Queue access --------------------------------------------------

    /**
     * Returns the underlying queue so the EDT can drain it.
     */
    public BlockingQueue<LogEntry> getQueue() {
        return queue;
    }

    // ---- Internal ------------------------------------------------------

    private void enqueue(Level level, String text) {
        String timestamp = LocalTime.now().format(TIME_FMT);
        // offer() won't block; if queue is full, oldest entries are silently dropped
        queue.offer(new LogEntry(level, timestamp + "  " + text));
    }
}
