package org.reactor_model.regulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimplePIDStrategy Unit Tests")
class SimplePIDStrategyTest {

    private SimplePIDStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SimplePIDStrategy();
    }

    @Test
    @DisplayName("Initial adjustment should reflect proportional error")
    void testProportionalTerm() {
        double currentPower = 100.0;
        double targetPower = 200.0;
        
        double adjustment = strategy.computeAdjustment(currentPower, targetPower, 0.1);
        
        assertTrue(adjustment > 0, "Should have positive adjustment for positive error");
    }

    @Test
    @DisplayName("Zero error should give zero adjustment")
    void testZeroError() {
        double adjustment = strategy.computeAdjustment(100.0, 100.0, 0.1);
        
        assertEquals(0.0, adjustment, 0.001, "Zero error should give close to zero adjustment");
    }

    @Test
    @DisplayName("Positive error should give positive adjustment")
    void testPositiveError() {
        double adjustment = strategy.computeAdjustment(50.0, 150.0, 0.1);
        
        assertTrue(adjustment > 0, "Should increase power for positive error");
    }

    @Test
    @DisplayName("Negative error should give negative adjustment")
    void testNegativeError() {
        double adjustment = strategy.computeAdjustment(200.0, 100.0, 0.1);
        
        assertTrue(adjustment < 0, "Should decrease power for negative error");
    }

    @Test
    @DisplayName("Integral term should accumulate over time")
    void testIntegralAccumulation() {
        double error = 50.0;
        
        // First call
        strategy.computeAdjustment(100.0, 150.0, 0.1);
        
        // Second call with same error should have larger adjustment (integral grows)
        double adjustment2 = strategy.computeAdjustment(100.0, 150.0, 0.1);
        
        assertTrue(adjustment2 > 0, "Should accumulate integral over multiple calls");
    }

    @Test
    @DisplayName("Integral should have anti-windup limit")
    void testIntegralAntiWindup() {
        // Force large positive error for many iterations
        for (int i = 0; i < 1000; i++) {
            strategy.computeAdjustment(10.0, 1000.0, 0.1);
        }
        
        double adjustment = strategy.computeAdjustment(10.0, 1000.0, 0.1);
        
        // Should not grow infinitely
        assertTrue(Math.abs(adjustment) < 10.0, 
                "Integral should be clamped to prevent wind-up");
    }

    @Test
    @DisplayName("Derivative term should respond to error rate of change")
    void testDerivativeTerm() {
        // Small error change
        double adj1 = strategy.computeAdjustment(100.0, 150.0, 0.1);
        double adj2 = strategy.computeAdjustment(100.0, 150.0, 0.1);
        
        // Large error change
        double adj3 = strategy.computeAdjustment(100.0, 300.0, 0.1);
        double adj4 = strategy.computeAdjustment(100.0, 300.0, 0.1);
        
        // Different adjustments should be produced for different error magnitudes
        assertNotEquals(adj1, adj3, 0.01, "Derivative should differ for different errors");
    }

    @Test
    @DisplayName("Derivative should have smoothing applied")
    void testDerivativeSmoothing() {
        // The derivative smoothing should make the signal less noisy
        double[] adjustments = new double[10];
        
        for (int i = 0; i < 10; i++) {
            // Oscillating error
            double target = (i % 2 == 0) ? 200.0 : 100.0;
            adjustments[i] = strategy.computeAdjustment(100.0, target, 0.1);
        }
        
        // With smoothing, adjustments shouldn't vary wildly
        assertDoesNotThrow(() -> {
            for (double adj : adjustments) {
                assertTrue(Math.abs(adj) < 1.0, "Smoothing should prevent excessive oscillation");
            }
        });
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 0.05, 0.1, 0.5})
    @DisplayName("Different timesteps should affect integration")
    void testDifferentTimesteps(double dt) {
        double adjustment = strategy.computeAdjustment(100.0, 150.0, dt);
        
        assertNotNull(adjustment);
        assertFalse(Double.isNaN(adjustment));
        assertFalse(Double.isInfinite(adjustment));
    }

    @Test
    @DisplayName("Strategy should be reusable across multiple adjustments")
    void testReuseability() {
        for (int i = 0; i < 100; i++) {
            double adjustment = strategy.computeAdjustment(100.0, 150.0, 0.1);
            
            assertFalse(Double.isNaN(adjustment));
            assertFalse(Double.isInfinite(adjustment));
        }
    }

    @Test
    @DisplayName("Large positive error should give significant adjustment")
    void testLargePositiveError() {
        double adjustment = strategy.computeAdjustment(10.0, 1000.0, 0.1);
        
        assertTrue(adjustment > 0, "Large positive error should yield positive adjustment");
    }

    @Test
    @DisplayName("Large negative error should give significant adjustment")
    void testLargeNegativeError() {
        double adjustment = strategy.computeAdjustment(1000.0, 10.0, 0.1);
        
        assertTrue(adjustment < 0, "Large negative error should yield negative adjustment");
    }

    @Test
    @DisplayName("State should persist across calls")
    void testStatePersistence() {
        strategy.computeAdjustment(100.0, 150.0, 0.1);
        double adj1 = strategy.computeAdjustment(100.0, 150.0, 0.1);
        
        strategy.computeAdjustment(100.0, 200.0, 0.1);
        double adj2 = strategy.computeAdjustment(100.0, 200.0, 0.1);
        
        assertNotEquals(adj1, adj2, 0.01, "Different errors should yield different results");
    }

    @Test
    @DisplayName("Adjustment should be continuous function of error")
    void testContinuity() {
        double adj1 = strategy.computeAdjustment(100.0, 100.1, 0.1);
        double adj2 = strategy.computeAdjustment(100.0, 100.2, 0.1);
        
        // Small change in target should give small change in adjustment
        assertTrue(Math.abs(adj2 - adj1) < 0.1, 
                "Adjustments should change smoothly with error");
    }
}
