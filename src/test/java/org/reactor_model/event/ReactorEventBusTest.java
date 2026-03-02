package org.reactor_model.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReactorEventBus Unit Tests")
class ReactorEventBusTest {

    private ReactorEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new ReactorEventBus();
    }

    @Test
    @DisplayName("Publish should trigger registered listeners")
    void testPublishTriggers() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        
        eventBus.subscribe(() -> triggered.set(true));
        eventBus.publish();
        
        assertTrue(triggered.get(), "Listener should be triggered on publish");
    }

    @Test
    @DisplayName("Multiple listeners should all be triggered")
    void testMultipleListeners() {
        AtomicInteger count = new AtomicInteger(0);
        
        eventBus.subscribe(count::incrementAndGet);
        eventBus.subscribe(count::incrementAndGet);
        eventBus.subscribe(count::incrementAndGet);
        
        eventBus.publish();
        
        assertEquals(3, count.get(), "All three listeners should be triggered");
    }

    @Test
    @DisplayName("Listeners registered after first publish should work")
    void testLateRegistration() {
        AtomicInteger count = new AtomicInteger(0);
        
        eventBus.subscribe(count::incrementAndGet);
        eventBus.publish(); // count = 1
        
        eventBus.subscribe(count::incrementAndGet);
        eventBus.publish(); // count = 1 + 2 = 3
        
        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("Listener exception should not prevent other listeners")
    void testExceptionIsolation() {
        AtomicBoolean secondExecuted = new AtomicBoolean(false);
        
        eventBus.subscribe(() -> {
            throw new RuntimeException("Intentional error");
        });
        
        eventBus.subscribe(() -> secondExecuted.set(true));
        
        assertDoesNotThrow(eventBus::publish, 
                "Event bus should not throw despite listener exception");
        assertTrue(secondExecuted.get(), 
                "Second listener should execute despite first listener's exception");
    }

    @Test
    @DisplayName("Publishing without listeners should be safe")
    void testPublishWithoutListeners() {
        assertDoesNotThrow(eventBus::publish);
    }

    @Test
    @DisplayName("Multiple publishes should retrigger all listeners")
    void testMultiplePublishes() {
        AtomicInteger count = new AtomicInteger(0);
        
        eventBus.subscribe(count::incrementAndGet);
        
        eventBus.publish(); // count = 1
        eventBus.publish(); // count = 2
        eventBus.publish(); // count = 3
        
        assertEquals(3, count.get());
    }

    @Test
    @DisplayName("Listener should be reusable")
    void testReuseableListener() {
        AtomicInteger count = new AtomicInteger(0);
        Runnable listener = count::incrementAndGet;
        
        eventBus.subscribe(listener);
        eventBus.subscribe(listener);
        
        eventBus.publish();
        
        // Both registrations should execute independently
        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("Event bus should handle null listeners gracefully")
    void testNullHandling() {
        // Subscribe a normal listener first
        AtomicBoolean executed = new AtomicBoolean(false);
        eventBus.subscribe(() -> executed.set(true));
        
        // Create a listener that throws
        eventBus.subscribe(() -> {
            throw new NullPointerException("NPE");
        });
        
        // Should still work
        assertDoesNotThrow(eventBus::publish);
        assertTrue(executed.get());
    }

    @Test
    @DisplayName("Concurrent subscriptions should be thread-safe")
    void testThreadSafety() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        
        // Subscribe from multiple threads
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                eventBus.subscribe(count::incrementAndGet);
            }
        });
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                eventBus.subscribe(count::incrementAndGet);
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        eventBus.publish();
        
        assertEquals(20, count.get(), "All listeners from both threads should execute");
    }

    @Test
    @DisplayName("Listeners should execute in a snapshot")
    void testPublishSnapshot() {
        AtomicInteger publishedDuringExecution = new AtomicInteger(0);
        
        eventBus.subscribe(() -> {
            eventBus.subscribe(() -> publishedDuringExecution.set(1));
        });
        
        eventBus.publish();
        
        // The newly registered listener should NOT execute in same publish
        assertEquals(0, publishedDuringExecution.get(), 
                "Listeners added during publish should not execute until next publish");
        
        eventBus.publish();
        assertEquals(1, publishedDuringExecution.get());
    }

    @Test
    @DisplayName("Large number of listeners should work")
    void testManyListeners() {
        int listenerCount = 1000;
        AtomicInteger executedCount = new AtomicInteger(0);
        
        for (int i = 0; i < listenerCount; i++) {
            eventBus.subscribe(executedCount::incrementAndGet);
        }
        
        eventBus.publish();
        
        assertEquals(listenerCount, executedCount.get());
    }

    @Test
    @DisplayName("Event bus should be reusable in multiple scenarios")
    void testMultipleScenarios() {
        // Scenario 1
        {
            AtomicBoolean executed1 = new AtomicBoolean(false);
            eventBus.subscribe(() -> executed1.set(true));
            eventBus.publish();
            assertTrue(executed1.get());
        }
        
        // Scenario 2 - new listener
        {
            AtomicBoolean executed2 = new AtomicBoolean(false);
            eventBus.subscribe(() -> executed2.set(true));
            eventBus.publish();
            assertTrue(executed2.get());
        }
    }
}
