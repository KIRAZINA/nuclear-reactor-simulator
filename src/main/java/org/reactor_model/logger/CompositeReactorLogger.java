package org.reactor_model.logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fans out every {@link ReactorLogger} call to a list of delegates
 * (e.g. console/UI plus file). A failing delegate never prevents the
 * remaining loggers from receiving the message.
 */
public class CompositeReactorLogger implements ReactorLogger {

    private final List<ReactorLogger> delegates;

    /**
     * Creates a composite forwarding to each non-null logger in order.
     *
     * @param loggers at least one non-null logger
     */
    public CompositeReactorLogger(ReactorLogger... loggers) {
        // Arrays.asList (unlike List.of) tolerates null elements so they can be
        // filtered below; a null array still means "no delegates".
        this(loggers == null ? List.of() : Arrays.asList(loggers));
    }

    /**
     * Creates a composite forwarding to each non-null logger in order.
     *
     * @param loggers at least one non-null logger
     */
    public CompositeReactorLogger(List<ReactorLogger> loggers) {
        List<ReactorLogger> cleaned = new ArrayList<>();
        if (loggers != null) {
            for (ReactorLogger logger : loggers) {
                if (logger != null) {
                    cleaned.add(logger);
                }
            }
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("CompositeReactorLogger requires at least one delegate logger.");
        }
        this.delegates = Collections.unmodifiableList(cleaned);
    }

    /** Returns the delegate loggers in dispatch order. */
    public List<ReactorLogger> getDelegates() {
        return delegates;
    }

    @Override
    public void logState(double power, double temperature, double coolantFlowRate,
                         double controlRodPosition, double reactivity) {
        for (ReactorLogger logger : delegates) {
            try {
                logger.logState(power, temperature, coolantFlowRate, controlRodPosition, reactivity);
            } catch (Exception e) {
                System.err.println("[CompositeReactorLogger] Delegate failed in logState: " + e.getMessage());
            }
        }
    }

    @Override
    public void logWarning(String message) {
        for (ReactorLogger logger : delegates) {
            try {
                logger.logWarning(message);
            } catch (Exception e) {
                System.err.println("[CompositeReactorLogger] Delegate failed in logWarning: " + e.getMessage());
            }
        }
    }

    @Override
    public void logDecision(String subsystem, String decision) {
        for (ReactorLogger logger : delegates) {
            try {
                logger.logDecision(subsystem, decision);
            } catch (Exception e) {
                System.err.println("[CompositeReactorLogger] Delegate failed in logDecision: " + e.getMessage());
            }
        }
    }
}
