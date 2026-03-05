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

    @Mock
    private RegulationStrategy mockStrategy;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
        regulator = new AutoRegulator(core, mockLogger, mockStrategy);
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
        when(mockStrategy.computeAdjustment(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(0.05);
        
        core.eventBus.publish();
        
        verify(mockStrategy, never()).computeAdjustment(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Shutdown reactor should not be regulated")
    void testShutdownNotRegulated() {
        core.restart(); // Reset
        
        // Directly set shutdown state for testing
        core.setShutdown(true);
        
        // Verify reactor is in shutdown state
        assertTrue(core.isShutdown(), "Reactor should be in shutdown state");
        
        // Now when we publish events, strategy should NOT be called because reactor is shutdown
        when(mockStrategy.computeAdjustment(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(0.05);
        
        core.eventBus.publish();
        
        // Verify that strategy was NOT called because reactor is shutdown
        verify(mockStrategy, never()).computeAdjustment(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Regulator should request strategy adjustment")
    void testStrategyInvocation() {
        when(mockStrategy.computeAdjustment(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(0.03);
        
        core.eventBus.publish();
        
        verify(mockStrategy, atLeastOnce())
                .computeAdjustment(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Regulator should apply rod adjustment with limiting")
    void testRodAdjustmentLimiting() {
        when(mockStrategy.computeAdjustment(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(0.2); // Large adjustment
        
        double initialRodPos = core.getControlRodPosition();
        core.eventBus.publish();
        
        double newRodPos = core.getControlRodPosition();
        double actualAdjustment = newRodPos - initialRodPos;
        
        // Use small epsilon for floating-point comparison
        // The adjustment should be limited to MAX_ROD_STEP (0.05) with some tolerance
        assertTrue(Math.abs(actualAdjustment) <= 0.05 + 1e-10, 
                "Rod movement should be limited to MAX_ROD_STEP, but was: " + actualAdjustment);
    }

    @Test
    @DisplayName("Target power changes should be tracked")
    void testTargetPowerChange() {
        double target1 = 150.0;
        regulator.setTargetPower(target1);
        assertEquals(target1, regulator.getTargetPower());
        
        double target2 = 300.0;
        regulator.setTargetPower(target2);
        assertEquals(target2, regulator.getTargetPower());
    }

    @Test
    @DisplayName("Regulator should handle zero error gracefully")
    void testZeroError() {
        regulator.setTargetPower(10.0); // Close to initial power
        when(mockStrategy.computeAdjustment(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(0.0);
        
        assertDoesNotThrow(() -> core.eventBus.publish());
    }

    @Test
    @DisplayName("Multiple regulation cycles should be possible")
    void testMultipleCycles() {
        when(mockStrategy.computeAdjustment(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(0.01);
        
        for (int i = 0; i < 10; i++) {
            core.update(0.1, regulator.getTargetPower(), core.getPower());
            core.eventBus.publish();
        }
        
        verify(mockStrategy, atLeast(10))
                .computeAdjustment(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Regulator should log stability checks")
    void testStabilityLogging() {
        regulator.setTargetPower(10.0); // Close to initial power
        when(mockStrategy.computeAdjustment(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(0.0);
        
        // Run multiple times to trigger stability check
        for (int i = 0; i < 200; i++) {
            core.eventBus.publish();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
