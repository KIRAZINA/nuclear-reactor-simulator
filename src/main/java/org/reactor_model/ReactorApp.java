package org.reactor_model;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.logger.ConsoleReactorLogger;
import org.reactor_model.logger.CompositeReactorLogger;
import org.reactor_model.logger.FileReactorLogger;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.simulation.SimulationLoop;
import org.reactor_model.ui.EventLogPanel;
import org.reactor_model.ui.ReactorDashboard;
import org.reactor_model.ui.ReactorUIAdapter;
import org.reactor_model.ui.UiReactorLogger;
import org.reactor_model.util.ConfigManager;
import org.reactor_model.util.StatePersistenceUtil;

import javax.swing.*;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
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
              demand             - inject artificial reactivity spike (alias: spike)
              spike              - inject artificial reactivity spike (alias: demand)
              failure            - simulate coolant pump failure
              failpump           - trip the main coolant pump (5 s coastdown, locked)
              bypass             - toggle auto-SCRAM bypass (ATWS testing)
              restart            - restart reactor after SCRAM
              save <filepath>    - save reactor state to file
              load <filepath>    - load reactor state from file (loop paused during load)
              speed <1|2|5|10>   - set simulation speed multiplier (1.0-10.0)
              help               - show this help message
              quit               - exit the program
            """;

    /** Shared file logger for post-mortem analysis; null when file logging is unavailable. */
    private static volatile FileReactorLogger fileLogger;

    public static void main(String[] args) {
        // External configuration: reactor.properties in the working directory, else defaults.
        if (ConfigManager.isConfigFileLoaded()) {
            System.out.println("[Config] Loaded configuration from '"
                    + ConfigManager.getConfigPath().toAbsolutePath() + "'.");
        } else {
            System.out.println("[Config] No '" + ConfigManager.CONFIG_FILE
                    + "' found — using default simulation parameters.");
        }

        // Persistent file logging for post-mortem analysis.
        try {
            fileLogger = new FileReactorLogger();
            System.out.println("[Logging] Writing operational logs to '"
                    + fileLogger.getLogFile().toAbsolutePath() + "'.");
        } catch (RuntimeException e) {
            System.err.println("[Logging] File logging disabled: " + e.getMessage());
            fileLogger = null;
        }
        if (fileLogger != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    fileLogger.close();
                } catch (Exception e) {
                    System.err.println("[Logging] Failed to close file logger: " + e.getMessage());
                }
            }, "ReactorFileLoggerShutdown"));
        }

        // Global safety net: prevent silent thread death anywhere in the app.
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[Fatal] Uncaught exception in thread '" + thread.getName() + "': " + throwable);
            throwable.printStackTrace(System.err);
            if (!GraphicsEnvironment.isHeadless()) {
                try {
                    JOptionPane.showMessageDialog(null,
                            "Fatal error in thread '" + thread.getName() + "':\n" + throwable,
                            "Reactor Simulator — Fatal Error",
                            JOptionPane.ERROR_MESSAGE);
                } catch (Exception dialogFailure) {
                    // GUI may already be torn down; fall back to console logging.
                    System.err.println("[Fatal] Could not display error dialog: " + dialogFailure);
                }
            }
        });
        if (args.length > 0 && args[0].equals("--cli")) {
            runCli();
        } else {
            runGui();
        }
    }

    // ---- GUI mode -------------------------------------------------------

    private static void runGui() {
        // Build simulation components (UI events + persistent file log).
        UiReactorLogger uiLogger   = new UiReactorLogger();
        ReactorLogger logger = withFileLogging(uiLogger);
        ReactorCore     core     = new ReactorCore(logger);
        AutoRegulator   regulator = new AutoRegulator(core, logger);
        PowerDemandSimulator demand = new PowerDemandSimulator(core, regulator, logger);
        CoolingSystem   cooling  = new CoolingSystem(core, logger);
        SimulationLoop  loop     = new SimulationLoop(core, regulator, demand, cooling);

        ReactorUIAdapter adapter  = new ReactorUIAdapter(core, regulator, loop, demand);
        EventLogPanel    eventLog = new EventLogPanel(uiLogger.getQueue());

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
        var console   = new ConsoleReactorLogger();
        ReactorLogger logger = withFileLogging(console);
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

    /**
     * Combines a primary logger with the shared file logger when available.
     * Falls back to the primary logger alone if file logging is disabled.
     */
    private static ReactorLogger withFileLogging(ReactorLogger primary) {
        if (fileLogger != null) {
            return new CompositeReactorLogger(primary, fileLogger);
        }
        return primary;
    }

    /**
     * Backward-compatible command entry point used by tests.
     * Delegates to the {@link ReactorLogger} overload.
     */
    private static void handleCommand(String input,
                                      ReactorCore core,
                                      AutoRegulator regulator,
                                      SimulationLoop loop,
                                      ConsoleReactorLogger logger) {
        handleCommand(input, core, regulator, loop, (ReactorLogger) logger);
    }

    private static void handleCommand(String input,
                                      ReactorCore core,
                                      AutoRegulator regulator,
                                      SimulationLoop loop,
                                      ReactorLogger logger) {
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

            case "demand", "spike" -> {
                core.addReactivity(0.006);
                logger.logDecision("User", "Artificial reactivity spike injected");
            }

            case "failure" -> {
                core.setCoolantFlowRate(0.0);
                logger.logDecision("User", "Cooling pump failure simulated");
            }

            case "failpump" -> {
                core.failCoolantPump();
                System.out.println("Main coolant pump tripped: flow coasting down over 5 s (manual lock).");
                logger.logDecision("User", "Main coolant pump tripped (LOFA drill)");
            }

            case "bypass" -> {
                boolean armed = !core.isBypassAutoScram();
                core.setBypassAutoScram(armed);
                System.out.println("Auto-SCRAM bypass " + (armed ? "ARMED (ATWS mode)" : "disarmed") + ".");
                logger.logDecision("User", "Auto-SCRAM bypass " + (armed ? "ARMED" : "disarmed"));
            }

            case "increasepower" -> {
                Double delta = parseDoubleSafe(parts, 1, "increasepower");
                if (delta == null) return;
                double newTarget = regulator.getTargetPower() + delta;
                if (newTarget < 0) {
                    System.out.println("Warning: Target power cannot be negative, setting to 0.0 MW.");
                    newTarget = 0.0;
                }
                regulator.setTargetPower(newTarget);
                logger.logDecision("User",
                        "Target power increased by " + delta + " → " + newTarget + " MW");
            }

            case "decreasepower" -> {
                Double delta = parseDoubleSafe(parts, 1, "decreasepower");
                if (delta == null) return;
                double newTarget = regulator.getTargetPower() - delta;
                if (newTarget < 0) {
                    System.out.println("Warning: Target power cannot be negative, setting to 0.0 MW.");
                    newTarget = 0.0;
                }
                regulator.setTargetPower(newTarget);
                logger.logDecision("User",
                        "Target power decreased by " + delta + " → " + newTarget + " MW");
            }

            case "restart" -> {
                if (core.isMeltedDown()) {
                    System.out.println("Cannot restart: Core is damaged.");
                } else if (core.isShutdown()) {
                    core.restart();
                    logger.logDecision("User", "Reactor restarted after emergency shutdown");
                } else {
                    System.out.println("Reactor is operating normally. Use 'stop' to halt simulation first.");
                }
            }

            case "save" -> {
                if (parts.length < 2) {
                    System.out.println("Error: 'save' requires a file path argument. Usage: save <filepath>");
                    return;
                }
                try {
                    StatePersistenceUtil.saveState(core, parts[1]);
                    System.out.println("Reactor state saved to '" + parts[1] + "'.");
                    logger.logDecision("User", "Reactor state saved to '" + parts[1] + "'");
                } catch (Exception e) {
                    System.out.println("Error saving state: " + e.getMessage());
                }
            }

            case "load" -> {
                if (parts.length < 2) {
                    System.out.println("Error: 'load' requires a file path argument. Usage: load <filepath>");
                    return;
                }
                // Stop-the-world load: halt physics so the core is not mutated mid-restore.
                boolean wasRunning = loop.isRunning();
                if (wasRunning) {
                    loop.stop();
                }
                try {
                    StatePersistenceUtil.loadState(core, parts[1]);
                    System.out.println("Reactor state loaded from '" + parts[1] + "'.");
                    logger.logDecision("User", "Reactor state loaded from '" + parts[1] + "'");
                    if (wasRunning) {
                        loop.start();
                    }
                } catch (Exception e) {
                    System.out.println("Error loading state: " + e.getMessage());
                    System.err.println("[ReactorApp] Load failed; loop left stopped for safety.");
                }
            }

            case "speed" -> {
                Double multiplier = parseDoubleSafe(parts, 1, "speed");
                if (multiplier == null) return;
                loop.setSpeedMultiplier(multiplier);
                System.out.println("Simulation speed set to " + loop.getSpeedMultiplier() + "x.");
                logger.logDecision("User", "Simulation speed set to " + loop.getSpeedMultiplier() + "x");
            }

            case "help" -> System.out.println(HELP_TEXT);

            case "quit" -> {
                loop.stop();
                System.exit(0);
            }

            default -> System.out.println("Unknown command: " + command + ". Type 'help' for available commands.");
        }
    }

    private static Double parseDoubleSafe(String[] parts, int index, String commandName) {
        if (parts.length <= index) {
            System.out.println("Error: '" + commandName + "' requires a numeric argument.");
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
