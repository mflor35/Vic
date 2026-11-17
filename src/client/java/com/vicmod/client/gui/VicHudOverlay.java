package com.vicmod.client.gui;

import com.vicmod.client.input.VicControllerService;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicPersona;
import com.vicmod.network.VicHudStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

public class VicHudOverlay {
    private static boolean hasReceivedState = false;
    private static boolean countdownActive = false;
    private static int currentDay = 1;
    private static int maxDays = 5;
    private static int karma = 25;
    private static int stage = 0;
    private static boolean demonAwakened = false;

    public static void updateState(VicHudStatePayload payload) {
        hasReceivedState = true;
        countdownActive = payload.countdownActive();
        currentDay = payload.currentDay();
        maxDays = Math.max(1, payload.maxDays());
        karma = MathHelper.clamp(payload.karma(), -100, 100);
        stage = MathHelper.clamp(payload.stage(), 0, 5);
        demonAwakened = payload.demonAwakened();
    }

    public static void reset() {
        hasReceivedState = false;
        countdownActive = false;
        currentDay = 1;
        maxDays = VicConfig.get().countdownDays;
        karma = 25;
        stage = 0;
        demonAwakened = false;
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
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
        TextRenderer font = client.textRenderer;
        int boxX = 8;
        int boxY = 8;
        int boxWidth = 148;
        int boxHeight = hasPad ? 52 : 40;

        VicPersona persona = VicConfig.get().getEffectivePersona();
        int personaHex = persona.getHexColor();
        int borderColor = switch (displayStage) {
            case 5 -> 0xCC55FF55;
            case 0, 1 -> 0xCC000000 | personaHex;
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

        // Row 1: Day counter & face badge
        String dayLabel;
        int dayColor;
        if (displayStage == 5) {
            dayLabel = "TRIAL SURVIVED [VICTORY]";
            dayColor = 0x55FF55;
        } else if (demonAwakened) {
            dayLabel = "DAY " + displayDay + "/" + displayMaxDays + " [EXPIRED]";
            dayColor = 0xFF5555;
        } else if (countdownActive) {
            dayLabel = "Day " + displayDay + "/" + displayMaxDays + " [" + persona.getShortLabel() + "]";
            dayColor = displayStage >= 2 ? 0xFFAA00 : personaHex;
        } else {
            dayLabel = "Day " + displayDay + "/" + displayMaxDays + " [" + persona.getShortLabel() + "]";
            dayColor = personaHex;
        }

        String faceBadge = switch (displayStage) {
            case 5 -> ":D";
            case 0 -> ":)";
            case 1 -> ":|";
            case 2 -> ":/";
            case 3 -> ":(";
            default -> "!!";
        };
        int badgeColor = switch (displayStage) {
            case 5 -> 0x55FF55;
            case 0 -> 0xFFE135;
            case 1 -> 0xFFAA00;
            case 2 -> 0xFF5555;
            default -> 0xCC0000;
        };

        context.drawTextWithShadow(font, dayLabel, boxX + 6, boxY + 5, dayColor);
        int badgeWidth = font.getWidth(faceBadge);
        context.drawTextWithShadow(font, faceBadge, boxX + boxWidth - 6 - badgeWidth, boxY + 5, badgeColor);

        // Row 2: Karma value & Stage text
        String karmaSign = displayKarma > 0 ? "+" : "";
        String karmaText = "Karma: " + karmaSign + displayKarma;
        int karmaColor;
        if (displayKarma >= 15) {
            karmaColor = 0x55FF55;
        } else if (displayKarma >= 0) {
            karmaColor = 0xFFFF55;
        } else if (displayKarma >= -35) {
            karmaColor = 0xFFAA00;
        } else {
            karmaColor = 0xFF5555;
        }

        String stageText = displayStage == 5 ? "CLEARED" : (demonAwakened ? "DEMON" : ("Stage " + displayStage));
        int stageWidth = font.getWidth(stageText);

        context.drawTextWithShadow(font, karmaText, boxX + 6, boxY + 17, karmaColor);
        context.drawTextWithShadow(font, stageText, boxX + boxWidth - 6 - stageWidth, boxY + 17, 0xAAAAAA);

        // Row 3: Karma visual bar (-100 to +100)
        int barX = boxX + 6;
        int barY = boxY + 30;
        int barWidth = boxWidth - 12; // 130px
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
        int pipX = MathHelper.clamp(centerBarX + (displayKarma >= 0 ? fillPixels : -fillPixels), barX, barX + barWidth - 2);
        context.fill(pipX, barY - 1, pipX + 2, barY + barHeight + 1, 0xFFFFFFFF);

        // Optional Row 4: Connected PS5 / Xbox Controller quick-action prompt
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
            context.drawTextWithShadow(font, padHint, boxX + 6, boxY + 40, 0x55FFFF);
        }
    }
}
