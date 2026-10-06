# Reactor Modeling

A Java 17 educational project that simulates a nuclear reactor control system with point-kinetics power evolution, S-curve rod worth, Xenon-135 poisoning, Wigner-Way decay heat, thermal feedback with boiling crisis, automatic regulation, cooling control, disturbance injection, accident drills (LOFA, ATWS, meltdown), and a Swing dashboard.

The project is built as a small control-system sandbox: you can run it as a desktop UI by default or use the CLI mode for direct command-driven interaction.

## Features

- Point-kinetics reactor model with delayed neutron groups
- S-curve differential control-rod worth (peak effectiveness mid-core)
- Xenon-135 / Iodine-135 poisoning with post-shutdown peak and dead time
- Wigner-Way decay heat (uncooled cores melt on residual heat alone)
- Temperature and coolant-flow thermal feedback, void (boiling) feedback
- Flow-dependent heat transfer with boiling-crisis (DNB) collapse
- Automatic regulator with anti-windup PID, slew limiting, 1.5% deadband,
  setpoint ramping, and asymmetric rod drive (slow withdraw, fast insert)
- Loss-of-Flow Accident (pump trip with flywheel coastdown) drills
- Permanent core meltdown progression with restart refusal
- ATWS testing via auto-SCRAM bypass
- Cooling subsystem with automatic flow adjustment
- Disturbance simulator that can be toggled on and off
- Safety logic for overheating and SCRAM conditions
- Swing dashboard with live gauges, controls, event log, and audit-hardened
  responsive layout (usable at 800x600, scrollbars instead of clipping)
- Status bar with non-intrusive feedback for save/load/export operations
- CLI mode for manual simulation commands
- Configuration via `reactor.properties` (timestep, refresh rate, safety thresholds)
- State Persistence (Save/Load) for pausing and resuming simulations
- Simulation Speed Control (1x, 2x, 5x, 10x) in the GUI and CLI
- CSV Data Export for offline analysis of recorded snapshots
- Persistent File Logging (`logs/`) alongside console/UI logs for post-mortem analysis
- Global Exception Handling & Auto-SCRAM on simulation faults
- JUnit 5, Mockito, and AssertJ-Swing test suite covering unit, integration, regression, UI, and command behavior

## Project Structure

```text
src/main/java/org/reactor_model/
├── core/          Reactor physics and kinetics
├── cooling/       Cooling control logic
├── disturbance/   Disturbance simulation
├── event/         Event bus
├── logger/        Console, UI, file, and composite logging
├── regulation/    Regulator strategies and PID control
├── simulation/    Simulation loop
├── ui/            Swing dashboard and adapter layer
└── util/          Config, persistence, CSV export, shared utilities

src/test/java/org/reactor_model/
├── core/          Reactor core, kinetics stability, safety & restart tests,
│                  S-curve/decay/xenon physics, Phase-8 accident safety
├── cooling/       Cooling tests
├── disturbance/   Disturbance tests
├── event/         Event bus tests
├── logger/        Composite and file logger tests
├── regression/    Stability and regression scenarios
├── regulation/    Auto-regulator unit, transition, PID unit, and stability tests
├── simulation/    Simulation loop and speed-control tests
├── ui/            AssertJ-Swing dashboard UI tests, layout audit tests
├── util/          Math, config, persistence, and CSV export tests
├── ReactorAppCommandTest.java
├── ReactorAppPhase8CommandTest.java
└── SafeLoadIntegrationTest.java
```

## Requirements

- Java 17 or newer
- Maven 3.8+

## Build

```bash
mvn clean compile
```

## Run Tests

```bash
mvn test
```

Current test status:

- **165 tests** across unit, integration, regression, and CLI command layers
- 0 failures (AssertJ-Swing dashboard UI tests excluded: they need a display
  and hit a known module-access issue on newer JDKs)

## Run the Application

### GUI Mode

Compile first:

```bash
mvn compile
```

Then run:

```bash
java -cp target/classes org.reactor_model.ReactorApp
```

### CLI Mode

```bash
java -cp target/classes org.reactor_model.ReactorApp --cli
```

## CLI Commands

| Command | Description |
|---|---|
| `start` | Start the simulation loop |
| `stop` | Stop the simulation loop |
| `increasepower X` | Increase target power by `X` MW |
| `decreasepower X` | Decrease target power by `X` MW |
| `toggleauto` | Toggle the automatic regulator |
| `demand` | Inject a reactivity spike |
| `spike` | Inject a reactivity spike (alias for `demand`) |
| `failure` | Simulate coolant failure |
| `failpump` | Trip the main coolant pump (5 s coastdown, manual lock) |
| `bypass` | Toggle auto-SCRAM bypass (ATWS testing) |
| `restart` | Restart the reactor after shutdown (refused if melted) |
| `save <file>` | Save reactor state to `file` |
| `load <file>` | Load reactor state from `file` (loop is paused during load, then resumed) |
| `speed <multiplier>` | Set simulation speed, clamped to 1.0–10.0 (e.g. `speed 5`) |
| `help` | Show available commands |
| `quit` | Exit the application |

Input validation: commands that require an argument (`increasepower`,
`decreasepower`, `save`, `load`, `speed`) report an error when the argument
is missing (`Error: 'save' requires a file path argument...`) or not a
number (`Invalid number: ...`) instead of throwing. File errors during
`save`/`load` print a user-friendly message and never crash the loop.

