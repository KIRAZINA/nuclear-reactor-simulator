package org.reactor_model.core;

import org.reactor_model.event.ReactorEventBus;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.MathUtil;

/**
 * Improved ReactorCore with point-kinetics model and realistic PWR constants.
 */
public class ReactorCore {

    // Physical state
    private final PointKineticsSolver kinetics;
    private double temperature = 300.0; // K
    private double coolantFlowRate = 1.0; // Normalized 0-1
    private boolean manualFlowControl = false;
    private double controlRodPosition = 0.5; // 0=fully inserted, 1=fully withdrawn
    private double reactivity = 0.0; // $
    private double externalReactivity = 0.0; // $
    private boolean shutdown = false;
    private int overheatTicks = 0;

    // PWR-like constants (realistic values)
    public static final double MAX_SAFE_POWER = 3411.0; // MWt (PWR nominal)
    public static final int OVERHEAT_MAX_TICKS = 100;

    // Reactivity coefficients
    private static final double BASE_REACTIVITY = 0.0; // $ (cold, balanced state)
    private static final double TEMP_COEFF = -0.00002; // $/K (Doppler + moderator)
    private static final double ROD_WORTH = 0.02; // $ total rod worth

    // Thermal-hydraulics
    private static final double HEAT_CAPACITY = 1.5e8; // J/K (core thermal mass)
    private static final double HEAT_TRANSFER_COEFF = 5e6; // W/K (effective HTC)
    private static final double COOLANT_TEMP = 290.0; // K (inlet temperature)
    private static final double FUEL_TEMP_COEFF = -2e-5; // $/K (Doppler effect)

    // Safety thresholds
    private static final double CRITICAL_TEMP = 1200.0; // K (fuel melting)
    private static final double OVERHEAT_THRESHOLD = 800.0; // K
    private static final double MIN_POWER = 0.01; // MWt

    // Decay heat (simplified model)
    private static final double DECAY_HEAT_FRACTION = 0.05; // 5% of full power
    private static final double DECAY_TIME_CONSTANT = 100.0; // s

    private final ReactorLogger logger;
    public final ReactorEventBus eventBus = new ReactorEventBus();

    // Rate limiting for logging
    private long lastHighReactivityWarning = 0;
    private long lastOverheatWarning = 0;
    private long lastShutdownStateLog = 0;
    private static final long WARNING_COOLDOWN_MS = 8000L;
    private static final long SHUTDOWN_LOG_INTERVAL = 30000L;

    public ReactorCore(ReactorLogger logger) {
        this.logger = logger;
        this.kinetics = new PointKineticsSolver(MIN_POWER);
    }

    /**
     * Improved update method with proper physics coupling.
     * Order: reactivity calculation -> neutronics -> thermal-hydraulics -> protections
     */
    public void update(double dt) {
        long now = System.currentTimeMillis();

        if (shutdown) {
            handleShutdownMode(dt, now);
            eventBus.publish();
            return;
        }

        // 1. Calculate total reactivity
        updateReactivity();

        // 2. Advance neutronics (point kinetics)
        double oldPower = kinetics.getPower();
        double newPower = kinetics.advance(reactivity, dt);

        // 3. Advance thermal-hydraulics
        updateThermalHydraulics(dt, newPower);

        // 4. Apply protections
        handleOverheating(now);
        checkScramCondition(now);
        checkHighReactivityWarning(now);

        eventBus.publish();
    }

    private void updateReactivity() {
        // Base reactivity + temperature feedback + rod effect + external
        double tempFeedback = TEMP_COEFF * (temperature - COOLANT_TEMP);
        double fuelFeedback = FUEL_TEMP_COEFF * (temperature - COOLANT_TEMP); // Doppler
        // Control rod effect: partially withdrawn rods add positive reactivity
        double rodEffect = ROD_WORTH * (controlRodPosition - 0.5);

        reactivity = BASE_REACTIVITY + tempFeedback + fuelFeedback + rodEffect + externalReactivity;
    }

