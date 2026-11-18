package com.vicmod.client.gui;

import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicPersona;
import com.vicmod.voice.VicTtsService;
import com.vicmod.voice.VicVoiceInputService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class VicConfigScreen extends Screen {
    private final Screen parent;

    private String aiProvider;
    private String ttsMode;
    private String sttMode;
    private VicPersona vicPersona;
    private int countdownDays;
    private boolean autoSpawnBox;
    private boolean enableWorldDecay;
    private boolean enableTrueDarkness;
    private int requestTimeoutSeconds;

    private EditBox ollamaUrlField;
    private EditBox ollamaModelField;
    private Button scanOllamaButton;

    private EditBox apiEndpointField;
    private EditBox apiModelField;
    private EditBox apiKeyField;
    private EditBox ttsVoiceField;
    private EditBox piperModelPathField;

    private Button providerButton;
    private Button ttsModeButton;
    private Button testTtsButton;
    private Button sttModeButton;
    private Button testMicButton;

    private Button daysButton;
    private Button personaButton;
    private Button autoSpawnButton;
    private Button worldDecayButton;
    private Button trueDarknessButton;

    private final List<String> detectedOllamaModels = new ArrayList<>();
    private int detectedModelIndex = -1;
    private int testTtsStage = 0;
    private String statusText = "";
    private int statusColor = 0xAAAAAA;

    public VicConfigScreen(Screen parent) {
        super(Component.literal("Vic Mod Configuration"));
        this.parent = parent;

        VicConfig cfg = VicConfig.get();
        this.aiProvider = cfg.getEffectiveProvider();
        this.ttsMode = cfg.getEffectiveTtsMode();
        this.sttMode = cfg.getEffectiveSttMode();
        this.vicPersona = cfg.getEffectivePersona();
        this.countdownDays = cfg.countdownDays;
        this.autoSpawnBox = cfg.autoSpawnDeliveryBoxOnFirstJoin;
        this.enableWorldDecay = cfg.enableWorldDecay;
        this.enableTrueDarkness = cfg.enableTrueDarknessDuringNight;
        this.requestTimeoutSeconds = cfg.requestTimeoutSeconds;
    }

    @Override
    protected void init() {
        VicConfig cfg = VicConfig.get();
        int centerX = this.width / 2;
        int leftColX = centerX - 155;
        int rightColX = centerX + 5;
        int y = 26;

        // 1. AI Provider Cycle Button
        this.providerButton = Button.builder(getProviderButtonText(), btn -> {
            this.aiProvider = switch (this.aiProvider) {
                case "SCRIPTED" -> "OLLAMA";
                case "OLLAMA" -> "OPENAI";
                default -> "SCRIPTED";
            };
            btn.setMessage(getProviderButtonText());
            updateFieldVisibility();
            if ("OLLAMA".equals(this.aiProvider) && this.detectedOllamaModels.isEmpty()) {
                scanAndCycleOllamaModels(false);
            }
        }).bounds(leftColX, y, 310, 20).build();
        this.addRenderableWidget(this.providerButton);

        y += 24;

        // 2. Ollama Fields
        this.ollamaUrlField = new EditBox(this.font, leftColX, y + 10, 150, 20, Component.literal("Ollama URL"));
        this.ollamaUrlField.setMaxLength(256);
        this.ollamaUrlField.setValue(cfg.ollamaUrl != null ? cfg.ollamaUrl : "http://localhost:11434");
        this.addRenderableWidget(this.ollamaUrlField);

        this.ollamaModelField = new EditBox(this.font, rightColX, y + 10, 95, 20, Component.literal("Ollama Model"));
        this.ollamaModelField.setMaxLength(128);
        this.ollamaModelField.setValue(cfg.ollamaModel != null ? cfg.ollamaModel : "timheinrich2011/verity-3b:latest");
        this.addRenderableWidget(this.ollamaModelField);

        this.scanOllamaButton = Button.builder(Component.literal("Models").withStyle(ChatFormatting.AQUA), btn -> {
            scanAndCycleOllamaModels(true);
        }).bounds(rightColX + 100, y + 10, 50, 20).build();
        this.addRenderableWidget(this.scanOllamaButton);

        // 3. OpenAI / Groq Fields
        this.apiEndpointField = new EditBox(this.font, leftColX, y + 10, 150, 20, Component.literal("API Endpoint"));
        this.apiEndpointField.setMaxLength(256);
        this.apiEndpointField.setValue(cfg.apiEndpoint != null ? cfg.apiEndpoint : "");
        this.addRenderableWidget(this.apiEndpointField);

        this.apiModelField = new EditBox(this.font, rightColX, y + 10, 150, 20, Component.literal("API Model"));
        this.apiModelField.setMaxLength(128);
        this.apiModelField.setValue(cfg.apiModel != null ? cfg.apiModel : "");
        this.addRenderableWidget(this.apiModelField);

        y += 34;

        // API credentials and TTS voice
        this.apiKeyField = new EditBox(this.font, leftColX, y + 10, 150, 20, Component.literal("API Key"));
        this.apiKeyField.setMaxLength(256);
        this.apiKeyField.setValue(cfg.apiKey != null ? cfg.apiKey : "");
        this.addRenderableWidget(this.apiKeyField);

        this.ttsVoiceField = new EditBox(this.font, rightColX, y + 10, 150, 20, Component.literal("OpenAI TTS Voice"));
        this.ttsVoiceField.setMaxLength(64);
        this.ttsVoiceField.setValue(cfg.ttsVoice != null ? cfg.ttsVoice : "alloy");
        this.addRenderableWidget(this.ttsVoiceField);

        y += 36;

        // 4. Text-to-Speech (TTS) & Voice Input (STT) Controls
        this.ttsModeButton = Button.builder(getTtsModeText(), btn -> {
            this.ttsMode = switch (this.ttsMode) {
                case "SHERPA" -> "PIPER";
                case "PIPER" -> "LOCAL";
                case "LOCAL" -> "SYNTH";
                case "SYNTH" -> "SYSTEM";
                case "SYSTEM" -> "OPENAI_TTS";
                case "OPENAI_TTS" -> "OFF";
                default -> "SHERPA";
            };
            btn.setMessage(getTtsModeText());
        }).bounds(leftColX, y, 108, 20).build();
        this.addRenderableWidget(this.ttsModeButton);

        this.testTtsButton = Button.builder(Component.literal("Test").withStyle(ChatFormatting.YELLOW), btn -> {
            applyTransientVoiceConfig();
            String sample = switch (this.testTtsStage) {
                case 0 -> "Hiya bestie! I am Vic, your cheerful companion!";
                case 1 -> "I am always watching over you, even while you sleep.";
                case 2 -> "All the animals are gone. Time is running out.";
                default -> "The shell is too tight. Do not hide on the surface.";
            };
            this.statusText = "Playing TTS Preview (Stage " + this.testTtsStage + ")...";
            this.statusColor = 0x55FFFF;
            VicTtsService.speak(sample, this.testTtsStage);
            this.testTtsStage = (this.testTtsStage + 1) % 4;
        }).bounds(leftColX + 112, y, 38, 20).build();
        this.addRenderableWidget(this.testTtsButton);

        this.sttModeButton = Button.builder(getSttModeText(), btn -> {
            this.sttMode = switch (this.sttMode) {
                case "SHERPA" -> "LOCAL_CLI";
                case "LOCAL_CLI" -> "AUTO";
                case "AUTO" -> "WHISPER_API";
                case "WHISPER_API" -> "OFF";
                default -> "SHERPA";
            };
            btn.setMessage(getSttModeText());
        }).bounds(rightColX, y, 108, 20).build();
        this.addRenderableWidget(this.sttModeButton);

        this.testMicButton = Button.builder(Component.literal("Mic").withStyle(ChatFormatting.AQUA), btn -> {
            applyTransientVoiceConfig();
            this.statusText = "Recording mic for 3 seconds... Speak now!";
            this.statusColor = 0xFF5555;
            VicVoiceInputService.recordFixedDurationAndTranscribe(3, res -> {
                Minecraft.getInstance().execute(() -> {
                    if (res.success()) {
                        this.statusText = "Heard: \"" + res.text() + "\"";
                        this.statusColor = 0x55FF55;
                    } else {
                        this.statusText = res.errorMessage();
                        this.statusColor = 0xFFAA00;
                    }
                });
            });
        }).bounds(rightColX + 112, y, 38, 20).build();
        this.addRenderableWidget(this.testMicButton);

        y += 24;

        this.piperModelPathField = new EditBox(this.font, leftColX, y + 10, 310, 20, Component.literal("Piper voice model path"));
        this.piperModelPathField.setMaxLength(512);
        this.piperModelPathField.setValue(cfg.piperModelPath != null ? cfg.piperModelPath : "");
        this.addRenderableWidget(this.piperModelPathField);

        y += 34;

        // 5. Gameplay Toggles (2 columns)
        this.daysButton = Button.builder(getDaysButtonText(), btn -> {
            this.countdownDays = (this.countdownDays % 15) + 1;
            btn.setMessage(getDaysButtonText());
        }).bounds(leftColX, y, 150, 20).build();
        this.addRenderableWidget(this.daysButton);

        this.personaButton = Button.builder(getPersonaButtonText(), btn -> {
            this.vicPersona = this.vicPersona.next();
            btn.setMessage(getPersonaButtonText());
        }).bounds(rightColX, y, 150, 20).build();
        this.addRenderableWidget(this.personaButton);

        y += 22;

        this.worldDecayButton = Button.builder(getToggleText("World Decay", this.enableWorldDecay), btn -> {
            this.enableWorldDecay = !this.enableWorldDecay;
            btn.setMessage(getToggleText("World Decay", this.enableWorldDecay));
        }).bounds(leftColX, y, 150, 20).build();
        this.addRenderableWidget(this.worldDecayButton);

        this.trueDarknessButton = Button.builder(getToggleText("Night Darkness", this.enableTrueDarkness), btn -> {
            this.enableTrueDarkness = !this.enableTrueDarkness;
            btn.setMessage(getToggleText("Night Darkness", this.enableTrueDarkness));
        }).bounds(rightColX, y, 150, 20).build();
        this.addRenderableWidget(this.trueDarknessButton);

        // 6. Save & Cancel Buttons
        int bottomY = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close").withStyle(ChatFormatting.GREEN), btn -> {
            saveChanges();
            this.onClose();
        }).bounds(leftColX, bottomY, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            this.onClose();
        }).bounds(rightColX, bottomY, 150, 20).build());

        updateFieldVisibility();
        if ("OLLAMA".equals(this.aiProvider)) {
            scanAndCycleOllamaModels(false);
        }
    }

    private void updateFieldVisibility() {
        boolean isOllama = "OLLAMA".equals(this.aiProvider);
        boolean isOpenAi = "OPENAI".equals(this.aiProvider);

        this.ollamaUrlField.setVisible(isOllama);
        this.ollamaModelField.setVisible(isOllama);
        this.scanOllamaButton.visible = isOllama;

        this.apiEndpointField.setVisible(isOpenAi);
        this.apiModelField.setVisible(isOpenAi);
        this.apiKeyField.setVisible(true);
    }

    private void applyTransientVoiceConfig() {
        VicConfig cfg = VicConfig.get();
        cfg.setTtsMode(this.ttsMode);
        cfg.setSttMode(this.sttMode);
        cfg.apiKey = this.apiKeyField.getValue().trim();
        cfg.ttsVoice = this.ttsVoiceField.getValue().trim();
        cfg.piperModelPath = this.piperModelPathField.getValue().trim();
    }

    private void scanAndCycleOllamaModels(boolean cycleIfAlreadyLoaded) {
        if (cycleIfAlreadyLoaded && !this.detectedOllamaModels.isEmpty()) {
            this.detectedModelIndex = (this.detectedModelIndex + 1) % this.detectedOllamaModels.size();
            String chosen = this.detectedOllamaModels.get(this.detectedModelIndex);
            this.ollamaModelField.setValue(chosen);
            this.statusText = "Selected Ollama model (" + (this.detectedModelIndex + 1) + "/" + this.detectedOllamaModels.size() + "): " + chosen;
            this.statusColor = 0x55FF55;
            return;
        }

        String url = this.ollamaUrlField.getValue();
        this.statusText = "Scanning Ollama models at " + VicAiService.normalizeOllamaBaseUrl(url) + "...";
        this.statusColor = 0xFFFF55;

        VicAiService.fetchOllamaModels(url).thenAccept(models -> {
            Minecraft.getInstance().execute(() -> {
                this.detectedOllamaModels.clear();
                this.detectedOllamaModels.addAll(models);
                if (models.isEmpty()) {
                    this.statusText = "No local Ollama models detected (is 'ollama serve' running?)";
                    this.statusColor = 0xFF5555;
                } else {
                    String current = this.ollamaModelField.getValue().trim();
                    int idx = models.indexOf(current);
                    if (idx == -1 || cycleIfAlreadyLoaded) {
                        this.detectedModelIndex = (idx + 1) % models.size();
                        this.ollamaModelField.setValue(models.get(this.detectedModelIndex));
                    } else {
                        this.detectedModelIndex = idx;
                    }
                    this.statusText = "Found " + models.size() + " Ollama model(s)! Click 'Models' to cycle.";
                    this.statusColor = 0x55FF55;
                }
            });
        });
    }

    private Component getProviderButtonText() {
        ChatFormatting color = switch (this.aiProvider) {
            case "OLLAMA" -> ChatFormatting.AQUA;
            case "OPENAI" -> ChatFormatting.GREEN;
            default -> ChatFormatting.YELLOW;
        };
        String label = switch (this.aiProvider) {
            case "OLLAMA" -> "Ollama (Local LLM)";
            case "OPENAI" -> "Remote API (OpenAI / Groq)";
            default -> "Built-in Offline Scripted";
        };
        return Component.literal("AI Mode: ").append(Component.literal(label).withStyle(color, ChatFormatting.BOLD));
    }

    private Component getTtsModeText() {
        ChatFormatting color = "OFF".equals(this.ttsMode) ? ChatFormatting.RED : ChatFormatting.GREEN;
        return Component.literal("TTS: ").append(Component.literal(this.ttsMode).withStyle(color, ChatFormatting.BOLD));
    }

    private Component getSttModeText() {
        ChatFormatting color = "OFF".equals(this.sttMode) ? ChatFormatting.RED
            : "SHERPA".equals(this.sttMode) ? ChatFormatting.GREEN : ChatFormatting.AQUA;
        return Component.literal("Mic STT: ").append(Component.literal(this.sttMode).withStyle(color, ChatFormatting.BOLD));
    }

    private Component getDaysButtonText() {
        return Component.literal("Trial Length: ").append(Component.literal(this.countdownDays + " Days").withStyle(ChatFormatting.YELLOW));
    }

    private Component getPersonaButtonText() {
        return Component.literal("Persona: ").append(Component.literal(this.vicPersona.getDisplayName()).withStyle(this.vicPersona.getChatFormatting(), ChatFormatting.BOLD));
    }

    private Component getToggleText(String name, boolean enabled) {
        return Component.literal(name + ": ").append(
                Component.literal(enabled ? "ON" : "OFF").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)
        );
    }

    private void saveChanges() {
        VicConfig cfg = VicConfig.get();
        cfg.setProvider(this.aiProvider);
        cfg.setTtsMode(this.ttsMode);
        cfg.setSttMode(this.sttMode);
        cfg.setPersona(this.vicPersona.getId());
        cfg.ollamaUrl = this.ollamaUrlField.getValue().trim();
        cfg.ollamaModel = this.ollamaModelField.getValue().trim();
        cfg.apiEndpoint = this.apiEndpointField.getValue().trim();
        cfg.apiModel = this.apiModelField.getValue().trim();
        cfg.apiKey = this.apiKeyField.getValue().trim();
        cfg.ttsVoice = this.ttsVoiceField.getValue().trim();
        cfg.piperModelPath = this.piperModelPathField.getValue().trim();
        if (cfg.ttsVoice.isBlank()) {
            cfg.ttsVoice = "alloy";
        }
        cfg.countdownDays = this.countdownDays;
        cfg.autoSpawnDeliveryBoxOnFirstJoin = this.autoSpawnBox;
        cfg.enableWorldDecay = this.enableWorldDecay;
        cfg.enableTrueDarknessDuringNight = this.enableTrueDarkness;
        cfg.requestTimeoutSeconds = this.requestTimeoutSeconds;
        VicConfig.save();

        if (this.minecraft != null && this.minecraft.player != null) {
            if (this.minecraft.player.connection != null) {
                this.minecraft.player.connection.sendCommand("vic color " + this.vicPersona.getId().toLowerCase());
            }
            this.minecraft.player.sendOverlayMessage(
                    Component.literal("[Vic Config] ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                            .append(Component.literal("Saved! Persona=" + this.vicPersona.getDisplayName()
                                    + ", AI=" + cfg.getEffectiveProvider()
                                    + ", TTS=" + cfg.getEffectiveTtsMode()).withStyle(ChatFormatting.GREEN))
            );
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int leftColX = centerX - 155;
        int rightColX = centerX + 5;

        context.centeredText(this.font, this.title, centerX, 10, 0xFFE135);

        if ("OLLAMA".equals(this.aiProvider)) {
            context.text(this.font, "Ollama Base URL:", leftColX, 50, 0xCCCCCC, true);
            context.text(this.font, "Ollama Model:", rightColX, 50, 0xCCCCCC, true);
        } else if ("OPENAI".equals(this.aiProvider)) {
            context.text(this.font, "API Endpoint URL:", leftColX, 50, 0xCCCCCC, true);
            context.text(this.font, "Model Name:", rightColX, 50, 0xCCCCCC, true);
        } else {
            context.centeredText(
                    this.font,
                    Component.literal("Using built-in stage-aware dialogue (no external AI server required)."),
                    centerX,
                    64,
                    0xAAAAAA
            );
        }

        context.text(this.font, "API Key:", leftColX, 84, 0xCCCCCC, true);
        context.text(this.font, "OpenAI TTS voice:", rightColX, 84, 0xCCCCCC, true);
        context.text(this.font, "Piper model (.onnx; blank = auto-detect):", leftColX, 144, 0xCCCCCC, true);

        if (this.statusText != null && !this.statusText.isEmpty()) {
            context.centeredText(this.font, this.statusText, centerX, this.height - 40, this.statusColor);
        } else {
            context.centeredText(
                    this.font,
                    "Tip: Hold [R] in-game to talk to Vic with your microphone!",
                    centerX,
                    this.height - 40,
                    0xAAAAAA
            );
        }
    }
}
