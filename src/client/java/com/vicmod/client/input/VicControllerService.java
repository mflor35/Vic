package com.vicmod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.vicmod.client.gui.VicConfigScreen;
import com.vicmod.config.VicConfig;
import com.vicmod.registry.ModItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLMouse;

import java.nio.IntBuffer;

public class VicControllerService {
    public enum ControllerProfile {
        NONE("None"),
        PS5("PS5 DualSense"),
        XBOX("Xbox Controller"),
        GENERIC("Gamepad");

        private final String displayName;

        ControllerProfile(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static Boolean externalControllerModLoaded = null;
    private static String externalControllerModName = "";

    private static long activeGamepad = 0;
    private static ControllerProfile activeProfile = ControllerProfile.NONE;
    private static String activeControllerName = "";
    private static boolean controllerDrivingMovement = false;

    private static final boolean[] prevButtons = new boolean[32];
    private static boolean prevLeftTriggerPressed = false;
    private static boolean prevRightTriggerPressed = false;
    private static boolean sneakToggled = false;
    private static boolean sprintToggled = false;
    private static boolean dpadUpVoiceHeld = false;

    /**
     * Checks if a dedicated controller mod (Controlify, MidnightControls, or Controllable) is loaded.
     * When present, VicControllerService yields movement, camera, and menu navigation to that mod
     * to prevent double-input conflicts while keeping Vic's voice/flashlight shortcuts active.
     */
    public static boolean isExternalControllerModLoaded() {
        if (externalControllerModLoaded == null) {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader.isModLoaded("controlify")) {
                externalControllerModLoaded = true;
                externalControllerModName = "Controlify";
            } else if (loader.isModLoaded("midnightcontrols")) {
                externalControllerModLoaded = true;
                externalControllerModName = "MidnightControls";
            } else if (loader.isModLoaded("controllable")) {
                externalControllerModLoaded = true;
                externalControllerModName = "Controllable";
            } else {
                externalControllerModLoaded = false;
                externalControllerModName = "";
            }
        }
        return externalControllerModLoaded;
    }

    public static String getExternalControllerModName() {
        isExternalControllerModLoaded();
        return externalControllerModName;
    }

    public static boolean isControllerConnected() {
        return activeGamepad != 0 && activeProfile != ControllerProfile.NONE;
    }

    public static ControllerProfile getActiveProfile() {
        return activeProfile;
    }

    public static String getActiveControllerName() {
        return activeControllerName;
    }

    public static boolean isDpadVoiceHeld() {
        return dpadUpVoiceHeld;
    }

    public static void tick(Minecraft client) {
        if (!VicConfig.get().enableController) {
            if (controllerDrivingMovement) {
                releaseMovementKeys(client);
                controllerDrivingMovement = false;
            }
            dpadUpVoiceHeld = false;
            closeActiveGamepad();
            return;
        }

        detectController(client);
        if (activeGamepad == 0) {
            if (controllerDrivingMovement) {
                releaseMovementKeys(client);
                controllerDrivingMovement = false;
            }
            dpadUpVoiceHeld = false;
            return;
        }

        SDLGamepad.SDL_UpdateGamepads();
        if (!SDLGamepad.SDL_GamepadConnected(activeGamepad)) {
            closeActiveGamepad();
            dpadUpVoiceHeld = false;
            return;
        }

        float deadzone = Mth.clamp(VicConfig.get().controllerDeadzone, 0.05f, 0.45f);
        float lx = applyDeadzone(SDLGamepad.SDL_GetGamepadAxis(activeGamepad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX) / 32767.0f, deadzone);
        float ly = applyDeadzone(SDLGamepad.SDL_GetGamepadAxis(activeGamepad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY) / 32767.0f, deadzone);
        float rx = applyDeadzone(SDLGamepad.SDL_GetGamepadAxis(activeGamepad, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX) / 32767.0f, deadzone);
        float ry = applyDeadzone(SDLGamepad.SDL_GetGamepadAxis(activeGamepad, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY) / 32767.0f, deadzone);
        float lt = Math.max(0.0f, SDLGamepad.SDL_GetGamepadAxis(activeGamepad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER) / 32767.0f);
        float rt = Math.max(0.0f, SDLGamepad.SDL_GetGamepadAxis(activeGamepad, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) / 32767.0f);

        boolean[] buttons = new boolean[32];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = SDLGamepad.SDL_GetGamepadButton(activeGamepad, i);
        }

