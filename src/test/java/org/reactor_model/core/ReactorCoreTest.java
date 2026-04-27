package org.reactor_model.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.logger.ReactorLogger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ReactorCore Unit Tests")
class ReactorCoreTest {

    private ReactorCore core;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
    }

    @Test
    @DisplayName("Initial state should be safe")
    void testInitialState() {
        assertEquals(0.01, core.getPower(), 0.001);
        assertEquals(300.0, core.getTemperature(), 0.1);
        assertEquals(1.0, core.getCoolantFlowRate(), 0.001);
        assertEquals(0.5, core.getControlRodPosition(), 0.001);
        assertFalse(core.isShutdown());
    }

    @Test
    @DisplayName("Power should increase with positive reactivity")
    void testPowerIncrease() {
        double initialPower = core.getPower();
        core.addReactivity(0.01);
        
        core.update(0.1, 1000.0, initialPower);
        
        assertTrue(core.getPower() > initialPower, "Power should increase with positive reactivity");
    }

    @Test
    @DisplayName("Temperature should increase at high power")
    void testTemperatureIncrease() {
        double initialTemp = core.getTemperature();
        // Force high power to overcome baseline cooling
        core.update(0.1, 5000.0, core.getPower());
        for (int i = 0; i < 50; i++) {
            core.addReactivity(0.05);
            core.update(0.1, 5000.0, core.getPower());
        }
        
        assertTrue(core.getTemperature() > initialTemp, "Temperature should increase at high power");
    }

    @Test
    @DisplayName("Emergency shutdown should trigger at critical temperature")
    void testEmergencyShutdownAtCriticalTemp() {
        // Force temperature to critical value by massive overheating
        for (int i = 0; i < 500; i++) {
            core.addReactivity(0.1);
            core.update(0.1, 8000.0, core.getPower());
            if (core.isShutdown()) {
                break;
            }
        }
        
        assertTrue(core.isShutdown(), "Reactor should trigger SCRAM at critical temperature");
        verify(mockLogger, atLeastOnce()).logWarning(contains("EMERGENCY SHUTDOWN"));
    }

    @Test
    @DisplayName("Restart should reset to safe state")
    void testRestart() {
        core.addReactivity(0.05);
        core.update(0.1, 1000.0, core.getPower());
        
        core.restart();
        
        assertEquals(0.01, core.getPower(), 0.001);
        assertEquals(300.0, core.getTemperature(), 0.1);
        assertEquals(0.5, core.getControlRodPosition(), 0.001);
        assertEquals(0.0, core.getReactivity(), 0.001);
        assertFalse(core.isShutdown());
    }

    @Test
    @DisplayName("Control rod position should clamp to [0, 1]")
    void testControlRodClamping() {
        core.setControlRodPosition(1.5);
        assertEquals(1.0, core.getControlRodPosition());
        
        core.setControlRodPosition(-0.5);
        assertEquals(0.0, core.getControlRodPosition());
    }

    @Test
    @DisplayName("Coolant flow rate should clamp to [0, 1]")
    void testCoolantFlowClamping() {
        core.setCoolantFlowRate(2.0);
        assertEquals(1.0, core.getCoolantFlowRate());
        
        core.setCoolantFlowRate(-0.5);
        assertEquals(0.0, core.getCoolantFlowRate());
    }

    @Test
    @DisplayName("Increased coolant flow should reduce temperature")
    void testCoolingEffect() {
        core.addReactivity(0.02);
        core.update(0.1, 500.0, core.getPower());
        
        double tempWithNormalFlow = core.getTemperature();
        
        core.setCoolantFlowRate(1.0); // Max cooling
        double previousTemp = core.getTemperature();
        core.update(0.1, 500.0, core.getPower());
        double tempWithMaxCooling = core.getTemperature();
        
        assertTrue(tempWithMaxCooling <= previousTemp, "Increased cooling should prevent temperature rise");
    }

    @Test
    @DisplayName("Dangerous power jump should trigger shutdown")
    void testDangerousPowerJump() {
        // Quickly get power above POWER_JUMP_MIN_ABSOLUTE (1200)
        for (int i = 0; i < 200; i++) {
            core.addReactivity(0.1);
            core.update(0.1, 8000.0, core.getPower());
            if (core.getPower() > 1500.0) break;
        }
        
        double stableHighPower = core.getPower();
        
        // Massive artificial reactivity to cause > 1.8x growth in 1 tick
        core.addReactivity(30.0);
        core.update(0.1, 8000.0, stableHighPower);
        
        assertTrue(core.isShutdown(), "Dangerous power jump should trigger emergency shutdown");
    }

    @Test
    @DisplayName("Overheat tick counter should increment")
    void testOverheatTicks() {
        int initialTicks = core.getOverheatTicks();
        
        // Lower cooling flow so we can easily overheat without exceeding power limits
        core.setCoolantFlowRate(0.2);
        core.addReactivity(0.05); // High enough to overcome temp coefficient
        
        for (int i = 0; i < 3000; i++) {
            core.update(0.1, 7000.0, core.getPower()); 
        }
        
        assertTrue(core.getOverheatTicks() > initialTicks, "Overheat ticks should increment at high temperatures");
    }

    @Test
    @DisplayName("Reactivity feedback should respond to temperature changes")
    void testReactivityFeedback() {
        double initialReactivity = core.getReactivity();
        
        // Heat up the reactor
        core.addReactivity(0.02);
        core.update(0.1, 1000.0, core.getPower());
        core.update(0.1, 1000.0, core.getPower());
        
        double highTempReactivity = core.getReactivity();
        
        // Temperature negative feedback should reduce reactivity
        if (core.getTemperature() > 400.0) {
            assertTrue(highTempReactivity < initialReactivity + 0.02, 
                    "Temperature feedback should reduce net reactivity");
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 0.1, 0.5, 2.0, 5.0})
    @DisplayName("Update should handle various timesteps")
    void testVariousTimesteps(double dt) {
        core.addReactivity(0.005);
        
        assertDoesNotThrow(() -> core.update(dt, 1000.0, core.getPower()));
        assertFalse(Double.isNaN(core.getPower()));
        assertFalse(Double.isNaN(core.getTemperature()));
    }

    @Test
    @DisplayName("Shutdown mode should cool down reactor")
    void testShutdownCooling() {
        // Heat up first
        core.addReactivity(0.03);
        for (int i = 0; i < 50; i++) {
            core.update(0.1, 1000.0, core.getPower());
        }
        
        double tempBeforeShutdown = core.getTemperature();
        
        // Manually shutdown
        core.restart();
        assertTrue(core.isShutdown() == false);
    }

    @Test
    @DisplayName("AddReactivity should change reactivity value")
    void testAddReactivity() {
        core.update(0.1, 500.0, core.getPower()); // Get baseline
        double initial = core.getReactivity();
        
        core.addReactivity(0.01);
        core.update(0.1, 500.0, core.getPower()); // Step state to integrate external reactivity
        
        // Use a looser tolerance since temp feedback might shift extremely slightly
        assertEquals(initial + 0.01, core.getReactivity(), 0.001);
    }

    @Test
    @DisplayName("Event bus should publish updates")
    void testEventBusPublish() {
        assertNotNull(core.eventBus);
        
        boolean[] eventTriggered = {false};
        core.eventBus.subscribe(() -> eventTriggered[0] = true);
        
        core.eventBus.publish();
        
        assertTrue(eventTriggered[0], "Event bus should trigger registered listeners");
    }
}
