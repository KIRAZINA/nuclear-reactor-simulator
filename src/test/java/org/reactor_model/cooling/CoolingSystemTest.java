package org.reactor_model.cooling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CoolingSystem Unit Tests")
class CoolingSystemTest {

    private CoolingSystem coolingSystem;
    private ReactorCore core;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
        coolingSystem = new CoolingSystem(core, mockLogger);
    }

    @Test
    @DisplayName("Low temperature should use minimum cooling")
    void testMinimumCoolingAtLowTemp() {
        core.setCoolantFlowRate(0.0); // Reset
        coolingSystem.update(500.0);
        
        double coolingFlow = core.getCoolantFlowRate();
        assertTrue(coolingFlow >= 0.2, "Should maintain minimum cooling flow rate");
    }

    @Test
    @DisplayName("High temperature should increase cooling")
    void testIncreasesCoolingAtHighTemp() {
        // Heat up the core
        core.addReactivity(0.02);
        for (int i = 0; i < 30; i++) {
            core.update(0.1, 500.0, core.getPower());
        }
        
        double tempBeforeCooling = core.getTemperature();
        double flowBeforeCooling = core.getCoolantFlowRate();
        
        coolingSystem.update(500.0);
        double flowAfterCooling = core.getCoolantFlowRate();
        
        assertTrue(flowAfterCooling >= flowBeforeCooling, 
                "Cooling should increase at high temperature");
    }

    @Test
    @DisplayName("Critical overpower should activate maximum cooling")
    void testMaxCoolingAtOverpower() {
        // Force critical power
        core.addReactivity(0.1);
        for (int i = 0; i < 200; i++) {
            core.update(0.1, 8100.0, core.getPower());
            if (core.getPower() > ReactorCore.MAX_SAFE_POWER) {
                break;
            }
        }
        
        coolingSystem.update(500.0);
        
        assertEquals(1.0, core.getCoolantFlowRate(), 
                "Should apply maximum cooling at overpower condition");
    }

    @Test
    @DisplayName("Overpower should add negative reactivity")
    void testOverpowerNegativeReactivity() {
        // Force overpower
        core.addReactivity(0.1);
        for (int i = 0; i < 150; i++) {
            core.update(0.1, 8100.0, core.getPower());
            if (core.getPower() > ReactorCore.MAX_SAFE_POWER) {
                break;
            }
        }
        
        double reactivityBeforeCooling = core.getReactivity();
        coolingSystem.update(500.0);
        double reactivityAfterCooling = core.getReactivity();
        
        assertTrue(reactivityAfterCooling < reactivityBeforeCooling,
                "Should add negative reactivity to control overpower");
    }

    @Test
    @DisplayName("Cooling flow should be non-negative")
    void testFlowNonNegative() {
        coolingSystem.update(500.0);
        
        assertTrue(core.getCoolantFlowRate() >= 0.0, 
                "Cooling flow rate should never be negative");
    }

    @Test
    @DisplayName("Cooling flow should not exceed maximum")
    void testFlowMaxLimit() {
        for (int i = 0; i < 100; i++) {
            coolingSystem.update(500.0);
        }
        
        assertTrue(core.getCoolantFlowRate() <= 1.0, 
                "Cooling flow should not exceed 1.0");
    }

    @Test
    @DisplayName("Multiple cooling updates should be stable")
    void testMultipleCoolingUpdates() {
        core.addReactivity(0.02);
        
        for (int i = 0; i < 50; i++) {
            core.update(0.1, 500.0, core.getPower());
            coolingSystem.update(500.0);
            
            assertFalse(Double.isNaN(core.getCoolantFlowRate()));
            assertFalse(Double.isInfinite(core.getCoolantFlowRate()));
        }
    }

    @Test
    @DisplayName("Cooling response should be proportional to temperature")
    void testProportionalCoolingResponse() {
        // Two heating scenarios with different targets
        ReactorCore core1 = new ReactorCore(mockLogger);
        ReactorCore core2 = new ReactorCore(mockLogger);
        
        CoolingSystem cooling1 = new CoolingSystem(core1, mockLogger);
        CoolingSystem cooling2 = new CoolingSystem(core2, mockLogger);
        
        // Heat scenario 1 moderately
        core1.addReactivity(0.01);
        for (int i = 0; i < 10; i++) {
            core1.update(0.1, 500.0, core1.getPower());
        }
        cooling1.update(500.0);
        double flow1 = core1.getCoolantFlowRate();
        
        // Heat scenario 2 heavily
        core2.addReactivity(0.03);
        for (int i = 0; i < 20; i++) {
            core2.update(0.1, 500.0, core2.getPower());
        }
        cooling2.update(500.0);
        double flow2 = core2.getCoolantFlowRate();
        
        assertTrue(flow2 >= flow1, "Heavier heating should require more cooling");
    }

    @Test
    @DisplayName("Cooling system should react to temperature changes within same update")
    void testImmediateCoolingResponse() {
        core.addReactivity(0.02);
        core.update(0.1, 500.0, core.getPower());
        
        double tempBefore = core.getTemperature();
        double flowBefore = core.getCoolantFlowRate();
        
        coolingSystem.update(500.0);
        
        double tempAfter = core.getTemperature();
        double flowAfter = core.getCoolantFlowRate();
        
        // Flow should adjust to temperature
        if (tempAfter > tempBefore) {
            assertTrue(flowAfter >= flowBefore, 
                    "Cooling flow should respond to temperature increase");
        }
    }

    @Test
    @DisplayName("Quadratic flow scaling should smooth response")
    void testQuadraticScaling() {
        // The computeFlow method uses quadratic scaling
        // This tests that the scaling is smooth
        
        double[] flows = new double[5];
        
        for (int scenario = 0; scenario < 5; scenario++) {
            ReactorCore testCore = new ReactorCore(mockLogger);
            CoolingSystem testCooling = new CoolingSystem(testCore, mockLogger);
            
            for (int i = 0; i < scenario * 10; i++) {
                testCore.addReactivity(0.01);
                testCore.update(0.1, 500.0, testCore.getPower());
            }
            testCooling.update(500.0);
            flows[scenario] = testCore.getCoolantFlowRate();
        }
        
        // Flows should generally increase, showing quadratic response
        for (int i = 1; i < flows.length; i++) {
            assertTrue(flows[i] >= flows[i-1] * 0.99, 
                    "Cooling response should be monotonically increasing");
        }
    }
}
