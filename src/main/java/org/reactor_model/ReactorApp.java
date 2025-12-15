// src/main/java/org/reactor_model/ReactorApp.java
package org.reactor_model;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ConsoleReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.regulation.SimplePIDStrategy;
import org.reactor_model.simulation.SimulationLoop;

import java.util.Scanner;

public class ReactorApp {
    public static void main(String[] args) {
        var logger = new ConsoleReactorLogger();
        var core = new ReactorCore(logger);
        var strategy = new SimplePIDStrategy();
        var regulator = new AutoRegulator(core, logger, strategy);
        var demand = new PowerDemandSimulator(core, regulator, logger);
        var cooling = new CoolingSystem(core, logger);
        var loop = new SimulationLoop(core, regulator, demand, cooling);

        System.out.println("=== NUCLEAR REACTOR SIMULATOR ===\n" +
                "Commands: start | stop | increasepower 100 | decreasepower 50 | toggleauto | demand | failure | quit");

        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String cmd = sc.nextLine().trim();
            switch (cmd.split(" ")[0]) {
                case "start" -> loop.start();
                case "stop" -> loop.stop();
                case "toggleauto" -> {
                    boolean newState = !regulator.isEnabled();
                    regulator.setEnabled(newState);
                    logger.logDecision("User", "Automatic regulator " + (newState ? "enabled" : "disabled"));
                }
                case "demand" -> {
                    core.addReactivity(0.006);
                    logger.logDecision("User", "Artificial jump in reactivity");
                }
                case "failure" -> {
                    core.setCoolantFlowRate(0.0);
                    logger.logDecision("User", "Simulation of cooling pump failure");
                }
                case "quit" -> {
                    loop.stop();
                    System.exit(0);
                }
                case "increasepower" -> {
                    double delta = Double.parseDouble(cmd.split(" ")[1]);
                    double newTarget = regulator.getTargetPower() + delta;
                    regulator.setTargetPower(newTarget);
                    logger.logDecision("User", "Increase in target capacity in " + delta + " → " + newTarget + " MW");
                }
                case "decreasepower" -> {
                    double delta = Double.parseDouble(cmd.split(" ")[1]);
                    double newTarget = regulator.getTargetPower() - delta;
                    regulator.setTargetPower(newTarget);
                    logger.logDecision("User", "Reduction in target capacity in " + delta + " → " + newTarget + " MW");
                }
                case "restart" -> {
                    core.restart();
                    logger.logDecision("User", "The reactor was restarted after an emergency shutdown.");
                }
                default -> System.out.println("Unknown command. Please try again.");
            }
        }
    }
}