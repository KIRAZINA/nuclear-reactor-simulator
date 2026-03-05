package org.reactor_model.core;

import org.reactor_model.event.ReactorEventBus;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.MathUtil;

public class ReactorCore {

    private double power = 0.01;
    private double temperature = 300.0;
    private double coolantFlowRate = 1.0;
    private boolean manualFlowControl = false; // When true, cooling system won't override
    private double controlRodPosition = 0.5;
    private double reactivity = 0.0;
    private double externalReactivity = 0.0;
    private boolean shutdown = false;
    private int overheatTicks = 0;

    public static final double MAX_SAFE_POWER = 8000.0;
    public static final int OVERHEAT_MAX_TICKS = 100;

    private static final double BASE_REACTIVITY = 0.0150;
    private static final double HEAT_CAPACITY = 2000.0;
    private static final double HEAT_TRANSFER_COEFF = 26.0;
    private static final double COOLANT_TEMP = 290.0;
    private static final double TEMP_COEFF = -0.00004;
    private static final double ROD_EFFECT = -0.026;
    private static final double CRITICAL_TEMP = 750.0;
    private static final double STARTUP_BOOST = 0.0;
    private static final double OVERHEAT_THRESHOLD = 680.0;
    private static final double MIN_POWER = 0.01;

    // Stabilization thresholds
    private static final double REACTIVITY_WARNING_THRESHOLD = 0.012;
    private static final double REACTIVITY_DAMPING_STEP = 0.0015;
    private static final double POWER_OVERSHOOT_THRESHOLD = 1.03;
    private static final double POWER_UNDERSHOOT_FACTOR = 0.75;

    // Power jump protection
    private static final double POWER_JUMP_LIMIT = 1.8;
    private static final double POWER_JUMP_MIN_ABSOLUTE = 1200.0;

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
            handleShutdownMode(dt, now);
            eventBus.publish();
            return;
        }

        updateReactivity(targetPower);
        applyStabilizationAroundTarget(targetPower);

        // Power evolution
        // Use a blended approach: below 500 MW, treat power as 500 MW for reactivity calculations.
        // This gives a linear 5-10 MW/s startup boost instead of a boring 10-minute exponential wait.
        double effectivePower = Math.max(power, 500.0);
        power += reactivity * effectivePower * dt;
        
        if (power < MIN_POWER) {
            power = MIN_POWER;
        }

        if (detectDangerousPowerJump(previousPower)) {
            emergencyShutdown("A sharp jump in power! Emergency stop.");
            return;
        }

        // Thermal evolution
        double coolingPower = coolantFlowRate * HEAT_TRANSFER_COEFF * (temperature - COOLANT_TEMP);
        temperature += (power - coolingPower) / HEAT_CAPACITY * dt;

        handleOverheating(now);
        checkScramCondition();
        checkHighReactivityWarning(now);

        eventBus.publish();
    }

    private void handleShutdownMode(double dt, long now) {
        power = 0.0;
        reactivity = 0.0;

        double coolingPower = coolantFlowRate * HEAT_TRANSFER_COEFF * (temperature - COOLANT_TEMP);
        temperature += (power - coolingPower) / HEAT_CAPACITY * dt;

        if (now - lastShutdownStateLog >= SHUTDOWN_LOG_INTERVAL) {
            logger.logState(power, temperature, coolantFlowRate, controlRodPosition, reactivity);
            logger.logWarning("The reactor is in SCRAM mode. Cooling of the core is ongoing.");
            lastShutdownStateLog = now;
        }
    }

    private void updateReactivity(double targetPower) {
        double tempFeedback = TEMP_COEFF * (temperature - COOLANT_TEMP);
        double rodContribution = ROD_EFFECT * (1 - controlRodPosition);
        reactivity = BASE_REACTIVITY + tempFeedback + rodContribution + externalReactivity;
    }

    private void applyStabilizationAroundTarget(double targetPower) {
        // If we overshoot the target significantly → add extra negative reactivity
        if (power > targetPower * POWER_OVERSHOOT_THRESHOLD) {
            reactivity -= REACTIVITY_DAMPING_STEP;
        }
        
        // If we fall far below target and reactivity is still negative → reduce damping slightly
        if (reactivity < -0.001 && power < targetPower * POWER_UNDERSHOOT_FACTOR) {
            reactivity += REACTIVITY_DAMPING_STEP * 0.5;
        }
    }

    private boolean detectDangerousPowerJump(double previousPower) {
        if (previousPower <= 0) {
            return false;
        }
        double powerChange = (power - previousPower) / previousPower;
        return powerChange > POWER_JUMP_LIMIT && power > POWER_JUMP_MIN_ABSOLUTE;
    }

    private void handleOverheating(long now) {
        if (temperature > OVERHEAT_THRESHOLD) {
            overheatTicks++;
            if (overheatTicks > OVERHEAT_MAX_TICKS) {
                if (now - lastOverheatWarning > WARNING_COOLDOWN_MS) {
                    logger.logWarning("Prolonged overheating detected. Protective mechanisms may adjust power targets.");
                    lastOverheatWarning = now;
                }
                overheatTicks = 0;
            }
        } else {
            overheatTicks = 0;
        }
    }

    private void checkScramCondition() {
        if (temperature > CRITICAL_TEMP) {
            emergencyShutdown("CRITICAL TEMPERATURE! SCRAM.");
        }
    }

    private void checkHighReactivityWarning(long now) {
        if (reactivity > REACTIVITY_WARNING_THRESHOLD &&
                now - lastHighReactivityWarning > WARNING_COOLDOWN_MS) {
            logger.logWarning("High reactivity detected. Enhanced cooling or control rod insertion is recommended.");
            lastHighReactivityWarning = now;
        }
    }

    private void emergencyShutdown(String reason) {
        shutdown = true;
        power = 0.0;
        controlRodPosition = 0.0;
        logger.logWarning(reason);
    }

    public void restart() {
        shutdown = false;
        power = MIN_POWER;
        temperature = 300.0;
        coolantFlowRate = 1.0;
        controlRodPosition = 0.5;
        reactivity = 0.0;
        externalReactivity = 0.0;
        overheatTicks = 0;
        lastHighReactivityWarning = 0;
        lastOverheatWarning = 0;
        lastShutdownStateLog = 0;
        logger.logDecision("System", "Reactor restarted.");
    }

    public boolean isShutdown() {
        return shutdown;
    }
    
    // Public method for testing
    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
    }

    public int getOverheatTicks() {
        return overheatTicks;
    }

    public void resetOverheatTicks() {
        overheatTicks = 0;
    }

    public void logCurrentState() {
        logger.logState(power, temperature, coolantFlowRate, controlRodPosition, reactivity);
    }

    public void setCoolantFlowRate(double rate) {
        this.coolantFlowRate = MathUtil.clamp(rate, 0.0, 1.0);
    }
    
    public boolean isManualFlowControl() {
        return manualFlowControl;
    }
    
    public void setManualFlowControl(boolean manual) {
        this.manualFlowControl = manual;
    }

    public void setControlRodPosition(double pos) {
        this.controlRodPosition = MathUtil.clamp(pos, 0.0, 1.0);
    }

    public double getPower() {
        return power;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getCoolantFlowRate() {
        return coolantFlowRate;
    }

    public double getControlRodPosition() {
        return controlRodPosition;
    }

    public double getReactivity() {
        return reactivity;
    }

    public void addReactivity(double delta) {
        this.externalReactivity += delta;
    }
}
