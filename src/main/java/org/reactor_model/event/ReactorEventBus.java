package org.reactor_model.event;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight event bus used to notify subsystems (e.g., AutoRegulator)
 * whenever the reactor core updates its state.
 */
public class ReactorEventBus {

    private final List<Runnable> listeners = new ArrayList<>();

    /**
     * Registers a listener that will be invoked on each publish().
     */
    public synchronized void subscribe(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously registered listener.
     * Returns true if the listener was found and removed.
     */
    public synchronized boolean unsubscribe(Runnable listener) {
        return listeners.remove(listener);
    }

    /**
     * Returns the number of registered listeners.
     * Useful for testing and debugging.
     */
    public synchronized int getListenerCount() {
        return listeners.size();
    }

    /**
     * Invokes all registered listeners.
     * One failing listener will not interrupt others.
     */
    public void publish() {
        List<Runnable> snapshot;

        // Copy under lock to avoid concurrent modification
        synchronized (this) {
            snapshot = new ArrayList<>(listeners);
        }

        for (Runnable listener : snapshot) {
            try {
                listener.run();
            } catch (Exception e) {
                // Prevent listener failure from breaking the simulation loop
                System.err.println("[EventBus] Listener error: " + e.getMessage());
            }
        }
    }
}
