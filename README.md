# Reactor Modeling

A Java 17 educational project that simulates a nuclear reactor control system with point-kinetics power evolution, thermal feedback, automatic regulation, cooling control, disturbance injection, and a Swing dashboard.

The project is built as a small control-system sandbox: you can run it as a desktop UI by default or use the CLI mode for direct command-driven interaction.

## Features

- Point-kinetics reactor model with delayed neutron groups
- Temperature and coolant-flow thermal feedback
- Automatic regulator with anti-windup PID behavior
- Cooling subsystem with automatic flow adjustment
- Disturbance simulator that can be toggled on and off
- Safety logic for overheating and SCRAM conditions
- Swing dashboard with live gauges, controls, and event log
- CLI mode for manual simulation commands
- JUnit 5 and Mockito test suite covering unit, integration, regression, and command behavior

## Project Structure

```text
src/main/java/org/reactor_model/
├── core/          Reactor physics and kinetics
├── cooling/       Cooling control logic
├── disturbance/   Disturbance simulation
├── event/         Event bus
├── logger/        Console and UI logging
├── regulation/    Regulator strategies and PID control
├── simulation/    Simulation loop
├── ui/            Swing dashboard and adapter layer
└── util/          Shared utilities

src/test/java/org/reactor_model/
├── core/          Reactor core tests
├── cooling/       Cooling tests
├── disturbance/   Disturbance tests
├── event/         Event bus tests
├── regression/    Stability and regression scenarios
├── simulation/    Simulation loop tests
└── ReactorAppCommandTest.java
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

- 92 tests
- 0 failures

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
| `failure` | Simulate coolant failure |
| `restart` | Restart the reactor after shutdown |
| `help` | Show available commands |
| `quit` | Exit the application |

## Architecture Notes

The main runtime flow is:

1. `SimulationLoop` advances the simulation on a fixed timestep.
2. `PowerDemandSimulator` optionally injects disturbances.
3. `ReactorCore` updates reactivity, kinetics, and thermal state.
4. `CoolingSystem` adjusts coolant flow unless manual control is active.
5. `AutoRegulator` reacts through the event bus and moves the control rods.
6. `ReactorUIAdapter` exposes the latest state to the Swing dashboard.

## Testing Scope

The test suite currently covers:

- reactor core safety and restart behavior
- regulator enable/disable logic
- simulation loop orchestration
- disturbance defaults and toggling
- cooling behavior and manual override
- CLI command handling
- integration between core, regulator, cooling, and UI snapshot updates

## Notes for Contributors

- The default application mode is the Swing dashboard.
- Disturbances are disabled by default for stable operation.
- The repository currently targets plain Maven + Java without extra runtime plugins.
- Keep documentation and public-facing text in English.

## License

See [LICENSE](LICENSE).
