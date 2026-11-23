package com.vicmod.client.gui;

import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicPersona;
import com.vicmod.config.VicPersonalityProfile;
import com.vicmod.voice.VicTtsService;
import com.vicmod.voice.VicVoiceInputService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
    private VicPersonalityProfile personalityProfile;
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
    private Button personalityProfileButton;
    private Button autoSpawnButton;
    private Button worldDecayButton;
    private Button trueDarknessButton;

    private final List<String> detectedOllamaModels = new ArrayList<>();
    private int detectedModelIndex = -1;
    private int testTtsStage = 0;
    private String statusText = "";
    private int statusColor = 0xFFAAAAAA;

    public VicConfigScreen(Screen parent) {
        super(Component.literal("Vic Mod Configuration"));
        this.parent = parent;

        VicConfig cfg = VicConfig.get();
        this.aiProvider = cfg.getEffectiveProvider();
        this.ttsMode = cfg.getEffectiveTtsMode();
        this.sttMode = cfg.getEffectiveSttMode();
        this.vicPersona = cfg.getEffectivePersona();
        this.personalityProfile = cfg.getEffectivePersonalityProfile();
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

        // 1. AI Provider Cycle Button
        this.providerButton = Button.builder(getProviderButtonText(), btn -> {
            this.aiProvider = switch (this.aiProvider) {
                case "OPENAI" -> "GEMINI";
                case "GEMINI" -> "CLAUDE";
                case "CLAUDE" -> "GROQ";
                case "GROQ" -> "DEEPSEEK";
                case "DEEPSEEK" -> "OPENROUTER";
                case "OPENROUTER" -> "MISTRAL";
                case "MISTRAL" -> "OLLAMA";
                case "OLLAMA" -> "SCRIPTED";
                default -> "OPENAI";
            };
            applyProviderPresetDefaults(this.aiProvider);
            btn.setMessage(getProviderButtonText());
            updateFieldVisibility();
            if ("OLLAMA".equals(this.aiProvider) && this.detectedOllamaModels.isEmpty()) {
                scanAndCycleOllamaModels(false);
            }
        }).bounds(leftColX, 20, 310, 20)
          .tooltip(Tooltip.create(Component.literal("Switch between OpenAI (Default), Gemini, Claude, Groq, DeepSeek, OpenRouter, Mistral, Ollama, or Scripted dialogue")))
          .build();
        this.addRenderableWidget(this.providerButton);

        // 2. Ollama Fields (y = 52)
        this.ollamaUrlField = new EditBox(this.font, leftColX, 52, 150, 20, Component.literal("Ollama URL"));
        this.ollamaUrlField.setMaxLength(256);
        this.ollamaUrlField.setValue(cfg.ollamaUrl != null ? cfg.ollamaUrl : "http://localhost:11434");
        this.ollamaUrlField.setHint(Component.literal("http://localhost:11434"));
        this.ollamaUrlField.setTooltip(Tooltip.create(Component.literal("Base URL of your local Ollama server")));
        this.addRenderableWidget(this.ollamaUrlField);

        this.ollamaModelField = new EditBox(this.font, rightColX, 52, 95, 20, Component.literal("Ollama Model"));
        this.ollamaModelField.setMaxLength(128);
        this.ollamaModelField.setValue(cfg.ollamaModel != null ? cfg.ollamaModel : "timheinrich2011/verity-3b:latest");
        this.ollamaModelField.setHint(Component.literal("model:tag"));
        this.ollamaModelField.setTooltip(Tooltip.create(Component.literal("Local Ollama model tag to use")));
        this.addRenderableWidget(this.ollamaModelField);

        this.scanOllamaButton = Button.builder(Component.literal("Models").withStyle(ChatFormatting.AQUA), btn -> {
            scanAndCycleOllamaModels(true);
        }).bounds(rightColX + 100, 52, 50, 20)
          .tooltip(Tooltip.create(Component.literal("Scan local Ollama for installed models and cycle through them")))
          .build();
        this.addRenderableWidget(this.scanOllamaButton);

        // 3. OpenAI / Groq Fields (y = 52)
        this.apiEndpointField = new EditBox(this.font, leftColX, 52, 150, 20, Component.literal("API Endpoint"));
        this.apiEndpointField.setMaxLength(256);
        this.apiEndpointField.setValue(cfg.apiEndpoint != null ? cfg.apiEndpoint : "");
        this.apiEndpointField.setHint(Component.literal("https://api.groq.com/openai/v1/chat/completions"));
        this.apiEndpointField.setTooltip(Tooltip.create(Component.literal("OpenAI-compatible chat completions endpoint (Groq, OpenAI, OpenRouter)")));
        this.addRenderableWidget(this.apiEndpointField);

        this.apiModelField = new EditBox(this.font, rightColX, 52, 150, 20, Component.literal("API Model"));
        this.apiModelField.setMaxLength(128);
        this.apiModelField.setValue(cfg.apiModel != null ? cfg.apiModel : "");
        this.apiModelField.setHint(Component.literal("llama-3.3-70b-versatile"));
        this.apiModelField.setTooltip(Tooltip.create(Component.literal("Model ID for remote API provider")));
        this.addRenderableWidget(this.apiModelField);

        // 4. API credentials & OpenAI TTS voice (y = 84)
        this.apiKeyField = new EditBox(this.font, leftColX, 84, 150, 20, Component.literal("API Key"));
        this.apiKeyField.setMaxLength(256);
        this.apiKeyField.setValue(cfg.apiKey != null ? cfg.apiKey : "");
        this.apiKeyField.setHint(Component.literal("sk-... (optional for local)"));
        this.apiKeyField.setTooltip(Tooltip.create(Component.literal("API Key for remote LLM, OpenAI TTS, or Whisper API")));
        this.addRenderableWidget(this.apiKeyField);

        this.ttsVoiceField = new EditBox(this.font, rightColX, 84, 150, 20, Component.literal("OpenAI TTS Voice"));
        this.ttsVoiceField.setMaxLength(64);
        this.ttsVoiceField.setValue(cfg.ttsVoice != null ? cfg.ttsVoice : "alloy");
        this.ttsVoiceField.setHint(Component.literal("alloy (echo, fable, onyx, etc.)"));
        this.ttsVoiceField.setTooltip(Tooltip.create(Component.literal("OpenAI TTS voice name (only used with OPENAI_TTS mode)")));
        this.addRenderableWidget(this.ttsVoiceField);

        // 5. TTS & STT Controls (y = 107)
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
            updateFieldVisibility();
        }).bounds(leftColX, 107, 108, 20)
          .tooltip(Tooltip.create(Component.literal("Cycle TTS voice engine: Sherpa-ONNX (bundled), Piper, Local, Synth, System, OpenAI TTS, or Off")))
          .build();
        this.addRenderableWidget(this.ttsModeButton);

        this.testTtsButton = Button.builder(Component.literal("Test").withStyle(ChatFormatting.YELLOW), btn -> {
            applyTransientVoiceConfig();
            String sample = switch (this.testTtsStage) {
                case 0 -> "Hiya friend! I am Vic, your cheerful companion!";
                case 1 -> "I am always watching over you, even while you sleep.";
                case 2 -> "All the animals are gone. Time is running out.";
                default -> "The shell is too tight. Do not hide on the surface.";
            };
            this.statusText = "Playing TTS Preview (Stage " + this.testTtsStage + ")...";
            this.statusColor = 0xFF55FFFF;
            VicTtsService.speak(sample, this.testTtsStage);
            this.testTtsStage = (this.testTtsStage + 1) % 4;
        }).bounds(leftColX + 112, 107, 38, 20)
          .tooltip(Tooltip.create(Component.literal("Test current TTS voice output")))
          .build();
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
            updateFieldVisibility();
        }).bounds(rightColX, 107, 108, 20)
          .tooltip(Tooltip.create(Component.literal("Cycle Speech-to-Text input: Sherpa (bundled offline), Local CLI, Auto, Whisper API, or Off")))
          .build();
        this.addRenderableWidget(this.sttModeButton);

        this.testMicButton = Button.builder(Component.literal("Mic").withStyle(ChatFormatting.AQUA), btn -> {
            applyTransientVoiceConfig();
            this.statusText = "Recording mic for 3 seconds... Speak now!";
            this.statusColor = 0xFFFF5555;
            VicVoiceInputService.recordFixedDurationAndTranscribe(3, res -> {
                Minecraft.getInstance().execute(() -> {
                    if (res.success()) {
                        this.statusText = "Heard: \"" + res.text() + "\"";
                        this.statusColor = 0xFF55FF55;
                    } else {
                        this.statusText = res.errorMessage();
                        this.statusColor = 0xFFFFAA00;
                    }
                });
            });
        }).bounds(rightColX + 112, 107, 38, 20)
          .tooltip(Tooltip.create(Component.literal("Record microphone for 3 seconds and display recognized text")))
          .build();
        this.addRenderableWidget(this.testMicButton);

        // 6. Piper Model Path & Personality Profile (y = 140)
        this.piperModelPathField = new EditBox(this.font, leftColX, 140, 150, 20, Component.literal("Piper voice model path"));
        this.piperModelPathField.setMaxLength(512);
        this.piperModelPathField.setValue(cfg.piperModelPath != null ? cfg.piperModelPath : "");
        this.piperModelPathField.setHint(Component.literal("Path to .onnx file (or blank)"));
        this.piperModelPathField.setTooltip(Tooltip.create(Component.literal("Custom path to Piper ONNX model file (only needed for Piper mode)")));
        this.addRenderableWidget(this.piperModelPathField);

        this.personalityProfileButton = Button.builder(getProfileButtonText(), btn -> {
            this.personalityProfile = this.personalityProfile.next();
            btn.setMessage(getProfileButtonText());
        }).bounds(rightColX, 140, 150, 20)
          .tooltip(Tooltip.create(Component.literal("Select AI Persona Profile (Classic, Stoic, Melancholy, Aggressive, Analytical)")))
          .build();
        this.addRenderableWidget(this.personalityProfileButton);

        // 7. Gameplay Toggles (y = 164 & 186)
        this.daysButton = Button.builder(getDaysButtonText(), btn -> {
            this.countdownDays = (this.countdownDays % 15) + 1;
            btn.setMessage(getDaysButtonText());
        }).bounds(leftColX, 164, 150, 20)
          .tooltip(Tooltip.create(Component.literal("Total days in the Vic trial before awakening (1-15)")))
          .build();
        this.addRenderableWidget(this.daysButton);

        this.personaButton = Button.builder(getPersonaButtonText(), btn -> {
            this.vicPersona = this.vicPersona.next();
            btn.setMessage(getPersonaButtonText());
        }).bounds(rightColX, 164, 150, 20)
          .tooltip(Tooltip.create(Component.literal("Vic's personality archetype and color theme")))
          .build();
        this.addRenderableWidget(this.personaButton);

        this.worldDecayButton = Button.builder(getToggleText("World Decay", this.enableWorldDecay), btn -> {
            this.enableWorldDecay = !this.enableWorldDecay;
            btn.setMessage(getToggleText("World Decay", this.enableWorldDecay));
        }).bounds(leftColX, 186, 150, 20)
          .tooltip(Tooltip.create(Component.literal("Enable eerie visual and environment changes as Vic progresses")))
          .build();
        this.addRenderableWidget(this.worldDecayButton);

        this.trueDarknessButton = Button.builder(getToggleText("Night Darkness", this.enableTrueDarkness), btn -> {
            this.enableTrueDarkness = !this.enableTrueDarkness;
            btn.setMessage(getToggleText("Night Darkness", this.enableTrueDarkness));
        }).bounds(rightColX, 186, 150, 20)
          .tooltip(Tooltip.create(Component.literal("Intensify darkness during nighttime when Vic is active")))
          .build();
        this.addRenderableWidget(this.trueDarknessButton);

        // 8. Save & Cancel Buttons
        int bottomY = this.height - 23;
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close").withStyle(ChatFormatting.GREEN), btn -> {
            saveChanges();
            this.onClose();
        }).bounds(leftColX, bottomY, 150, 20)
          .tooltip(Tooltip.create(Component.literal("Save all configuration changes and return to game")))
          .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            this.onClose();
        }).bounds(rightColX, bottomY, 150, 20)
          .tooltip(Tooltip.create(Component.literal("Discard any unsaved changes and return to game")))
          .build());

        updateFieldVisibility();
        if ("OLLAMA".equals(this.aiProvider)) {
            scanAndCycleOllamaModels(false);
        }
    }

    private void updateFieldVisibility() {
        boolean isOllama = "OLLAMA".equals(this.aiProvider);
        boolean isScripted = "SCRIPTED".equals(this.aiProvider);
        boolean isRemote = !isOllama && !isScripted;

        this.ollamaUrlField.setVisible(isOllama);
        this.ollamaUrlField.active = isOllama;
        this.ollamaUrlField.setEditable(isOllama);

        this.ollamaModelField.setVisible(isOllama);
        this.ollamaModelField.active = isOllama;
        this.ollamaModelField.setEditable(isOllama);

        this.scanOllamaButton.visible = isOllama;
        this.scanOllamaButton.active = isOllama;

        this.apiEndpointField.setVisible(isRemote);
        this.apiEndpointField.active = isRemote;
        this.apiEndpointField.setEditable(isRemote);

        this.apiModelField.setVisible(isRemote);
        this.apiModelField.active = isRemote;
        this.apiModelField.setEditable(isRemote);
    }

    private void applyProviderPresetDefaults(String provider) {
        switch (provider) {
            case "OPENAI" -> {
                this.apiEndpointField.setValue("https://api.openai.com/v1/chat/completions");
                this.apiModelField.setValue("gpt-4o-mini");
            }
            case "GEMINI" -> {
                this.apiEndpointField.setValue("https://generativelanguage.googleapis.com/v1beta/models");
                this.apiModelField.setValue("gemini-1.5-flash");
            }
            case "CLAUDE" -> {
                this.apiEndpointField.setValue("https://api.anthropic.com/v1/messages");
                this.apiModelField.setValue("claude-3-5-haiku-20241022");
            }
            case "GROQ" -> {
                this.apiEndpointField.setValue("https://api.groq.com/openai/v1/chat/completions");
                this.apiModelField.setValue("llama-3.3-70b-versatile");
            }
            case "DEEPSEEK" -> {
                this.apiEndpointField.setValue("https://api.deepseek.com/chat/completions");
                this.apiModelField.setValue("deepseek-chat");
            }
            case "OPENROUTER" -> {
                this.apiEndpointField.setValue("https://openrouter.ai/api/v1/chat/completions");
                this.apiModelField.setValue("meta-llama/llama-3.3-70b-instruct");
            }
            case "MISTRAL" -> {
                this.apiEndpointField.setValue("https://api.mistral.ai/v1/chat/completions");
                this.apiModelField.setValue("mistral-small-latest");
            }
        }
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
            this.statusColor = 0xFF55FF55;
            return;
        }

        String url = this.ollamaUrlField.getValue();
        this.statusText = "Scanning Ollama models at " + VicAiService.normalizeOllamaBaseUrl(url) + "...";
        this.statusColor = 0xFFFFFF55;

        VicAiService.fetchOllamaModels(url).thenAccept(models -> {
            Minecraft.getInstance().execute(() -> {
                this.detectedOllamaModels.clear();
                this.detectedOllamaModels.addAll(models);
                if (models.isEmpty()) {
                    this.statusText = "No local Ollama models detected (is 'ollama serve' running?)";
                    this.statusColor = 0xFFFF5555;
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
                    this.statusColor = 0xFF55FF55;
                }
            });
        });
    }

    private Component getProviderButtonText() {
        ChatFormatting color = switch (this.aiProvider) {
            case "OPENAI" -> ChatFormatting.GREEN;
            case "GEMINI" -> ChatFormatting.BLUE;
            case "CLAUDE" -> ChatFormatting.GOLD;
            case "GROQ" -> ChatFormatting.LIGHT_PURPLE;
            case "DEEPSEEK" -> ChatFormatting.AQUA;
            case "OPENROUTER" -> ChatFormatting.DARK_AQUA;
            case "MISTRAL" -> ChatFormatting.RED;
            case "OLLAMA" -> ChatFormatting.DARK_GREEN;
            default -> ChatFormatting.YELLOW;
        };
        String label = switch (this.aiProvider) {
            case "OPENAI" -> "OpenAI (Default)";
            case "GEMINI" -> "Google Gemini";
            case "CLAUDE" -> "Anthropic Claude";
            case "GROQ" -> "Groq (Ultra-Fast)";
            case "DEEPSEEK" -> "DeepSeek";
            case "OPENROUTER" -> "OpenRouter";
            case "MISTRAL" -> "Mistral AI";
            case "OLLAMA" -> "Ollama (Local LLM)";
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

    private Component getProfileButtonText() {
        return Component.literal("Profile: ").append(Component.literal(this.personalityProfile.getDisplayName()).withStyle(this.personalityProfile.getFormatting(), ChatFormatting.BOLD));
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
        cfg.setPersonalityProfile(this.personalityProfile.getId());
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
                this.minecraft.player.connection.sendCommand("vic profile " + this.personalityProfile.getId().toLowerCase());
            }
            this.minecraft.player.sendOverlayMessage(
                    Component.literal("[Vic Config] ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                            .append(Component.literal("Saved! Persona=" + this.vicPersona.getDisplayName()
                                    + " (" + this.personalityProfile.getDisplayName() + ")"
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
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        // Dim the background overlay for crisp readability
        context.fill(0, 0, this.width, this.height, 0xD0101014);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int leftColX = centerX - 155;
        int rightColX = centerX + 5;

        // Title
        context.centeredText(this.font, this.title, centerX, 8, 0xFFFFE135);

        // Section 1: AI Provider
        if ("OLLAMA".equals(this.aiProvider)) {
            context.text(this.font, "Ollama Base URL:", leftColX, 42, 0xFFCCCCCC, true);
            context.text(this.font, "Ollama Model:", rightColX, 42, 0xFFCCCCCC, true);
        } else if ("SCRIPTED".equals(this.aiProvider)) {
            context.centeredText(
                    this.font,
                    Component.literal("Using built-in stage-aware dialogue (no external AI server required)."),
                    centerX,
                    56,
                    0xFFAAAAAA
            );
        } else {
            String endpointLabel = switch (this.aiProvider) {
                case "GEMINI" -> "Gemini API Base URL:";
                case "CLAUDE" -> "Claude API Endpoint:";
                default -> "API Endpoint URL:";
            };
            String modelLabel = switch (this.aiProvider) {
                case "GEMINI" -> "Gemini Model (e.g. gemini-1.5-flash):";
                case "CLAUDE" -> "Claude Model (e.g. claude-3-5-haiku):";
                default -> "API Model ID:";
            };
            context.text(this.font, endpointLabel, leftColX, 42, 0xFFCCCCCC, true);
            context.text(this.font, modelLabel, rightColX, 42, 0xFFCCCCCC, true);
        }

        // Section 2: Credentials & Voice
        context.text(this.font, "API Key (OpenAI / Gemini / Claude / etc.):", leftColX, 74, 0xFFCCCCCC, true);
        context.text(this.font, "OpenAI TTS Voice (if used):", rightColX, 74, 0xFFCCCCCC, true);

        // Section 3: Piper Model Path & Personality Profile
        context.text(this.font, "Piper Model (.onnx):", leftColX, 130, 0xFFCCCCCC, true);
        context.text(this.font, "Personality Profile:", rightColX, 130, 0xFFCCCCCC, true);

        // Status text / Tip
        int bottomY = this.height - 23;
        int statusY = Math.max(208, bottomY - 14);
        if (this.statusText != null && !this.statusText.isEmpty()) {
            context.centeredText(this.font, this.statusText, centerX, statusY, 0xFF000000 | (this.statusColor & 0x00FFFFFF));
        } else {
            context.centeredText(
                    this.font,
                    "Tip: Hold [R] in-game to talk to Vic with your microphone!",
                    centerX,
                    statusY,
                    0xFFAAAAAA
            );
        }
    }
}
