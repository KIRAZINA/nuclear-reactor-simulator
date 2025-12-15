package org.reactor_model.logger;

public class ConsoleReactorLogger implements ReactorLogger {
    @Override
    public void logState(double power, double temperature, double coolantFlowRate, double controlRodPosition, double reactivity) {
        System.out.printf("%s [STATE] Power: %.2f MW, Temp: %.2f C, Coolant: %.2f, Rods: %.2f%%, Reactivity: %.4f%n",
                getTimestamp(), power, temperature, coolantFlowRate, controlRodPosition, reactivity);
    }

    @Override
    public void logWarning(String message) {
        System.out.printf("%s [WARNING] %s%n", getTimestamp(), message);
    }

    @Override
    public void logDecision(String subsystem, String decision) {
        System.out.printf("%s [%s] Decision: %s%n", getTimestamp(), subsystem.toUpperCase(), decision);
    }
}