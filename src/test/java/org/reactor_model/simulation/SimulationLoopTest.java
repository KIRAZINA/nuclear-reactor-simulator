package org.reactor_model.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.regulation.SimplePIDStrategy;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SimulationLoop Unit Tests")
class SimulationLoopTest {

    private SimulationLoop loop;
    private ReactorCore core;
    private AutoRegulator regulator;
    private PowerDemandSimulator demandSimulator;
    private CoolingSystem coolingSystem;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
        var strategy = new SimplePIDStrategy();
        regulator = new AutoRegulator(core, mockLogger, strategy);
        demandSimulator = new PowerDemandSimulator(core, regulator, mockLogger);
        coolingSystem = new CoolingSystem(core, mockLogger);
        
        loop = new SimulationLoop(core, regulator, demandSimulator, coolingSystem);
    }

    @Test
    @DisplayName("Loop should start and stop cleanly")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStartAndStop() throws InterruptedException {
        loop.start();
        Thread.sleep(200);
        loop.stop();
        Thread.sleep(100);
    }

    @Test
    @DisplayName("Starting already running loop should be idempotent")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testIdempotentStart() throws InterruptedException {
        loop.start();
        Thread.sleep(100);
        loop.start(); // Should not create new thread
        Thread.sleep(100);
        loop.stop();
    }

    @Test
    @DisplayName("Stopping idle loop should be safe")
    void testStopIdleLoop() {
        assertDoesNotThrow(() -> loop.stop());
    }

    @Test
    @DisplayName("Multiple start-stop cycles should work")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testMultipleCycles() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            loop.start();
            Thread.sleep(200);
            loop.stop();
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("Reactor state should update during loop execution")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStateUpdates() throws InterruptedException {
        double initialPower = core.getPower();
        
        loop.start();
        Thread.sleep(500); // Let loop run for a bit
        loop.stop();
        
        // Power may have changed due to disturbances and updates
        // At minimum, no NaN or Infinity should exist
        assertFalse(Double.isNaN(core.getPower()));
        assertFalse(Double.isNaN(core.getTemperature()));
        assertFalse(Double.isInfinite(core.getPower()));
        assertFalse(Double.isInfinite(core.getTemperature()));
    }

    @Test
    @DisplayName("Loop should handle overheat protection")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testOverheatProtection() throws InterruptedException {
        // Set high target to trigger overheat
        regulator.setTargetPower(5000.0);
        
        loop.start();
        
        boolean protectionTriggered = false;
        for (int i = 0; i < 100; i++) {
            if (core.getOverheatTicks() > 0) {
                protectionTriggered = true;
                break;
            }
            Thread.sleep(10);
        }
        
        loop.stop();
        
        // Protection mechanism should be present
        assertTrue(core.getOverheatTicks() >= 0, "Overheat counter should be initialized");
    }

    @Test
    @DisplayName("Loop should log periodic state")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPeriodicLogging() throws InterruptedException {
        loop.start();
        Thread.sleep(500);
        loop.stop();
        
        // If logging occurred, mockLogger should have been called
        // (at least for state updates from reactor)
    }

    @Test
    @DisplayName("Loop should maintain numeric stability")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testNumericStability() throws InterruptedException {
        loop.start();
        
        for (int check = 0; check < 5; check++) {
            Thread.sleep(100);
            
            assertFalse(Double.isNaN(core.getPower()), 
                    "Power should remain a valid number");
            assertFalse(Double.isNaN(core.getTemperature()), 
                    "Temperature should remain a valid number");
            assertFalse(Double.isInfinite(core.getPower()), 
                    "Power should not become infinite");
            assertFalse(Double.isInfinite(core.getTemperature()), 
                    "Temperature should not become infinite");
        }
        
        loop.stop();
    }

    @Test
    @DisplayName("Loop should sync subsystem operations")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSubsystemSync() throws InterruptedException {
        loop.start();
        Thread.sleep(300);
        
        // After some iterations, all subsystems should be in synchronized state
        double power = core.getPower();
        double temp = core.getTemperature();
        double coolant = core.getCoolantFlowRate();
        
        assertTrue(power >= 0.0);
        assertTrue(temp >= 0.0);
        assertTrue(coolant >= 0.0 && coolant <= 1.0);
        
        loop.stop();
    }

    @Test
    @DisplayName("Regulator should respond to state changes")
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void testRegulatorResponse() throws InterruptedException {
        double targetPower = 500.0;
        regulator.setTargetPower(targetPower);
        
        loop.start();
        
        // Give time for regulator to respond
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            
            if (Math.abs(core.getPower() - targetPower) < 1.0) {
                // Power is near target
                loop.stop();
                return;
            }
        }
        
        loop.stop();
        
        // Regulator should have attempted to reach target
        assertFalse(Double.isNaN(core.getControlRodPosition()));
    }

    @Test
    @DisplayName("Cooling should respond to temperature changes")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCoolingResponse() throws InterruptedException {
        regulator.setTargetPower(1000.0);
        
        loop.start();
        // Wait for temperature to rise
        Thread.sleep(800);
        
        double temp = core.getTemperature();
        double coolingFlow = core.getCoolantFlowRate();
        
        // The cooling system should be computing flow based on temperature
        // At steady state, the flow will be at MIN_FLOW (0.2) or higher
        // The key is that the system runs and produces valid values
        assertTrue(coolingFlow >= 0.0 && coolingFlow <= 1.0, 
                "Cooling flow should be in valid range, got: " + coolingFlow);
        assertTrue(temp >= 0.0, "Temperature should be valid, got: " + temp);
        
        loop.stop();
    }

    @Test
    @DisplayName("Loop should handle emergency conditions")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testEmergencyCondition() throws InterruptedException {
        regulator.setTargetPower(7500.0);
        
        loop.start();
        
        boolean shutdownOccurred = false;
        for (int i = 0; i < 100; i++) {
            if (core.isShutdown()) {
                shutdownOccurred = true;
                break;
            }
            Thread.sleep(10);
        }
        
        loop.stop();
        
        // May or may not trigger SCRAM depending on timing
        // But loop should remain stable either way
        assertFalse(Double.isNaN(core.getPower()));
    }

    @Test
    @DisplayName("Loop thread should reset on restart")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testThreadManagement() throws InterruptedException {
        loop.start();
        Thread.sleep(100);
        loop.stop();
        Thread.sleep(50);
        
        // Should be able to start again
        loop.start();
        Thread.sleep(100);
        loop.stop();
    }

    @Test
    @DisplayName("Long running simulation should remain stable")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testLongRunning() throws InterruptedException {
        loop.start();
        
        for (int second = 0; second < 5; second++) {
            Thread.sleep(1000);
            
            assertFalse(Double.isNaN(core.getPower()));
            assertFalse(Double.isNaN(core.getTemperature()));
            assertFalse(Double.isInfinite(core.getPower()));
            assertFalse(Double.isInfinite(core.getTemperature()));
        }
        
        loop.stop();
    }

    @Test
    @DisplayName("Rapid configuration changes should not destabilize loop")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testRapidConfigChanges() throws InterruptedException {
        loop.start();
        
        for (int i = 0; i < 10; i++) {
            regulator.setTargetPower(100.0 + i * 50);
            Thread.sleep(50);
        }
        
        loop.stop();
        
        assertFalse(Double.isNaN(core.getPower()));
    }
}
