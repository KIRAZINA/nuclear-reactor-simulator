// src/main/java/org/reactor_model/core/ReactorCore.java
package org.reactor_model.core;

import org.reactor_model.event.ReactorEventBus;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.MathUtil;

public class ReactorCore {

    private double power = 0.01;
    private double temperature = 300.0;
    private double coolantFlowRate = 1.0;
    private double controlRodPosition = 0.5;
    private double reactivity = 0.0;
    private boolean shutdown = false;
    private int overheatTicks = 0;

    public static final double MAX_SAFE_POWER = 8000.0;
    private static final double BASE_REACTIVITY = 0.007;
    private static final double HEAT_CAPACITY = 1000.0;
    private static final double HEAT_TRANSFER_COEFF = 12.0;
    private static final double COOLANT_TEMP = 300.0;
    private static final double TEMP_COEFF = -0.00003;
    private static final double ROD_EFFECT = -0.015;
    private static final double CRITICAL_TEMP = 700.0;
    private static final double STARTUP_BOOST = 0.003;
    private static final double OVERHEAT_THRESHOLD = 650.0;
    public static final int OVERHEAT_MAX_TICKS = 100;

    private final ReactorLogger logger;
    public final ReactorEventBus eventBus = new ReactorEventBus();

    // Rate limiting
    private long lastHighReactivityWarning = 0;
    private long lastOverheatWarning = 0;
    private long lastShutdownStateLog = 0;
    private static final long WARNING_COOLDOWN_MS = 8000L;
    private static final long SHUTDOWN_LOG_INTERVAL = 30000L;

    public ReactorCore(ReactorLogger logger) {
        this.logger = logger;
    }

    public void update(double dt, double targetPower, double previousPower) {
        long now = System.currentTimeMillis();

        if (shutdown) {
            power = 0.0;
            reactivity = 0.0;

            double coolingPower = coolantFlowRate * HEAT_TRANSFER_COEFF * (temperature - COOLANT_TEMP);
            temperature += (power - coolingPower) / HEAT_CAPACITY * dt;

            if (now - lastShutdownStateLog >= SHUTDOWN_LOG_INTERVAL) {
                logger.logState(power, temperature, coolantFlowRate, controlRodPosition, reactivity);
                logger.logWarning("The reactor is in SCRAM mode. Cooling of the core is ongoing.");
                lastShutdownStateLog = now;
            }

            eventBus.publish();
            return;
        }

        double tempFeedback = TEMP_COEFF * (temperature - COOLANT_TEMP);
        double rodContribution = ROD_EFFECT * (1 - controlRodPosition);
        reactivity = BASE_REACTIVITY + tempFeedback + rodContribution;

        if (power < targetPower && targetPower <= MAX_SAFE_POWER) {
            double boostFactor = MathUtil.clamp((targetPower - power) / targetPower, 0.0, 1.0);
            reactivity += STARTUP_BOOST * 2 * boostFactor;
        }

        if (reactivity < 0 && power > targetPower * 0.8) {
            reactivity += 0.001;
        }

        if (power > targetPower * 1.02) {
            reactivity -= 0.001;
        }

        double powerChange = previousPower > 0 ? (power - previousPower) / previousPower : 0;
        if (powerChange > 2.0 && power > 1000.0) {
            emergencyShutdown("A sharp jump in power! Emergency stop.");
            return;
        }

        power += reactivity * power * dt;
        if (power < 0.01) power = 0.01;

        double coolingPower = coolantFlowRate * HEAT_TRANSFER_COEFF * (temperature - COOLANT_TEMP);
        temperature += (power - coolingPower) / HEAT_CAPACITY * dt;

        if (temperature > OVERHEAT_THRESHOLD) {
            overheatTicks++;
            if (overheatTicks > OVERHEAT_MAX_TICKS) {
                if (now - lastOverheatWarning > WARNING_COOLDOWN_MS) {
                    logger.logWarning("Prolonged overheating! Automatic target reduction.");
                    lastOverheatWarning = now;
                }
                overheatTicks = 0;
            }
        } else {
            overheatTicks = 0;
        }

        // SCRAM
        if (temperature > CRITICAL_TEMP) {
            emergencyShutdown("CRITICAL TEMPERATURE! SCRAM.");
        }

        if (reactivity > 0.01) {
            if (now - lastHighReactivityWarning > WARNING_COOLDOWN_MS) {
                logger.logWarning("High reactivity! Enhanced cooling is recommended.");
                lastHighReactivityWarning = now;
            }
        }

        eventBus.publish();
    }

    private void emergencyShutdown(String reason) {
        shutdown = true;
        power = 0.0;
        controlRodPosition = 0.0;
        logger.logWarning(reason);
    }

    public void restart() {
        shutdown = false;
        power = 0.01;
        temperature = 300.0;
        overheatTicks = 0;
        lastHighReactivityWarning = 0;
        lastOverheatWarning = 0;
        lastShutdownStateLog = 0;
        logger.logDecision("System", "Reactor restarted.");
    }

    public boolean isShutdown() { return shutdown; }
    public int getOverheatTicks() { return overheatTicks; }
    public void resetOverheatTicks() { overheatTicks = 0; }

    public void logCurrentState() {
        logger.logState(power, temperature, coolantFlowRate, controlRodPosition, reactivity);
    }

    public void setCoolantFlowRate(double rate) {
        this.coolantFlowRate = MathUtil.clamp(rate, 0.0, 1.0);
    }

    public void setControlRodPosition(double pos) {
        this.controlRodPosition = MathUtil.clamp(pos, 0.0, 1.0);
    }

    public double getPower() { return power; }
    public double getTemperature() { return temperature; }
    public double getCoolantFlowRate() { return coolantFlowRate; }
    public double getControlRodPosition() { return controlRodPosition; }
    public double getReactivity() { return reactivity; }
    public void addReactivity(double delta) { this.reactivity += delta; }
}