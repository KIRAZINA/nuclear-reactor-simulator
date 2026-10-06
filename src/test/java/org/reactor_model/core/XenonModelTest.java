package org.reactor_model.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers Xenon-135 / Iodine-135 dynamics with realistic constants.
 * Long sim-times are driven directly with large dt steps (explicit Euler is
 * stable here: LAMBDA * dt stays well below 1).
 */
@DisplayName("Xenon-135 Model Tests")
class XenonModelTest {

    private XenonModel xenon;

    @BeforeEach
    void setUp() {
        xenon = new XenonModel();
    }

    @Test
    @DisplayName("Fresh fuel should carry no xenon reactivity")
    void freshFuelHasNoPoison() {
        assertEquals(0.0, xenon.getXenonReactivity(), 1e-12);
        xenon.update(0.0, 600.0);
        assertEquals(0.0, xenon.getXenonReactivity(), 1e-12);
        assertEquals(0.0, xenon.getIodineConc(), 1e-12);
        assertEquals(0.0, xenon.getXenonConc(), 1e-12);
    }

    @Test
    @DisplayName("Full-power equilibrium should sit near -$2.50")
    void fullPowerEquilibriumNearTwoFifty() {
        // ~42 sim-hours at 3000 MW: many Xe time constants, fully converged.
        for (int i = 0; i < 250; i++) {
            xenon.update(3000.0, 600.0);
        }
        assertEquals(-2.50, xenon.getXenonReactivity(), 0.30,
                "Equilibrium xenon worth at 3000 MW should be about -$2.50");
    }

    @Test
    @DisplayName("Post-shutdown xenon should peak above equilibrium, then decay away")
    void shutdownPeakThenDecay() {
        for (int i = 0; i < 250; i++) {
            xenon.update(3000.0, 600.0);
        }
        double equilibrium = xenon.getXenonReactivity();

        // Iodine keeps decaying into xenon with no burnout: track the worst.
        double peak = equilibrium;
        for (int i = 0; i < 60; i++) { // ~10 sim-hours on decay heat
            xenon.update(50.0, 600.0);
            peak = Math.min(peak, xenon.getXenonReactivity());
        }
        assertTrue(peak < equilibrium - 0.5,
                "Post-shutdown peak (" + peak + ") must exceed equilibrium ("
                        + equilibrium + ") in magnitude");

        // Let it decay for ~10 more days of sim-time: poisoning must lift.
        for (int i = 0; i < 1400; i++) {
            xenon.update(0.0, 600.0);
        }
        assertTrue(xenon.getXenonReactivity() > -1.0,
                "Xenon must decay away eventually, was: " + xenon.getXenonReactivity());
    }

    @Test
    @DisplayName("Inventory must stay non-negative under extreme stepping")
    void concentrationsStayNonNegative() {
        for (int i = 0; i < 10; i++) {
            xenon.update(3000.0, 600.0);
        }
        for (int i = 0; i < 50; i++) {
            xenon.update(0.0, 3600.0);
        }
        assertTrue(xenon.getIodineConc() >= 0.0);
        assertTrue(xenon.getXenonConc() >= 0.0);
    }

    @Test
    @DisplayName("Setters should clamp negatives and support persistence round-trips")
    void settersClampAndRoundTrip() {
        xenon.setIodineConc(-5.0);
        xenon.setXenonConc(-7.0);
        assertEquals(0.0, xenon.getIodineConc(), 1e-12);
        assertEquals(0.0, xenon.getXenonConc(), 1e-12);

        xenon.setIodineConc(1.5e16);
        xenon.setXenonConc(2.5e16);
        assertEquals(1.5e16, xenon.getIodineConc(), 1e6);
        assertEquals(2.5e16, xenon.getXenonConc(), 1e6);

        xenon.reset();
        assertEquals(0.0, xenon.getXenonReactivity(), 1e-12);
    }
}
