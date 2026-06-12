package org.reactor_model.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PointKineticsSolver Stability Tests")
class PointKineticsSolverStabilityTest {

    private PointKineticsSolver solver;

    @BeforeEach
    void setUp() {
        solver = new PointKineticsSolver(100.0);
    }

    @Test
    @DisplayName("Large reactivity insertion should not produce NaN or Infinity")
    void testLargeReactivityInsertionDoesNotBlowUp() {
        double reactivity = 0.01;
        double dt = 0.1;

        solver.advance(reactivity, dt);

        double power = solver.getPower();
        assertFalse(Double.isNaN(power), "Power should not be NaN after large reactivity insertion");
        assertFalse(Double.isInfinite(power), "Power should not be Infinite");
        assertTrue(power > 100.0, "Power should increase after positive reactivity insertion");
        assertTrue(power < 1e15,
                "Implicit solver should prevent unrealistic exponential blow-up (no Infinity): " + power);
        assertFalse(Double.isInfinite(power), "Power must not be infinite");
    }

    @Test
    @DisplayName("Negative reactivity should decrease power without going negative")
    void testNegativeReactivityIsStable() {
        solver.advance(-0.01, 0.1);

        double power = solver.getPower();
        assertFalse(Double.isNaN(power), "Power should not be NaN");
        assertFalse(Double.isInfinite(power), "Power should not be Infinite");
        assertTrue(power >= 1e-10, "Power should stay above minimum threshold");
        assertTrue(power <= 100.0, "Power should decrease or stay the same with negative reactivity");
    }

    @Test
    @DisplayName("Zero reactivity should maintain steady power")
    void testZeroReactivitySteady() {
        double initialPower = solver.getPower();

        for (int i = 0; i < 10; i++) {
            solver.advance(0.0, 0.1);
        }

        double power = solver.getPower();
        assertFalse(Double.isNaN(power), "Power should not be NaN");
        assertEquals(initialPower, power, 0.001, "Power should remain nearly constant with zero reactivity");
    }

    @Test
    @DisplayName("setPower should scale precursors proportionally, not reset to equilibrium")
    void testSetPowerMaintainsPrecursorContinuity() {
        solver.advance(0.005, 0.5);
        double[] precursorsBefore = solver.getPrecursors();
        double powerBefore = solver.getPower();

        solver.setPower(200.0);

        double ratio = 200.0 / powerBefore;
        double[] precursorsAfter = solver.getPrecursors();
        for (int i = 0; i < 6; i++) {
            assertEquals(precursorsBefore[i] * ratio, precursorsAfter[i], 1e-9,
                    "Precursor[" + i + "] should scale proportionally to power ratio");
        }
        assertEquals(200.0, solver.getPower(), 0.001, "Power should be set to the requested value");
    }

    @Test
    @DisplayName("Multiple ticks with positive reactivity should remain numerically stable")
    void testManyTicksRemainStable() {
        for (int i = 0; i < 1000; i++) {
            solver.advance(0.003, 0.1);
            double power = solver.getPower();
            assertFalse(Double.isNaN(power), "Power should not be NaN at tick " + i);
            assertFalse(Double.isInfinite(power), "Power should not be Infinite at tick " + i);
            assertTrue(power > 0, "Power should remain positive at tick " + i);
        }
    }
}
