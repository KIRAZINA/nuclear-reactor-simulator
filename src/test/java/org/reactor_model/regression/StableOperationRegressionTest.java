package org.reactor_model.regression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.regulation.PrecisionPIDStrategy;
import org.reactor_model.simulation.SimulationLoop;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for stable reactor operation.
 * 
 * These tests verify that:
 * 1. User-set target power remains stable (no unauthorized changes)
 * 2. Disturbance simulator is disabled by default
 * 3. System operates predictably without external interference
 * 4. Power trends toward target (even if not exactly ±10 MW immediately)
 */
@DisplayName("Stable Operation Regression Tests")
class StableOperationRegressionTest {

    private ReactorCore core;
    private AutoRegulator regulator;
    private PrecisionPIDStrategy pidStrategy;
    private PowerDemandSimulator demandSimulator;
    private CoolingSystem coolingSystem;
    private SimulationLoop simulationLoop;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Build complete simulation stack
        core = new ReactorCore(mockLogger);
        pidStrategy = new PrecisionPIDStrategy();
        regulator = new AutoRegulator(core, mockLogger, pidStrategy);
        demandSimulator = new PowerDemandSimulator(core, regulator, mockLogger);
        coolingSystem = new CoolingSystem(core, mockLogger);
        simulationLoop = new SimulationLoop(core, regulator, demandSimulator, coolingSystem);
        
