# 💡 Vic Mod Feature Ideas - TODO List

## 1. Deepening the Psychological Horror & Narrative (Atmosphere & Tension)

*   **Ambient Soundscape Decay:** As the trial progresses (Days 3-5), introduce subtle, unique, and escalating ambient sounds tied to Vic's mood or world decay (e.g., distorted whispers, mechanical clicks, resonating hums).
    *   *Status:* [x] Implemented. Server tick triggers escalating ambient sound cues (`WARDEN_HEARTBEAT`, `SCULK_SHRIEKER_SHRIEK`, `AMETHYST_BLOCK_RESONATE`, `CROSSBOW_LOADING_MIDDLE`, `PORTAL_AMBIENT`) to nearby players during high stage or anxious/hostile moods.
*   **Shadow Entity Encounters (Pre-Demon):** Introduce minor, unpredictable, non-lethal entities (wisps/distorted figures) before the Vic Demon. They react to player actions (fleeing from high Karma, appearing in darkness) to serve as minor environmental stressors.
    *   *Status:* [x] Implemented. Added `ShadowWispEntity` (registered in `ModEntities`), non-lethal dark wisps spawning in dark environments during trial progression that dissolve on contact or flee when lit with a flashlight or when facing high karma.
*   **Memory Corruption/Unreliable Narration:** Implement a small mechanic where low Karma interactions cause the player to question reality (e.g., configuration settings reverting, Vic commenting on non-existent events).
    *   *Status:* [x] Implemented. Added system prompt guidelines and scripted unreality statements (phantom knocks, moved chests, distorted promises) when Vic is in `ANXIOUS`, `HOSTILE`, or `DEMONIC` mood states.
*   **Vic's "Mood" Visual/Aural Cue:** Give Vic a subtle, persistent visual or aural "mood ring." High Karma = soft glow/chuckle; Low Karma = visual static/discordant hum.
    *   *Status:* [x] Implemented. Synced `MOOD_STATE` on `VicEntity` spawning happy villager sparkles and chime notes for `CURIOUS`/`BALANCED`, and sculk charge / smoke particles with discordant bass hums for `ANXIOUS`/`HOSTILE`.

## 2. Enhancing AI Interaction & Gameplay Loop (Systemic Depth)

*   **Complex Tasking/Long-Term Memory:** Expand Vic's memory beyond 8 turns. Allow players to give multi-step, long-term tasks (e.g., "Build a shelter with iron and wood."), requiring Vic to track resources and plan actions over multiple chat interactions.
    *   *Status:* [x] Implemented. Expanded dialogue history to 24 turns in `VicWorldState`. Added `VicTask` tracking multi-step survival tasks (`BUILD_SHELTER`, `GATHER_IRON`, `FIND_FOOD`), command control (`/vic task start/step/clear`), and AI contextual awareness.
*   **Procedural Lore Reveals:** Allow Vic to randomly reference lore fragments or world details found in the environment (e.g., "The old map shows a strange nexus point..."), expanding the background story subtly.
    *   *Status:* [x] Implemented. Integrated lore revelations (subterranean resonance, laboratory logs, simulated skies, forgotten foundations) into Vic's prompt and scripted dialogue triggers.
*   **Intervention/Truce Mechanic:** Introduce a rare, high-Karma action that allows the player to "stabilize" Vic's state, temporarily pausing the trial clock or significantly reducing world decay.
    *   *Status:* [x] Implemented. Players can offer a Vic Core, Netherite Ingot, or Golden Apple to Vic (or run `/vic truce`) to gain 24,000 ticks (1 in-game day) of stabilization, pausing the trial countdown and halting world decay.

## 3. Expanding Systems & Player Agency (Mechanical Layering)

*   **Karma Spectrum (Stages of Vic):** Replace simple +/- Karma with distinct emotional stages (Curious, Anxious, Hostile, Demonic). This allows for nuanced entity and world behavior changes beyond just the 5-day clock.
    *   *Status:* [x] Implemented. Created `VicMood` enum (`CURIOUS`, `BALANCED`, `ANXIOUS`, `HOSTILE`, `DEMONIC`) derived from karma and trial stage, displayed on the HUD with stylized badges and synced to client renderers.
*   **Customizable AI Persona:** In `VicConfig`, allow users to select a specific **Personality Profile** (Stoic, Melancholy, Aggressive, Analytical) for Vic. The AI system prompt is dynamically adjusted based on this choice.
    *   *Status:* [x] Implemented. Added `VicPersonalityProfile` enum (`CLASSIC`, `STOIC`, `MELANCHOLY`, `AGGRESSIVE`, `ANALYTICAL`) configurable in `VicConfig`, adjustable in the GUI config screen and `/vic profile <profile>`, dynamically adjusting the LLM system prompt.
*   **Resource Dependency (Survival Layer):** Tie Vic's stability and world decay speed to the player's actual survival efforts (shelter, food). Prolonged neglect/lack of resources makes Vic more agitated and speeds up world decay.
    *   *Status:* [x] Implemented. `VicEntity` monitors nearby player health, hunger, and sky exposure; starvation or exposure raises agitation levels which accelerate world decay ticks, while feeding food to Vic calms agitation.
