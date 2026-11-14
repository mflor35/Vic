# Vic Mod (Minecraft 1.21.1 Fabric)

A psychological horror & AI companion mod for **Minecraft 1.21.1 (Fabric)** where a mysterious yellow delivery package (`VIC-UNIT-01`) arrives on your first day. Unseal your new friend, chat with him, manage your Karma during the 5-day trial period, and survive when the trial expires.

---

## Requirements

- **Minecraft**: `1.21.1`
- **Mod Loader**: [Fabric Loader](https://fabricmc.net/) (`>= 0.16.0`)
- **Dependencies**: [Fabric API](https://modrinth.com/mod/fabric-api) (`0.102.0+1.21.1`)
- **Java**: **Java 21**

---

## How to Run & Build

### Option A: Run the Development Client (Fastest for testing)
From the project root directory, run:

```bash
./gradlew runClient
```

This launches a development instance of Minecraft 1.21.1 with the mod and Fabric API pre-loaded.

### Option B: Build the Mod `.jar` for a Standard Launcher
```bash
./gradlew build
```
Once finished, copy `build/libs/vic-1.0.0.jar` into your Minecraft 1.21.1 Fabric profile's `mods/` folder alongside **Fabric API**.

---

## How to Test Features In-Game

Create a new Singleplayer world with **Allow Commands (Cheats) ON**:

### 1. First-Join Delivery & Box Unsealing
- When joining a world for the first time, you automatically receive a **Flashlight** (`vic:flashlight`) and a yellow delivery package (`vic:vic_box`) spawns directly in front of you.
- **Right-click** the box to unseal it, spawning **Vic** (`vic:vic`) and beginning the 5-day trial countdown.

### 2. Talking to Vic & Karma System
- Send a message in standard game chat or run `/vic say <message>` while near Vic.
- Friendly conversation increases your **Karma**; rude messages or attacking Vic decreases Karma and escalates hostility stages faster.
- **Optional External AI (OpenAI-compatible API)**:
  - By default, Vic uses built-in stage-aware offline dialogue so it works out of the box.
  - To enable live LLM responses, run:
    ```text
    /vic apikey <YOUR_API_KEY>
    ```
  - You can also customize the endpoint (`apiEndpoint`), model (`apiModel`), and gameplay toggles in `config/vic.json` (or `run/config/vic.json` in dev).

### 3. Commands (`/vic`)
- `/vic status` — View current countdown status, Day (`1`–`5`), Karma (`-100` to `100`), Stage (`1`–`4`), Demon status, and AI config.
- `/vic say <message>` — Send a message directly to Vic.
- `/vic setkarma <value>` *(OP level 2)* — Set Karma between `-100` and `100` to test different behavioral stages.
- `/vic setday <day>` *(OP level 2)* — Jump to a specific day (`1`–`30`):
  - `/vic setday 3` — Test Stage 2+ world decay (wildlife vanishing, stalking behavior, and true darkness at night).
  - `/vic setday 6` — Immediately expire the 5-day trial and trigger the **Vic Demon** (`vic:vic_demon`) awakening.
- `/vic apikey <key>` — Save and enable an API key for live AI chat responses.

### 4. Items & Entities
- **Creative Inventory**:
  - **Tools & Utilities**: Flashlight (`vic:flashlight`) — Right-click to toggle on/off. Counters nighttime True Darkness and stuns/repels the Vic Demon when aimed at it.
  - **Spawn Eggs**: Delivery Box (`vic:vic_box_spawn_egg`), Vic (`vic:vic_spawn_egg`), and Vic Demon (`vic:vic_demon_spawn_egg`).
- **Summon / Give Commands**:
  ```text
  /give @s vic:flashlight
  /summon vic:vic_box
  /summon vic:vic
  /summon vic:vic_demon
  ```
