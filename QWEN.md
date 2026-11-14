# QWEN.md — Vic Mod (Verity Minecraft Mod Clone)

## Project Summary
**Vic** (`com.vicmod`) is a Fabric Minecraft mod that clones the mechanics and atmosphere of the **Verity** Minecraft ARG / psychological horror mod.
Players receive a mysterious yellow delivery package (`VIC-UNIT-01`) and a **Flashlight** on their first join. Unsealing the box spawns **Vic**, a floating yellow spherical AI assistant who converses with the player via text or voice, drops helpful items, and scans for nearby ores or villages—while secretly counting down a **5-day trial period** that culminates in his transformation into **The Vic Demon**.

---

## Key Architectural Rules for Coding Agents

1. **Singleton Vic Constraint**:
   - At most **one** Vic entity (`VicBoxEntity`, `VicEntity`, or `VicDemonEntity`) may exist in a world/game session at any time.
   - Always check `VicWorldState.tryClaimSingletonVic(server, entity)` on entity load/tick and use `VicWorldState.transferSingletonVic(oldEntity, newEntity)` when transitioning between `VicBoxEntity -> VicEntity -> VicDemonEntity`.

2. **Local-First Defaults (Ollama + Local TTS + Local STT)**:
   - **LLM Backend (`VicConfig.aiProvider`)**: Defaults to `"OLLAMA"` (`http://localhost:11434`, model `timheinrich2011/verity-3b:latest`, `"think": false`). `VicAiService` automatically starts `ollama serve` if installed locally, auto-detects installed models via `/api/tags` if the default model is missing, and falls back to offline scripted responses if unreachable.
   - **Text-to-Speech (`VicConfig.ttsMode`)**: Defaults to `"LOCAL"` (`enableTts = true`). `VicTtsService` uses local system speech synthesis (`espeak-ng`, `espeak`, `spd-say`, `say`, Windows SAPI) or its built-in zero-dependency **Formant Speech Synthesizer (`SYNTH`)** with corruption-stage pitch and timbre modulation.
   - **Voice Input (`VicConfig.sttMode`)**: Defaults to `"LOCAL_CLI"` (`enableVoiceInput = true`). Holding **`R`** records `16kHz 16-bit mono` audio (`VicVoiceInputService`) and transcribes it locally (`faster-whisper`, `whisper-cli`, `vosk-transcriber`, Python `speech_recognition`) or via `WHISPER_API`.

3. **Action Tag Protocol in `VicAiService`**:
   - Both LLM prompts and scripted responses use trailing action tags that `VicAiService.processAndDeliverResponse` strips before displaying/speaking and executes in-world:
     - `[ACTION:GIVE_FOOD]` — Tosses 3 Cooked Beef.
     - `[ACTION:GIVE_TORCH]` — Tosses 8 Torches.
     - `[ACTION:GIVE_IRON]` — Tosses 3 Iron Ingots.
     - `[ACTION:GIVE_BEDROCK]` — Tosses 1 Bedrock + plays Elder Guardian curse when Karma is low / player is rude.
     - `[ACTION:LOCATE_VILLAGE]` — Scans for nearest village structure.
     - `[ACTION:LOCATE_DIAMOND]` — Scans nearby chunks for diamond ore veins.

---

## Important Files & Responsibilities

| File | Responsibility |
| :--- | :--- |
| `src/main/java/com/vicmod/VicMod.java` | Main mod initializer, `/vic` commands, singleton entity enforcement, world decay & night darkness ticks |
| `src/main/java/com/vicmod/config/VicConfig.java` | Persistent JSON configuration (`config/vic.json`) |
| `src/main/java/com/vicmod/state/VicWorldState.java` | World-level NBT state (Day, Karma, Stage computation, Singleton `activeVicUuid`) |
| `src/main/java/com/vicmod/ai/VicAiService.java` | Ollama & OpenAI/Groq HTTP clients, system prompt builder, offline dialogue fallback, action tags |
| `src/main/java/com/vicmod/voice/VicTtsService.java` | Stage-aware local OS TTS, built-in Formant Synthesizer (`SYNTH`), and OpenAI TTS |
| `src/main/java/com/vicmod/voice/VicVoiceInputService.java` | Push-to-Talk (`R`) mic recording (`arecord` / Java `TargetDataLine`) and STT transcription |
| `src/main/java/com/vicmod/entity/VicBoxEntity.java` | Delivery package entity (`vic:vic_box`) |
| `src/main/java/com/vicmod/entity/VicEntity.java` | Floating companion sphere (`vic:vic`) |
| `src/main/java/com/vicmod/entity/VicDemonEntity.java` | Wall-climbing hostile demon (`vic:vic_demon`) |
| `src/main/java/com/vicmod/item/FlashlightItem.java` | Flashlight item (`vic:flashlight`) that counters darkness and stuns the Demon |
| `src/client/java/com/vicmod/client/VicModClient.java` | Client initializer, renderers, keybinds (`V` Config GUI, `R` Push-to-Talk) |
| `src/client/java/com/vicmod/client/gui/VicConfigScreen.java` | In-game configuration screen (`/vicconfig` or `V`) |

---

## Development & Testing Workflow

1. **Build & Compile Check**:
   ```bash
   ./gradlew build
   ```
2. **Launch Dev Client**:
   ```bash
   ./gradlew runClient
   ```
3. **In-Game Verification Checklist**:
   - Verify only one `vic:vic_box` / `vic:vic` / `vic:vic_demon` can exist at a time (test with spawn eggs or `/summon`).
   - Press **`V`** to open `VicConfigScreen`, test **Models** scan for Ollama, and click **Test** (TTS) and **Mic** (STT).
   - Hold **`R`** in-game to speak to Vic via microphone.
   - Test stage progression with `/vic setkarma -50` and `/vic setday 6`.

---

## Compatibility with Newest Minecraft Versions

- **Current Metadata**: `src/main/resources/fabric.mod.json` specifies `"minecraft": ">=1.21"`, allowing Fabric Loader to load the mod across `1.21.x` releases without strict version locking.
- **Porting / Building Against `1.21.2` – `1.21.5+`**:
  - Update `minecraft_version`, `yarn_mappings`, `loader_version`, and `fabric_version` in `gradle.properties` (check https://fabricmc.net/develop for exact versions).
  - If compiling directly against `1.21.2+` mappings:
    1. Pass `RegistryKey` into `Item.Settings#registryKey(...)` in `ModItems.java` and `EntityType.Builder#build(RegistryKey)` in `ModEntities.java`.
    2. Update `VicBoxEntityRenderer`, `VicEntityRenderer`, and `VicDemonEntityRenderer` to use `LivingEntityRenderState` introduced in `1.21.2`.
    3. Replace `ClickEvent` / `HoverEvent` constructors in `VicMod.java` if targeting `1.21.5+` where records (`ClickEvent.RunCommand`, `HoverEvent.ShowText`) are used.
