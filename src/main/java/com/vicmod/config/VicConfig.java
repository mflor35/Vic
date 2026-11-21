package com.vicmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.vicmod.VicMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class VicConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("vic.json");

    private static volatile VicConfig INSTANCE;

    // Default to lightweight built-in scripted dialogue for potato/low-spec compatibility
    // AI Provider: "SCRIPTED" (0 extra CPU/RAM, instant, runs on any potato PC), "OLLAMA" (local LLM), or "OPENAI" (cloud API)
    public String aiProvider = "SCRIPTED";
    public boolean enableAiApi = false;

    // Ollama settings
    public String ollamaUrl = "http://localhost:11434";
    public String ollamaModel = "timheinrich2011/verity-3b:latest";

    // OpenAI-compatible API settings (Groq, OpenAI, OpenRouter, etc.)
    public String apiEndpoint = "https://api.groq.com/openai/v1/chat/completions";
    public String apiKey = "";
    public String apiModel = "llama-3.3-70b-versatile";

    // Text-to-Speech (TTS) settings — Bundled Sherpa-ONNX Piper voice is preferred
    // ttsMode: "SHERPA", "PIPER", "LOCAL", "SYNTH", "SYSTEM", "OPENAI_TTS", or "OFF"
    public boolean enableTts = true;
    public String ttsMode = "SHERPA";
    public String piperModelPath = "";
    public String ttsEndpoint = "https://api.openai.com/v1/audio/speech";
    public String ttsVoice = "alloy";
    public float ttsVolume = 0.85f;

    // Voice Input / Speech-to-Text (STT) settings — Bundled Sherpa model runs offline
    // sttMode: "SHERPA", "LOCAL_CLI", "AUTO", "WHISPER_API", or "OFF"
    public boolean enableVoiceInput = true;
    public String sttMode = "SHERPA";
    public String sttEndpoint = "http://localhost:8000/v1/audio/transcriptions";
    public String sttModel = "whisper-large-v3-turbo";
    public String sttApiKey = "";
    public String localSttCommand = "";

    // Gameplay & Personality settings
    // vicPersona: "YELLOW" (Cheerful), "BLUE" (Liar), "GREEN" (Innocent), "PINK" (Loving), "PURPLE" (Cynic)
    public String vicPersona = "YELLOW";
    public String personalityProfile = "CLASSIC";
    public int countdownDays = 5;
    public boolean autoSpawnDeliveryBoxOnFirstJoin = true;
    public boolean enableWorldDecay = true;
    public boolean enableTrueDarknessDuringNight = true;
    public boolean enableHudOverlay = true;
    public boolean enableController = true;
    public float controllerSensitivity = 8.0f;
    public float controllerDeadzone = 0.18f;
    public int requestTimeoutSeconds = 8;

    public String getEffectiveProvider() {
        if (aiProvider == null || aiProvider.isBlank()) {
            return "SCRIPTED";
        }
        String upper = aiProvider.trim().toUpperCase();
        if ("OLLAMA".equals(upper) || "OPENAI".equals(upper) || "SCRIPTED".equals(upper)) {
            return upper;
        }
        return "SCRIPTED";
    }

    public void setProvider(String provider) {
        String upper = provider == null ? "OLLAMA" : provider.trim().toUpperCase();
        switch (upper) {
            case "OLLAMA" -> {
                this.aiProvider = "OLLAMA";
                this.enableAiApi = true;
            }
            case "OPENAI" -> {
                this.aiProvider = "OPENAI";
                this.enableAiApi = true;
            }
            default -> {
                this.aiProvider = "SCRIPTED";
                this.enableAiApi = false;
            }
        }
    }

    public String getEffectiveTtsMode() {
        if (!enableTts || ttsMode == null || ttsMode.isBlank()) {
            return enableTts ? "SHERPA" : "OFF";
        }
        String upper = ttsMode.trim().toUpperCase();
        if ("OFF".equals(upper)) return "OFF";
        if ("AUTO".equals(upper)) return "LOCAL";
        if ("SHERPA".equals(upper) || "PIPER".equals(upper) || "LOCAL".equals(upper) || "SYNTH".equals(upper) || "SYSTEM".equals(upper) || "OPENAI_TTS".equals(upper)) {
            return upper;
        }
        return "SHERPA";
    }

    public void setTtsMode(String mode) {
        String upper = mode == null ? "LOCAL" : mode.trim().toUpperCase();
        if ("OFF".equals(upper)) {
            this.enableTts = false;
            this.ttsMode = "OFF";
        } else {
            this.enableTts = true;
            this.ttsMode = switch (upper) {
                case "SHERPA", "PIPER", "SYNTH", "SYSTEM", "OPENAI_TTS", "LOCAL" -> upper;
                default -> "SHERPA";
            };
        }
    }

    public String getEffectiveSttMode() {
        if (!enableVoiceInput || sttMode == null || sttMode.isBlank()) {
            return enableVoiceInput ? "SHERPA" : "OFF";
        }
        String upper = sttMode.trim().toUpperCase();
        if ("OFF".equals(upper)) return "OFF";
        if ("SHERPA".equals(upper) || "WHISPER_API".equals(upper) || "LOCAL_CLI".equals(upper) || "AUTO".equals(upper)) {
            return upper;
        }
        return "SHERPA";
    }

    public void setSttMode(String mode) {
        String upper = mode == null ? "SHERPA" : mode.trim().toUpperCase();
        if ("OFF".equals(upper)) {
            this.enableVoiceInput = false;
            this.sttMode = "OFF";
        } else {
            this.enableVoiceInput = true;
            this.sttMode = switch (upper) {
                case "SHERPA", "WHISPER_API", "AUTO", "LOCAL_CLI" -> upper;
                default -> "SHERPA";
            };
        }
    }

    public String getEffectiveSttApiKey() {
        if (sttApiKey != null && !sttApiKey.isBlank()) {
            return sttApiKey.trim();
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return "";
    }

    public VicPersona getEffectivePersona() {
        return VicPersona.fromString(this.vicPersona);
    }

    public VicPersonalityProfile getEffectivePersonalityProfile() {
        return VicPersonalityProfile.fromString(this.personalityProfile);
    }

    public void setPersonalityProfile(String profile) {
        this.personalityProfile = VicPersonalityProfile.fromString(profile).getId();
    }

    public void setPersona(String persona) {
        this.vicPersona = VicPersona.fromString(persona).getId();
    }

    public static VicConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(reader, VicConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new VicConfig();
                }
                boolean migrated = false;
                if (INSTANCE.aiProvider == null || INSTANCE.aiProvider.isBlank()) {
                    INSTANCE.aiProvider = "SCRIPTED";
                    INSTANCE.enableAiApi = false;
                    migrated = true;
                }
                if (INSTANCE.ttsMode == null || INSTANCE.ttsMode.isBlank() || "AUTO".equalsIgnoreCase(INSTANCE.ttsMode)) {
                    INSTANCE.ttsMode = INSTANCE.enableTts ? "SHERPA" : "OFF";
                    migrated = true;
                } else if ("LOCAL".equalsIgnoreCase(INSTANCE.ttsMode)
                    || "PIPER".equalsIgnoreCase(INSTANCE.ttsMode)) {
                    INSTANCE.ttsMode = "SHERPA";
                    migrated = true;
                }
                if (INSTANCE.sttMode == null || INSTANCE.sttMode.isBlank()) {
                    INSTANCE.sttMode = INSTANCE.enableVoiceInput ? "SHERPA" : "OFF";
                    migrated = true;
                }
                if (migrated) {
                    save();
                }
            } catch (JsonParseException e) {
                VicMod.LOGGER.error("Invalid JSON in {} ({}); using defaults and leaving the file untouched",
                        CONFIG_PATH, e.getClass().getSimpleName());
                INSTANCE = new VicConfig();
            } catch (IOException e) {
                VicMod.LOGGER.error("Failed to read vic.json config, using defaults", e);
                INSTANCE = new VicConfig();
            }
        } else {
            INSTANCE = new VicConfig();
            save();
        }
    }

    public static synchronized void save() {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(get(), writer);
            }
            try {
                Files.move(tempPath, CONFIG_PATH, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException moveEx) {
                Files.move(tempPath, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            VicMod.LOGGER.error("Failed to save vic.json config", e);
        }
    }
}
