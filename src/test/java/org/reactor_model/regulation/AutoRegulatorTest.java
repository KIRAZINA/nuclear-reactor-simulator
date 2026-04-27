package org.reactor_model.regulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AutoRegulator Unit Tests")
class AutoRegulatorTest {

    private AutoRegulator regulator;
    private ReactorCore core;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
        regulator = new AutoRegulator(core, mockLogger);
    }

    @Test
    @DisplayName("Initial state should have default target power")
    void testInitialState() {
        assertEquals(100.0, regulator.getTargetPower());
        assertTrue(regulator.isEnabled());
    }

    @Test
    @DisplayName("Target power should be settable")
    void testSetTargetPower() {
        regulator.setTargetPower(500.0);
        assertEquals(500.0, regulator.getTargetPower());
        
        regulator.setTargetPower(2000.0);
        assertEquals(2000.0, regulator.getTargetPower());
    }

    @Test
    @DisplayName("Regulator should be toggleable")
    void testToggleEnabled() {
        assertTrue(regulator.isEnabled());
        
        regulator.setEnabled(false);
        assertFalse(regulator.isEnabled());
        
        regulator.setEnabled(true);
        assertTrue(regulator.isEnabled());
    }

    @Test
    @DisplayName("Disabled regulator should not regulate")
    void testDisabledRegulator() {
        regulator.setEnabled(false);
        
        // Set a power level different from target to see if regulation happens
        core.setControlRodPosition(0.8); // Different from default 0.5
        
        core.eventBus.publish();
        
        // Since regulator is disabled, rod position should remain unchanged
        assertEquals(0.8, core.getControlRodPosition(), 0.001);
    }

    @Test
    @DisplayName("Shutdown reactor should not be regulated")
    void testShutdownNotRegulated() {
        core.restart(); // Reset
        
        // Directly set shutdown state for testing
        core.setShutdown(true);
        
        // Verify reactor is in shutdown state
        assertTrue(core.isShutdown(), "Reactor should be in shutdown state");
        
        // Set a power level different from target
        core.setControlRodPosition(0.8);
        
        core.eventBus.publish();
        
        // Since reactor is shutdown, rod position should remain unchanged
        assertEquals(0.8, core.getControlRodPosition(), 0.001);
    }

    @Test
    @DisplayName("Regulator should adjust control rods")
    void testRodAdjustment() {
        // Set target power higher than current
        regulator.setTargetPower(200.0);
        
        // Current power is very low (MIN_POWER), so error is large
        core.eventBus.publish();
        
        // Rod position should have changed (moved towards withdrawal)
        double newPos = core.getControlRodPosition();
        assertTrue(newPos > 0.5, "Rod should be withdrawn when power is too low");
    }
}
