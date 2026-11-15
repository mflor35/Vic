package com.vicmod.client.input;

import com.vicmod.client.gui.VicConfigScreen;
import com.vicmod.config.VicConfig;
import com.vicmod.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Locale;

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

    private static int activeJoystickId = -1;
    private static ControllerProfile activeProfile = ControllerProfile.NONE;
    private static String activeControllerName = "";
    private static boolean controllerDrivingMovement = false;

    private static final boolean[] prevButtons = new boolean[15];
    private static boolean prevLeftTriggerPressed = false;
    private static boolean prevRightTriggerPressed = false;
    private static boolean sneakToggled = false;
    private static boolean sprintToggled = false;
    private static boolean dpadUpVoiceHeld = false;

    public static boolean isControllerConnected() {
        return activeJoystickId != -1 && activeProfile != ControllerProfile.NONE;
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

    public static String getVoiceButtonLabel() {
        return activeProfile == ControllerProfile.PS5 ? "D-Pad Up (PS5)" : "D-Pad Up";
    }

    public static void tick(MinecraftClient client) {
        if (!VicConfig.get().enableController) {
            if (controllerDrivingMovement) {
                releaseMovementKeys(client);
                controllerDrivingMovement = false;
            }
            dpadUpVoiceHeld = false;
            activeJoystickId = -1;
            activeProfile = ControllerProfile.NONE;
            return;
        }

        detectController(client);
        if (activeJoystickId == -1) {
            if (controllerDrivingMovement) {
                releaseMovementKeys(client);
                controllerDrivingMovement = false;
            }
            dpadUpVoiceHeld = false;
            return;
        }

        float[] axes = new float[6];
        boolean[] buttons = new boolean[15];
        if (!readControllerState(activeJoystickId, axes, buttons)) {
            activeJoystickId = -1;
            activeProfile = ControllerProfile.NONE;
            dpadUpVoiceHeld = false;
            return;
        }

        float deadzone = MathHelper.clamp(VicConfig.get().controllerDeadzone, 0.05f, 0.45f);
        float lx = applyDeadzone(axes[GLFW.GLFW_GAMEPAD_AXIS_LEFT_X], deadzone);
        float ly = applyDeadzone(axes[GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y], deadzone);
        float rx = applyDeadzone(axes[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X], deadzone);
        float ry = applyDeadzone(axes[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y], deadzone);
        float lt = axes[GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER];
        float rt = axes[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER];

        // If a GUI Screen is open, allow virtual cursor navigation & A/B actions
        if (client.currentScreen != null) {
            if (controllerDrivingMovement) {
                releaseMovementKeys(client);
                controllerDrivingMovement = false;
            }
            dpadUpVoiceHeld = false;
            handleScreenNavigation(client, lx, ly, buttons);
            System.arraycopy(buttons, 0, prevButtons, 0, buttons.length);
            return;
        }

        if (client.player == null || client.world == null) {
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
            client.options.forwardKey.setPressed(moveForward);
            client.options.backKey.setPressed(moveBack);
            client.options.leftKey.setPressed(moveLeft);
            client.options.rightKey.setPressed(moveRight);
            if (!moveForward) sprintToggled = false;
            client.options.sprintKey.setPressed(sprintToggled && moveForward);
            client.options.sneakKey.setPressed(sneakToggled);
            client.options.jumpKey.setPressed(buttons[GLFW.GLFW_GAMEPAD_BUTTON_A]);
            controllerDrivingMovement = anyMove || buttons[GLFW.GLFW_GAMEPAD_BUTTON_A] || sneakToggled || sprintToggled;
        }

        // 2. Right Stick Camera Look
        if (Math.abs(rx) > 0.001f || Math.abs(ry) > 0.001f) {
            float sens = MathHelper.clamp(VicConfig.get().controllerSensitivity, 1.0f, 25.0f) * 2.2f;
            float curvedRx = Math.copySign(rx * rx, rx) * sens;
            float curvedRy = Math.copySign(ry * ry, ry) * sens;
            client.player.changeLookDirection(curvedRx, curvedRy);
        }

        // 3. L3 (Sprint) & R3 (Sneak Toggle)
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB)) {
            sprintToggled = !sprintToggled;
        }
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB)) {
            sneakToggled = !sneakToggled;
            client.options.sneakKey.setPressed(sneakToggled);
        }

        // 4. Bumpers (L1/LB & R1/RB): Cycle Hotbar Slots
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)) {
            PlayerInventory inv = client.player.getInventory();
            inv.selectedSlot = (inv.selectedSlot + 8) % 9;
        }
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)) {
            PlayerInventory inv = client.player.getInventory();
            inv.selectedSlot = (inv.selectedSlot + 1) % 9;
        }

        // 5. Triggers (L2/LT = Use/Interact, R2/RT = Attack/Mine)
        boolean ltPressed = lt > 0.35f;
        boolean rtPressed = rt > 0.35f;

        if (ltPressed != prevLeftTriggerPressed) {
            client.options.useKey.setPressed(ltPressed);
            if (ltPressed) {
                triggerKeyClick(client.options.useKey);
            }
            prevLeftTriggerPressed = ltPressed;
        }
        if (rtPressed != prevRightTriggerPressed) {
            client.options.attackKey.setPressed(rtPressed);
            if (rtPressed) {
                triggerKeyClick(client.options.attackKey);
            }
            prevRightTriggerPressed = rtPressed;
        }

        // 6. Face Buttons:
        // Triangle / Y -> Open Inventory
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_Y)) {
            client.setScreen(new InventoryScreen(client.player));
        }
        // Square / X -> Quick-Equip or Toggle Flashlight (or Swap Offhand)
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_X)) {
            if (!quickToggleFlashlight(client)) {
                triggerKeyClick(client.options.swapHandsKey);
            }
        }
        // Circle / B -> Drop Active Item
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_B)) {
            client.player.dropSelectedItem(false);
        }

        // 7. Center Buttons:
        // Options (PS5) / Start (Xbox) -> Pause Game Menu
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_START)) {
            client.openGameMenu(false);
        }
        // Create/Share (PS5) / Back (Xbox) -> Open Vic Config Screen
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_BACK)) {
            client.setScreen(new VicConfigScreen(null));
        }

        // 8. D-Pad Vic Mod Actions:
        // D-Pad Up (Hold): Push-to-Talk Voice Input to Vic
        dpadUpVoiceHeld = buttons[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP];

        // D-Pad Down: Quick-Equip & Toggle Flashlight
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) {
            quickToggleFlashlight(client);
        }

        // D-Pad Right: Open Vic Mod Config Screen
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) {
            client.setScreen(new VicConfigScreen(null));
        }

        // D-Pad Left: Toggle Camera Perspective (First/Third Person)
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) {
            client.options.setPerspective(client.options.getPerspective().next());
        }

        System.arraycopy(buttons, 0, prevButtons, 0, buttons.length);
    }

    private static void handleScreenNavigation(MinecraftClient client, float lx, float ly, boolean[] buttons) {
        long windowHandle = client.getWindow().getHandle();

        // Move OS cursor smoothly with Left Stick so any button on VicConfigScreen or Inventory can be hovered
        if (Math.abs(lx) > 0.01f || Math.abs(ly) > 0.01f) {
            double[] cx = new double[1];
            double[] cy = new double[1];
            GLFW.glfwGetCursorPos(windowHandle, cx, cy);
            double speed = 11.0;
            double newX = MathHelper.clamp(cx[0] + lx * speed, 0, client.getWindow().getWidth());
            double newY = MathHelper.clamp(cy[0] + ly * speed, 0, client.getWindow().getHeight());
            GLFW.glfwSetCursorPos(windowHandle, newX, newY);
        }

        // Cross (PS5) / A (Xbox) clicks at current mouse position
        if (justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_A) && client.currentScreen != null) {
            double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
            client.currentScreen.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            client.currentScreen.mouseReleased(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }

        // Circle (PS5) / B (Xbox) or Start closes current screen
        if ((justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_B) || justPressed(buttons, GLFW.GLFW_GAMEPAD_BUTTON_START))
                && client.currentScreen != null) {
            client.currentScreen.close();
        }
    }

    private static boolean quickToggleFlashlight(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return false;
        PlayerInventory inv = client.player.getInventory();

        // Check main hand or off hand first
        if (client.player.getMainHandStack().isOf(ModItems.FLASHLIGHT)) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            return true;
        }
        if (client.player.getOffHandStack().isOf(ModItems.FLASHLIGHT)) {
            client.interactionManager.interactItem(client.player, Hand.OFF_HAND);
            return true;
        }

        // Search hotbar (slots 0..8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(ModItems.FLASHLIGHT)) {
                inv.selectedSlot = i;
                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private static void triggerKeyClick(KeyBinding keyBinding) {
        InputUtil.Key boundKey = InputUtil.fromTranslationKey(keyBinding.getBoundKeyTranslationKey());
        KeyBinding.onKeyPressed(boundKey);
    }

    private static void releaseMovementKeys(MinecraftClient client) {
        if (client == null || client.options == null) return;
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.attackKey.setPressed(false);
        client.options.useKey.setPressed(false);
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

    private static void detectController(MinecraftClient client) {
        if (activeJoystickId != -1 && GLFW.glfwJoystickPresent(activeJoystickId)) {
            return;
        }

        activeJoystickId = -1;
        activeProfile = ControllerProfile.NONE;
        activeControllerName = "";

        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_16; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;

            String name = GLFW.glfwGetGamepadName(jid);
            if (name == null || name.isBlank()) {
                name = GLFW.glfwGetJoystickName(jid);
            }
            if (name == null) {
                name = "Gamepad";
            }

            String lower = name.toLowerCase(Locale.ROOT);
            // Ignore non-gamepad motion/touchpad sub-devices exposed by Linux hid-playstation
            if (lower.contains("motion sensors") || lower.contains("touchpad")) {
                continue;
            }

            FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
            ByteBuffer btns = GLFW.glfwGetJoystickButtons(jid);
            if ((axes == null || axes.remaining() < 4) && !GLFW.glfwJoystickIsGamepad(jid)) {
                continue;
            }
            if (btns == null || btns.remaining() < 10) {
                continue;
            }

            activeJoystickId = jid;
            activeControllerName = name;
            if (lower.contains("dualsense") || lower.contains("ps5") || lower.contains("playstation")
                    || lower.contains("sony") || lower.contains("dualshock") || lower.contains("wireless controller")) {
                activeProfile = ControllerProfile.PS5;
            } else if (lower.contains("xbox") || lower.contains("x-box") || lower.contains("microsoft")) {
                activeProfile = ControllerProfile.XBOX;
            } else {
                activeProfile = ControllerProfile.GENERIC;
            }

            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("[Vic Controller] ").formatted(Formatting.AQUA, Formatting.BOLD)
                                .append(Text.literal("Connected: " + activeProfile.getDisplayName() + " (" + name + ")").formatted(Formatting.GREEN)),
                        true
                );
            }
            break;
        }
    }

    private static boolean readControllerState(int jid, float[] axesOut, boolean[] buttonsOut) {
        if (!GLFW.glfwJoystickPresent(jid)) {
            return false;
        }

        if (GLFW.glfwJoystickIsGamepad(jid)) {
            try (GLFWGamepadState state = GLFWGamepadState.malloc()) {
                if (GLFW.glfwGetGamepadState(jid, state)) {
                    for (int i = 0; i < 6; i++) {
                        axesOut[i] = state.axes(i);
                    }
                    for (int i = 0; i < 15; i++) {
                        buttonsOut[i] = state.buttons(i) == GLFW.GLFW_PRESS;
                    }
                    return true;
                }
            }
        }

        // Raw Joystick Fallback (e.g., PS5 DualSense or Xbox controller on Linux if unmapped in GLFW's internal SDL DB)
        FloatBuffer rawAxes = GLFW.glfwGetJoystickAxes(jid);
        ByteBuffer rawButtons = GLFW.glfwGetJoystickButtons(jid);
        if (rawAxes == null || rawButtons == null || rawAxes.remaining() < 4) {
            return false;
        }

        boolean isPs = activeProfile == ControllerProfile.PS5;
        axesOut[GLFW.GLFW_GAMEPAD_AXIS_LEFT_X] = rawAxes.get(0);
        axesOut[GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y] = rawAxes.get(1);

        if (isPs && rawAxes.remaining() >= 6) {
            // Linux hid-playstation raw axis order: 0=LX, 1=LY, 2=L2, 3=RX, 4=RY, 5=R2
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] = rawAxes.get(2);
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X] = rawAxes.get(3);
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y] = rawAxes.get(4);
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] = rawAxes.get(5);
        } else {
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X] = rawAxes.get(2);
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y] = rawAxes.get(3);
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] = rawAxes.remaining() > 4 ? rawAxes.get(4) : -1.0f;
            axesOut[GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] = rawAxes.remaining() > 5 ? rawAxes.get(5) : -1.0f;
        }

        int btnCount = rawButtons.remaining();
        for (int i = 0; i < Math.min(btnCount, 15); i++) {
            buttonsOut[i] = rawButtons.get(i) == GLFW.GLFW_PRESS;
        }

        ByteBuffer hats = GLFW.glfwGetJoystickHats(jid);
        if (hats != null && hats.remaining() > 0) {
            byte hat = hats.get(0);
            buttonsOut[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP] |= (hat & GLFW.GLFW_HAT_UP) != 0;
            buttonsOut[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT] |= (hat & GLFW.GLFW_HAT_RIGHT) != 0;
            buttonsOut[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN] |= (hat & GLFW.GLFW_HAT_DOWN) != 0;
            buttonsOut[GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT] |= (hat & GLFW.GLFW_HAT_LEFT) != 0;
        }

        return true;
    }
}
