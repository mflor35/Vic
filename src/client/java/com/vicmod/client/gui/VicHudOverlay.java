package com.vicmod.client.gui;

import com.vicmod.client.input.VicControllerService;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicMood;
import com.vicmod.config.VicPersona;
import com.vicmod.entity.VicEntity;
import com.vicmod.network.VicHudStatePayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class VicHudOverlay implements HudElement {
    private static boolean hasReceivedState = false;
    private static boolean countdownActive = false;
    private static int currentDay = 1;
    private static int maxDays = 5;
    private static int karma = 25;
    private static int stage = 0;
    private static boolean demonAwakened = false;
    private static String moodId = "BALANCED";
    private static boolean stabilized = false;
    private static String activeTaskTitle = "";

    public static void updateState(VicHudStatePayload payload) {
        hasReceivedState = true;
        countdownActive = payload.countdownActive();
        currentDay = payload.currentDay();
        maxDays = Math.max(1, payload.maxDays());
        karma = Mth.clamp(payload.karma(), -100, 100);
        stage = Mth.clamp(payload.stage(), 0, 5);
        demonAwakened = payload.demonAwakened();
        moodId = payload.mood() != null ? payload.mood() : "BALANCED";
        stabilized = payload.stabilized();
        activeTaskTitle = payload.activeTaskTitle() != null ? payload.activeTaskTitle() : "";
    }

    public static void reset() {
        hasReceivedState = false;
        countdownActive = false;
        currentDay = 1;
        maxDays = VicConfig.get().countdownDays;
        karma = 25;
        stage = 0;
        demonAwakened = false;
        moodId = "BALANCED";
        stabilized = false;
        activeTaskTitle = "";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || client.gui.hud.getDebugOverlay().showDebugScreen()) {
            return;
        }
        if (!VicConfig.get().enableHudOverlay) {
            return;
        }

        int displayMaxDays = hasReceivedState ? maxDays : Math.max(1, VicConfig.get().countdownDays);
        int displayDay = hasReceivedState ? currentDay : 1;
        int displayKarma = hasReceivedState ? karma : 25;
        int displayStage = hasReceivedState ? stage : 0;

        boolean hasPad = VicControllerService.isControllerConnected();
        boolean hasTask = activeTaskTitle != null && !activeTaskTitle.isBlank();
        Font font = client.font;
        int boxX = 8;
        int boxY = 8;
        int boxWidth = 158;

        int extraHeight = (hasPad ? 14 : 0) + (hasTask ? 12 : 0);
        int boxHeight = 44 + extraHeight;

        VicPersona persona = resolveActivePersona(client);
        int personaHex = persona.getHexColor() & 0x00FFFFFF;
        int borderColor = switch (displayStage) {
            case 5 -> 0xCC55FF55;
            case 0, 1 -> stabilized ? 0xCCFFD700 : (0xCC000000 | personaHex);
            case 2 -> 0xCCFF5555;
            case 3 -> 0xCCAA0000;
            default -> 0xFFFF1111;
        };

        // Background panel & 1px border
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xB0101014);
        context.fill(boxX, boxY, boxX + boxWidth, boxY + 1, borderColor);
        context.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, borderColor);
        context.fill(boxX, boxY, boxX + 1, boxY + boxHeight, borderColor);
        context.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, borderColor);

        // Separate stratum so foreground text and bar render cleanly above background
        context.nextStratum();

        // Row 1: Day counter & mood face badge
        String dayLabel;
        int dayColor;
        if (displayStage == 5) {
            dayLabel = "TRIAL SURVIVED [VICTORY]";
            dayColor = 0xFF55FF55;
        } else if (demonAwakened) {
            dayLabel = "DAY " + displayDay + "/" + displayMaxDays + " [EXPIRED]";
            dayColor = 0xFFFF5555;
        } else if (stabilized) {
            dayLabel = "Day " + displayDay + "/" + displayMaxDays + " [TRUCE]";
            dayColor = 0xFFFFD700;
        } else if (countdownActive) {
            dayLabel = "Day " + displayDay + "/" + displayMaxDays + " [" + persona.getShortLabel() + "]";
            dayColor = displayStage >= 2 ? 0xFFFFAA00 : (0xFF000000 | personaHex);
        } else {
            dayLabel = "Day " + displayDay + "/" + displayMaxDays + " [" + persona.getShortLabel() + "]";
            dayColor = 0xFF000000 | personaHex;
        }

        VicMood mood;
        try {
            mood = VicMood.valueOf(moodId);
        } catch (Exception e) {
            mood = VicMood.compute(displayKarma, displayStage, demonAwakened);
        }

        String faceBadge = mood.getBadge();
        int badgeColor = 0xFF000000 | mood.getColorHex();

        context.text(font, dayLabel, boxX + 6, boxY + 5, dayColor, true);
        int badgeWidth = font.width(faceBadge);
        context.text(font, faceBadge, boxX + boxWidth - 6 - badgeWidth, boxY + 5, badgeColor, true);

        // Row 2: Karma value & Mood Stage text
        String karmaSign = displayKarma > 0 ? "+" : "";
        String karmaText = "Karma: " + karmaSign + displayKarma;
        int karmaColor;
        if (displayKarma >= 15) {
            karmaColor = 0xFF55FF55;
        } else if (displayKarma >= 0) {
            karmaColor = 0xFFFFFF55;
        } else if (displayKarma >= -35) {
            karmaColor = 0xFFFFAA00;
        } else {
            karmaColor = 0xFFFF5555;
        }

        String stageText = displayStage == 5 ? "CLEARED" : (demonAwakened ? "DEMON" : mood.getDisplayName());
        int stageWidth = font.width(stageText);

        context.text(font, karmaText, boxX + 6, boxY + 17, karmaColor, true);
        context.text(font, stageText, boxX + boxWidth - 6 - stageWidth, boxY + 17, badgeColor, true);

        // Row 3: Karma visual bar (-100 to +100)
        int barX = boxX + 6;
        int barY = boxY + 30;
        int barWidth = boxWidth - 12; // 146px
        int barHeight = 5;
        int centerBarX = barX + barWidth / 2;

        // Track background
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF26262E);

        // Fill from center (0) toward left (-100) or right (+100)
        int halfWidth = barWidth / 2;
        int fillPixels = Math.round((Math.abs(displayKarma) / 100.0f) * halfWidth);
        if (displayKarma > 0) {
            int fillColor = displayKarma >= 15 ? 0xFF33DD55 : 0xFFDDDD44;
            context.fill(centerBarX, barY + 1, Math.min(barX + barWidth, centerBarX + fillPixels), barY + barHeight - 1, fillColor);
        } else if (displayKarma < 0) {
            int fillColor = displayKarma <= -35 ? 0xFFDD2222 : 0xFFFF8800;
            context.fill(Math.max(barX, centerBarX - fillPixels), barY + 1, centerBarX, barY + barHeight - 1, fillColor);
        }

        // Center neutral tick mark (0)
        context.fill(centerBarX, barY, centerBarX + 1, barY + barHeight, 0xFF888888);

        // Current Karma cursor pip
        int pipX = Mth.clamp(centerBarX + (displayKarma >= 0 ? fillPixels : -fillPixels), barX, barX + barWidth - 2);
        context.fill(pipX, barY - 1, pipX + 2, barY + barHeight + 1, 0xFFFFFFFF);

        int currentY = boxY + 39;

        // Row 4 (Conditional): Active Task line
        if (hasTask) {
            context.text(font, activeTaskTitle, boxX + 6, currentY, 0xFF55FF55, true);
            currentY += 12;
        }

        // Row 5 (Conditional): Connected Controller quick-action prompt
        if (hasPad) {
            String padPrefix = switch (VicControllerService.getActiveProfile()) {
                case PS5 -> "PS5";
                case XBOX -> "Xbox";
                default -> "Pad";
            };
            if (VicControllerService.isExternalControllerModLoaded()) {
                padPrefix += " (" + VicControllerService.getExternalControllerModName() + ")";
            }
            String padHint = padPrefix + ": [D-Up] Mic [D-Dn] Light";
            context.text(font, padHint, boxX + 6, currentY, 0xFF55FFFF, true);
        }
    }

    private static VicPersona resolveActivePersona(Minecraft client) {
        if (client.level != null && client.player != null) {
            List<VicEntity> vics = client.level.getEntitiesOfClass(
                    VicEntity.class,
                    client.player.getBoundingBox().inflate(96.0),
                    Entity::isAlive
            );
            if (!vics.isEmpty()) {
                return vics.getFirst().getPersona();
            }
        }
        return VicConfig.get().getEffectivePersona();
    }
}
