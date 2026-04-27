package org.reactor_model;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ConsoleReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.regulation.PrecisionPIDStrategy;
import org.reactor_model.simulation.SimulationLoop;
import org.reactor_model.ui.EventLogPanel;
import org.reactor_model.ui.ReactorDashboard;
import org.reactor_model.ui.ReactorUIAdapter;
import org.reactor_model.ui.UiReactorLogger;

import javax.swing.*;
import java.awt.Color;
import java.util.Scanner;

/**
 * Entry point for the Nuclear Reactor Simulator.
 * <p>
 * Default mode: launches the graphical dashboard with auto-started simulation.
 * CLI mode:     pass {@code --cli} as first argument to use the original text interface.
 */
public class ReactorApp {

    private static final String HELP_TEXT = """
            === NUCLEAR REACTOR SIMULATOR (CLI) ===
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
        if (args.length > 0 && args[0].equals("--cli")) {
            runCli();
        } else {
            runGui();
        }
    }

    // ---- GUI mode -------------------------------------------------------

    private static void runGui() {
        // Build simulation components
        UiReactorLogger logger   = new UiReactorLogger();
        ReactorCore     core     = new ReactorCore(logger);
        AutoRegulator   regulator = new AutoRegulator(core, logger);
        PowerDemandSimulator demand = new PowerDemandSimulator(core, regulator, logger);
        CoolingSystem   cooling  = new CoolingSystem(core, logger);
        SimulationLoop  loop     = new SimulationLoop(core, regulator, demand, cooling);

        ReactorUIAdapter adapter  = new ReactorUIAdapter(core, regulator, loop, demand);
        EventLogPanel    eventLog = new EventLogPanel(logger.getQueue());

        SwingUtilities.invokeLater(() -> {
            applyDarkLookAndFeel();
            ReactorDashboard dashboard = new ReactorDashboard(adapter, eventLog);
            dashboard.setVisible(true);

            // Auto-start the simulation with auto-regulator enabled
            // Disturbances are DISABLED by default for stable operation
            regulator.setEnabled(true);
            adapter.startLoop();
        });
    }

    // ---- CLI mode -------------------------------------------------------

    private static void runCli() {
        var logger    = new ConsoleReactorLogger();
        var core      = new ReactorCore(logger);
        var regulator = new AutoRegulator(core, logger);
        var demand    = new PowerDemandSimulator(core, regulator, logger);
        var cooling   = new CoolingSystem(core, logger);
        var loop      = new SimulationLoop(core, regulator, demand, cooling);

        System.out.println(HELP_TEXT);

        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            handleCommand(line, core, regulator, loop, logger);
        }
    }

    private static void handleCommand(String input,
                                      ReactorCore core,
                                      AutoRegulator regulator,
                                      SimulationLoop loop,
                                      ConsoleReactorLogger logger) {
        String[] parts   = input.split("\\s+");
        String   command = parts[0];

        switch (command) {
            case "start"    -> loop.start();
            case "stop"     -> loop.stop();

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
                    logger.logDecision("User", "Reactor restarted after emergency shutdown");
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

    // ---- Helpers --------------------------------------------------------

    private static void applyDarkLookAndFeel() {
        try {
            // Try Nimbus first for a cleaner dark base
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    // Override Nimbus base colors
                    UIManager.put("nimbusBase",             new Color(18, 22, 38));
                    UIManager.put("nimbusBlueGrey",         new Color(30, 35, 55));
                    UIManager.put("control",                new Color(22, 27, 44));
                    UIManager.put("text",                   new Color(200, 210, 230));
                    UIManager.put("nimbusLightBackground",  new Color(14, 17, 28));
                    UIManager.put("nimbusBorder",           new Color(50, 60, 90));
                    UIManager.put("nimbusSelectionBackground", new Color(0, 120, 80));
                    return;
                }
            }
            // Fallback to system L&F
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore – default L&F will be used
        }
    }
}
