package org.reactor_model.event;

import java.util.ArrayList;
import java.util.List;

public class ReactorEventBus {
    private final List<Runnable> listeners = new ArrayList<>();

    public void subscribe(Runnable listener) {
        listeners.add(listener);
    }

    public void publish() {
        listeners.forEach(Runnable::run);
    }
}
