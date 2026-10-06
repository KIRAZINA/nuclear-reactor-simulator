package org.reactor_model;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ConsoleReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.simulation.SimulationLoop;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * CLI coverage for the Phase 8 accident commands (failpump, bypass).
 * Mirrors the reflection pattern of ReactorAppCommandTest so the private
 * command dispatcher stays testable without signature changes.
 */
@DisplayName("ReactorApp Phase-8 Command Tests")
class ReactorAppPhase8CommandTest {

    private static Method handleCommand;

    private ReactorCore core;
    private AutoRegulator regulator;
    private SimulationLoop loop;
    private ConsoleReactorLogger logger;

    @BeforeAll
    static void prepareReflection() throws Exception {
        handleCommand = ReactorApp.class.getDeclaredMethod(
                "handleCommand",
                String.class,
                ReactorCore.class,
                AutoRegulator.class,
                SimulationLoop.class,
                ConsoleReactorLogger.class
        );
        handleCommand.setAccessible(true);
    }

    @BeforeEach
    void setUp() {
        logger = Mockito.spy(new ConsoleReactorLogger());
        core = new ReactorCore(logger);
        regulator = new AutoRegulator(core, logger);
        loop = mock(SimulationLoop.class);
    }

    @Test
    @DisplayName("failpump should trip the pump and lock manual flow control")
    void failpumpTripsPump() throws Exception {
        invokeCommand("failpump");

        assertTrue(core.isPumpTripped(), "failpump must latch the pump trip");
        assertTrue(core.isManualFlowControl(), "failpump must lock manual flow control");
    }

    @Test
    @DisplayName("bypass should arm and disarm the auto-SCRAM bypass")
    void bypassTogglesBypass() throws Exception {
        assertFalse(core.isBypassAutoScram());

        String armed = invokeCommand("bypass");
        assertTrue(core.isBypassAutoScram(), "First bypass must arm ATWS mode");
        assertTrue(armed.contains("ARMED"));

        String cleared = invokeCommand("bypass");
        assertFalse(core.isBypassAutoScram(), "Second bypass must disarm");
        assertTrue(cleared.contains("disarmed"));
    }

    @Test
    @DisplayName("restart on a melted core must report damage, not restart")
    void restartRefusedWhenMelted() throws Exception {
        core.setMeltedDown(true);

        String output = invokeCommand("restart");

        assertTrue(output.contains("damaged"), "CLI must report refusal, was: " + output);
        assertTrue(core.isMeltedDown(), "Core must stay melted");
    }

    private String invokeCommand(String command) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            handleCommand.invoke(null, command, core, regulator, loop, logger);
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
