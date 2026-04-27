package org.reactor_model.simulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.regulation.AutoRegulator;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@DisplayName("SimulationLoop Unit Tests")
class SimulationLoopTest {

    private SimulationLoop loop;
    private ReactorCore core;
    private AutoRegulator regulator;
    private PowerDemandSimulator demandSimulator;
    private CoolingSystem coolingSystem;

    @BeforeEach
    void setUp() {
        core = mock(ReactorCore.class);
        regulator = mock(AutoRegulator.class);
        demandSimulator = mock(PowerDemandSimulator.class);
        coolingSystem = mock(CoolingSystem.class);

        when(regulator.getTargetPower()).thenReturn(1000.0);
        when(core.getOverheatTicks()).thenReturn(0);

        loop = new SimulationLoop(core, regulator, demandSimulator, coolingSystem);
    }

    @AfterEach
    void tearDown() {
        loop.stop();
    }

    @Test
    @DisplayName("Loop should call all subsystems while running")
    @Timeout(5)
    void startInvokesSubsystems() throws Exception {
        loop.start();

        waitUntil(() -> mockingDetails(core).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("update")), Duration.ofSeconds(2));

        verify(demandSimulator, atLeastOnce()).update();
        verify(core, atLeastOnce()).update(0.1);
        verify(coolingSystem, atLeastOnce()).update(1000.0);
    }

    @Test
    @DisplayName("Stopping the loop should halt further updates")
    @Timeout(5)
    void stopHaltsFurtherUpdates() throws Exception {
        loop.start();
        waitUntil(() -> mockingDetails(core).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .count() >= 2, Duration.ofSeconds(2));

        long beforeStop = mockingDetails(core).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .count();

        loop.stop();
        Thread.sleep(250);

        long afterStop = mockingDetails(core).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .count();

        assertEquals(beforeStop, afterStop, "Loop should stop scheduling updates after stop()");
    }

    @Test
    @DisplayName("Repeated start should reuse the existing loop thread")
    @Timeout(5)
    void repeatedStartIsIdempotent() throws Exception {
        loop.start();
        Thread firstThread = extractLoopThread();

        waitUntil(() -> firstThread != null && firstThread.isAlive(), Duration.ofSeconds(2));

        loop.start();
        Thread secondThread = extractLoopThread();

        assertSame(firstThread, secondThread, "Second start() should not replace the running loop thread");
    }

    @Test
    @DisplayName("Overheat protection should reduce target power and reset the counter")
    @Timeout(5)
    void overheatProtectionReducesTargetPower() throws Exception {
        when(regulator.getTargetPower()).thenReturn(1000.0);
        when(core.getOverheatTicks()).thenReturn(ReactorCore.OVERHEAT_MAX_TICKS + 1, 0, 0, 0);

        loop.start();

        waitUntil(() -> mockingDetails(regulator).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("setTargetPower")), Duration.ofSeconds(2));

        verify(regulator, atLeastOnce()).setTargetPower(800.0);
        verify(core, atLeastOnce()).resetOverheatTicks();
    }

    @Test
    @DisplayName("Loop should emit periodic state logging")
    @Timeout(5)
    void loopLogsPeriodicState() throws Exception {
        loop.start();

        waitUntil(() -> mockingDetails(core).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("logCurrentState")), Duration.ofSeconds(3));

        verify(core, atLeastOnce()).logCurrentState();
    }

    @Test
    @DisplayName("Stopping an idle loop should be safe")
    void stopIdleLoopIsSafe() {
        assertDoesNotThrow(() -> loop.stop());
    }

    private void waitUntil(BooleanSupplier condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Condition was not met within " + timeout);
    }

    private Thread extractLoopThread() throws Exception {
        Field field = SimulationLoop.class.getDeclaredField("loopThread");
        field.setAccessible(true);
        return (Thread) field.get(loop);
    }
}
