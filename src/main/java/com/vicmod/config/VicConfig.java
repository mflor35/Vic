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
                // Migrate legacy enableAiApi flag if aiProvider wasn't set
                if (INSTANCE.aiProvider == null || INSTANCE.aiProvider.isBlank()) {
                    INSTANCE.aiProvider = INSTANCE.enableAiApi ? "OPENAI" : "SCRIPTED";
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
