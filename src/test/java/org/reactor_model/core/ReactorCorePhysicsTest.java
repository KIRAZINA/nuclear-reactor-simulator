package org.reactor_model.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.StatePersistenceUtil;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Phase 7 core physics: rod S-curve shape, cold-start
 * capability, Wigner-Way decay heat, and xenon plumb-through into reactivity.
 */
@DisplayName("ReactorCore Phase-7 Physics Tests")
class ReactorCorePhysicsTest {

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** Cold core (300 K / 450 K fuel): temperature feedback ≈ -$0.0034. */
    private ReactorCore coldCoreAt(double rodPosition) {
        ReactorCore core = new ReactorCore(mockLogger);
        core.setControlRodPosition(rodPosition);
        core.update(0.1); // single tick: computes reactivity, no SCRAM risk at 0.01 MW
        return core;
    }

    @Test
    @DisplayName("Rod S-curve should span -worth to +worth with zero mid-travel effect")
    void rodSCurveEndpoints() {
        // rodEffect = 0.02 * sin(pi * (pos - 0.5)); feedback ≈ -0.0034 cold.
        assertEquals(-0.0234, coldCoreAt(0.0).getReactivity(), 1e-4);
        assertEquals(-0.0034, coldCoreAt(0.5).getReactivity(), 1e-4);
        assertEquals(0.0166, coldCoreAt(1.0).getReactivity(), 1e-4);
    }

    @Test
    @DisplayName("Differential rod worth should peak mid-core, not at the ends")
    void differentialWorthPeaksMidCore() {
        double midStep = coldCoreAt(0.6).getReactivity() - coldCoreAt(0.5).getReactivity();
        double endStep = coldCoreAt(1.0).getReactivity() - coldCoreAt(0.9).getReactivity();
        assertTrue(midStep > 5.0 * endStep,
                "Mid-core 10% withdrawal (+" + midStep + ") must dwarf end-of-travel (+"
                        + endStep + "): rods bite hardest mid-core");
    }

    @Test
    @DisplayName("Full withdrawal from cold must drive the reactor supercritical")
    void coldStartReachesCriticality() {
        ReactorCore core = new ReactorCore(mockLogger);
        core.setControlRodPosition(1.0);
        for (int i = 0; i < 5 && !core.isShutdown(); i++) {
            core.update(0.1);
        }
        // +0.0166 $ overwhelms cold feedback: power explodes until protection SCRAMs.
        assertTrue(core.isShutdown() || core.getPower() > 100.0,
                "Withdrawn rods must start the reactor from cold");
    }

    @Test
    @DisplayName("SCRAM should settle on Wigner-Way decay heat, not zero")
    void scramSettlesOnDecayHeat() {
        ReactorCore core = new ReactorCore(mockLogger);
        core.setPower(3000.0);
        core.emergencyShutdown("test SCRAM");

        for (int i = 0; i < 10; i++) {
            core.update(0.1); // 1 sim-second: tau clamps to 1 s -> 6.6% of P0
        }

        // 3000 * 0.066 * 1^-0.2 = 198 MW plateau (cooling on: no meltdown).
        assertEquals(198.0, core.getPower(), 48.0,
                "Post-SCRAM power must ride the Wigner-Way ~6.6% plateau");
        assertTrue(core.getPower() >= 1e-5, "Decay heat must never vanish entirely");
        assertTrue(core.getTemperature() < 600.0,
                "Working cooling must remove decay heat, temp was: " + core.getTemperature());
    }

    @Test
    @DisplayName("Equilibrium xenon must flow into core reactivity")
    void xenonFeedsReactivity() {
        ReactorCore core = new ReactorCore(mockLogger);
        // ~42 sim-hours at full power through the model's own integrator.
        for (int i = 0; i < 250; i++) {
            core.getXenonModel().update(3000.0, 600.0);
        }
        core.update(0.1);
        // Rod at 0.5 contributes 0, feedback ≈ -0.0034, xenon ≈ -2.50.
        assertEquals(-2.50, core.getReactivity(), 0.35,
                "Core reactivity must carry equilibrium xenon poisoning");
        assertEquals(core.getXenonModel().getXenonReactivity(), core.getXenonReactivity(), 1e-12);
    }

    @Test
    @DisplayName("Save/load must preserve decay-heat and xenon state")
    void persistencePreservesPhase7State() throws Exception {
        ReactorCore core = new ReactorCore(mockLogger);
        core.setPower(3000.0);
        core.emergencyShutdown("test SCRAM");
        core.update(0.1);
        core.getXenonModel().update(3000.0, 600.0);

        Path file = Files.createTempFile("phase7-state", ".properties");
        try {
            StatePersistenceUtil.saveState(core, file.toString());

            ReactorCore restored = new ReactorCore(mockLogger);
            StatePersistenceUtil.loadState(restored, file.toString());

            assertEquals(core.getPowerBeforeShutdown(), restored.getPowerBeforeShutdown(), 1e-9);
            assertEquals(core.getSecondsSinceShutdown(), restored.getSecondsSinceShutdown(), 1e-9);
            assertEquals(core.getXenonConcentration(), restored.getXenonConcentration(), 1e-6);
            assertEquals(core.getIodineConcentration(), restored.getIodineConcentration(), 1e-6);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
