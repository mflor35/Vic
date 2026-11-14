# GEMINI.md — Vic Mod (Verity Minecraft Mod Clone)

## Project Overview
**Vic** (`com.vicmod`) is a Fabric Minecraft mod inspired by and cloned from the **Verity** Minecraft ARG / psychological horror mod.
When a player joins a world for the first time, a mysterious yellow delivery box (`VIC-UNIT-01`) spawns in front of them alongside a starter **Flashlight**. Opening the box releases **Vic**, a floating yellow spherical AI companion who chats with the player, synthesizes items (food, torches, iron), and scans for villages or diamond veins.

Behind his cheerful persona, a **5-day trial countdown** and **Karma system (`-100` to `100`)** govern 4 progressive corruption stages:
- **Stage 0 (Day 1 / High Karma)**: Cheerful, polite, helpful best friend.
- **Stage 1 (Day 2)**: Slightly uncanny remarks about watching the player sleep and how quiet the world is becoming.
- **Stage 2 (Day 3–4 / Low Karma)**: Cracking corporate mask, passive animals subtly vanish in puffs of smoke (**World Decay**), and **Nighttime True Darkness** sets in unless holding an active Flashlight.
- **Stage 3 (Final Day / Very Low Karma)**: Glitching, hostile dialogue; warns the player not to hide on the surface; tosses Bedrock if insulted.
- **Stage 4 (Trial Expired / Day > `countdownDays`)**: Vic transforms into **The Vic Demon** (`vic:vic_demon`), a 3.8-block tall wall-climbing, door-breaking stalker that inflicts darkness and can only be stunned/repelled by aiming an active **Flashlight** beam at its face.

---

## Core Design Invariants (Must Always Be Preserved)

1. **Single Vic Per World / Session**:
   - Only **one** Vic character (`VicBoxEntity`, `VicEntity`, or `VicDemonEntity`) is allowed to exist in a world/session at any time.
   - Enforced via `VicWorldState.tryClaimSingletonVic(...)`, `ServerEntityEvents.ENTITY_LOAD` in `VicMod`, and entity `tick()` checks.
   - Transitions (`VicBoxEntity -> VicEntity` on unbox, and `VicEntity -> VicDemonEntity` on trial expiration) must atomically transfer the singleton slot using `VicWorldState.transferSingletonVic(oldEntity, newEntity)` before discarding the old entity.

2. **Local-First AI, TTS, and Voice Input by Default**:
   - **LLM Provider (`aiProvider`)**: Defaults to `"OLLAMA"` (`http://localhost:11434`, default model `timheinrich2011/verity-3b:latest` with `"think": false`, auto-falling back to the first installed model from `/api/tags` if needed, and falling back to built-in offline scripted dialogue if Ollama is unreachable). Also supports `"OPENAI"` (Groq/OpenAI) and `"SCRIPTED"`.
   - **Text-to-Speech (`ttsMode`)**: Defaults to `"LOCAL"` (`enableTts = true`). Uses local OS TTS (`espeak-ng`, `espeak`, `spd-say`, macOS `say`, Windows SAPI) with stage-adjusted pitch/speed, or the zero-dependency built-in Formant Speech Synthesizer (`SYNTH`) in `VicTtsService`.
   - **Voice Input / Push-to-Talk (`sttMode`)**: Defaults to `"LOCAL_CLI"` (`enableVoiceInput = true`, Push-to-Talk key **`R`**). Records `16kHz 16-bit mono PCM` via `arecord` (PipeWire/PulseAudio safe) or Java `TargetDataLine`, wraps a valid 44-byte RIFF WAV header, and transcribes via local CLI (`faster-whisper`, `whisper-cli`, `vosk-transcriber`, Python `speech_recognition`) or `WHISPER_API`.

3. **In-Game Configuration**:
   - Accessible anytime via keybind **`V`** (`key.vic.open_config`) or client command **`/vicconfig`** (`VicConfigScreen`).
   - All config changes persist to `config/vic.json` via `VicConfig.save()`.

---

## Project Structure

