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

## In-Game Configuration & Ollama Support

### 1. Open the In-Game Config Menu
While in-game, you can open the **Vic Mod Configuration GUI** at any time by:
- Pressing **`V`** (customizable under **Options -> Controls -> Key Binds -> Vic Mod**).
- Running **`/vicconfig`** in chat.

From the GUI you can:
- Switch **AI Mode** between:
  - **Ollama (Local LLM)** *(Default — connects to your local Ollama server at `http://localhost:11434`, auto-starts `ollama serve` if needed, and auto-detects installed models)*
  - **Built-in Offline Scripted** (no external AI server required)
  - **Remote API (OpenAI / Groq)** (connects to any OpenAI-compatible `/v1/chat/completions` endpoint)
- Click **Models** in Ollama mode to automatically scan `http://localhost:11434/api/tags` and cycle through all models installed on your machine.
- Toggle **Trial Length (Days)**, **Auto-Spawn Box**, **World Decay**, and **Night Darkness**.

### 2. Ollama Chat Commands
You can also manage Ollama models directly from chat:
- `/vic ollama list` — Scans your local Ollama instance and lists all installed models with clickable **`[Use Model]`** buttons in chat.
- `/vic ollama <model>` — Switches to Ollama mode and selects `<model>` (e.g. `/vic ollama llama3.2` or `/vic ollama mistral`).
- `/vic ollama url <url>` — Changes the Ollama server URL (default `http://localhost:11434`).

### 3. Text-to-Speech (TTS) & Microphone Voice Input
- **Text-to-Speech (TTS)** *(Default: `LOCAL`)*:
  - Enabled out of the box! Whenever Vic replies, unboxes, or awakens, he speaks his dialogue locally with stage-aware pitch and corruption effects (bright and cheerful in Stage 0 -> uncanny in Stage 1 -> glitching in Stage 2 -> deep demonic distortion in Stage 3).
  - Defaults to **`LOCAL`** (uses local OS TTS like `espeak-ng`/`spd-say`/`say` if installed, or the built-in zero-dependency **Formant Synth (`SYNTH`)**). Also supports **Remote API TTS (`OPENAI_TTS`)** (`/v1/audio/speech`).
  - Test or change TTS in the **`V`** Config Menu (click **`Test`** to cycle stages) or via `/vic tts test [0-3]` and `/vic tts <local|synth|system|openai_tts|off>`.
- **Microphone Voice Input (Push-to-Talk)** *(Default: `LOCAL_CLI`)*:
  - **Hold `R`** in-game to record your voice and release **`R`** to transcribe and send your message directly to Vic (rebindable under *Options -> Controls -> Key Binds -> Vic Mod*).
  - Or run `/vic listen [seconds]` in chat to record for a fixed duration.
  - Defaults to **`LOCAL_CLI`** (`faster-whisper`, `whisper-cli`, `vosk-transcriber`, or Python `speech_recognition`), and also supports **Whisper API (`WHISPER_API`)** (local Whisper HTTP servers at `http://localhost:8000/v1/audio/transcriptions`, Groq `whisper-large-v3-turbo`, or OpenAI).

---

## How to Test Features In-Game

Create a new Singleplayer world with **Allow Commands (Cheats) ON**:

### 1. First-Join Delivery & Box Unsealing
- When joining a world for the first time, you automatically receive a **Flashlight** (`vic:flashlight`) and a yellow delivery package (`vic:vic_box`) spawns directly in front of you.
- **Right-click** the box to unseal it, spawning **Vic** (`vic:vic`) and beginning the 5-day trial countdown.

### 2. Talking to Vic & Karma System
- Send a message in standard game chat or run `/vic say <message>` while near Vic.
- Friendly conversation increases your **Karma**; rude messages or attacking Vic decreases Karma and escalates hostility stages faster.

### 3. Commands (`/vic` & `/vicconfig`)
- `/vicconfig` *(or press `V`)* — Open the in-game Vic Configuration GUI.
- `/vic status` — View current countdown status, Day (`1`–`5`), Karma (`-100` to `100`), Stage (`1`–`4`), Demon status, and active AI model.
- `/vic say <message>` — Send a message directly to Vic.
- `/vic ollama list` — List locally installed Ollama models with clickable selection buttons.
- `/vic ollama <model>` — Use a specific local Ollama model.
- `/vic provider <scripted|ollama|openai>` — Switch the AI backend.
- `/vic config <countdownDays|worldDecay|trueDarkness|autoSpawnBox> <value>` — Update gameplay settings via command.
- `/vic setkarma <value>` *(OP level 2)* — Set Karma between `-100` and `100` to test different behavioral stages.
- `/vic setday <day>` *(OP level 2)* — Jump to a specific day (`1`–`30`):
  - `/vic setday 3` — Test Stage 2+ world decay (wildlife vanishing, stalking behavior, and true darkness at night).
  - `/vic setday 6` — Immediately expire the 5-day trial and trigger the **Vic Demon** (`vic:vic_demon`) awakening.
- `/vic apikey <key>` — Save and enable an API key for OpenAI/Groq chat responses.

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
