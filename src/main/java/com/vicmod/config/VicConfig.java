package com.vicmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    private static VicConfig INSTANCE;

    // AI Provider: "SCRIPTED", "OLLAMA", or "OPENAI"
    public String aiProvider = "SCRIPTED";
    public boolean enableAiApi = false;

    // Ollama settings
    public String ollamaUrl = "http://localhost:11434";
    public String ollamaModel = "llama3.2";

    // OpenAI-compatible API settings (Groq, OpenAI, OpenRouter, etc.)
    public String apiEndpoint = "https://api.groq.com/openai/v1/chat/completions";
    public String apiKey = "";
    public String apiModel = "llama-3.3-70b-versatile";

    // Text-to-Speech (TTS) settings
    // ttsMode: "AUTO", "SYNTH", "SYSTEM", "OPENAI_TTS", or "OFF"
    public boolean enableTts = true;
    public String ttsMode = "AUTO";
    public String ttsEndpoint = "https://api.openai.com/v1/audio/speech";
    public String ttsVoice = "alloy";
    public float ttsVolume = 0.85f;

    // Voice Input / Speech-to-Text (STT) settings
    // sttMode: "AUTO", "WHISPER_API", "LOCAL_CLI", or "OFF"
    public boolean enableVoiceInput = true;
    public String sttMode = "AUTO";
    public String sttEndpoint = "https://api.groq.com/openai/v1/audio/transcriptions";
    public String sttModel = "whisper-large-v3-turbo";
    public String sttApiKey = "";
    public String localSttCommand = "";

    // Gameplay settings
    public int countdownDays = 5;
    public boolean autoSpawnDeliveryBoxOnFirstJoin = true;
    public boolean enableWorldDecay = true;
    public boolean enableTrueDarknessDuringNight = true;
    public int requestTimeoutSeconds = 20;

    public String getEffectiveProvider() {
        if (aiProvider == null || aiProvider.isBlank()) {
            return enableAiApi ? "OPENAI" : "SCRIPTED";
        }
        String upper = aiProvider.trim().toUpperCase();
        if (!enableAiApi && "SCRIPTED".equals(upper)) {
            return "SCRIPTED";
        }
        if ("OLLAMA".equals(upper) || "OPENAI".equals(upper) || "SCRIPTED".equals(upper)) {
            return upper;
        }
        return enableAiApi ? "OPENAI" : "SCRIPTED";
    }

    public void setProvider(String provider) {
        String upper = provider == null ? "SCRIPTED" : provider.trim().toUpperCase();
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
            return enableTts ? "AUTO" : "OFF";
        }
        String upper = ttsMode.trim().toUpperCase();
        if ("OFF".equals(upper)) return "OFF";
        if ("SYNTH".equals(upper) || "SYSTEM".equals(upper) || "OPENAI_TTS".equals(upper) || "AUTO".equals(upper)) {
            return upper;
        }
        return "AUTO";
    }

    public void setTtsMode(String mode) {
        String upper = mode == null ? "OFF" : mode.trim().toUpperCase();
        if ("OFF".equals(upper)) {
            this.enableTts = false;
            this.ttsMode = "OFF";
        } else {
            this.enableTts = true;
            this.ttsMode = switch (upper) {
                case "SYNTH", "SYSTEM", "OPENAI_TTS" -> upper;
                default -> "AUTO";
            };
        }
    }

    public String getEffectiveSttMode() {
        if (!enableVoiceInput || sttMode == null || sttMode.isBlank()) {
            return enableVoiceInput ? "AUTO" : "OFF";
        }
        String upper = sttMode.trim().toUpperCase();
        if ("OFF".equals(upper)) return "OFF";
        if ("WHISPER_API".equals(upper) || "LOCAL_CLI".equals(upper) || "AUTO".equals(upper)) {
            return upper;
        }
        return "AUTO";
    }

    public void setSttMode(String mode) {
        String upper = mode == null ? "OFF" : mode.trim().toUpperCase();
        if ("OFF".equals(upper)) {
            this.enableVoiceInput = false;
            this.sttMode = "OFF";
        } else {
            this.enableVoiceInput = true;
            this.sttMode = switch (upper) {
                case "WHISPER_API", "LOCAL_CLI" -> upper;
                default -> "AUTO";
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
                if (INSTANCE.aiProvider == null || INSTANCE.aiProvider.isBlank()) {
                    INSTANCE.aiProvider = INSTANCE.enableAiApi ? "OPENAI" : "SCRIPTED";
                }
                if (INSTANCE.ttsMode == null || INSTANCE.ttsMode.isBlank()) {
                    INSTANCE.ttsMode = INSTANCE.enableTts ? "AUTO" : "OFF";
                }
                if (INSTANCE.sttMode == null || INSTANCE.sttMode.isBlank()) {
                    INSTANCE.sttMode = INSTANCE.enableVoiceInput ? "AUTO" : "OFF";
                }
            } catch (IOException e) {
                VicMod.LOGGER.error("Failed to read vic.json config, using defaults", e);
                INSTANCE = new VicConfig();
            }
        } else {
            INSTANCE = new VicConfig();
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(get(), writer);
            }
        } catch (IOException e) {
            VicMod.LOGGER.error("Failed to save vic.json config", e);
        }
    }
}