```text
src/
├── main/
│   ├── java/com/vicmod/
│   │   ├── VicMod.java                 # Main entrypoint, /vic commands, world tick progression, singleton enforcement
│   │   ├── ai/VicAiService.java        # Ollama (/api/chat), OpenAI/Groq, offline scripted responses, [ACTION:*] tag execution
│   │   ├── config/VicConfig.java       # JSON config loader/saver (config/vic.json)
│   │   ├── entity/
│   │   │   ├── VicBoxEntity.java       # Unsealable delivery package (VIC-UNIT-01)
│   │   │   ├── VicEntity.java          # Floating spherical companion (Stages 0–3)
│   │   │   └── VicDemonEntity.java     # Hostile wall-climbing demon after trial expiration
│   │   ├── item/FlashlightItem.java    # Toggleable flashlight (wards off night darkness & stuns Vic Demon)
│   │   ├── registry/
│   │   │   ├── ModEntities.java        # EntityType registrations & attributes
│   │   │   └── ModItems.java           # Item & SpawnEgg registrations + Creative tab entries
│   │   ├── state/VicWorldState.java    # PersistentState NBT (day, karma, countdown, singleton UUID)
│   │   └── voice/
│   │       ├── VicTtsService.java      # Stage-aware local/synth/API Text-to-Speech
│   │       └── VicVoiceInputService.java # Microphone capture & local/Whisper Speech-to-Text
│   └── resources/
│       ├── fabric.mod.json             # Mod metadata ("minecraft": ">=1.21")
│       └── assets/vic/                 # Textures (vic_stage0..3, vic_box, vic_demon, flashlight), models, lang
└── client/
    └── java/com/vicmod/client/
        ├── VicModClient.java           # Client entrypoint, renderers, [V] config key & [R] Push-to-Talk key
        ├── gui/VicConfigScreen.java    # In-game configuration & model/voice testing GUI
        ├── model/                      # Entity models (VicBoxEntityModel, VicEntityModel, VicDemonEntityModel)
        └── render/                     # Entity renderers
```

---

## Build, Run & Test Commands

- **Run Development Client**:
  ```bash
  ./gradlew runClient
  ```
- **Build Release `.jar`**:
  ```bash
  ./gradlew build
  ```
  Output artifact: `build/libs/vic-1.0.0.jar`

- **Key In-Game Commands**:
  - `/vicconfig` (or press **`V`**) — Open the in-game configuration GUI.
  - Hold **`R`** — Push-to-Talk microphone voice input to Vic.
  - `/vic status` — Display countdown, Day, Karma, Stage, active AI model, and TTS/STT status.
  - `/vic ollama list` / `/vic ollama <model>` — Scan and select local Ollama models.
  - `/vic tts test [0-3]` — Preview Vic's stage-aware TTS voice.
  - `/vic listen [seconds]` — Record microphone voice via command.
  - `/vic setday <1-30>` / `/vic setkarma <-100..100>` — Test stage transitions and Demon awakening.

---

## Supporting the Newest Minecraft Versions (`1.21.x` & Beyond)

### 1. Runtime Version Compatibility (`fabric.mod.json`)
- `src/main/resources/fabric.mod.json` uses `"minecraft": ">=1.21"` and `"fabric-api": "*"` so Fabric Loader does not block loading on newer `1.21.x` point releases.

### 2. Updating `gradle.properties` for Newer `1.21.x` Releases (e.g., `1.21.4` / `1.21.5+`)
When bumping `minecraft_version`, `yarn_mappings`, and `fabric_version` in `gradle.properties` to `1.21.2`–`1.21.5+`, keep these Yarn/Mojang API changes in mind:
1. **Registry Keys on Items & Entities (`1.21.2+`)**:
   - `Item.Settings` and `EntityType.Builder.build(...)` require passing the `RegistryKey<Item>` / `RegistryKey<EntityType<?>>` before construction (`new Item.Settings().registryKey(key)` and `.build(key)`).
2. **Entity Render States (`1.21.2+`)**:
   - `MobEntityRenderer<T, S, M>` introduces an `EntityRenderState` type parameter (`S extends LivingEntityRenderState`). For `VicEntityRenderer`, store `corruptionStage` on a custom `VicEntityRenderState extends LivingEntityRenderState` inside `updateRenderState(VicEntity entity, VicEntityRenderState state, float tickDelta)`.
3. **World & Player Helpers**:
   - Keep NBT persistence in `VicWorldState` backward-compatible so existing worlds upgrade cleanly.
