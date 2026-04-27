package org.reactor_model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.simulation.SimulationLoop;
import org.reactor_model.ui.ReactorStateSnapshot;
import org.reactor_model.ui.ReactorUIAdapter;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reactor Simulation Integration Tests")
class ReactorSimulationIntegrationTest {

    private ReactorCore core;
    private AutoRegulator regulator;
    private PowerDemandSimulator demandSimulator;
    private CoolingSystem coolingSystem;
    private SimulationLoop loop;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        core = new ReactorCore(mockLogger);
        regulator = new AutoRegulator(core, mockLogger);
        demandSimulator = new PowerDemandSimulator(core, regulator, mockLogger);
        coolingSystem = new CoolingSystem(core, mockLogger);
        loop = new SimulationLoop(core, regulator, demandSimulator, coolingSystem);
    }

    @Test
    @DisplayName("Automatic regulator should withdraw rods when target power is above current power")
    void automaticControlWithdrawsRods() {
        regulator.setTargetPower(500.0);

        advanceTicks(6);

        assertTrue(core.getControlRodPosition() > 0.5,
                "Regulator should withdraw rods to increase power from the cold state");
        assertFalse(core.isShutdown(), "Normal target changes should not trigger SCRAM");
    }

    @Test
    @DisplayName("Disabled disturbances should not modify the selected target power")
    void disabledDisturbancesPreserveTargetPower() {
        regulator.setTargetPower(900.0);

        advanceTicks(250);

        assertFalse(demandSimulator.isEnabled(), "Disturbances should stay disabled by default");
        assertEquals(900.0, regulator.getTargetPower(), 0.0001,
                "Target power should remain operator-controlled while disturbances are disabled");
    }

    @Test
    @DisplayName("Manual coolant failure should prevent automatic cooling recovery")
    void manualCoolantFailureRaisesTemperature() {
        core.setManualFlowControl(true);
        core.setCoolantFlowRate(0.0);
        core.setControlRodPosition(1.0);
        core.addReactivity(0.02);

        double initialTemperature = core.getTemperature();

        advanceTicks(40);

        assertEquals(0.0, core.getCoolantFlowRate(), 0.0001,
                "Cooling system should not override manual flow control");
        assertTrue(core.getTemperature() > initialTemperature,
                "Temperature should rise when heat is produced without coolant flow");
    }

    @Test
    @DisplayName("Cooling system should raise coolant flow as the core heats up")
    void coolingRespondsToHeatLoad() {
        coolingSystem.update(regulator.getTargetPower());
        double initialFlow = core.getCoolantFlowRate();

        core.setControlRodPosition(1.0);
        core.addReactivity(0.015);

        advanceTicks(80);

        assertTrue(core.getTemperature() > 300.0, "Core should heat up under sustained positive reactivity");
        assertTrue(core.getCoolantFlowRate() > initialFlow,
                "Automatic cooling should increase flow in response to higher temperature");
    }

    @Test
    @DisplayName("UI adapter snapshots should refresh after reactor updates")
    void uiAdapterReflectsLatestState() {
        ReactorUIAdapter adapter = new ReactorUIAdapter(core, regulator, loop, demandSimulator);
        ReactorStateSnapshot initial = adapter.getSnapshot();

        regulator.setTargetPower(650.0);
        advanceTicks(3);

        ReactorStateSnapshot updated = adapter.getSnapshot();

        assertNotNull(initial);
        assertNotNull(updated);
        assertEquals(650.0, updated.targetPower, 0.0001);
        assertTrue(updated.controlRodPosition >= initial.controlRodPosition,
                "Snapshot should reflect regulator action after state updates");
    }

    private void advanceTicks(int ticks) {
        for (int i = 0; i < ticks; i++) {
            demandSimulator.update();
            core.update(0.1);
            coolingSystem.update(regulator.getTargetPower());
        }
    }
}
