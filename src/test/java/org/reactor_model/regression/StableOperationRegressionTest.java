package org.reactor_model.regression;

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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stable Operation Regression Tests")
class StableOperationRegressionTest {

    private ReactorCore core;
    private AutoRegulator regulator;
    private PowerDemandSimulator demandSimulator;
    private CoolingSystem coolingSystem;
    private SimulationLoop simulationLoop;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        core = new ReactorCore(mockLogger);
        regulator = new AutoRegulator(core, mockLogger);
        demandSimulator = new PowerDemandSimulator(core, regulator, mockLogger);
        coolingSystem = new CoolingSystem(core, mockLogger);
        simulationLoop = new SimulationLoop(core, regulator, demandSimulator, coolingSystem);
    }

    @Test
    @DisplayName("Disturbances should remain disabled unless explicitly enabled")
    void disturbancesAreDisabledByDefault() {
        assertFalse(demandSimulator.isEnabled());

        for (int i = 0; i < 200; i++) {
            demandSimulator.update();
        }

        assertFalse(demandSimulator.isEnabled());
        assertEquals(100.0, regulator.getTargetPower(), 0.0001,
                "Disabled disturbances must not mutate the target power");
    }

    @Test
    @DisplayName("Operator target power should stay unchanged during stable operation")
    void targetPowerRemainsOperatorControlled() {
        regulator.setTargetPower(1200.0);

        advanceTicks(300);

        assertEquals(1200.0, regulator.getTargetPower(), 0.0001);
    }

    @Test
    @DisplayName("Manual coolant mode should block automatic cooling changes")
    void manualFlowModeBlocksCoolingOverride() {
        core.setManualFlowControl(true);
        core.setCoolantFlowRate(0.35);

        advanceTicks(50);

        assertEquals(0.35, core.getCoolantFlowRate(), 0.0001,
                "Cooling system should respect manual flow control");
    }

    @Test
    @DisplayName("Restart should clear shutdown state and restore cold defaults")
    void restartRestoresColdState() {
        core.setShutdown(true);
        core.addReactivity(0.03);
        core.update(0.1);

        core.restart();

        assertFalse(core.isShutdown());
        assertEquals(0.01, core.getPower(), 0.001);
        assertEquals(300.0, core.getTemperature(), 0.1);
        assertEquals(0.5, core.getControlRodPosition(), 0.001);
        assertEquals(0.0, core.getReactivity(), 0.001);
    }

    @Test
    @DisplayName("Repeated target changes should keep the simulation numerically stable")
    void repeatedTargetChangesStayStable() {
        double[] targets = {300.0, 800.0, 1400.0, 600.0, 1000.0};

        for (double target : targets) {
            regulator.setTargetPower(target);
            advanceTicks(40);

            assertEquals(target, regulator.getTargetPower(), 0.0001);
            assertFalse(Double.isNaN(core.getPower()));
            assertFalse(Double.isNaN(core.getTemperature()));
            assertTrue(core.getControlRodPosition() >= 0.0 && core.getControlRodPosition() <= 1.0);
            assertTrue(core.getCoolantFlowRate() >= 0.0 && core.getCoolantFlowRate() <= 1.0);
        }
    }

    @Test
    @DisplayName("Loop start-stop cycle should not leave the reactor in an invalid state")
    void startStopCycleLeavesValidState() throws InterruptedException {
        regulator.setTargetPower(700.0);

        simulationLoop.start();
        Thread.sleep(400);
        simulationLoop.stop();
        Thread.sleep(100);

        assertFalse(Double.isNaN(core.getPower()));
        assertFalse(Double.isNaN(core.getTemperature()));
        assertTrue(core.getCoolantFlowRate() >= 0.0 && core.getCoolantFlowRate() <= 1.0);
    }

    private void advanceTicks(int ticks) {
        for (int i = 0; i < ticks; i++) {
            demandSimulator.update();
            core.update(0.1);
            coolingSystem.update(regulator.getTargetPower());
        }
    }
}
