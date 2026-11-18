# Vic Mod (Minecraft 26.x Fabric)

A psychological horror & AI companion mod for **Minecraft 26.x (Fabric)** where a mysterious yellow delivery package (`VIC-UNIT-01`) arrives on your first day. Unseal your new friend, chat with him, manage your Karma during the 5-day trial period, and survive when the trial expires.

---

## Requirements

- **Minecraft**: `26.x` (target: `26.3`)
- **Mod Loader**: [Fabric Loader](https://fabricmc.net/) (`>= 0.20.1`)
- **Dependencies**: [Fabric API](https://modrinth.com/mod/fabric-api) (`0.166.2+26.3`)
- **Java**: [**Java 25**](https://adoptium.net/temurin/releases/?version=25)

---

## How to Run & Build

For JDK/IDE setup, development-client instructions, and validation tasks, see [DEVELOPMENT.md](DEVELOPMENT.md).

### Option A: Run the Development Client (Fastest for testing)
From the project root directory, run:

```bash
./gradlew runClient
```

This launches a development instance of Minecraft 26.x with the mod and Fabric API pre-loaded.

### Option B: Build the Mod `.jar` for a Standard Launcher
```bash
./gradlew build
```
Once finished, copy `build/libs/vic-1.0.0.jar` into your Minecraft 26.x Fabric profile's `mods/` folder alongside **Fabric API**.

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

### 2. Ollama Chat Commands & Recommended Models
You can also manage Ollama models directly from chat:
- `/vic ollama list` — Scans your local Ollama instance and lists all installed models with clickable **`[Use Model]`** buttons in chat.
- `/vic ollama <model>` — Switches to Ollama mode and selects `<model>` (e.g. `/vic ollama llama3.2:3b` or `/vic ollama qwen2.5:7b`).
- `/vic ollama url <url>` — Changes the Ollama server URL (default `http://localhost:11434`).

#### Choosing the Best Model for Your Hardware
Because Minecraft 1.21.1 and the bundled Sherpa-ONNX TTS/STT voice models share your GPU and RAM, pick an Ollama model that fits comfortably inside your available VRAM alongside the game:

| Hardware Tier | Specs (GPU VRAM / RAM) | Recommended Model(s) | Download Size | Why Use It |
| :--- | :--- | :--- | :--- | :--- |
| **Mid-Range / High-End Gaming** *(Recommended)* | **8 GB+ VRAM** (e.g. RTX 3070/4060, RX 7600/7700S, Apple M2/M3 16GB+) | `qwen2.5:7b` or `llama3.1:8b` | ~4.7 – 4.9 GB | Best overall balance. Fits 100% in VRAM alongside Minecraft, reliably triggers `[ACTION:...]` tags (giving items, building structures), and nails Vic's persona transitions across all 4 stages. |
| **Entry-Level Gaming / Laptops** | **4 – 6 GB VRAM** (e.g. GTX 1650/1660, RTX 3050, RX 6500M) or **Shaders Enabled** | `llama3.2:3b` or `qwen3.5:4b` | ~2.0 – 3.4 GB | Ultra-fast (<0.5s responses) and leaves plenty of VRAM headroom for high render distances or shaders without causing frame drops. |
| **Low-End / Integrated Graphics (iGPU)** | **No dedicated GPU / 8 – 16 GB System RAM** (e.g. Intel Iris Xe, Radeon 780M/Vega, Steam Deck) | `qwen2.5:1.5b`, `llama3.2:1b`, or `gemma2:2b` | ~0.9 – 1.6 GB | Runs smoothly on CPU or shared iGPU memory with minimal impact on Minecraft tick rate or FPS. |
| **Potato / Ultra-Low-End Devices** | **< 8 GB RAM** or older dual/quad-core CPUs | **Built-in Scripted Mode** (`/vic provider scripted`) or **Remote API** (`/vic provider openai`) | 0 MB (Local) | Zero extra RAM/VRAM overhead. Scripted mode works 100% offline with full stage progression and item/building actions, or you can offload AI to a fast cloud provider like Groq (`llama-3.3-70b-versatile`). |
| **Enthusiast / High-VRAM Workstations** | **12 – 24 GB+ VRAM** or **32 – 64 GB+ Fast Unified/System RAM** | `gemma3:12b`, `qwen2.5:14b`, or `timheinrich2011/verity-3b:latest` | ~8.1 – 19 GB | Maximum nuance and roleplay depth. *(Note: `timheinrich2011/verity-3b:latest` is an unquantized ~19 GB image; only use it if you have enough VRAM/RAM to avoid slow disk swap).* |

To install any of these models before launching the game, run:
```bash
ollama pull qwen2.5:7b      # Recommended for 8GB+ VRAM
ollama pull llama3.2:3b     # Recommended for 4-6GB VRAM / Laptops
ollama pull llama3.2:1b     # Recommended for Low-End / Integrated Graphics
```

### 3. Text-to-Speech (TTS) & Microphone Voice Input
- **Text-to-Speech (TTS)** *(Default: `SHERPA`)*:
  - Enabled out of the box! Whenever Vic replies, unboxes, or awakens, he speaks his dialogue locally with stage-aware pitch and corruption effects (bright and cheerful in Stage 0 -> uncanny in Stage 1 -> glitching in Stage 2 -> deep demonic distortion in Stage 3).
  - **`SHERPA`** runs Piper neural TTS in-process with Sherpa-ONNX and a bundled 78 MB English voice model and pronunciation lexicon. It extracts these to `config/vic/tts/` on first use; the mod jar includes native runtimes for Linux, macOS, and Windows x64/ARM64. The voice is trained on LibriTTS-R (CC BY 4.0; attribution is included in the mod resources). Server replies send text and stage to each target player's client, so speech plays locally in multiplayer. If Sherpa cannot initialize, Vic tries the external Piper CLI, system TTS (`espeak-ng`/`spd-say`/`say`), then the built-in **Formant Synth (`SYNTH`)**.
  - **`PIPER`** remains available for an external Piper CLI and a separately installed voice model. Also supports **Remote API TTS (`OPENAI_TTS`)** (`/v1/audio/speech`). Test or change TTS in the **`V`** Config Menu (click **`Test`** to cycle stages); set the OpenAI-compatible TTS voice there as well. You can also use `/vic tts test [0-3]` and `/vic tts <sherpa|piper|local|synth|system|openai_tts|off>`.
- **Microphone Voice Input (Push-to-Talk)** *(Default: `SHERPA`)*:
  - **Hold `R`** (or **Hold `D-Pad Up`** on a PS5/Xbox controller) in-game to record your voice and release it to transcribe and send your message directly to Vic (rebindable under *Options -> Controls -> Key Binds -> Vic Mod*).
  - Or run `/viclisten [seconds]` in chat to record for a fixed duration using your client microphone. Transcribed requests go privately to Vic through `/vic say`, not to public chat.
  - Defaults to **`SHERPA`**, which uses the bundled, offline English Zipformer-small model through Sherpa-ONNX. The model is Apache-2.0 licensed and is extracted to `config/vic/stt/` on first use. **`LOCAL_CLI`** is also fully local; **`AUTO`** tries Sherpa, then an installed local CLI, then the configured Whisper API fallback (audio may be sent remotely).

### 4. PS5 DualSense & Xbox Controller Support (`Controlify` / `MidnightControls` + Built-in Mode)
- **Recommended Controller Mods**: Drop **[Controlify](https://modrinth.com/mod/controlify)** *(plus [YetAnotherConfigLib](https://modrinth.com/mod/yacl))* or **[MidnightControls](https://modrinth.com/mod/midnightcontrols)** for Fabric `1.21.1` into your `mods/` folder (or `run/mods/` when using `./gradlew runClient`).
  - When **Controlify** or **MidnightControls** is installed, Vic automatically detects it (`FabricLoader.isModLoaded`) and yields movement, camera, radial menus, and inventory snapping to the controller mod so there are zero double-input conflicts.
  - All three Vic actions are registered as standard Fabric keybindings under the **Vic Mod** category so you can bind them to any controller button or Radial Menu slot inside Controlify / MidnightControls:
    - **Push to Talk (Speak to Vic)** (`R` / **Hold D-Pad Up**)
    - **Quick-Toggle Flashlight** (`G` / **D-Pad Down**)
    - **Open Vic Config Menu** (`V` / **D-Pad Right**)
- **Built-in Fallback Mode**: Even without an external controller mod installed, connecting a **PS5 DualSense** or **Xbox Controller** (USB or Bluetooth) enables built-in gamepad support:
  - **Left Stick / Right Stick**: Movement & smooth camera look (`L3` = Sprint toggle, `R3` = Sneak toggle).
  - **R2 / RT**: Attack / Mine | **L2 / LT**: Use Item / Unbox Vic / Toggle Flashlight.
  - **L1 / LB & R1 / RB**: Cycle Hotbar slots.
  - **Cross (PS5) / A (Xbox)**: Jump (or click buttons in menus).
  - **Circle (PS5) / B (Xbox)**: Drop item (or close open menus).
  - **Square (PS5) / X (Xbox)** or **D-Pad Down**: Quick-equip & toggle **Flashlight** (`vic:flashlight`) from hotbar/hands.
  - **Triangle (PS5) / Y (Xbox)**: Open Inventory.
  - **D-Pad Up (Hold)**: **Push-to-Talk Voice Input** to Vic.
  - **D-Pad Right** or **Create / Back**: Open **Vic Config Screen**.
  - **D-Pad Left**: Toggle Camera Perspective (`F5`).
- Toggle controller support anytime via `/vic config controller <true|false>`.

---

## How to Test Features In-Game

Create a new Singleplayer world with **Allow Commands (Cheats) ON**:

### 1. First-Join Delivery & Box Unsealing
- When joining a world for the first time, you automatically receive a **Flashlight** (`vic:flashlight`) and a yellow delivery package (`vic:vic_box`) spawns directly in front of you.
- **Right-click** the box to unseal it, spawning **Vic** (`vic:vic`) and beginning the 5-day trial countdown.
- A live **Day & Karma HUD panel** in the top-left corner of your screen tracks the current **Day**, **Stage**, and a visual **Karma meter bar (`-100` to `+100`)** in real time (toggleable via `/vic config hud <true|false>`).

### 2. Talking to Vic, Rolling Memory & Item Offerings
- Send a message in standard game chat or run `/vic say <message>` while near Vic.
- **Colors & Personalities**: Change Vic's color and personality at any time via the **`V` Config Menu (`Persona` button)**, `/vic color <yellow|blue|green|pink|purple>`, or by **right-clicking Vic with a Dye**:
  - **Yellow (`YELLOW` — Cheerful)**: Classic overly enthusiastic best friend whose corporate smile slowly cracks.
  - **Blue (`BLUE` — Compulsive Liar)**: Shameless liar and gaslighter who invents hilarious, blatantly false "facts" about Minecraft.
  - **Green (`GREEN` — Innocent & Pure)**: Childlike, gentle, and wholesome; apologizes to blocks and begs you to hide when his shell starts cracking.
  - **Pink (`PINK` — Very Loving)**: Overwhelmingly affectionate, clingy, and devoted (escalating into an obsessive yandere in later stages).
  - **Purple (`PURPLE` — Cynic)**: Deadpan, sarcastic, and world-weary; roasts your survival skills while begrudgingly helping you.
- **Rolling Conversation Memory**: Vic remembers your last 8 conversation turns (persisted in world NBT) across both LLM (`Ollama` / `OpenAI`) and scripted modes.
- **Proactive Event Reactions**: Vic comments dynamically when your health drops critically low (even tossing emergency food in early stages), when night falls, or after long periods of silence.
- **Physical Item Offerings (Right-Click Vic)**:
  - **Legendary (`+20` Karma + Buffs)**: Enchanted Golden Apple, Nether Star.
  - **Precious (`+10` Karma)**: Golden Apple, Diamond, Emerald, Amethyst Shard.
  - **Circuit Snacks (`+6` Karma)**: Redstone, Gold Ingot, Copper Ingot, Quartz.
  - **Sweet Treats & Flowers (`+5` Karma)**: Flowers, Cookies, Cake, Berries, Apples, Honey, Bread.
  - **Garbage / Insults (`-10` Karma + Recoil)**: Rotten Flesh, Spider Eye, Dirt, Cobblestone, Gravel.
- Ask Vic for help directly — he can **give items** (food, golden apples, torches, iron) and **build for you**: a **Chest**, a **Furnace**, or an **Iron Wall** (e.g. `/vic say build me a chest`, `make me a furnace`, or "put up an iron wall"). He places them in the open space in front of you and shows a preview before confirming.

### 3. Boss Fight & Victory Ending (`The Vic Demon` & `Vic's Core`)
- When the 5-day trial expires (`/vic setday 6`), **The Vic Demon** (`vic:vic_demon`) awakens with a **Server Boss Bar**, a telegraphed **Corruption Beam** (which can be interrupted and blinded by aiming your active **Flashlight** at its face), and a **Shadow Leap & Knockback Slam**.
- Defeating the Demon triggers the **Trial Survived [Victory]** ending, clears the curse, updates the HUD, and drops **Vic's Core** (`vic:vic_core`) — an epic trophy item that purges Darkness/Wither and grants Night Vision, Regeneration II, and Speed II when activated.

### 4. Commands (`/vic` & `/vicconfig`)
- `/vicconfig` *(or press `V`)* — Open the in-game Vic Configuration GUI.
- `/vic status` — View current countdown status, Day (`1`–`5`), Karma (`-100` to `100`), Stage (`1`–`4`), Demon status, and active AI model.
- `/vic say <message>` — Send a message directly to Vic.
- `/vic ollama list` — List locally installed Ollama models with clickable selection buttons.
- `/vic ollama <model>` — Use a specific local Ollama model.
- `/vic provider <scripted|ollama|openai>` — Switch the AI backend.
- `/vic config <countdownDays|worldDecay|trueDarkness|autoSpawnBox> <value>` — Update gameplay settings via command.
- `/vic setkarma <value>` *(OP level 2)* — Set Karma between `-100` and `100` to test different behavioral stages.
- `/vic setday <day>` *(OP level 2)* — Jump to a specific day (`1`–`30`):
  - `/vic setday 1` — Reset the trial countdown to Day 1; if the Vic Demon is active, restore Vic.
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
