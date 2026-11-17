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

/**
 * Handles the loading, saving, and initialization of VicConfig.
 * Separates persistence logic from the VicConfig data model.
 */
public class VicConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("vic.json");
    
    public static VicConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                VicConfig instance = GSON.fromJson(reader, VicConfig.class);
                if (instance == null) {
                    instance = new VicConfig();
                }
                
                // Run migration checks if loaded from disk
                VicConfig migrationHandler = new VicConfig(instance);
                migrationHandler.runMigrationChecks();
                
                return instance;
            } catch (JsonParseException e) {
                VicMod.LOGGER.error("Invalid JSON in {} ({}); using defaults and leaving the file untouched", CONFIG_PATH, e.getClass().getSimpleName());
                return new VicConfig();
            } catch (IOException e) {
                VicMod.LOGGER.error("Failed to read vic.json config, using defaults", e);
                return new VicConfig();
            }
        } else {
            VicConfig newConfig = new VicConfig();
            save(newConfig);
            return newConfig;
        }
    }

    public static void save(VicConfig config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            VicMod.LOGGER.error("Failed to save vic.json config", e);
        }
    }
}