    private void updateThermalHydraulics(double dt, double power) {
        // Heat generation
        double heatGeneration = power * 1e6; // Convert MW to W

        // Heat removal
        double deltaT = temperature - COOLANT_TEMP;
        double heatRemoval = coolantFlowRate * HEAT_TRANSFER_COEFF * deltaT;

        // Temperature evolution
        temperature += (heatGeneration - heatRemoval) / HEAT_CAPACITY * dt;

        // Prevent unrealistic temperatures
        temperature = MathUtil.clamp(temperature, 290.0, 1500.0);
    }

    private void handleShutdownMode(double dt, long now) {
        // SCRAM: rods fully inserted
        controlRodPosition = 0.0;
        reactivity = BASE_REACTIVITY - ROD_WORTH; // Large negative reactivity

        // Decay heat (simplified exponential decay)
        double decayPower = kinetics.getPower() * DECAY_HEAT_FRACTION *
                           Math.exp(-dt / DECAY_TIME_CONSTANT);
        kinetics.setPower(decayPower);

        // Continue thermal evolution with decay heat
        updateThermalHydraulics(dt, decayPower);

        if (now - lastShutdownStateLog >= SHUTDOWN_LOG_INTERVAL) {
            logger.logState(kinetics.getPower(), temperature, coolantFlowRate, controlRodPosition, reactivity);
            logger.logWarning("Reactor is in SCRAM mode. Decay heat removal ongoing.");
            lastShutdownStateLog = now;
        }
    }

    private void handleOverheating(long now) {
        if (temperature > OVERHEAT_THRESHOLD) {
            overheatTicks++;
            if (overheatTicks > OVERHEAT_MAX_TICKS) {
                if (now - lastOverheatWarning > WARNING_COOLDOWN_MS) {
                    logger.logWarning("Prolonged overheating detected. Automatic power reduction recommended.");
                    lastOverheatWarning = now;
                }
                overheatTicks = 0;
            }
        } else {
            overheatTicks = 0;
        }
    }

    private void checkScramCondition(long now) {
        if (temperature > CRITICAL_TEMP || kinetics.getPower() > MAX_SAFE_POWER * 1.1) {
            emergencyShutdown("CRITICAL CONDITION! Automatic SCRAM initiated.");
        }
    }

    private void checkHighReactivityWarning(long now) {
        if (Math.abs(reactivity) > 0.01 && now - lastHighReactivityWarning > WARNING_COOLDOWN_MS) {
            logger.logWarning(String.format("High reactivity: %.4f $", reactivity));
            lastHighReactivityWarning = now;
        }
    }

    private void emergencyShutdown(String reason) {
        shutdown = true;
        kinetics.scram();
        logger.logWarning("EMERGENCY SHUTDOWN: " + reason);
    }

    // Public API methods
    public double getPower() { return kinetics.getPower(); }
    public double getTemperature() { return temperature; }
    public double getCoolantFlowRate() { return coolantFlowRate; }
    public double getControlRodPosition() { return controlRodPosition; }
    public double getReactivity() { return reactivity; }
    public boolean isShutdown() { return shutdown; }
    public boolean isManualFlowControl() { return manualFlowControl; }

    public void setCoolantFlowRate(double rate) {
        this.coolantFlowRate = MathUtil.clamp(rate, 0.0, 1.0);
    }

    public void setControlRodPosition(double position) {
        this.controlRodPosition = MathUtil.clamp(position, 0.0, 1.0);
    }

    public void setManualFlowControl(boolean manualFlowControl) {
        this.manualFlowControl = manualFlowControl;
    }

    public void addReactivity(double delta) {
        this.externalReactivity += delta;
    }

    public void update(double dt, double targetPower, double currentPower) {
        update(dt);
    }

    public void restart() {
        shutdown = false;
        temperature = 300.0;
        kinetics.setPower(MIN_POWER);
        externalReactivity = 0.0;
        controlRodPosition = 0.5;
        reactivity = 0.0;
        logger.logDecision("System", "Reactor restarted from cold condition");
    }

    public void logCurrentState() {
        logger.logState(getPower(), temperature, coolantFlowRate, controlRodPosition, reactivity);
    }

    public int getOverheatTicks() {
        return overheatTicks;
    }

    public void resetOverheatTicks() {
        overheatTicks = 0;
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
    }
}
