package org.reactor_model.core;

import org.reactor_model.event.ReactorEventBus;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.ConfigManager;
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
    private double fuelTemperature = 450.0; // K (core + FUEL_TEMP_OFFSET)

    // PWR-like constants (realistic values).
    // Kept as static defaults for backward compatibility (tests and other
    // subsystems reference MAX_SAFE_POWER); the live thresholds below are
    // initialized from ConfigManager and may be overridden via reactor.properties.
    public static final double MAX_SAFE_POWER = ConfigManager.DEFAULT_MAX_SAFE_POWER; // MWt (PWR nominal)
    public static final int OVERHEAT_MAX_TICKS = 100;
    private static final double DEFAULT_CRITICAL_TEMP = ConfigManager.DEFAULT_CRITICAL_TEMP; // K (fuel melting)

    // Live safety thresholds (configurable via reactor.properties).
    private double maxSafePower = ConfigManager.DEFAULT_MAX_SAFE_POWER;
    private double criticalTemp = DEFAULT_CRITICAL_TEMP;

    // Reactivity coefficients
    private static final double BASE_REACTIVITY = 0.0; // $ (cold, balanced state)
    private static final double TEMP_COEFF = -0.00002; // $/K (moderator temperature coefficient)
    private static final double ROD_WORTH = 0.02; // $ total rod worth (see S-curve below)

    // Thermal-hydraulics
    private static final double HEAT_CAPACITY = 1.5e8; // J/K (core thermal mass)
    private static final double HEAT_TRANSFER_COEFF = 5e6; // W/K (effective HTC)
    private static final double COOLANT_TEMP = 290.0; // K (inlet temperature)
    private static final double FUEL_TEMP_COEFF = -2e-5; // $/K (Doppler effect)
    private static final double FUEL_TEMP_OFFSET = 150.0; // K, typical PWR pellet-to-coolant delta
    // Boiling crisis (DNB): below this normalized flow, nucleate boiling
    // collapses into film boiling and heat transfer falls off a cliff.
    private static final double DNB_FLOW_THRESHOLD = 0.2;
    private static final double DNB_TRANSFER_FRACTION = 0.1;
    // Void feedback: boiling moderator out of the core inserts strong negative
    // reactivity above this coolant temperature.
    private static final double VOID_TEMP_THRESHOLD = 600.0; // K
    private static final double VOID_COEFF = -0.005; // $/K above threshold

    // Safety thresholds
    private static final double OVERHEAT_THRESHOLD = 800.0; // K
    private static final double MIN_POWER = 0.01; // MWt

    // Coolant pump trip state (Loss of Flow Accident support).
    private boolean pumpTripped = false;
    private boolean pumpTripLogged = false;
    // Linear coastdown rate (fraction per sim-second): full flow coasts to
    // zero over 5 simulation seconds, like a real pump flywheel rundown.
    private static final double PUMP_COASTDOWN_RATE = 0.2;

    // ATWS testing: when true, automatic SCRAM is suppressed (severe warnings
    // still fire). Default false; operator/test action only.
    private boolean bypassAutoScram = false;
    private long lastBypassWarning = 0;
    private static final long BYPASS_WARNING_COOLDOWN_MS = 5000L;

    // Core damage state. Temperature is clamped at MELT_TEMPERATURE, so melting
    // is declared on SUSTAINED exposure (consecutive ticks at the cap), which
    // models melt progression: a prompt burst kisses the clamp for a tick or
    // two without damage, while uncooled decay heat pinned at the cap for
    // ~100 sim-seconds destroys the core.
    private boolean meltedDown = false;
    private int meltTicks = 0;
    private static final double MELT_TEMPERATURE = 1500.0; // K, fuel melting point
    private static final int MELT_TICKS_TO_FAIL = 1000; // consecutive ticks at cap
    private long lastMeltStateLog = 0;

    // Decay heat (Wigner-Way approximation: P = P0 * 0.066 * tau^-0.2).
    // Driven by sim-seconds accumulated in shutdown so the curve is exact at
    // any speed multiplier; wall-clock time is recorded alongside for logs.
    private double powerBeforeShutdown = 0.0; // MWt just before SCRAM
    private double secondsSinceShutdown = 0.0; // sim-seconds elapsed in shutdown
    private static final double DECAY_HEAT_COEFF = 0.066;
    private static final double DECAY_HEAT_EXPONENT = -0.2;
    private static final double DECAY_HEAT_MIN_POWER = 1e-5; // MWt floor

    // Xenon-135 / Iodine-135 fission-product poisoning (the "reactor killer").
    // Persists across restart(): it is inventory, not a cold-condition reset.
    private final XenonModel xenonModel = new XenonModel();

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
        this.fuelTemperature = 300.0 + FUEL_TEMP_OFFSET;
        // Configurable safety thresholds (reactor.properties or defaults).
        this.maxSafePower = ConfigManager.getMaxSafePower();
        this.criticalTemp = ConfigManager.getCriticalTemp();
    }

    /** Live SCRAM power threshold (configurable via {@code reactor.max_safe_power}). */
    public double getMaxSafePower() { return maxSafePower; }

    /** Live critical-temperature threshold (configurable via {@code reactor.critical_temp}). */
    public double getCriticalTemp() { return criticalTemp; }

    /**
     * Improved update method with proper physics coupling.
     * Order: pump coastdown -> meltdown check -> reactivity -> neutronics ->
     * thermal-hydraulics -> protections
     */
    public void update(double dt) {
        // Pump coastdown progresses in every state (even melted debris benefits
        // from restored flow); manual flow control stays locked while tripped.
        coastDownPump(dt);

        long now = System.currentTimeMillis();

        if (!meltedDown) {
            updateMeltdown();
        }
        if (meltedDown) {
            handleMeltedMode(dt, now);
            eventBus.publish();
            return;
        }

        if (shutdown) {
            handleShutdownMode(dt, now);
            eventBus.publish();
            return;
        }

        // 1. Calculate total reactivity
        updateReactivity();

        // 2. Advance neutronics (point kinetics)
        double newPower = kinetics.advance(reactivity, dt);

        // 3. Advance thermal-hydraulics
        updateThermalHydraulics(dt, newPower);

        // 3b. Advance fission-product poisons on the new power level
        xenonModel.update(newPower, dt);

        // 4. Apply protections
        handleOverheating(now);
        checkScramCondition(now);
        checkHighReactivityWarning(now);

        eventBus.publish();
    }

    private void updateReactivity() {
        // Base reactivity + decoupled temperature feedback + rod effect + external
        double coolantDelta = temperature - COOLANT_TEMP;
        double fuelDelta = fuelTemperature - COOLANT_TEMP;
        double tempFeedback = TEMP_COEFF * coolantDelta;       // moderator coefficient
        double fuelFeedback = FUEL_TEMP_COEFF * fuelDelta;     // Doppler coefficient
        // Control rod S-curve (differential worth): rods bite hardest mid-core.
        // d(effect)/dpos peaks at pos=0.5 and vanishes at the travel ends, while
        // the integral spans -ROD_WORTH (fully inserted) to +ROD_WORTH (withdrawn).
        // ROD_WORTH (0.02 $) comfortably overcomes cold temperature feedback
        // (~-0.0034 $), so the reactor can still start from cold.
        double rodEffect = ROD_WORTH * Math.sin(Math.PI * (controlRodPosition - 0.5));

        reactivity = BASE_REACTIVITY + tempFeedback + fuelFeedback + rodEffect
                + externalReactivity + xenonModel.getXenonReactivity()
                + voidReactivity();
    }

    /**
     * Void (boiling) feedback: with degraded flow and hot coolant the moderator
     * boils out of the core, inserting strong negative reactivity. Displayed
     * and summed wherever total reactivity is reported.
     */
    private double voidReactivity() {
        if (coolantFlowRate < DNB_FLOW_THRESHOLD && temperature > VOID_TEMP_THRESHOLD) {
            return VOID_COEFF * (temperature - VOID_TEMP_THRESHOLD);
        }
        return 0.0;
    }

    private void updateThermalHydraulics(double dt, double power) {
        // Heat generation
        double heatGeneration = power * 1e6; // Convert MW to W

        // Heat removal with boiling-crisis (DNB) degradation: below 20% flow,
        // film boiling collapses heat transfer to a tenth of forced convection.
        double effectiveTransfer = (coolantFlowRate < DNB_FLOW_THRESHOLD)
                ? HEAT_TRANSFER_COEFF * coolantFlowRate * DNB_TRANSFER_FRACTION
                : HEAT_TRANSFER_COEFF * coolantFlowRate;
        double deltaT = temperature - COOLANT_TEMP;
        double heatRemoval = effectiveTransfer * deltaT;

        // Temperature evolution
        temperature += (heatGeneration - heatRemoval) / HEAT_CAPACITY * dt;

        // Prevent unrealistic temperatures
        temperature = MathUtil.clamp(temperature, 290.0, 1500.0);

        // Coupled fuel temperature (simplified thermal model: fuel hotter than coolant)
        fuelTemperature = temperature + FUEL_TEMP_OFFSET;
    }

    private void handleShutdownMode(double dt, long now) {
        // SCRAM: rods fully inserted
        controlRodPosition = 0.0;
        // Xenon poison still applies while shut down (and keeps building up);
        // void feedback too, so the displayed reactivity stays honest.
        reactivity = BASE_REACTIVITY - ROD_WORTH + xenonModel.getXenonReactivity()
                + voidReactivity();

        // Wigner-Way decay heat: P = P0 * 0.066 * tau^-0.2, tau >= 1 s so the
        // singularity at tau=0 yields the physical ~6.6% initial level.
        // A SCRAM does NOT save a reactor that has lost cooling: this heat must
        // still be removed or temperature keeps climbing toward meltdown.
        secondsSinceShutdown += dt;
        double tau = Math.max(1.0, secondsSinceShutdown);
        double decayPower = Math.max(
                powerBeforeShutdown * DECAY_HEAT_COEFF * Math.pow(tau, DECAY_HEAT_EXPONENT),
                DECAY_HEAT_MIN_POWER);
        kinetics.setPower(decayPower);

        // Xenon keeps evolving on decay-heat flux: expect the poisoning peak
        // hours (sim-time) after shutdown, then a slow decay.
        xenonModel.update(decayPower, dt);

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
        boolean trip = temperature >= criticalTemp
                || kinetics.getPower() > maxSafePower * 1.1;
        if (!trip) {
            return;
        }
        if (bypassAutoScram) {
            // ATWS testing: protection setpoint exceeded, but the breakers are
            // held closed. Severe warning (rate-limited) instead of a trip.
            if (now - lastBypassWarning >= BYPASS_WARNING_COOLDOWN_MS) {
                logger.logWarning(
                        "WARNING: Auto-SCRAM bypassed! Temperature/Power critical!");
                lastBypassWarning = now;
            }
            return;
        }
        emergencyShutdown("CRITICAL CONDITION! Automatic SCRAM initiated.");
    }

    /**
     * Melt-progression counter: sustained temperature at the fuel melting
     * point accumulates damage; any relief below the cap resets it.
     * Runs in every non-melted state (running AND shutdown).
     */
    private void updateMeltdown() {
        if (temperature >= MELT_TEMPERATURE) {
            meltTicks++;
            if (meltTicks >= MELT_TICKS_TO_FAIL) {
                triggerMeltdown();
            }
        } else {
            meltTicks = 0;
        }
    }

    private void triggerMeltdown() {
        meltedDown = true;
        shutdown = true;
        reactivity = BASE_REACTIVITY - ROD_WORTH + xenonModel.getXenonReactivity()
                + voidReactivity();
        logger.logWarning("CORE MELTDOWN! Fuel has exceeded melting point.");
    }

    /**
     * Frozen-pile mode: no fission and no kinetics — power pinned at the floor
     * while debris relaxes toward ambient coolant temperature (restored flow
     * speeds the cooling, but nothing ever restarts this core).
     */
    private void handleMeltedMode(double dt, long now) {
        kinetics.setPower(DECAY_HEAT_MIN_POWER);

        double coolRate = Math.min(1.0, dt * (0.005 + 0.05 * coolantFlowRate));
        temperature += (COOLANT_TEMP - temperature) * coolRate;
        temperature = MathUtil.clamp(temperature, COOLANT_TEMP, MELT_TEMPERATURE);
        fuelTemperature = temperature + FUEL_TEMP_OFFSET;

        if (now - lastMeltStateLog >= SHUTDOWN_LOG_INTERVAL) {
            logger.logState(kinetics.getPower(), temperature, coolantFlowRate,
                    controlRodPosition, reactivity);
            logger.logWarning("MELTED CORE: debris cooling ongoing. Restart impossible.");
            lastMeltStateLog = now;
        }
    }

    /**
     * Trips the main coolant pump: locks manual flow control (the automatic
     * system cannot override the trip) and latches a flywheel coastdown that
     * {@link #update(double)} progresses to zero flow over 5 simulation
     * seconds. Idempotent.
     */
    public void failCoolantPump() {
        pumpTripped = true;
        setManualFlowControl(true);
        logger.logDecision("Operator", "Coolant pump trip initiated: flow coasting down.");
    }

    /** Progresses a latched pump coastdown toward zero flow. */
    private void coastDownPump(double dt) {
        if (!pumpTripped || coolantFlowRate <= 0.0) {
            return;
        }
        setCoolantFlowRate(coolantFlowRate - PUMP_COASTDOWN_RATE * dt);
        if (coolantFlowRate <= 0.0 && !pumpTripLogged) {
            pumpTripLogged = true;
            logger.logWarning("Coolant pump coastdown complete: flow is zero.");
        }
    }

    private void checkHighReactivityWarning(long now) {
        if (Math.abs(reactivity) > 0.01 && now - lastHighReactivityWarning > WARNING_COOLDOWN_MS) {
            logger.logWarning(String.format("High reactivity: %.4f $", reactivity));
            lastHighReactivityWarning = now;
        }
    }

    public void emergencyShutdown(String reason) {
        // Capture pre-SCRAM power BEFORE kinetics.scram() collapses it: it seeds
        // the Wigner-Way decay-heat curve for the whole shutdown that follows.
        // Capped at the protection setpoint: prompt-burst overshoot above it is
        // a numerical artifact of the rate limiter, not physical inventory.
        powerBeforeShutdown = Math.min(kinetics.getPower(), maxSafePower * 1.1);
        secondsSinceShutdown = 0.0;
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
    public double getExternalReactivity() { return externalReactivity; }
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

    /**
     * State-persistence support: direct field injection used by
     * {@code StatePersistenceUtil} when restoring a saved simulation state.
     * Bounds mirror the physical limits enforced during normal operation.
     */
    public void setTemperature(double temperature) {
        this.temperature = MathUtil.clamp(temperature, 290.0, 1500.0);
        this.fuelTemperature = this.temperature + FUEL_TEMP_OFFSET;
    }

    public void setExternalReactivity(double externalReactivity) {
        this.externalReactivity = externalReactivity;
    }

    public void setReactivity(double reactivity) {
        this.reactivity = reactivity;
    }

    public void setOverheatTicks(int overheatTicks) {
        this.overheatTicks = Math.max(overheatTicks, 0);
    }

    public void setFuelTemperature(double fuelTemperature) {
        this.fuelTemperature = Math.max(fuelTemperature, 290.0);
    }

    public void setPower(double power) {
        kinetics.setPower(power);
    }

    public double[] getPrecursorConcentrations() {
        return kinetics.getPrecursors();
    }

    public void setPrecursorConcentrations(double[] precursors) {
        kinetics.setPrecursors(precursors);
    }

    public void restart() {
        if (meltedDown) {
            logger.logWarning("Cannot restart: Core is damaged.");
            return;
        }
        shutdown = false;
        temperature = 300.0;
        fuelTemperature = 300.0 + FUEL_TEMP_OFFSET;
        kinetics.setPower(MIN_POWER);
        externalReactivity = 0.0;
        controlRodPosition = 0.5;
        reactivity = 0.0;
        manualFlowControl = false;
        coolantFlowRate = 1.0;
        overheatTicks = 0;
        // A restarted (repaired) unit has a working pump and a fresh damage clock.
        pumpTripped = false;
        pumpTripLogged = false;
        meltTicks = 0;
        // Fresh decay-heat curve for the next shutdown. Xenon/iodine inventory
        // is deliberately NOT reset: fission products do not vanish on restart,
        // which is exactly what creates the post-SCRAM "xenon dead time".
        powerBeforeShutdown = 0.0;
        secondsSinceShutdown = 0.0;
        logger.logDecision("System", "Reactor restarted from cold condition");
    }

    public void logCurrentState() {
        logger.logState(getPower(), temperature, coolantFlowRate, controlRodPosition, reactivity);
    }

    public int getOverheatTicks() {
        return overheatTicks;
    }

    public double getFuelTemperature() {
        return fuelTemperature;
    }

    /**
     * Emergency override for when the automatic regulator is disabled and
     * the core is overheating. Directly inserts control rods to reduce power.
     * Called by SimulationLoop when overheat protection triggers without an active regulator.
     */
    public void handleOverheatProtectionManualOverride() {
        double newPos = Math.max(0.0, controlRodPosition - 0.1);
        setControlRodPosition(newPos);
        logger.logWarning("CRITICAL: Regulator disabled — control rods forcibly inserted due to overheating");
    }

    public void resetOverheatTicks() {
        overheatTicks = 0;
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
    }

    // ---- Xenon-135 / decay-heat state (physics + persistence) -----------

    /** Direct access to the fission-product poison model. */
    public XenonModel getXenonModel() { return xenonModel; }

    /** Current Xenon-135 reactivity contribution in dollars (<= 0). */
    public double getXenonReactivity() { return xenonModel.getXenonReactivity(); }

    public double getIodineConcentration() { return xenonModel.getIodineConc(); }

    public void setIodineConcentration(double iodineConc) {
        xenonModel.setIodineConc(iodineConc);
    }

    public double getXenonConcentration() { return xenonModel.getXenonConc(); }

    public void setXenonConcentration(double xenonConc) {
        xenonModel.setXenonConc(xenonConc);
    }

    public double getPowerBeforeShutdown() { return powerBeforeShutdown; }

    public void setPowerBeforeShutdown(double powerBeforeShutdown) {
        this.powerBeforeShutdown = Math.max(powerBeforeShutdown, 0.0);
    }

    public double getSecondsSinceShutdown() { return secondsSinceShutdown; }

    public void setSecondsSinceShutdown(double secondsSinceShutdown) {
        this.secondsSinceShutdown = Math.max(secondsSinceShutdown, 0.0);
    }

    // ---- LOFA / ATWS / meltdown state ----------------------------------

    public boolean isMeltedDown() { return meltedDown; }

    /**
     * Restores persisted damage state. Engaging damage also forces the
     * shutdown flag (a melted core is necessarily shut down); clearing it
     * never clears shutdown (only restart() does that, and it refuses).
     */
    public void setMeltedDown(boolean meltedDown) {
        this.meltedDown = meltedDown;
        if (meltedDown) {
            this.shutdown = true;
        }
    }

    public boolean isPumpTripped() { return pumpTripped; }

    public void setPumpTripped(boolean pumpTripped) {
        this.pumpTripped = pumpTripped;
        if (!pumpTripped) {
            this.pumpTripLogged = false;
        }
    }

    public boolean isBypassAutoScram() { return bypassAutoScram; }

    public void setBypassAutoScram(boolean bypassAutoScram) {
        this.bypassAutoScram = bypassAutoScram;
    }
}
