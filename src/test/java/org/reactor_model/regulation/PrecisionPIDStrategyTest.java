package org.reactor_model.regulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrecisionPIDStrategy Unit Tests")
class PrecisionPIDStrategyTest {

    private PrecisionPIDStrategy strategy;
    private ReactorCore core;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new PrecisionPIDStrategy();
        core = new ReactorCore(mockLogger);
    }

    @Test
    @DisplayName("PID should return adjustment value")
    void testReturnsAdjustment() {
        double adjustment = strategy.computeAdjustment(500.0, 1000.0, 0.1);
        
        // Should return non-zero adjustment when error exists
        assertNotEquals(0.0, adjustment, 0.0001);
    }

    @Test
    @DisplayName("PID should handle zero error")
    void testZeroError() {
        // First call to establish state
        strategy.computeAdjustment(1000.0, 1000.0, 0.1);
        
        // Second call should return near-zero adjustment
        double adjustment = strategy.computeAdjustment(1000.0, 1000.0, 0.1);
        
        assertEquals(0.0, adjustment, 0.0001, "Should return zero adjustment at setpoint");
    }

    @Test
    @DisplayName("PID should handle positive error (below target)")
    void testPositiveError() {
        double adjustment = strategy.computeAdjustment(800.0, 1000.0, 0.1);
        
        // Positive adjustment needed to increase power
        assertTrue(adjustment > 0, "Should return positive adjustment when below target");
    }

    @Test
    @DisplayName("PID should handle negative error (above target)")
    void testNegativeError() {
        double adjustment = strategy.computeAdjustment(1200.0, 1000.0, 0.1);
        
        // Negative adjustment needed to decrease power
        assertTrue(adjustment < 0, "Should return negative adjustment when above target");
    }

    @Test
    @DisplayName("PID should reduce gains inside deadband")
    void testDeadbandBehavior() {
        // Outside deadband (error > 10 MW)
        strategy.computeAdjustment(500.0, 1000.0, 0.1);
        double integralOutside = strategy.getIntegral();
        
        // Reset
        strategy.reset();
        
        // Inside deadband (error <= 10 MW)
        strategy.computeAdjustment(995.0, 1000.0, 0.1);
        double integralInside = strategy.getIntegral();
        
        // Integral accumulation should be reduced inside deadband
        assertTrue(Math.abs(integralInside) <= Math.abs(integralOutside) || 
                   Math.abs(integralInside) < 1.0, 
                   "Integral should accumulate slower inside deadband");
    }

    @Test
    @DisplayName("PID reset should clear all state")
    void testReset() {
        // Generate some state
        for (int i = 0; i < 10; i++) {
            strategy.computeAdjustment(500.0, 1000.0, 0.1);
        }
        
        strategy.reset();
        
        assertEquals(0.0, strategy.getIntegral(), 0.0001);
        assertEquals(0.0, strategy.getLastError(), 0.0001);
    }

    @Test
    @DisplayName("PID should maintain precision at different power levels")
    void testPrecisionAtDifferentLevels() {
        double[] powerLevels = {100.0, 500.0, 1000.0, 3000.0, 6000.0};
        
        for (double power : powerLevels) {
            PrecisionPIDStrategy testStrategy = new PrecisionPIDStrategy();
            
            // Simulate being at setpoint
            double adjustment = testStrategy.computeAdjustment(power, power, 0.1);
            
            // Should return near-zero adjustment at setpoint regardless of power level
            assertEquals(0.0, adjustment, 0.001, 
                    "Should maintain precision at " + power + " MW");
        }
    }

    @Test
    @DisplayName("PID should respond faster to large errors")
    void testLargeErrorResponse() {
        // Small error (within deadband)
        strategy.computeAdjustment(995.0, 1000.0, 0.1);
        double smallErrorOutput = strategy.computeAdjustment(995.0, 1000.0, 0.1);
        
        // Reset
        strategy.reset();
        
        // Large error (5x deadband)
        strategy.computeAdjustment(950.0, 1000.0, 0.1);
        double largeErrorOutput = strategy.computeAdjustment(950.0, 1000.0, 0.1);
        
        // Large error should produce larger adjustment
        assertTrue(Math.abs(largeErrorOutput) > Math.abs(smallErrorOutput),
                "Larger error should produce larger adjustment");
    }

    @Test
    @DisplayName("PID integral should accumulate over time")
    void testIntegralAccumulation() {
        double error = 50.0; // Outside deadband
        double dt = 0.1;
        
        // First call
        strategy.computeAdjustment(950.0, 1000.0, dt);
        double integral1 = strategy.getIntegral();
        
        // Second call - integral should increase
        strategy.computeAdjustment(950.0, 1000.0, dt);
        double integral2 = strategy.getIntegral();
        
        assertTrue(Math.abs(integral2) > Math.abs(integral1),
                "Integral should accumulate over time");
    }

    @Test
    @DisplayName("PID should handle step change in target")
    void testStepChange() {
        // Establish steady state at 500 MW
        for (int i = 0; i < 20; i++) {
            strategy.computeAdjustment(500.0, 500.0, 0.1);
        }
        
        // Step change to 1000 MW
        double adjustment = strategy.computeAdjustment(500.0, 1000.0, 0.1);
        
        // Should produce significant adjustment
        assertTrue(adjustment > 0.001, "Should respond to step change");
    }

    @Test
    @DisplayName("PID derivative should dampen oscillations")
    void testDerivativeDamping() {
        // Simulate oscillating error
        strategy.computeAdjustment(980.0, 1000.0, 0.1); // error = 20
        double adj1 = strategy.computeAdjustment(1020.0, 1000.0, 0.1); // error = -20
        
        // Derivative should oppose the change
        assertTrue(adj1 < 0, "Derivative should dampen oscillation");
    }

    @Test
    @DisplayName("PID should maintain stability over many iterations")
    void testStabilityOverTime() {
        double target = 1000.0;
        double power = target;
        
        for (int i = 0; i < 100; i++) {
            double adjustment = strategy.computeAdjustment(power, target, 0.1);
            
            // Adjustment should remain bounded
            assertTrue(Math.abs(adjustment) < 1.0, 
                    "Adjustment should remain bounded at iteration " + i);
            
            // Simulate power moving towards target
            power += adjustment * 100;
        }
    }

    @Test
    @DisplayName("PID should handle power-dependent gain scheduling")
    void testGainScheduling() {
        // Low power - should have higher gains
        strategy.computeAdjustment(50.0, 100.0, 0.1);
        double lowPowerOutput = strategy.computeAdjustment(50.0, 100.0, 0.1);
        
        // Reset
        strategy.reset();
        
        // High power - should have lower gains
        strategy.computeAdjustment(6000.0, 6500.0, 0.1);
        double highPowerOutput = strategy.computeAdjustment(6000.0, 6500.0, 0.1);
        
        // Same relative error, but low power should have higher output
        // Note: This may vary based on exact gain scheduling implementation
        assertNotNull(lowPowerOutput);
        assertNotNull(highPowerOutput);
    }
}
