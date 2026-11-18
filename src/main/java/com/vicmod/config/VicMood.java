package com.vicmod.config;

import net.minecraft.ChatFormatting;

public enum VicMood {
    CURIOUS("CURIOUS", "Curious", ChatFormatting.GREEN, 0x55FF55, ":)"),
    BALANCED("BALANCED", "Balanced", ChatFormatting.YELLOW, 0xFFFF55, ":|"),
    ANXIOUS("ANXIOUS", "Anxious", ChatFormatting.GOLD, 0xFFAA00, ":/"),
    HOSTILE("HOSTILE", "Hostile", ChatFormatting.RED, 0xFF5555, ":("),
    DEMONIC("DEMONIC", "Demonic", ChatFormatting.DARK_RED, 0xAA0000, "!!");

    private final String id;
    private final String displayName;
    private final ChatFormatting formatting;
    private final int colorHex;
    private final String badge;

    VicMood(String id, String displayName, ChatFormatting formatting, int colorHex, String badge) {
        this.id = id;
        this.displayName = displayName;
        this.formatting = formatting;
        this.colorHex = colorHex;
        this.badge = badge;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatFormatting getFormatting() {
        return formatting;
    }

    public int getColorHex() {
        return colorHex;
    }

    public String getBadge() {
        return badge;
    }

    public static VicMood compute(int karma, int stage, boolean demonAwakened) {
        if (demonAwakened || stage >= 4 && karma < 0) {
            return DEMONIC;
        }
        if (karma <= -25 || stage >= 3) {
            return HOSTILE;
        }
        if (karma <= 5 || stage == 2) {
            return ANXIOUS;
        }
        if (karma >= 40) {
            return CURIOUS;
        }
        return BALANCED;
    }
}
