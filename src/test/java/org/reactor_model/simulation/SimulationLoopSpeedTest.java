package org.reactor_model.simulation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.regulation.AutoRegulator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the Phase 3 simulation speed control:
 * clamping in {@link SimulationLoop#setSpeedMultiplier} and the
 * sleep-duration calculation in {@link SimulationLoop#computeSleepMs}.
 */
@DisplayName("SimulationLoop Speed Control Tests")
class SimulationLoopSpeedTest {

    private SimulationLoop loop;

    @BeforeEach
    void setUp() {
        ReactorCore core = mock(ReactorCore.class);
        AutoRegulator regulator = mock(AutoRegulator.class);
        PowerDemandSimulator demand = mock(PowerDemandSimulator.class);
        CoolingSystem cooling = mock(CoolingSystem.class);
        when(regulator.getTargetPower()).thenReturn(1000.0);
        when(core.getOverheatTicks()).thenReturn(0);

        loop = new SimulationLoop(core, regulator, demand, cooling);
    }

    @AfterEach
    void tearDown() {
        loop.stop();
    }

    @Test
    @DisplayName("Default speed multiplier should be 1.0x")
    void defaultSpeedIsOne() {
        assertEquals(1.0, loop.getSpeedMultiplier(), 1e-9);
    }

    @Test
    @DisplayName("Speed multiplier should be clamped to [1.0, 10.0]")
    void speedMultiplierIsClamped() {
        loop.setSpeedMultiplier(20.0);
        assertEquals(10.0, loop.getSpeedMultiplier(), 1e-9,
                "Values above 10.0 should clamp to 10.0");

        loop.setSpeedMultiplier(0.5);
        assertEquals(1.0, loop.getSpeedMultiplier(), 1e-9,
                "Values below 1.0 should clamp to 1.0");

        loop.setSpeedMultiplier(-3.0);
        assertEquals(1.0, loop.getSpeedMultiplier(), 1e-9,
                "Negative values should clamp to 1.0");

        loop.setSpeedMultiplier(5.0);
        assertEquals(5.0, loop.getSpeedMultiplier(), 1e-9,
                "In-range values should pass through unchanged");
    }

    @Test
    @DisplayName("Sleep duration should shorten proportionally with speed")
    void sleepShortensWithSpeed() {
        double baseMs = loop.getDt() * 1000.0;

        loop.setSpeedMultiplier(1.0);
        assertEquals(Math.max((long) baseMs, 10L), loop.computeSleepMs(),
                "1x speed should sleep the full timestep");

        loop.setSpeedMultiplier(2.0);
        assertEquals(Math.max((long) (baseMs / 2.0), 10L), loop.computeSleepMs(),
                "2x speed should halve the sleep");

        loop.setSpeedMultiplier(5.0);
        assertEquals(Math.max((long) (baseMs / 5.0), 10L), loop.computeSleepMs(),
                "5x speed should cut the sleep to a fifth");
    }

    @Test
    @DisplayName("Sleep duration should never drop below 10ms")
    void sleepNeverBelowFloor() {
        loop.setSpeedMultiplier(10.0);
        assertTrue(loop.computeSleepMs() >= 10L,
                "Even at 10x speed the sleep floor of 10ms must hold");
        assertEquals(10L, loop.computeSleepMs(),
                "With the default 0.1s timestep, 10x speed hits the 10ms floor exactly");
    }
}
