package org.reactor_model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ConsoleReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.simulation.SimulationLoop;
import org.reactor_model.ui.ReactorUIAdapter;
import org.reactor_model.util.StatePersistenceUtil;
import org.reactor_model.util.StatePersistenceUtil.StatePersistenceException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Phase 3 stop-the-world load sequence: a running loop is
 * paused while state is restored and resumes afterwards without errors.
 */
@DisplayName("Safe State Loading Integration Tests")
class SafeLoadIntegrationTest {

    @TempDir
    private Path tempDir;

    private ReactorCore core;
    private AutoRegulator regulator;
    private PowerDemandSimulator demand;
    private CoolingSystem cooling;
    private SimulationLoop loop;
    private ReactorUIAdapter adapter;

    @BeforeEach
    void setUp() {
        var logger = new ConsoleReactorLogger();
        core = new ReactorCore(logger);
        regulator = new AutoRegulator(core, logger);
        demand = new PowerDemandSimulator(core, regulator, logger);
        cooling = new CoolingSystem(core, logger);
        loop = new SimulationLoop(core, regulator, demand, cooling);
        adapter = new ReactorUIAdapter(core, regulator, loop, demand);
    }

    @AfterEach
    void tearDown() {
        loop.stop();
    }

    @Test
    @Timeout(10)
    @DisplayName("Adapter load should pause a running loop, restore state, and resume")
    void loadRestartsRunningLoop() throws Exception {
        // Arrange: known state saved to disk, then mutated.
        core.setTemperature(500.0);
        core.setControlRodPosition(0.75);
        core.setPower(1500.0);
        Path file = tempDir.resolve("running-state.properties");
        StatePersistenceUtil.saveState(core, file.toString());

        loop.start();
        assertTrue(loop.isRunning(), "Loop should be running before load");

        // Act: stop-the-world load through the adapter (same path the GUI uses).
        assertDoesNotThrow(() -> adapter.loadState(file.toString()));

        // Assert: loop resumed and saved values restored.
        assertTrue(loop.isRunning(), "Loop should be running again after successful load");
        assertEquals(500.0, core.getTemperature(), 1e-9);
        assertEquals(0.75, core.getControlRodPosition(), 1e-9);
        assertEquals(1500.0, core.getPower(), 1e-9);
    }

    @Test
    @Timeout(10)
    @DisplayName("Adapter load on a stopped loop should restore state and stay stopped")
    void loadOnStoppedLoopStaysStopped() throws Exception {
        core.setTemperature(420.0);
        Path file = tempDir.resolve("stopped-state.properties");
        StatePersistenceUtil.saveState(core, file.toString());
        core.setTemperature(300.0);

        assertFalse(loop.isRunning(), "Loop should be stopped before load");
        adapter.loadState(file.toString());

        assertFalse(loop.isRunning(), "Loop should remain stopped after load");
        assertEquals(420.0, core.getTemperature(), 1e-9);
    }

    @Test
    @Timeout(10)
    @DisplayName("Failed load should throw and leave the loop stopped for safety")
    void failedLoadLeavesLoopStopped() {
        loop.start();
        assertTrue(loop.isRunning());

        Path missing = tempDir.resolve("missing.properties");
        assertThrows(StatePersistenceException.class,
                () -> adapter.loadState(missing.toString()));

        assertFalse(loop.isRunning(),
                "Loop must be left stopped after a failed load to avoid running on half-restored state");
    }
}
