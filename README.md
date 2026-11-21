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
  - **Built-in Offline Scripted** *(Default — runs on any potato PC with 0 extra RAM, 0 extra CPU/GPU, instant responses, full 4-stage dialog, house building, and item synthesis)*
  - **Ollama (Local LLM)** *(Connects to your local Ollama server at `http://localhost:11434`, auto-starts `ollama serve` if needed, and auto-detects installed models. Automatically throttled with potato-safe limits like 1024 context and max 2 threads)*
  - **Remote API (OpenAI / Groq)** *(Connects to any OpenAI-compatible `/v1/chat/completions` endpoint)*
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
| **Potato / Ultra-Low-End Devices** *(Default)* | **< 8 GB RAM** or older dual/quad-core CPUs (or no GPU) | **Built-in Scripted Mode** *(Default)* or **Remote API** (`/vic provider openai`) | 0 MB (Local) | Default out-of-the-box configuration. Zero extra RAM/VRAM overhead and instant 0ms response time. Works 100% offline with full 4-stage progression, building, and material synthesis. If you want LLMs on a potato, offload to a fast free/cheap cloud provider like Groq (`llama-3.3-70b-versatile`). |
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

### 4. Controller Support (`Controlify` / `Controllify` / `MidnightControls` + Built-in Mode)
- **Controlify & Controllify Compatibility**:
  - Full, native compatibility with **[Controlify](https://modrinth.com/mod/controlify)** (also known as `Controllify`).
  - All three Vic actions are registered under their own official **Vic Mod** category (`VIC_CATEGORY`) so they cleanly appear grouped in Controlify's Controller Settings:
    - **Push to Talk (Speak to Vic)** (`key.vic.voice_ptt`, default `R`)
    - **Quick-Toggle Flashlight** (`key.vic.toggle_flashlight`, default `G`)
    - **Open Vic Config Menu** (`key.vic.open_config`, default `V`)
  - **Radial Menu Custom Icons**: Built-in `assets/controlify/radial_icons.json` maps each action to its actual 3D item model (`vic:item/flashlight`, `minecraft:item/echo_shard`, `vic:item/vic_core`) when using Controlify's radial menu.
  - **Zero Double-Input Conflicts**: When Controlify or MidnightControls is detected, Vic automatically yields all gamepad polling, camera rotation, and movement driving to the controller mod, avoiding SDL hardware contention.
- **Built-in Gamepad Mode**: Even without an external controller mod installed, connecting a **PS5 DualSense** or **Xbox Controller** (USB or Bluetooth) enables built-in gamepad support:
  - **Left Stick / Right Stick**: Movement & responsive camera look with per-frame rendering and **progressive turn-around acceleration** (180° turnaround in ~0.25–0.4s).
    - `L3`: Sprint toggle.
    - `R3`: Sneak toggle (or **Pull Left Stick Back + Click R3** for an instant **Quick 180° Turn**).
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
- **Colors & Personalities**: Change Vic's color, appearance, and personality at any time via the **`V` Config Menu (`Persona` button)**, `/vic color <yellow|blue|green|pink|purple|axolotl|freddy|foxy|bonnie|springtrap>`, or by **right-clicking Vic with dyes or theme items**:
  - **Yellow (`YELLOW` — Cheerful)**: Classic overly enthusiastic best friend whose corporate smile slowly cracks.
  - **Blue (`BLUE` — Compulsive Liar)**: Shameless liar and gaslighter who invents hilarious, blatantly false "facts" about Minecraft.
  - **Green (`GREEN` — Innocent & Pure)**: Childlike, gentle, and wholesome; apologizes to blocks and begs you to hide when his shell starts cracking.
  - **Pink (`PINK` — Very Loving)**: Overwhelmingly affectionate, clingy, and devoted (escalating into an obsessive yandere in later stages).
  - **Purple (`PURPLE` — Cynic)**: Deadpan, sarcastic, and world-weary; roasts your survival skills while begrudgingly helping you.
  - **Axolotl (`AXOLOTL` — Playful)**: Cute pink amphibious friend with animated flapping gills that squeaks and bloops *(Right-click with a Water Bucket, Tropical Fish, or Axolotl Bucket)*.
  - **Freddy Fazbear (`FREDDY` — FNAF)**: The animatronic frontman with a 3D top hat and bear ears, "hur-hur-hur-hur" laughs, pizza, and nighttime security guard lore *(Right-click with a Jukebox or Leather, or Brown Dye)*.
  - **Foxy the Pirate (`FOXY` — FNAF)**: Pirate fox with 3D fox ears, snout, eyepatch, hook, and sprint dashes *(Right-click with a Fishing Rod, Shears, or Red Dye)*.
  - **Bonnie the Bunny (`BONNIE` — FNAF)**: Lead guitarist purple rabbit with 3D bunny ears that twitch when corrupted *(Right-click with a Rabbit Foot, Carrot, or Purple Dye)*.
  - **Springtrap (`SPRINGTRAP` — FNAF)**: Decayed golden-olive green rabbit with mechanical wheezing and "I always come back" *(Right-click with a Tripwire Hook, Flint and Steel, or Green Dye)*.
- **Rolling Conversation Memory**: Vic remembers up to 24 conversation turns (persisted in world NBT) across both LLM (`Ollama` / `OpenAI`) and scripted modes.
- **Personality Profiles & Emotional Spectrum**: Configure personality via `/vic profile <classic|stoic|melancholy|aggressive|analytical>` or the `V` menu. Vic's emotional state (`CURIOUS`, `BALANCED`, `ANXIOUS`, `HOSTILE`, `DEMONIC`) reflects his karma and trial day, showing dynamic visual/aural cues and an in-game HUD badge.
- **Complex Survival Tasks**: Assign multi-step tasks such as shelter construction, iron mining, or food gathering via `/vic task start <shelter|iron|food>` or in natural chat.
- **Truce & Stabilization**: Offer high-tier items (Vic Core, Netherite Ingot, Golden Apple) or run `/vic truce` to stabilize Vic for 24,000 ticks (1 in-game day), freezing the trial countdown and world decay.
- **Shadow Wisps & Ambient Decay**: Dark areas manifest non-lethal Shadow Wisps (`vic:shadow_wisp`) that flee from high karma and flashlights. Late trial days feature escalating ambient audio and psychological distortion.
- **Proactive Event Reactions**: Vic comments dynamically when your health drops critically low (even tossing emergency food in early stages), when night falls, or after long periods of silence.
- **Physical Item Offerings (Right-Click Vic)**:
  - **Legendary (`+20` Karma + Buffs)**: Enchanted Golden Apple, Nether Star.
  - **Precious (`+10` Karma)**: Golden Apple, Diamond, Emerald, Amethyst Shard.
  - **Circuit Snacks (`+6` Karma)**: Redstone, Gold Ingot, Copper Ingot, Quartz.
  - **Sweet Treats & Flowers (`+5` Karma)**: Flowers, Cookies, Cake, Berries, Apples, Honey, Bread.
  - **Garbage / Insults (`-10` Karma + Recoil)**: Rotten Flesh, Spider Eye, Dirt, Cobblestone, Gravel.
- **Hyper-Realistic Walking Body & Strides**:
  - Instead of floating, Vic and all personas walk on the ground with hyper-realistic anatomical models and fluid motion physics: natural walking strides, leg and arm swings, torso bounce and sway, organic breathing, smooth head tracking, and dynamic persona attachments (fluttering axolotl gills and aquatic tail, moving Foxy fox tail and pirate hook, Freddy microphone and top hat, Bonnie/Springtrap bunny ears and puff tail).
  - During later corruption stages, Vic displays eerie analog horror micro-twitches and psychological breakdown postures.
- **Architect & Universal Materializer**:
  - **Full House Construction**: Ask Vic to build a house (e.g. `/vic say build me a house`, `make a house`, or `build a home`). Vic clears the area in front of you and constructs a complete, stylish, fully furnished survival house with wood pillars, plank walls, glass pane windows, an oak door, a hanging ceiling lantern, a cozy red bed, a crafting table, a furnace, and a storage chest!
  - **Universal Material & Item Synthesis**: Tell Vic to give you any material or block in the game! (e.g. `/vic say give me diamonds`, `give me 64 obsidian`, `give me netherite`, `give me a stack of oak logs`, `give me gold`). Vic automatically resolves the item, synthesizes the exact requested count (up to 64), and tosses it directly to your feet!
  - Also builds individual **Chests**, **Furnaces**, and **Iron Barriers** upon request.

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
- `/vic profile <classic|stoic|melancholy|aggressive|analytical>` — Change Vic's AI personality profile.
- `/vic task [start <shelter|iron|food> | clear]` — Manage active multi-step survival tasks.
- `/vic truce` *(OP level 2)* — Temporarily stabilize Vic and pause the trial clock for 1 in-game day.

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