        // If Controlify or MidnightControls is installed, yield core movement/camera/UI to it
        // and only provide D-Pad Up Push-to-Talk & D-Pad Right Config Screen shortcuts in-game.
        if (isExternalControllerModLoaded()) {
            if (controllerDrivingMovement) {
                releaseMovementKeys(client);
                controllerDrivingMovement = false;
            }
            if (client.gui.screen() == null && client.player != null) {
                dpadUpVoiceHeld = buttons[SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP];
                if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN)) {
                    quickToggleFlashlight(client);
                }
                if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT)) {
                    client.gui.setScreen(new VicConfigScreen(null));
                }
            } else {
                dpadUpVoiceHeld = false;
            }
            System.arraycopy(buttons, 0, prevButtons, 0, buttons.length);
            return;
        }

        // If a GUI Screen is open, allow virtual cursor navigation & A/B actions
        if (client.gui.screen() != null) {
            if (controllerDrivingMovement) {
                releaseMovementKeys(client);
                controllerDrivingMovement = false;
            }
            dpadUpVoiceHeld = false;
            handleScreenNavigation(client, lx, ly, buttons);
            System.arraycopy(buttons, 0, prevButtons, 0, buttons.length);
            return;
        }

        if (client.player == null || client.level == null) {
            System.arraycopy(buttons, 0, prevButtons, 0, buttons.length);
            return;
        }

        // 1. Left Stick Movement
        boolean moveForward = ly < -0.25f;
        boolean moveBack = ly > 0.25f;
        boolean moveLeft = lx < -0.25f;
        boolean moveRight = lx > 0.25f;
        boolean anyMove = moveForward || moveBack || moveLeft || moveRight;

        if (anyMove || controllerDrivingMovement) {
            client.options.keyUp.setDown(moveForward);
            client.options.keyDown.setDown(moveBack);
            client.options.keyLeft.setDown(moveLeft);
            client.options.keyRight.setDown(moveRight);
            if (!moveForward) sprintToggled = false;
            client.options.keySprint.setDown(sprintToggled && moveForward);
            client.options.keyShift.setDown(sneakToggled);
            client.options.keyJump.setDown(buttons[SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH]);
            controllerDrivingMovement = anyMove || buttons[SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH] || sneakToggled || sprintToggled;
        }

        // 2. Right Stick Camera Look
        if (Math.abs(rx) > 0.001f || Math.abs(ry) > 0.001f) {
            float sens = Mth.clamp(VicConfig.get().controllerSensitivity, 1.0f, 25.0f) * 2.2f;
            float curvedRx = Math.copySign(rx * rx, rx) * sens;
            float curvedRy = Math.copySign(ry * ry, ry) * sens;
            client.player.turn(curvedRx, curvedRy);
        }

        // 3. L3 (Sprint) & R3 (Sneak Toggle)
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK)) {
            sprintToggled = !sprintToggled;
        }
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK)) {
            sneakToggled = !sneakToggled;
            client.options.keyShift.setDown(sneakToggled);
        }

        // 4. Bumpers (L1/LB & R1/RB): Cycle Hotbar Slots
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER)) {
            Inventory inv = client.player.getInventory();
            inv.setSelectedSlot((inv.getSelectedSlot() + 8) % 9);
        }
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER)) {
            Inventory inv = client.player.getInventory();
            inv.setSelectedSlot((inv.getSelectedSlot() + 1) % 9);
        }

        // 5. Triggers (L2/LT = Use/Interact, R2/RT = Attack/Mine)
        boolean ltPressed = lt > 0.35f;
        boolean rtPressed = rt > 0.35f;

        if (ltPressed != prevLeftTriggerPressed) {
            client.options.keyUse.setDown(ltPressed);
            if (ltPressed) {
                triggerKeyClick(client.options.keyUse);
            }
            prevLeftTriggerPressed = ltPressed;
        }
        if (rtPressed != prevRightTriggerPressed) {
            client.options.keyAttack.setDown(rtPressed);
            if (rtPressed) {
                triggerKeyClick(client.options.keyAttack);
            }
            prevRightTriggerPressed = rtPressed;
        }

        // 6. Face Buttons:
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH)) {
            client.gui.setScreen(new InventoryScreen(client.player));
        }
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_WEST)) {
            if (!quickToggleFlashlight(client)) {
                triggerKeyClick(client.options.keySwapOffhand);
            }
        }
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST)) {
            triggerKeyClick(client.options.keyDrop);
        }

        // 7. Center Buttons:
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_START)) {
            client.gui.setPauseScreen(false, false);
        }
        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_BACK)) {
            client.gui.setScreen(new VicConfigScreen(null));
        }

        // 8. D-Pad Vic Mod Actions:
        dpadUpVoiceHeld = buttons[SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP];

        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN)) {
            quickToggleFlashlight(client);
        }

        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT)) {
            client.gui.setScreen(new VicConfigScreen(null));
        }

        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT)) {
            client.options.setCameraType(client.options.getCameraType().cycle());
        }

        System.arraycopy(buttons, 0, prevButtons, 0, buttons.length);
    }

    private static void handleScreenNavigation(Minecraft client, float lx, float ly, boolean[] buttons) {
        long windowHandle = client.getWindow().handle();

        if (Math.abs(lx) > 0.01f || Math.abs(ly) > 0.01f) {
            double cx = client.mouseHandler.xpos();
            double cy = client.mouseHandler.ypos();
            double speed = 11.0;
            double newX = Mth.clamp(cx + lx * speed, 0, client.getWindow().getWidth());
            double newY = Mth.clamp(cy + ly * speed, 0, client.getWindow().getHeight());
            SDLMouse.SDL_WarpMouseInWindow(windowHandle, (float) newX, (float) newY);
        }

        if (justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH) && client.gui.screen() != null) {
            double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
            double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
            MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            client.gui.screen().mouseClicked(event, false);
            client.gui.screen().mouseReleased(event);
        }

        if ((justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST) || justPressed(buttons, SDLGamepad.SDL_GAMEPAD_BUTTON_START))
                && client.gui.screen() != null) {
            client.gui.screen().onClose();
        }
    }

    public static boolean quickToggleFlashlight(Minecraft client) {
        if (client.player == null || client.gameMode == null) return false;
        Inventory inv = client.player.getInventory();

        if (client.player.getMainHandItem().is(ModItems.FLASHLIGHT)) {
            client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
            return true;
        }
        if (client.player.getOffhandItem().is(ModItems.FLASHLIGHT)) {
            client.gameMode.useItem(client.player, InteractionHand.OFF_HAND);
            return true;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.FLASHLIGHT)) {
                inv.setSelectedSlot(i);
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private static void triggerKeyClick(KeyMapping keyMapping) {
        KeyMapping.click(keyMapping.getDefaultKey());
    }

    private static void releaseMovementKeys(Minecraft client) {
        if (client == null || client.options == null) return;
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
    }

    private static boolean justPressed(boolean[] buttons, int index) {
        return index >= 0 && index < buttons.length && buttons[index] && !prevButtons[index];
    }

    private static float applyDeadzone(float value, float deadzone) {
        if (Math.abs(value) < deadzone) {
            return 0.0f;
        }
        return (value - Math.copySign(deadzone, value)) / (1.0f - deadzone);
    }

    private static void detectController(Minecraft client) {
        if (activeGamepad != 0 && SDLGamepad.SDL_GamepadConnected(activeGamepad)) {
            return;
        }

        closeActiveGamepad();

        SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
        IntBuffer gamepads = SDLGamepad.SDL_GetGamepads();
        if (gamepads == null || !gamepads.hasRemaining()) {
            return;
        }

        int jid = gamepads.get(0);
        long gp = SDLGamepad.SDL_OpenGamepad(jid);
        if (gp == 0) {
            return;
        }

        activeGamepad = gp;
        String name = SDLGamepad.SDL_GetGamepadName(gp);
        if (name == null || name.isBlank()) {
            name = "Gamepad";
        }
        activeControllerName = name;

        int type = SDLGamepad.SDL_GetGamepadType(gp);
        if (type == SDLGamepad.SDL_GAMEPAD_TYPE_PS5 || type == SDLGamepad.SDL_GAMEPAD_TYPE_PS4 || type == SDLGamepad.SDL_GAMEPAD_TYPE_PS3) {
            activeProfile = ControllerProfile.PS5;
        } else if (type == SDLGamepad.SDL_GAMEPAD_TYPE_XBOX360 || type == SDLGamepad.SDL_GAMEPAD_TYPE_XBOXONE) {
            activeProfile = ControllerProfile.XBOX;
        } else {
            activeProfile = ControllerProfile.GENERIC;
        }

        if (client.player != null) {
            String modeInfo = isExternalControllerModLoaded()
                    ? " via " + getExternalControllerModName()
                    : " (Built-in Mode)";
            client.player.sendOverlayMessage(
                    Component.literal("[Vic Controller] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                            .append(Component.literal("Connected: " + activeProfile.getDisplayName() + modeInfo).withStyle(ChatFormatting.GREEN))
            );
        }
    }

    private static void closeActiveGamepad() {
        if (activeGamepad != 0) {
            try {
                SDLGamepad.SDL_CloseGamepad(activeGamepad);
            } catch (Throwable ignored) {
            }
            activeGamepad = 0;
        }
        activeProfile = ControllerProfile.NONE;
        activeControllerName = "";
    }
}