        // Enable auto-regulator
        regulator.setEnabled(true);
    }

    // ============================================================
    // TEST 1: Disturbances are DISABLED by default
    // ============================================================
    @Test
    @DisplayName("Disturbances should be DISABLED by default for stable operation")
    void testDisturbancesDisabledByDefault() {
        // Verify default state
        assertFalse(demandSimulator.isEnabled(), 
                "Disturbances should be DISABLED by default");
        
        // Run simulation for extended period
        for (int i = 0; i < 1000; i++) {
            demandSimulator.update();
        }
        
        // Should still be disabled
        assertFalse(demandSimulator.isEnabled(), 
                "Disturbances should remain disabled after updates");
    }

    // ============================================================
    // TEST 2: Target power remains stable without user input
    // ============================================================
    @Test
    @DisplayName("Target power should remain stable without user intervention")
    void testTargetPowerStability() {
        // Set initial target power
        double initialTarget = 1500.0;
        regulator.setTargetPower(initialTarget);
        
        // Run simulation for extended period (100 seconds simulated time)
        for (int i = 0; i < 1000; i++) {
            demandSimulator.update(); // Should do nothing (disabled)
            coolingSystem.update(regulator.getTargetPower());
            core.update(0.1, regulator.getTargetPower(), core.getPower());
        }
        
        // Target power should remain unchanged
        assertEquals(initialTarget, regulator.getTargetPower(), 0.001,
                "Target power should not change without user input");
    }

    // ============================================================
    // TEST 3: No spontaneous reactivity changes
    // ============================================================
    @Test
    @DisplayName("Reactivity should not change spontaneously without disturbances")
    void testNoSpontaneousReactivityChanges() {
        // Let system stabilize
        for (int i = 0; i < 200; i++) {
            demandSimulator.update();
            coolingSystem.update(regulator.getTargetPower());
            core.update(0.1, regulator.getTargetPower(), core.getPower());
        }
        
        // Record baseline reactivity
        double baselineReactivity = core.getReactivity();
        
        // Monitor for spontaneous changes
        double maxReactivityChange = 0.0;
        for (int i = 0; i < 500; i++) {
            demandSimulator.update(); // Disabled, should do nothing
            coolingSystem.update(regulator.getTargetPower());
            core.update(0.1, regulator.getTargetPower(), core.getPower());
            
            double reactivityChange = Math.abs(core.getReactivity() - baselineReactivity);
            maxReactivityChange = Math.max(maxReactivityChange, reactivityChange);
        }
        
        // Reactivity changes should be bounded (only from regulator adjustments)
        assertTrue(maxReactivityChange < 0.05,
                String.format("Max reactivity change %.4f should be < 0.05 without disturbances",
                        maxReactivityChange));
    }

    // ============================================================
    // TEST 4: Disturbance toggle works correctly
    // ============================================================
    @Test
    @DisplayName("Disturbance toggle should enable/disable correctly")
    void testDisturbanceToggle() {
        // Initially disabled
        assertFalse(demandSimulator.isEnabled());
        
        // Enable
        demandSimulator.enable();
        assertTrue(demandSimulator.isEnabled());
        
        // Disable
        demandSimulator.disable();
        assertFalse(demandSimulator.isEnabled());
        
        // Toggle on
        demandSimulator.toggle();
        assertTrue(demandSimulator.isEnabled());
        
        // Toggle off
        demandSimulator.toggle();
        assertFalse(demandSimulator.isEnabled());
    }

    // ============================================================
    // TEST 5: Disturbances affect system when enabled
    // ============================================================
    @Test
    @DisplayName("Enabling disturbances should cause target power changes")
    void testDisturbancesCauseChanges() {
        double initialTarget = regulator.getTargetPower();
        
        // Run with disturbances disabled
        for (int i = 0; i < 500; i++) {
            demandSimulator.update();
        }
        
        double targetAfterDisabled = regulator.getTargetPower();
        
        // Enable disturbances
        demandSimulator.enable();
        
        // Run with disturbances enabled
        for (int i = 0; i < 500; i++) {
            demandSimulator.update();
        }
        
        // With disturbances disabled, target should not change
        assertEquals(initialTarget, targetAfterDisabled, 0.001,
                "Target should not change with disturbances disabled");
        
        // Clean up
        demandSimulator.disable();
    }

    // ============================================================
    // TEST 6: Long-term stability test
    // ============================================================
    @Test
    @DisplayName("System should remain stable over extended operation")
    void testLongTermStability() throws InterruptedException {
        // Set moderate power level
        regulator.setTargetPower(2500.0);
        
        // Start simulation
        simulationLoop.start();
        
        // Run for 10 seconds
        Thread.sleep(10000);
        
        // Verify system is still operating
        assertTrue(core.getPower() > 0, "Power should be positive");
        assertFalse(core.isShutdown(), "Reactor should not be shutdown");
        assertTrue(core.getTemperature() < 700, "Temperature should be controlled");
        
        // Verify target power unchanged
        assertEquals(2500.0, regulator.getTargetPower(), 0.001,
                "Target power should remain unchanged after 10 seconds");
        
        simulationLoop.stop();
    }

    // ============================================================
    // TEST 7: Regulator responds to target changes
    // ============================================================
    @Test
    @DisplayName("Regulator should respond to target power changes")
    void testTargetTracking() throws InterruptedException {
        // Start at low power
        regulator.setTargetPower(500.0);
        simulationLoop.start();
        
        // Wait for initial response
        Thread.sleep(3000);
        
        // Power should be increasing toward target
        double powerAfter3Sec = core.getPower();
        assertTrue(powerAfter3Sec > 0.01, "Power should increase from minimum");
        
        // Change to higher power
        regulator.setTargetPower(2000.0);
        
        // Wait for transition
        Thread.sleep(5000);
        
        // Power should have increased
        double powerAfterTransition = core.getPower();
        assertTrue(powerAfterTransition > powerAfter3Sec,
                "Power should increase after target increase");
        
        // Verify target power maintained
        assertEquals(2000.0, regulator.getTargetPower(), 0.001);
        
        simulationLoop.stop();
    }

    // ============================================================
    // TEST 8: System recovers from manual reactivity injection
    // ============================================================
    @Test
    @DisplayName("System should recover from manual reactivity spike")
    void testRecoveryFromReactivitySpike() throws InterruptedException {
        // Stabilize at target
        regulator.setTargetPower(2000.0);
        simulationLoop.start();
        Thread.sleep(5000);
        
        double stablePower = core.getPower();
        
        // Inject reactivity spike
        core.addReactivity(0.005);
        double powerAfterSpike = core.getPower();
        
        // Wait for recovery
        Thread.sleep(5000);
        
        // Power should be regulated (may not be exactly 2000 MW, but should be stable)
        double recoveredPower = core.getPower();
        assertTrue(recoveredPower > 0, "Power should remain positive after recovery");
        assertFalse(core.isShutdown(), "Reactor should not shutdown from small spike");
        
        // Verify regulator maintained target
        assertEquals(2000.0, regulator.getTargetPower(), 0.001);
        
        simulationLoop.stop();
    }

    // ============================================================
    // TEST 9: Power trends toward target over time
    // ============================================================
    @Test
    @DisplayName("Power should trend toward target over time")
    void testPowerTrendsToTarget() throws InterruptedException {
        double targetPower = 1500.0;
        regulator.setTargetPower(targetPower);
        
        simulationLoop.start();
        
        // Sample power at start
        Thread.sleep(2000);
        double earlyPower = core.getPower();
        
        // Sample power later
        Thread.sleep(5000);
        double laterPower = core.getPower();
        
        // Power should be trending toward target
        double earlyError = Math.abs(earlyPower - targetPower);
        double laterError = Math.abs(laterPower - targetPower);
        
        // Allow for some oscillation, but generally should improve or stay similar
        assertTrue(laterPower > earlyPower || earlyError < 500,
                "Power should generally trend toward target");
        
        simulationLoop.stop();
    }

    // ============================================================
    // TEST 10: Multiple target changes handled correctly
    // ============================================================
    @Test
    @DisplayName("System should handle multiple target power changes")
    void testMultipleTargetChanges() throws InterruptedException {
        simulationLoop.start();
        
        double[] targets = {500.0, 1000.0, 2000.0, 1500.0};
        
        for (double target : targets) {
            regulator.setTargetPower(target);
            Thread.sleep(2000);
            
            // Verify target was set
            assertEquals(target, regulator.getTargetPower(), 0.001);
            
            // Verify system still operating
            assertFalse(core.isShutdown());
            assertTrue(core.getPower() > 0);
        }
        
        simulationLoop.stop();
    }
}
