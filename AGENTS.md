# Agent Instructions

These are the shared project instructions for any coding agent working in this repository. Treat the current source code as authoritative when older documentation differs.

## Project Structure
- This is a Fabric mod targeting Minecraft 1.21.1 and Java 21. Use the Gradle wrapper.
- Common and server code belongs in `src/main/java`; client-only code belongs in `src/client/java`. Do not reference client-only APIs from common/server classes.
- `VicConfig` owns saved configuration, and `VicWorldState` owns persistent world progression.

## Behavioral Invariants
- Preserve the single-Vic invariant across `VicBoxEntity`, `VicEntity`, and `VicDemonEntity`. Use `VicWorldState.tryClaimSingletonVic` and `transferSingletonVic` when creating or transitioning these entities.
- AI actions are implemented across the prompt, scripted response logic, and server-side dispatch in `VicAiService`. Keep these paths consistent, validate actions on the server, and preserve cooldowns.
- Voice capture runs on the client. Send recognized requests privately through `/vic say`; do not broadcast transcripts as public chat. `LOCAL_CLI` must stay local, while `AUTO` may use its configured remote fallback.
- Keep user-facing setup instructions in `README.md` aligned with actual behavior.

## Development and Validation
- Follow nearby Java and Fabric patterns. Keep changes focused and avoid adding dependencies without a clear need.
- Inspect the owning implementation and nearby call sites before changing behavior. Preserve unrelated worktree changes.
- Compile both source sets with `./gradlew compileJava compileClientJava`. Run `./gradlew build` for broader verification when appropriate.
- For behavior changes, run the narrowest relevant check and update applicable documentation.