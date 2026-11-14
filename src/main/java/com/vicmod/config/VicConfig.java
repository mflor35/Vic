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

    public boolean enableAiApi = false;
    public String apiEndpoint = "https://api.groq.com/openai/v1/chat/completions";
    public String apiKey = "";
    public String apiModel = "llama-3.3-70b-versatile";
    public int countdownDays = 5;
    public boolean autoSpawnDeliveryBoxOnFirstJoin = true;
    public boolean enableWorldDecay = true;
    public boolean enableTrueDarknessDuringNight = true;
    public int requestTimeoutSeconds = 12;

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
