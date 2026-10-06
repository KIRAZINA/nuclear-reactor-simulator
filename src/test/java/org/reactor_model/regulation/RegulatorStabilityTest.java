package org.reactor_model.regulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Phase 7 control stability: deadband hold (no rod hunting) and
 * oscillation-free settling after a large target step.
 */
@DisplayName("Regulator Stability Tests")
class RegulatorStabilityTest {

    private ReactorCore core;
    private AutoRegulator regulator;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
        regulator = new AutoRegulator(core, mockLogger);
    }

    @Test
    @DisplayName("Rods should hold still inside a stable 1.5% deadband")
    void deadbandHoldsRodsSteady() {
        regulator.setTargetPower(1000.0);
        core.setPower(995.0); // 0.5% error once the ramped setpoint arrives...
        // ...but the effective setpoint starts at 100 MW and ramps 10 MW/call,
        // so drive it up to the operator target first (rods legitimately move).
        for (int i = 0; i < 100; i++) {
            regulator.regulateForTest();
        }
        double settled = core.getControlRodPosition();

        regulator.regulateForTest(); // trend steady, error tiny: must hold
        assertEquals(settled, core.getControlRodPosition(), 1e-12,
                "Inside a stable deadband the regulator must not touch the rods");
    }

    @Test
    @DisplayName("Rods must still move for errors outside the deadband")
    void outsideDeadbandStillRegulates() {
        regulator.setTargetPower(1000.0);
        // Below the ramp start (100 MW): error stays positive as it arrives.
        core.setPower(50.0);

        regulator.regulateForTest();
        regulator.regulateForTest();

        assertTrue(core.getControlRodPosition() > 0.5,
                "Large low-power error must withdraw rods");
    }

    @Test
    @DisplayName("50% target step should settle within 5% without rod hunting")
    void fiftyPercentStepSettlesCleanly() {
        regulator.setTargetPower(1000.0);
        for (int i = 0; i < 600; i++) {
            core.update(0.1);
        }
        assertFalse(core.isShutdown(), "Setup must reach 1000 MW without SCRAM");

        regulator.setTargetPower(1500.0);
        int signFlips = 0;
        double previousError = 1500.0 - core.getPower();
        for (int i = 0; i < 500; i++) {
            core.update(0.1);
            double error = 1500.0 - core.getPower();
            if (i % 10 == 0) {
                if (previousError != 0.0 && Math.signum(error) != Math.signum(previousError)) {
                    signFlips++;
                }
                previousError = error;
            }
        }

        assertFalse(core.isShutdown(), "Step must not SCRAM the reactor");
        assertEquals(1500.0, core.getPower(), 75.0,
                "Power must settle within 5% of the new target");
        assertTrue(signFlips <= 4,
                "Settling must not oscillate around the target (flips: " + signFlips + ")");
    }
}