## GUI Guide

- **Simulation**: Start/Stop with rapid-fire protection, plus a speed
  selector (1x, 2x, 5x, 10x) for fast-forwarding long-term behavior.
- **Target Power**: slider + spinner hard-limited to 0–5000 MW; typed input
  is clamped to the same range.
- **Operator Actions**: reactivity spike, coolant failure (confirmed),
  pump trip with realistic coastdown (confirmed), emergency SCRAM (disabled
  while shut down), ATWS bypass toggle (red while armed), restart (enabled
  only after shutdown, permanently disabled after meltdown), Save/Load state,
  and **Export Data (CSV)**.
- **Manual rods**: the rod slider sends its position on release (no drag spam)
  and locks with a warning when Xenon-135 poisoning overwhelms the rods.
- **Status bar**: the bottom bar confirms operations
  ("Reactor state saved to ...") and turns red on errors, replacing
  modal popups for routine feedback.
- **CSV export**: every simulation tick is buffered; exporting writes
  `Time (s), Power (MW), Temperature (K), Reactivity ($), Rod Position,
  Coolant Flow, Target Power` and clears the buffer.

## Configuration

Create a `reactor.properties` file in the directory where you launch the
application to override simulation parameters without recompiling. If the
file is missing (or a key is absent/unparseable), hardcoded defaults apply
and the app logs which mode it is using at startup:

```properties
# Physics timestep in seconds (default 0.1)
simulation.dt=0.1
# Dashboard refresh period in milliseconds (default 200)
simulation.refresh_ms=200
# SCRAM power threshold in MWt (default 3411.0)
reactor.max_safe_power=3411.0
# Fuel-temperature SCRAM threshold in K (default 1200.0)
reactor.critical_temp=1200.0
```

Available keys and defaults:

| Key | Default | Used by |
|---|---|---|
| `simulation.dt` | `0.1` | `SimulationLoop` physics timestep and sleep pacing |
| `simulation.refresh_ms` | `200` | `ReactorDashboard` gauge refresh timer |
| `reactor.max_safe_power` | `3411.0` | `ReactorCore` over-power SCRAM threshold |
| `reactor.critical_temp` | `1200.0` | `ReactorCore` over-temperature SCRAM threshold |

## Logging

Every run writes a timestamped operational log to
`logs/reactor_log_<timestamp>.txt` in addition to the console (CLI) or
event panel (GUI). A JVM shutdown hook flushes and closes the file, so
anomalies can be investigated after the fact.

## Architecture

The main runtime flow is:

1. `SimulationLoop` advances the simulation on a fixed timestep.
2. `PowerDemandSimulator` optionally injects disturbances.
3. `ReactorCore` updates reactivity (S-curve rods, temperature/Doppler,
   xenon, void, external), kinetics, xenon inventory, and thermal state with
   flow-dependent (DNB) heat transfer.
4. `CoolingSystem` adjusts coolant flow unless manual control is active.
5. `AutoRegulator` reacts through the event bus and moves the control rods
   (ramped setpoint, deadband, slew-limited, asymmetric drive).
6. Protections trip SCRAM (unless bypassed for ATWS), Wigner-Way decay heat
   follows shutdowns, and sustained 1500 K exposure melts the core for good.
7. `ReactorUIAdapter` exposes the latest state to the Swing dashboard.

## Testing Scope

The test suite currently covers:

- reactor core physics, safety, and restart behavior
- point-kinetics solver numerical stability
- regulator enable/disable and PID transition logic
- simulation loop orchestration
- disturbance defaults and toggling
- cooling behavior and manual override
- CLI command handling (start, stop, spike, failure, failpump, bypass,
  restart, save/load, speed, etc.)
- integration between core, regulator, cooling, and UI snapshot updates
- state persistence round-trips and failure paths (`StatePersistenceUtilTest`)
- speed clamping and sleep pacing (`SimulationLoopSpeedTest`)
- stop-the-world safe loading while the loop runs (`SafeLoadIntegrationTest`)
- CSV export content, buffer clearing, and invalid input (`CsvDataExporterTest`)
- configuration loading, defaults, and malformed-value fallback (`ConfigManagerTest`)
- composite fan-out, fault isolation, and file logging (`CompositeAndFileLoggerTest`)
- PID clamping, slew limiting, and derivative filtering (`AntiWindupPIDTest`)
- xenon equilibrium, post-shutdown peak, and decay (`XenonModelTest`)
- S-curve shape, cold start, decay plateau, xenon plumbing (`ReactorCorePhysicsTest`)
- deadband hold and oscillation-free 50% steps (`RegulatorStabilityTest`)
- DNB, void, pump coastdown, LOFA meltdown, ATWS, restart refusal (`ReactorSafetyPhase8Test`)
- layout minimums, scrollbar activation, and 800x600 usability (`LayoutAuditTest`)
- **UI component interaction via AssertJ-Swing** (button clicks, spinner values, gauge updates, modal dialogs)

## Notes for Contributors

- The default application mode is the Swing dashboard.
- Disturbances are disabled by default for stable operation.
- The repository currently targets plain Maven + Java without extra runtime plugins.
- UI tests use AssertJ-Swing 3.17.1 with the AWT robot; modal dialogs require a background-thread click pattern (see `ReactorDashboardUiTest.testScramButton`).
- Keep documentation and public-facing text in English.

## License

See [LICENSE](LICENSE).
