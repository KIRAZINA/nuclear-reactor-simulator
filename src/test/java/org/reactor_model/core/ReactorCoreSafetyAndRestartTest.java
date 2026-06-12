package org.reactor_model.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.logger.ReactorLogger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReactorCore Safety & Restart Tests")
class ReactorCoreSafetyAndRestartTest {

    private ReactorCore core;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
    }

    @Test
    @DisplayName("Restart should reset manual flow control and restore coolant flow")
    void testRestartResetsManualFlowControl() {
        core.setManualFlowControl(true);
        core.setCoolantFlowRate(0.0);

        // Force shutdown state
        for (int i = 0; i < 500; i++) {
            core.addReactivity(0.1);
            core.update(0.1);
            if (core.isShutdown()) break;
        }

        assertTrue(core.isShutdown(), "Reactor should be in shutdown before restart");
        assertTrue(core.isManualFlowControl(), "Manual flow control should be set");

        core.restart();

        assertFalse(core.isManualFlowControl(), "Manual flow control must be reset on restart");
        assertEquals(1.0, core.getCoolantFlowRate(), 0.001, "Coolant flow must be restored to 1.0");
        assertFalse(core.isShutdown(), "Reactor should no longer be in shutdown state");
        assertEquals(0.01, core.getPower(), 0.001, "Power should return to minimum");
    }

    @Test
    @DisplayName("Fuel temperature should be decoupled from coolant temperature")
    void testFuelTemperatureDecoupled() {
        assertEquals(450.0, core.getFuelTemperature(), 1.0, "Initial fuel temperature should be offset from coolant");

        // Heat up the core
        core.setCoolantFlowRate(0.2);
        core.addReactivity(0.05);
        for (int i = 0; i < 100; i++) {
            core.update(0.1);
        }

        double fuelTemp = core.getFuelTemperature();
        double coolantTemp = core.getTemperature();

        assertTrue(fuelTemp > coolantTemp, "Fuel temperature should be higher than coolant temperature");
        assertEquals(coolantTemp + 150.0, fuelTemp, 1.0, "Fuel should maintain ~150 K offset from coolant");
    }

    @Test
    @DisplayName("Restart should reset overheat ticks")
    void testRestartResetsOverheatTicks() {
        core.setCoolantFlowRate(0.1);
        core.addReactivity(0.05);

        for (int i = 0; i < 500; i++) {
            core.update(0.1);
        }

        assertTrue(core.getOverheatTicks() > 0, "Overheat ticks should accumulate");
        core.restart();
        assertEquals(0, core.getOverheatTicks(), "Overheat ticks should be zero after restart");
    }

    @Test
    @DisplayName("handleOverheatProtectionManualOverride should insert control rods")
    void testManualOverrideInsertsRods() {
        core.setControlRodPosition(0.8);
        core.handleOverheatProtectionManualOverride();

        assertTrue(core.getControlRodPosition() < 0.8, "Manual override should insert rods");
        assertTrue(core.getControlRodPosition() >= 0.0, "Rod position should stay non-negative");
    }
}
