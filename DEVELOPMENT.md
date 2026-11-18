# Development Setup

This guide covers setting up and running the Vic Fabric mod from source. Player-facing features and commands are documented in [README.md](README.md).

## Requirements

- [JDK 25](https://adoptium.net/temurin/releases/?version=25)
- [Git](https://git-scm.com/downloads)
- Internet access for the first Gradle dependency download
- An IDE is optional: [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) or [VS Code](https://code.visualstudio.com/) with the [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) can import the project as a Gradle workspace.

The project uses [Minecraft 26.x](https://www.minecraft.net/), [Fabric Loader](https://fabricmc.net/use/installer/), [Fabric API](https://modrinth.com/mod/fabric-api), official Mojang mappings, and the [Fabric Loom Gradle plugin](https://github.com/FabricMC/fabric-loom). The Gradle wrapper is included in the repository and downloads [Gradle 9.8.2](https://docs.gradle.org/9.8.2/userguide/gradle_wrapper.html) automatically. You do not need to install these game/build components separately or have a separate Minecraft installation to run the development client.

Sherpa-ONNX is used for local speech recognition and synthesis. Its [Java API](https://k2-fsa.github.io/sherpa/onnx/java-api/non-android-java.html), [native runtime](https://github.com/k2-fsa/sherpa-onnx), and the bundled [Zipformer-small English STT model](https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-small-en-2023-06-26) and [LibriTTS-R Piper TTS model](https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/libritts_r/medium) are documented here for reference. The model assets and supported native runtimes are already included in the mod build.

## Verify Java and Gradle

From the repository root, check that Java 25 is active and that the wrapper can run:

```sh
java -version
./gradlew --version
```

The Gradle wrapper downloads the configured Gradle 9.8.2 distribution on its first run. In your IDE, open the repository root and allow it to import/sync the Gradle project. Make sure the IDE's Gradle JVM is set to the JDK 25 linked above.

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Run the Development Client

Launch Minecraft with the mod and Fabric API loaded:

```sh
./gradlew runClient
```

The development game directory is `run/`. Worlds, logs, configuration, and downloaded runtime data are stored there and are separate from your regular Minecraft installation. Use a test world when trying commands that change progression or entities.

## Build and Validate

Compile common/server and client source sets:

```sh
./gradlew compileJava compileClientJava
```

Build the distributable mod:

```sh
./gradlew build
```

The mod jar is written to `build/libs/`. The build includes the bundled Sherpa voice models and the supported Sherpa native runtimes; first-use model extraction happens under the development instance's `run/config/vic/` directory.

## Optional Local Services

The default chat provider is [Ollama](https://ollama.com/download). To use it, start `ollama serve` if it is not already running, and download a model from the [Ollama model library](https://ollama.com/library). Vic connects to `http://localhost:11434` by default and attempts model discovery. You can switch to built-in scripted dialogue in the in-game config screen without Ollama.

Sherpa TTS and STT use bundled models and run locally; no external speech service is required for their default modes. The `PIPER` TTS fallback can use the [Piper CLI](https://github.com/rhasspy/piper) and a separately downloaded [Piper voice](https://huggingface.co/rhasspy/piper-voices). The `AUTO` STT mode may use its configured Whisper API fallback if local recognition fails. Remote transcription can use an [OpenAI-compatible audio transcription endpoint](https://platform.openai.com/docs/guides/speech-to-text) such as [Groq](https://console.groq.com/docs/speech-to-text); `OPENAI_TTS` uses an [OpenAI-compatible speech endpoint](https://platform.openai.com/docs/guides/text-to-speech). Remote modes require network access and suitable endpoint credentials.

## Troubleshooting

- **Wrong Java version:** Point `JAVA_HOME` and the IDE's Gradle JVM to a JDK 21 installation, then rerun `./gradlew --version`.
- **Gradle dependency resolution fails:** Check network access and retry `./gradlew --refresh-dependencies`.
- **Ollama replies fall back to scripted text:** Confirm Ollama is running, a model is installed, and the URL/model in the `V` config screen are correct.
- **No microphone audio:** Check OS microphone permission and input-device selection. Sherpa STT expects client microphone capture; its model runs locally after recording.
- **Speech playback falls back:** Check the game log for Sherpa initialization errors. The external Piper/system/synth fallbacks remain available from the `V` config screen.

For architectural rules and invariants when changing code, see [AGENTS.md](AGENTS.md).
