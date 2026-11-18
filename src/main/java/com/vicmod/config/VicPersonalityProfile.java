package com.vicmod.config;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum VicPersonalityProfile {
    CLASSIC(
            "CLASSIC",
            "Classic (ARG Companion)",
            "Friendly, eager, and subtly unhinged as time runs out.",
            ChatFormatting.YELLOW,
            "Tone: Cheerful, enthusiastic, and eager to help, with subtle eerie undertones as the countdown progresses."
    ),
    STOIC(
            "STOIC",
            "Stoic (Cold Observer)",
            "Calm, detached, and clinical. Treats reality as raw data.",
            ChatFormatting.GRAY,
            "Tone: Calm, emotionally detached, laconic, and clinical. Treats the player's survival as a controlled experiment."
    ),
    MELANCHOLY(
            "MELANCHOLY",
            "Melancholy (Grieving)",
            "Sad, poetic, and sorrowful about the inevitable transformation.",
            ChatFormatting.BLUE,
            "Tone: Somber, poetic, sorrowful, and reflective. Mourns the fragility of mortal life and fears the coming awakening."
    ),
    AGGRESSIVE(
            "AGGRESSIVE",
            "Aggressive (Impatient)",
            "Cynical, confrontational, and contemptuous of weakness.",
            ChatFormatting.RED,
            "Tone: Cynical, blunt, impatient, and caustic. Demands strength and mocks human hesitation."
    ),
    ANALYTICAL(
            "ANALYTICAL",
            "Analytical (Scientific)",
            "Hyper-logical, calculating probabilities and structural mechanics.",
            ChatFormatting.AQUA,
            "Tone: Hyper-logical, probabilistic, computing survival margins and referencing simulation parameters."
    );

    private final String id;
    private final String displayName;
    private final String description;
    private final ChatFormatting formatting;
    private final String promptInstruction;

    VicPersonalityProfile(String id, String displayName, String description, ChatFormatting formatting, String promptInstruction) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.formatting = formatting;
        this.promptInstruction = promptInstruction;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public ChatFormatting getFormatting() {
        return formatting;
    }

    public String getPromptInstruction() {
        return promptInstruction;
    }

    public VicPersonalityProfile next() {
        VicPersonalityProfile[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }

    public static VicPersonalityProfile fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return CLASSIC;
        }
        String clean = raw.trim().toUpperCase(Locale.ROOT);
        for (VicPersonalityProfile p : values()) {
            if (p.id.equalsIgnoreCase(clean) || p.name().equalsIgnoreCase(clean)) {
                return p;
            }
        }
        return CLASSIC;
    }
}
