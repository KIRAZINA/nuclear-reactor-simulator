package org.reactor_model;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ConsoleReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.regulation.SimplePIDStrategy;
import org.reactor_model.simulation.SimulationLoop;

import java.util.Scanner;

/**
 * Entry point for the Nuclear Reactor Simulator.
 * Provides a simple CLI for interacting with the simulation.
 */
public class ReactorApp {

    private static final String HELP_TEXT = """
            === NUCLEAR REACTOR SIMULATOR ===
            Available commands:
              start              - start simulation loop
              stop               - stop simulation loop
              increasepower X    - increase target power by X MW
              decreasepower X    - decrease target power by X MW
              toggleauto         - enable/disable automatic regulator
              demand             - inject artificial reactivity spike
              failure            - simulate coolant pump failure
              restart            - restart reactor after SCRAM
              help               - show this help message
              quit               - exit the program
            """;

    public static void main(String[] args) {

        var logger = new ConsoleReactorLogger();
        var core = new ReactorCore(logger);
        var strategy = new SimplePIDStrategy();
        var regulator = new AutoRegulator(core, logger, strategy);
        var demand = new PowerDemandSimulator(core, regulator, logger);
        var cooling = new CoolingSystem(core, logger);
        var loop = new SimulationLoop(core, regulator, demand, cooling);

        System.out.println(HELP_TEXT);

        Scanner sc = new Scanner(System.in);

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            handleCommand(line, core, regulator, loop, logger);
        }
    }

    private static void handleCommand(String input,
                                      ReactorCore core,
                                      AutoRegulator regulator,
                                      SimulationLoop loop,
                                      ConsoleReactorLogger logger) {

        String[] parts = input.split("\\s+");
        String command = parts[0];

        switch (command) {

            case "start" -> loop.start();

            case "stop" -> loop.stop();

            case "toggleauto" -> {
                boolean newState = !regulator.isEnabled();
                regulator.setEnabled(newState);
                logger.logDecision("User",
                        "Automatic regulator " + (newState ? "enabled" : "disabled"));
            }

            case "demand" -> {
                core.addReactivity(0.006);
                logger.logDecision("User", "Artificial reactivity spike injected");
            }

            case "failure" -> {
                core.setCoolantFlowRate(0.0);
                logger.logDecision("User", "Cooling pump failure simulated");
            }

            case "increasepower" -> {
                Double delta = parseDoubleSafe(parts, 1);
                if (delta == null) return;

                double newTarget = regulator.getTargetPower() + delta;
                regulator.setTargetPower(newTarget);

                logger.logDecision("User",
                        "Target power increased by " + delta + " → " + newTarget + " MW");
            }

            case "decreasepower" -> {
                Double delta = parseDoubleSafe(parts, 1);
                if (delta == null) return;

                double newTarget = regulator.getTargetPower() - delta;
                regulator.setTargetPower(newTarget);

                logger.logDecision("User",
                        "Target power decreased by " + delta + " → " + newTarget + " MW");
            }

            case "restart" -> {
                if (core.isShutdown()) {
                    core.restart();
                    logger.logDecision("User",
                            "Reactor restarted after emergency shutdown");
                } else {
                    System.out.println("Reactor is operating normally. Use 'stop' to halt simulation first.");
                }
            }

            case "help" -> System.out.println(HELP_TEXT);

            case "quit" -> {
                loop.stop();
                System.exit(0);
            }

            default -> System.out.println("Unknown command. Type 'help' for a list of commands.");
        }
    }

    private static Double parseDoubleSafe(String[] parts, int index) {
        if (parts.length <= index) {
            System.out.println("Missing numeric argument.");
            return null;
        }

        try {
            return Double.parseDouble(parts[index]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number: " + parts[index]);
            return null;
        }
    }
}
