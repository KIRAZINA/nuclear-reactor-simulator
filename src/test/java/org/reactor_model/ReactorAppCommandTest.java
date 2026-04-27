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
import static org.mockito.Mockito.verify;

@DisplayName("ReactorApp Command Tests")
class ReactorAppCommandTest {

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
    @DisplayName("Start command should launch the simulation loop")
    void startCommandStartsLoop() throws Exception {
        invokeCommand("start");

        verify(loop).start();
    }

    @Test
    @DisplayName("Toggleauto should switch regulator state")
    void toggleAutoFlipsRegulatorState() throws Exception {
        assertTrue(regulator.isEnabled());

        invokeCommand("toggleauto");
        assertFalse(regulator.isEnabled());

        invokeCommand("toggleauto");
        assertTrue(regulator.isEnabled());
    }

    @Test
    @DisplayName("Increasepower should raise the target power by the requested delta")
    void increasePowerUpdatesTarget() throws Exception {
        invokeCommand("increasepower 250");

        assertEquals(350.0, regulator.getTargetPower(), 0.0001);
    }

    @Test
    @DisplayName("Failure command should drop coolant flow to zero")
    void failureCommandCutsCoolantFlow() throws Exception {
        core.setCoolantFlowRate(1.0);

        invokeCommand("failure");

        assertEquals(0.0, core.getCoolantFlowRate(), 0.0001);
    }

    @Test
    @DisplayName("Restart should only act when reactor is shutdown")
    void restartRequiresShutdownState() throws Exception {
        String runningOutput = invokeCommand("restart");
        assertTrue(runningOutput.contains("Reactor is operating normally"));

        core.setShutdown(true);
        invokeCommand("restart");

        assertFalse(core.isShutdown());
        assertEquals(0.01, core.getPower(), 0.001);
    }

    @Test
    @DisplayName("Invalid numeric input should be reported without changing the target")
    void invalidNumericInputIsRejected() throws Exception {
        double initialTarget = regulator.getTargetPower();

        String output = invokeCommand("increasepower nope");

        assertTrue(output.contains("Invalid number: nope"));
        assertEquals(initialTarget, regulator.getTargetPower(), 0.0001);
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
