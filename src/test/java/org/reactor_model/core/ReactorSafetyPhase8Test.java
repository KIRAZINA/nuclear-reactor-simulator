package org.reactor_model.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.StatePersistenceUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Phase 8 accident-safety coverage: DNB degradation, void feedback, pump
 * coastdown, ATWS bypass, meltdown permanence, and safety-state persistence.
 */
@DisplayName("Phase-8 Thermal-Hydraulics & Safety Tests")
class ReactorSafetyPhase8Test {

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ReactorCore freshCore() {
        return new ReactorCore(mockLogger);
    }

    @Test
    @DisplayName("DNB should collapse heat removal below 20% flow")
    void dnbCollapsesHeatRemoval() {
        // Start hot (900 K): at large delta-T the removal term dominates, so
        // the flow-dependent transfer gap is unmistakable within a few ticks.
        // (Near ambient, both removals are negligible next to 1 GW of heating.)
        ReactorCore starved = freshCore();
        starved.setCoolantFlowRate(0.05);
        starved.setTemperature(900.0);
        ReactorCore cooled = freshCore();
        cooled.setCoolantFlowRate(1.0);
        cooled.setTemperature(900.0);

        // 30 ticks (3 sim-s): force-cooled core sheds ~2 K/tick while the
        // starved core (DNB film boiling) barely moves. No SCRAM risk.
        for (int i = 0; i < 30; i++) {
            starved.update(0.1);
            cooled.update(0.1);
        }

        assertTrue(starved.getTemperature() - cooled.getTemperature() > 30.0,
                "Starved core (flow 0.05) must stay far hotter than force-cooled core (flow 1.0)");
    }

    @Test
    @DisplayName("Boiling void should insert strong negative reactivity")
    void voidFeedbackInsertsNegativeReactivity() {
        ReactorCore core = freshCore();
        core.setCoolantFlowRate(0.1);
        core.setTemperature(700.0); // 100 K into voiding
        core.update(0.1);

        // -0.005 $/K * 100 K = -0.50 $, dwarfing rod/feedback terms.
        assertTrue(core.getReactivity() < -0.4,
                "Voiding must slam reactivity negative, was: " + core.getReactivity());
    }

    @Test
    @DisplayName("Pump trip should lock manual control and coast flow to zero")
    void pumpTripCoastsDown() {
        ReactorCore core = freshCore();
        core.failCoolantPump();

        assertTrue(core.isManualFlowControl(), "Trip must lock manual flow control");
        assertTrue(core.isPumpTripped());

        for (int i = 0; i < 60; i++) {
            core.update(0.1); // 6 sim-seconds: 5 s coastdown completes
        }
        assertEquals(0.0, core.getCoolantFlowRate(), 1e-9,
                "Flow must coast to zero after a pump trip");

        assertDoesNotThrow(core::failCoolantPump, "Repeat trips must be idempotent");
    }

    @Test
    @DisplayName("LOFA without cooling must SCRAM, then melt on decay heat")
    void lofaMeltsUncooledCore() {
        ReactorCore core = freshCore();
        // Operating state: high power, hot, rods out — then lose the pump.
        core.setPower(3000.0);
        core.setControlRodPosition(1.0);
        core.setTemperature(500.0);
        core.failCoolantPump();

        boolean scrammed = false;
        for (int i = 0; i < 3000 && !core.isMeltedDown(); i++) {
            core.update(0.1);
            scrammed |= core.isShutdown();
        }

        assertTrue(scrammed, "LOFA must trigger SCRAM");
        assertTrue(core.isMeltedDown(),
                "Uncooled decay heat must eventually melt the core");
    }

    @Test
    @DisplayName("Melted core must refuse restart and freeze physics")
    void meltdownRefusesRestart() {
        ReactorCore core = freshCore();
        core.setTemperature(1500.0);
        core.setPower(2000.0);
        core.setManualFlowControl(true);
        core.setCoolantFlowRate(0.0);

        for (int i = 0; i < 1100 && !core.isMeltedDown(); i++) {
            core.update(0.1);
        }
        assertTrue(core.isMeltedDown(), "Sustained 1500 K must melt the core");

        core.restart();
        assertTrue(core.isMeltedDown(), "Restart must not clear damage");
        assertTrue(core.isShutdown(), "Melted core stays shut down");

        core.update(0.1);
        assertTrue(core.getPower() < 1.0, "Melted core must hold ~zero power");
        assertFalse(Double.isNaN(core.getTemperature()), "Temperature must stay numeric");
    }

    @Test
    @DisplayName("ATWS bypass must suppress SCRAM until disarmed")
    void bypassSuppressesScram() {
        ReactorCore core = freshCore();
        core.setBypassAutoScram(true);
        core.addReactivity(0.5);

        for (int i = 0; i < 3; i++) {
            core.update(0.1);
        }
        assertFalse(core.isShutdown(),
                "Bypassed protection must not SCRAM on critical conditions");
        assertTrue(Double.isFinite(core.getPower()), "Power must stay finite under ATWS");
        verify(mockLogger, atLeastOnce()).logWarning(
                org.mockito.ArgumentMatchers.contains("bypassed"));

        core.setBypassAutoScram(false);
        core.update(0.1);
        assertTrue(core.isShutdown(), "Disarming must restore protection on next trip");
    }

    @Test
    @DisplayName("Transient temperature touches must not melt the core")
    void transientTouchDoesNotMelt() {
        ReactorCore core = freshCore();
        core.addReactivity(0.05);
        core.update(0.1); // prompt burst kisses the 1500 K clamp...

        core.restart(); // ...yet restart must still work (no sustained exposure)
        assertFalse(core.isMeltedDown());
        assertFalse(core.isShutdown());
    }

    @Test
    @DisplayName("Safety state must persist across save/load, old files still load")
    void safetyStatePersists() throws Exception {
        ReactorCore core = freshCore();
        core.failCoolantPump();
        core.setBypassAutoScram(true);

        Path file = Files.createTempFile("phase8-safety", ".properties");
        try {
            StatePersistenceUtil.saveState(core, file.toString());
            ReactorCore restored = freshCore();
            StatePersistenceUtil.loadState(restored, file.toString());

            assertTrue(restored.isPumpTripped());
            assertTrue(restored.isBypassAutoScram());
            assertFalse(restored.isMeltedDown());

            // Pre-Phase-8 file: only legacy keys -> safe defaults.
            Path legacy = Files.createTempFile("legacy-state", ".properties");
            try {
                Files.writeString(legacy,
                        "temperature=300.0\ncoolantFlowRate=1.0\nmanualFlowControl=false\n"
                                + "controlRodPosition=0.5\nexternalReactivity=0.0\nreactivity=0.0\n"
                                + "shutdown=false\noverheatTicks=0\nfuelTemperature=450.0\npower=0.01\n"
                                + "precursor.0=1.0\nprecursor.1=1.0\nprecursor.2=1.0\n"
                                + "precursor.3=1.0\nprecursor.4=1.0\nprecursor.5=1.0\n",
                        StandardCharsets.UTF_8);
                ReactorCore legacyCore = freshCore();
                assertDoesNotThrow(() -> StatePersistenceUtil.loadState(legacyCore, legacy.toString()));
                assertFalse(legacyCore.isMeltedDown());
                assertFalse(legacyCore.isPumpTripped());
                assertFalse(legacyCore.isBypassAutoScram());
            } finally {
                Files.deleteIfExists(legacy);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
