# Nuclear Reactor Control System Simulator

A modular Java simulation of a nuclear reactor core, including thermal dynamics, automatic regulation, cooling control, disturbance modeling, and a real‑time simulation loop.  
This project demonstrates control‑system design, PID‑based regulation, event‑driven updates, and safe‑operation logic (SCRAM, overheating protection, reactivity feedback).

---

## Features

### 🔥 Reactor Core Physics
- Power evolution based on reactivity and thermal feedback
- Temperature dynamics with heat capacity and coolant heat transfer
- Control rod effects on reactivity
- Automatic SCRAM on critical temperature
- Overheat tracking and protective behavior
- Startup boost and stabilization logic

### 🧊 Cooling System
- Temperature‑dependent coolant flow
- Automatic maximum flow at high temperature or overpower
- Rate‑limited logging to avoid console spam
- Reactivity suppression during aggressive cooling

### 🎛 Automatic Regulator (PID)
- PID‑based control rod adjustment
- Target power tracking
- Stability detection (±2% tolerance)
- Rate‑limited decision logging
- Can be toggled on/off via CLI

### ⚡ Disturbance Simulator
- Random reactivity spikes
- Random increases in target power
- Clamped to safe maximum power
- Simulates real‑world demand fluctuations

### 🔄 Simulation Loop
- Real‑time threaded simulation
- Fixed timestep (`dt = 0.1s`)
- Periodic state logging
- Automatic target reduction on prolonged overheating
- Clean start/stop lifecycle

### 🧩 Event Bus
- Lightweight publish/subscribe mechanism
- Regulator reacts to core updates without tight coupling

### 📝 Logging
- Timestamped state, warnings, and subsystem decisions
- Console‑based logger with unified formatting

---

## Project Structure

```
src/main/java/org/reactor_model/
│
├── core/               # Reactor physics and SCRAM logic
├── cooling/            # Cooling system controller
├── regulation/         # PID regulator and strategy interface
├── disturbance/        # Random demand and reactivity events
├── simulation/         # Main simulation loop (threaded)
├── event/              # Simple event bus
├── logger/             # Logging interface + console implementation
└── util/               # Math utilities (clamp)
```

---

## How It Works

The simulation runs in a dedicated thread.  
Each cycle performs:

1. Apply random disturbances
2. Update cooling system
3. Update reactor core physics
4. Trigger regulator via event bus
5. Log state periodically
6. Sleep for `dt` to maintain real‑time pacing

The CLI allows interactive control of the reactor.

---

## CLI Commands

| Command | Description |
|--------|-------------|
| `start` | Start the simulation loop |
| `stop` | Stop the simulation loop |
| `increasepower <x>` | Increase target power by *x* MW |
| `decreasepower <x>` | Decrease target power by *x* MW |
| `toggleauto` | Enable/disable automatic regulator |
| `demand` | Inject artificial reactivity spike |
| `failure` | Simulate coolant pump failure |
| `restart` | Restart reactor after SCRAM |
| `quit` | Exit the application |

---

## Running the Project

### Requirements
- Java 17+
- Maven (optional, if you want to package)

### Run from source
```
mvn compile exec:java -Dexec.mainClass="org.reactor_model.ReactorApp"
```

### Or package and run
```
mvn package
java -jar target/reactor-simulator.jar
```

---

## Example Output

```
2025-01-01 12:00:00 [STATE] Power: 120.55 MW, Temp: 345.12 C, Coolant: 0.62, Rods: 48%, Reactivity: 0.0032
2025-01-01 12:00:05 [AUTOREGULATOR] Decision: Rod correction: -0.0021 → 0.478
2025-01-01 12:00:10 [WARNING] High reactivity! Enhanced cooling is recommended.
```

---

## Architecture Overview

The system follows a clean separation of concerns:

- **ReactorCore** — domain model and physics
- **AutoRegulator** — control logic (PID)
- **CoolingSystem** — thermal management
- **SimulationLoop** — orchestrator
- **EventBus** — decoupled communication
- **Logger** — unified output layer

This makes the project easy to extend with new regulators, cooling models, or visualization layers.

---

## Possible Extensions

- GUI dashboard (JavaFX or web UI)
- More advanced thermal‑hydraulic models
- Multi‑loop control (steam generator, turbine)
- Configurable PID parameters via config file
- File‑based logging or metrics export
