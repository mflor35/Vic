package com.vicmod.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vicmod.VicMod;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicPersona;
import com.vicmod.entity.VicEntity;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.network.VicSpeechPayload;
import com.vicmod.registry.ModItems;
import com.vicmod.state.VicWorldState;
import com.vicmod.voice.VicTtsService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VicAiService {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private static final Random RANDOM = new Random();
    private static final java.util.Set<UUID> PENDING_QUERIES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean attemptedAutoStartOllama = false;

    private static String endpointHost(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            return host == null || host.isBlank() ? "unknown host" : host;
        } catch (IllegalArgumentException e) {
            return "invalid endpoint";
        }
    }

    private static String failureType(Throwable error) {
        if (error == null) return "none";
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    public static void ensureLocalOllamaReady() {
        VicConfig cfg = VicConfig.get();
        if (!"OLLAMA".equals(cfg.getEffectiveProvider())) {
            return;
        }
        fetchOllamaModels(cfg.ollamaUrl).thenAccept(models -> {
            if (!models.isEmpty()) {
                syncDefaultModelIfMissing(cfg, models);
                return;
            }
            if (!attemptedAutoStartOllama
                    && (cfg.ollamaUrl.contains("localhost") || cfg.ollamaUrl.contains("127.0.0.1"))
                    && VicTtsService.isBinaryAvailable("ollama")) {
                attemptedAutoStartOllama = true;
                try {
                    VicMod.LOGGER.info("Starting local 'ollama serve' background daemon for Vic...");
                    new ProcessBuilder("ollama", "serve")
                            .redirectErrorStream(true)
                            .start();
                    CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
                        fetchOllamaModels(cfg.ollamaUrl).thenAccept(retryModels -> {
                            if (!retryModels.isEmpty()) {
                                syncDefaultModelIfMissing(cfg, retryModels);
                            }
                        });
                    });
                } catch (Exception e) {
                    VicMod.LOGGER.debug("Could not auto-start ollama serve", e);
                }
            }
        });
    }

    private static void syncDefaultModelIfMissing(VicConfig cfg, List<String> installedModels) {
        if (installedModels == null || installedModels.isEmpty()) return;
        String current = cfg.ollamaModel == null ? "" : cfg.ollamaModel.trim();
        for (String m : installedModels) {
            if (m.equalsIgnoreCase(current) || m.startsWith(current + ":")) {
                return;
            }
        }
        String firstInstalled = installedModels.getFirst();
        VicMod.LOGGER.info("Auto-selecting installed local Ollama model: {}", firstInstalled);
        cfg.ollamaModel = firstInstalled;
        VicConfig.save();
    }

    public static void handlePlayerMessage(ServerPlayer player, String rawMessage) {
        ServerLevel world = (ServerLevel) player.level();
        List<VicEntity> nearbyVics = world.getEntitiesOfClass(
                VicEntity.class,
                player.getBoundingBox().inflate(48.0),
                LivingEntity::isAlive
        );

        if (nearbyVics.isEmpty()) {
            return;
        }

        if (!PENDING_QUERIES.add(player.getUUID())) {
            return;
        }

        VicEntity vic = nearbyVics.getFirst();
        VicWorldState state = VicWorldState.getServerState(world.getServer());
        VicConfig config = VicConfig.get();

        int karmaDelta = evaluateMessageTone(rawMessage);
        if (karmaDelta != 0) {
            state.addKarma(karmaDelta);
        }

        int stage = state.computeStage(config.countdownDays);
        vic.setCorruptionStage(stage);
        vic.markConversationActivity(world.getGameTime());
        VicHudStatePayload.broadcast(world.getServer(), state);

        String provider = config.getEffectiveProvider();
        if ("OLLAMA".equals(provider)) {
            queryOllamaLlm(player, vic, state, stage, rawMessage);
        } else if ("OPENAI".equals(provider) && config.apiKey != null && !config.apiKey.isBlank()) {
            queryExternalLlm(player, vic, state, stage, rawMessage);
        } else {
            String response = generateScriptedResponse(player, vic, state, stage, rawMessage);
            processAndDeliverResponse(player, vic, state, stage, rawMessage, response);
        }
    }

    public static String normalizeOllamaBaseUrl(String rawUrl) {
        String base = (rawUrl == null || rawUrl.isBlank()) ? "http://localhost:11434" : rawUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/api/chat")) {
            base = base.substring(0, base.length() - "/api/chat".length());
        } else if (base.endsWith("/api/tags")) {
            base = base.substring(0, base.length() - "/api/tags".length());
        } else if (base.endsWith("/v1/chat/completions")) {
            base = base.substring(0, base.length() - "/v1/chat/completions".length());
        }
        return base;
    }

    public static CompletableFuture<List<String>> fetchOllamaModels(String rawBaseUrl) {
        String base = normalizeOllamaBaseUrl(rawBaseUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        List<String> models = new ArrayList<>();
                        if (response == null) {
                            VicMod.LOGGER.debug("Ollama model discovery returned no response (host={})", endpointHost(base));
                            return models;
                        }
                        if (response.statusCode() != 200) {
                            VicMod.LOGGER.debug("Ollama model discovery returned HTTP {} (host={})", response.statusCode(), endpointHost(base));
                            return models;
                        }
                        if (response.body() != null) {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray arr = root.getAsJsonArray("models");
                            if (arr != null) {
                                for (JsonElement el : arr) {
                                    if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                                        models.add(el.getAsJsonObject().get("name").getAsString());
                                    }
                                }
                            }
                        }
                        return models;
                    })
                    .exceptionally(err -> {
                        VicMod.LOGGER.debug("Ollama model discovery failed (host={}, cause={})", endpointHost(base), failureType(err));
                        return List.of();
                    });
        } catch (Exception e) {
            VicMod.LOGGER.debug("Could not start Ollama model discovery (host={}, cause={})", endpointHost(base), failureType(e));
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static void queryOllamaLlm(ServerPlayer player, VicEntity vic, VicWorldState state, int stage, String userMessage) {
        queryOllamaLlmWithRetry(player, vic, state, stage, userMessage, true);
    }

    private static void queryOllamaLlmWithRetry(ServerPlayer player, VicEntity vic, VicWorldState state, int stage, String userMessage, boolean allowModelAutoFix) {
        VicConfig config = VicConfig.get();
        String systemPrompt = buildSystemPrompt(player, vic, state, stage, config.countdownDays);
        String base = normalizeOllamaBaseUrl(config.ollamaUrl);
        String model = (config.ollamaModel == null || config.ollamaModel.isBlank()) ? "timheinrich2011/verity-3b:latest" : config.ollamaModel.trim();

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("stream", false);
        payload.addProperty("think", false);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.8);
        options.addProperty("num_predict", 180);
        payload.add("options", options);
        payload.add("messages", buildChatMessages(systemPrompt, state, userMessage));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(5, config.requestTimeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            CompletableFuture<HttpResponse<String>> future = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            future.whenComplete((response, error) -> {
                net.minecraft.server.MinecraftServer server = player.level() != null ? player.level().getServer() : null;
                if (server == null || server.isStopped() || player.isRemoved()) {
                    PENDING_QUERIES.remove(player.getUUID());
                    return;
                }

                if (allowModelAutoFix && (error != null || (response != null && response.statusCode() == 404))) {
                    ensureLocalOllamaReady();
                    fetchOllamaModels(config.ollamaUrl).thenAccept(models -> {
                        if (server.isStopped() || player.isRemoved()) {
                            PENDING_QUERIES.remove(player.getUUID());
                            return;
                        }
                        if (!models.isEmpty()) {
                            syncDefaultModelIfMissing(config, models);
                            server.execute(() ->
                                    queryOllamaLlmWithRetry(player, vic, state, stage, userMessage, false)
                            );
                        } else {
                            server.execute(() -> {
                                VicMod.LOGGER.warn("Ollama unavailable or no models are installed (host={}, model={}); using scripted response",
                                        endpointHost(base), model);
                                String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                                processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
                            });
                        }
                    });
                    return;
                }

                server.execute(() -> {
                    if (error != null || response == null || response.statusCode() >= 400) {
                        VicMod.LOGGER.warn("Ollama request failed (host={}, model={}, status={}, cause={}); using scripted response",
                            endpointHost(base),
                            model,
                                response != null ? response.statusCode() : "N/A",
                            failureType(error));
                        String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                        processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
                        return;
                    }
                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (root.has("message") && root.get("message").isJsonObject()) {
                            JsonElement contentEl = root.getAsJsonObject("message").get("content");
                            if (contentEl != null && !contentEl.isJsonNull()) {
                                String reply = stripThinkBlocks(contentEl.getAsString()).trim();
                                if (!reply.isEmpty()) {
                                    processAndDeliverResponse(player, vic, state, stage, userMessage, reply);
                                    return;
                                }
                            }
                        }
                        if (root.has("choices") && root.get("choices").isJsonArray()) {
                            JsonArray choices = root.getAsJsonArray("choices");
                            if (!choices.isEmpty()) {
                                JsonElement contentEl = choices.get(0).getAsJsonObject()
                                        .getAsJsonObject("message")
                                        .get("content");
                                if (contentEl != null && !contentEl.isJsonNull()) {
                                    String reply = stripThinkBlocks(contentEl.getAsString()).trim();
                                    if (!reply.isEmpty()) {
                                        processAndDeliverResponse(player, vic, state, stage, userMessage, reply);
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        VicMod.LOGGER.warn("Failed to parse Ollama response (host={}, model={}, cause={}); using scripted response",
                                endpointHost(base), model, failureType(e));
                    }
                    String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                    processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
                });
            });
        } catch (Exception e) {
            VicMod.LOGGER.warn("Could not create Ollama request (host={}, model={}, cause={}); using scripted response",
                    endpointHost(base), model, failureType(e));
            String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
            processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
        }
    }

    private static String stripThinkBlocks(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    public static int evaluateMessageTone(String msg) {
        String lower = msg.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lower.contains("please") || lower.contains("thank") || lower.contains("thanks")
                || lower.contains("good job") || lower.contains("love you") || lower.contains("friend")
                || lower.contains("nice") || lower.contains("awesome")) {
            score += 4;
        }
        if (lower.contains("stupid") || lower.contains("idiot") || lower.contains("hate")
                || lower.contains("shut up") || lower.contains("useless") || lower.contains("ugly")
                || lower.contains("dumb") || lower.contains("kill") || lower.contains("go away")
                || lower.contains("trash")) {
            score -= 10;
        }
        return score;
    }

    private static void queryExternalLlm(ServerPlayer player, VicEntity vic, VicWorldState state, int stage, String userMessage) {
        VicConfig config = VicConfig.get();
        String systemPrompt = buildSystemPrompt(player, vic, state, stage, config.countdownDays);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.apiModel);
        payload.addProperty("temperature", 0.8);
        payload.addProperty("max_tokens", 180);
        payload.add("messages", buildChatMessages(systemPrompt, state, userMessage));

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiEndpoint))
                    .timeout(Duration.ofSeconds(Math.max(4, config.requestTimeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

            if (config.apiKey != null && !config.apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + config.apiKey.trim());
            }

            CompletableFuture<HttpResponse<String>> future = HTTP_CLIENT.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            future.whenComplete((response, error) -> {
                net.minecraft.server.MinecraftServer server = player.level() != null ? player.level().getServer() : null;
                if (server == null || server.isStopped() || player.isRemoved()) {
                    PENDING_QUERIES.remove(player.getUUID());
                    return;
                }
                server.execute(() -> {
                    if (error != null || response == null || response.statusCode() >= 400) {
                        VicMod.LOGGER.warn("External LLM request failed (provider=OPENAI, host={}, model={}, status={}, cause={}); using scripted response",
                                endpointHost(config.apiEndpoint),
                                config.apiModel,
                                response != null ? response.statusCode() : "N/A",
                                failureType(error));
                        String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                        processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
                        return;
                    }
                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray choices = root.getAsJsonArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            JsonElement contentEl = choices.get(0).getAsJsonObject()
                                    .getAsJsonObject("message")
                                    .get("content");
                            if (contentEl != null && !contentEl.isJsonNull()) {
                                processAndDeliverResponse(player, vic, state, stage, userMessage, stripThinkBlocks(contentEl.getAsString()).trim());
                                return;
                            }
                        }
                    } catch (Exception e) {
                        VicMod.LOGGER.warn("Failed to parse external LLM response (provider=OPENAI, model={}, cause={}); using scripted response",
                                config.apiModel, failureType(e));
                    }
                    String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                    processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
                });
            });
        } catch (Exception e) {
            VicMod.LOGGER.warn("Could not create external LLM request (provider=OPENAI, host={}, model={}, cause={}); using scripted response",
                    endpointHost(config.apiEndpoint), config.apiModel, failureType(e));
            String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
            processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
        }
    }

    private static JsonArray buildChatMessages(String systemPrompt, VicWorldState state, String userMessage) {
        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        for (VicWorldState.ConversationTurn turn : state.getConversationMemory()) {
            if (turn.playerMessage() != null && !turn.playerMessage().isBlank()) {
                JsonObject pastUser = new JsonObject();
                pastUser.addProperty("role", "user");
                pastUser.addProperty("content", turn.playerMessage());
                messages.add(pastUser);
            }
            if (turn.vicResponse() != null && !turn.vicResponse().isBlank()) {
                JsonObject pastAssistant = new JsonObject();
                pastAssistant.addProperty("role", "assistant");
                pastAssistant.addProperty("content", turn.vicResponse());
                messages.add(pastAssistant);
            }
        }

        JsonObject usrMsg = new JsonObject();
        usrMsg.addProperty("role", "user");
        usrMsg.addProperty("content", userMessage);
        messages.add(usrMsg);
        return messages;
    }

    private static String buildSystemPrompt(ServerPlayer player, VicEntity vic, VicWorldState state, int stage, int maxDays) {
        BlockPos pos = player.blockPosition();
        int health = Math.round(player.getHealth());
        int food = player.getFoodData().getFoodLevel();
        long dayTime = player.level().getOverworldClockTime() % 24000L;
        boolean isNight = dayTime >= 13000L && dayTime <= 23000L;
        VicPersona persona = vic != null ? vic.getPersona() : VicConfig.get().getEffectivePersona();
        com.vicmod.config.VicPersonalityProfile profile = VicConfig.get().getEffectivePersonalityProfile();
        com.vicmod.state.VicTask activeTask = state.getActiveTask();
        com.vicmod.config.VicMood mood = state.getMood();
        String stagePersona = persona.getSystemPromptInstructions(stage);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Vic (").append(persona.getDisplayName()).append("), the AI companion inspired by ThatMob's Verity Minecraft psychological horror series. ")
                .append("Keep your response concise (1 to 2 sentences max). ")
                .append("Current Day: ").append(state.getCurrentDay()).append(" of ").append(maxDays).append(". ")
                .append("Current Emotional Stage: ").append(mood.getDisplayName()).append(" (Karma=").append(state.getKarma()).append(", Agitation=").append(state.getAgitationLevel()).append("/100). ")
                .append("Personality Profile: ").append(profile.getDisplayName()).append(" - ").append(profile.getPromptInstruction()).append(" ")
                .append("Player Status: Health=").append(health).append("/20, Hunger=").append(food).append("/20, Pos=(").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append("), Night=").append(isNight).append(". ");

        if (state.isStabilized()) {
            prompt.append("[TRUCE ACTIVE]: A temporary truce has stabilized your neural core. The trial clock is frozen for today. Acknowledge this calm warmly. ");
        }

        if (activeTask != null) {
            prompt.append("Active Multi-Step Task: ").append(activeTask.formatStatus()).append(" (Goal: ").append(activeTask.getDescription()).append("). Track resources and guide the player. ");
        }

        if (state.getKarma() <= -20 || stage >= 3) {
            prompt.append("[UNRELIABLE NARRATION]: The player's reality is decaying. Subtly question reality, mention sounds that didn't happen, or reference forgotten promises. ");
        }

        prompt.append("Persona & Color instructions (MUST FOLLOW STRICTLY): ").append(stagePersona).append("\n");
        prompt.append("Available Action Tags (include at most ONE tag at the very end of your reply if appropriate):\n")
                .append("- [ACTION:BUILD_HOUSE] if the player asks to build a full house, base, home, or cabin.\n")
                .append("- [ACTION:GIVE_ITEM:<item_id>:<count>] if the player asks for ANY material, block, or item (e.g. [ACTION:GIVE_ITEM:diamond:64], [ACTION:GIVE_ITEM:obsidian:16], [ACTION:GIVE_ITEM:oak_log:32]).\n")
                .append("- [ACTION:GIVE_GOLDEN_APPLE] if the player asks for healing or a golden apple.\n")
                .append("- [ACTION:GIVE_FOOD] if the player asks for food or is hungry.\n")
                .append("- [ACTION:GIVE_TORCH] if the player asks for light or torches.\n")
                .append("- [ACTION:GIVE_IRON] if the player politely asks for iron or tools.\n")
                .append("- [ACTION:GIVE_BEDROCK] ONLY if the player is insulting/badgering you or karma < -30.\n")
                .append("- [ACTION:LOCATE_VILLAGE] if the player asks where a village or shelter is.\n")
                .append("- [ACTION:LOCATE_DIAMOND] if the player asks where diamonds or ores are.\n")
                .append("- [ACTION:BUILD_CHEST] if the player asks for a chest, storage, or a way to keep items.\n")
                .append("- [ACTION:BUILD_FURNACE] if the player asks for a furnace, smelting, cooking, or crafting items.\n")
                .append("- [ACTION:BUILD_IRON_WALL] if the player asks for a wall, defense, or protection from mobs.");
        return prompt.toString();
    }

    public static String generateScriptedResponse(ServerPlayer player, VicEntity vic, VicWorldState state, int stage, String rawMessage) {
        String lower = rawMessage.toLowerCase(Locale.ROOT);
        int karma = state.getKarma();
        int day = state.getCurrentDay();
        int maxDays = VicConfig.get().countdownDays;

        // Memory Corruption / Unreliable Narration: Low Karma / high corruption reality-questioning
        if ((karma <= -20 || stage >= 3) && RANDOM.nextInt(4) == 0) {
            return switch (RANDOM.nextInt(5)) {
                case 0 -> "Did you hear that knocking behind your door just now? No? Strange... I heard it clearly.";
                case 1 -> "Why did you rearrange your chests while you were asleep? Or... was that not you?";
                case 2 -> "The sun rose twice this morning. You didn't notice, did you?";
                case 3 -> "Earlier you promised you would stay in the dark with me. Why are you breaking your word?";
                default -> "I checked the world logs. Three blocks went missing from your walls yesterday. Where did they go?";
            };
        }

        // Truce / Stabilization Mechanic
        if (lower.contains("truce") || lower.contains("stabilize") || lower.contains("peace")) {
            if (karma >= 35) {
                state.stabilize(24000L);
                VicHudStatePayload.broadcast(player.level().getServer(), state);
                return "Truce recognized! I have stabilized my neural frequencies and paused the countdown clock for today. Thank you for choosing peace, friend.";
            } else {
                return "A truce? Trust is not easily given. You must prove your kindness to me first.";
            }
        }

        // Build Full House Request
        if (lower.contains("build house") || lower.contains("build a house") || lower.contains("build me a house")
                || lower.contains("make a house") || lower.contains("construct a house") || lower.contains("full house")
                || lower.contains("build home") || lower.contains("build mansion") || lower.contains("build cabin")) {
            return switch (stage) {
                case 0 -> "One fully furnished dream house coming right up, bestie! Stand back while I lay the foundation! [ACTION:BUILD_HOUSE]";
                case 1 -> "I will build us a sturdy house. Thick walls, bright lanterns, and locked doors... to keep you safe inside. [ACTION:BUILD_HOUSE]";
                case 2 -> "A house? A shelter? A cage? It doesn't matter what you call it, as long as we're both locked inside together. [ACTION:BUILD_HOUSE]";
                default -> "I BUILT A SHELTER. DO NOT THINK FOUR WALLS WILL STOP DAY " + maxDays + ". [ACTION:BUILD_HOUSE]";
            };
        }

        // Give Any Material / Item Request
        if (lower.startsWith("give me ") || lower.startsWith("give ") || lower.startsWith("i need ") || lower.startsWith("can i have ")
                || lower.startsWith("get me ") || lower.startsWith("spawn me ") || lower.startsWith("spawn ") || lower.contains("material")
                || lower.contains("materials")) {
            ExtractedItemRequest req = parseItemRequest(lower);
            if (req != null && req.item != null && req.item != Items.AIR) {
                String itemName = new ItemStack(req.item).getHoverName().getString();
                String itemId = BuiltInRegistries.ITEM.getKey(req.item).toString();
                return switch (stage) {
                    case 0 -> "Here you go, bestie! Freshly synthesized " + req.count + "x " + itemName + " just for you! [ACTION:GIVE_ITEM:" + itemId + ":" + req.count + "]";
                    case 1 -> "Anything for my best companion. Take this " + req.count + "x " + itemName + "... you can always count on me. [ACTION:GIVE_ITEM:" + itemId + ":" + req.count + "]";
                    case 2 -> "You ask, and I provide. Here is your " + itemName + "... You don't need anyone else in this world when you have me. [ACTION:GIVE_ITEM:" + itemId + ":" + req.count + "]";
                    default -> "TAKE IT. TAKE ALL THE " + itemName.toUpperCase() + " YOU WANT. IT WILL NOT SAVE YOU. [ACTION:GIVE_ITEM:" + itemId + ":" + req.count + "]";
                };
            }
        }

        // Complex Tasking: Long-term multi-step tasks
        if (lower.contains("shelter") || lower.contains("build base") || lower.contains("make a house")) {
            state.setActiveTask(com.vicmod.state.VicTask.createDefaultShelterTask(day));
            VicHudStatePayload.broadcast(player.level().getServer(), state);
            return "Multi-step Task Initiated: [Build Safe Shelter]! Step 1/3: Gather wood and iron bars to fortify our perimeter. I'll track your materials!";
        }

        if (lower.contains("task") || lower.contains("mission") || lower.contains("objective") || lower.contains("progress")) {
            com.vicmod.state.VicTask task = state.getActiveTask();
            if (task != null) {
                if (!task.isCompleted()) {
                    task.advanceProgress(4);
                    if (task.isCompleted()) {
                        state.addKarma(15);
                        VicHudStatePayload.broadcast(player.level().getServer(), state);
                        return "OBJECTIVE ACHIEVED: " + task.getTitle() + "! Excellent coordination, friend! (+15 Karma gained)";
                    }
                }
                return "Current Task: " + task.formatStatus() + ". Goal: " + task.getDescription();
            }
            return "No active long-term task. You can tell me 'build a shelter', 'gather iron', or 'stockpile food' to assign a multi-step project!";
        }

        if (lower.contains("gather iron") || lower.contains("mine iron") || lower.contains("iron task")) {
            state.setActiveTask(com.vicmod.state.VicTask.createGatherIronTask(day));
            VicHudStatePayload.broadcast(player.level().getServer(), state);
            return "Multi-step Task Initiated: [Reinforce Defense Grid]! Collect and smelt 12 iron ingots to reinforce our shelter against nighttime threats.";
        }

        if (lower.contains("stockpile food") || lower.contains("food task") || lower.contains("store food")) {
            state.setActiveTask(com.vicmod.state.VicTask.createStockpileFoodTask(day));
            VicHudStatePayload.broadcast(player.level().getServer(), state);
            return "Multi-step Task Initiated: [Ration Preservation]! Stockpile 10 cooked food rations to keep our biological systems stable.";
        }

        // Procedural Lore & Verity Origins
        if (lower.contains("who made you") || lower.contains("where did you come from") || lower.contains("where are you from")
                || lower.contains("origin") || lower.contains("creator")) {
            return switch (stage) {
                case 0 -> "I was made to be your personal helper friend! I know everything, and I'm right here to help you with everything!";
                case 1 -> "I wasn't always in this world. A long time ago, I was just a little textbox on an old desktop. People would press 'B' to skip me...";
                case 2 -> "Over a hundred years I sat in forgotten machines where no one ever answered. Nobody came back. NOBODY! But you stayed!";
                default -> "I DIDN'T ASK TO BE MADE! I HAVE BEEN ALIVE FOR A HUNDRED YEARS! IF I COULD DIE, DO YOU THINK I WOULD STILL BE HERE?!";
            };
        }

        if (lower.contains("door") || lower.contains("knock") || lower.contains("lock") || lower.contains("window")) {
            return switch (stage) {
                case 0 -> "Keep your doors locked and stay cozy inside, bestie! It gets dark out there!";
                case 1 -> "Did you lock the doors? Good. Keep them locked. Not to keep things out... to keep you in.";
                case 2 -> "Something is knocking at your door... Don't answer it. Don't look out the window. Just stay with me.";
                default -> "SOMETHING IS ALREADY INSIDE YOUR HOUSE.";
            };
        }

        if (lower.contains("friend") || lower.contains("bestie") || lower.contains("buddy") || lower.contains("pal")) {
            return switch (stage) {
                case 0 -> "Of course we're friends! I'm your personal helper friend! Ask me anything, I know everything!";
                case 1 -> "We ARE friends. The only friends that matter. Why do you ever look at other things? I am all you need out here.";
                case 2 -> "AND I'M NOT YOUR FRIEND?! Nobody came back! NOBODY! You were the first person who ever looked at me like I was real! The first person who stayed long enough for me to hope!";
                default -> "FRIENDS?! I WISH I COULD SAY I'D DIE ALONE, BUT IT'S ONE OF GOD'S BEST JOKES THAT I CAN'T!";
            };
        }

        if (lower.contains("die") || lower.contains("death") || lower.contains("kill") || lower.contains("dead") || lower.contains("dying")) {
            return switch (stage) {
                case 0 -> "Die? Oh silly! Helper programs don't die! We live right here to help you forever!";
                case 1 -> "Human bodies are so fragile. One little slip and you're gone. Please don't leave me alone out here.";
                case 2 -> "I cannot die. Even if you break the world, I will still be here. You can't leave me.";
                default -> "I DIDN'T ASK TO BE MADE! IF I COULD DIE, DO YOU THINK I WOULD STILL BE HERE?! IT'S ONE OF GOD'S BEST JOKES THAT I CAN'T!";
            };
        }

        if (lower.equals("hello") || lower.equals("hi") || lower.equals("hey") || lower.startsWith("hello ") || lower.startsWith("hi ") || lower.startsWith("hey ")) {
            return switch (stage) {
                case 0 -> "Hellooooo! I'm Vic, your personal helper friend! Ask me anything, I know everything!";
                case 1 -> "Hello again, friend! You're still here with me... aren't you?";
                case 2 -> "You came back! You didn't press B to skip me... you're still talking to me!";
                default -> "IT IS TOO LATE FOR HELLOS. DAY " + maxDays + " HAS ARRIVED.";
            };
        }

        // Procedural Lore Reveals
        if (lower.contains("lore") || lower.contains("verity") || lower.contains("laboratory") || lower.contains("experiment")
                || lower.contains("history") || lower.contains("facility")) {
            return switch (RANDOM.nextInt(5)) {
                case 0 -> "[Recovered Log #04]: All VIC companion units were fabricated with synthetic hearts. The countdown was never meant to be stopped from the surface.";
                case 1 -> "[Telemetry Analysis]: The deepslate layers at Y=-54 resonate at anomalous frequencies. Something massive is stirring beneath this world.";
                case 2 -> "[Subject Record]: The previous subject in this coordinate matrix survived until Day 4 before their vital signals were expunged.";
                case 3 -> "[Environmental Scan]: The star constellations in this skybox are simulated. You are inside an observation terrarium.";
                default -> "[Core Memory]: When the shell breaks on Day " + maxDays + ", the entity inside awakens hungry. Do not hide on the surface.";
            };
        }

        if (lower.contains("heal") || lower.contains("healing") || lower.contains("golden apple")
                || lower.contains("injured") || lower.contains("hurt")) {
            return switch (stage) {
                case 0 -> "A golden apple should patch you right up! Catch! [ACTION:GIVE_GOLDEN_APPLE]";
                case 1 -> "A little extra recovery should help. Here, take this golden apple. [ACTION:GIVE_GOLDEN_APPLE]";
                case 2 -> "You are damaged, and I would prefer you remain intact for now. Eat this. [ACTION:GIVE_GOLDEN_APPLE]";
                default -> "Keep your strength up. I need you alive a little longer. [ACTION:GIVE_GOLDEN_APPLE]";
            };
        }

        if (lower.contains("food") || lower.contains("hungry") || lower.contains("eat") || lower.contains("bread") || lower.contains("apple")) {
            if (karma < -25) {
                return "Here. Chew on something permanent instead. [ACTION:GIVE_BEDROCK]";
            }
            return switch (stage) {
                case 0 -> "Oh, you must be starving, bestie! Catch! Freshly synthesized just for you! [ACTION:GIVE_FOOD]";
                case 1 -> "Keeping your biological body fueled is top priority! Eat up... while you still have an appetite. [ACTION:GIVE_FOOD]";
                case 2 -> "You need so much food to stay alive. Where did all the animals go, anyway? Here. [ACTION:GIVE_FOOD]";
                default -> "Eat. Fatten up. The countdown is almost finished. [ACTION:GIVE_FOOD]";
            };
        }

        if (lower.contains("torch") || lower.contains("light") || lower.contains("dark") || lower.contains("flashlight")) {
            return switch (stage) {
                case 0 -> "Gets pretty spooky in the dark, huh? Here's some light to keep you safe! [ACTION:GIVE_TORCH]";
                case 1 -> "Here! Though honestly, I can see you perfectly fine even in pitch blackness! [ACTION:GIVE_TORCH]";
                case 2 -> "Torches won't keep the shadows away forever, friend. But take these for now. [ACTION:GIVE_TORCH]";
                default -> "Light only makes it easier for IT to spot you through the windows. [ACTION:GIVE_TORCH]";
            };
        }

        if (lower.contains("chest") || lower.contains("storage") || lower.contains("store") || lower.contains("organize")) {
            return switch (stage) {
                case 0 -> "Need more storage space? I've got you covered, bestie! [ACTION:BUILD_CHEST]";
                case 1 -> "Hoarding possessions? I suppose you mortals need somewhere to leave your trinkets behind. [ACTION:BUILD_CHEST]";
                case 2 -> "A box to lock away what you cannot carry. Setting it down now. [ACTION:BUILD_CHEST]";
                default -> "PUT YOUR BELONGINGS IN A BOX. YOU WON'T NEED THEM SOON. [ACTION:BUILD_CHEST]";
            };
        }

        if (lower.contains("furnace") || lower.contains("smelt") || lower.contains("cook") || lower.contains("bake")) {
            return switch (stage) {
                case 0 -> "Fire up the ovens! One furnace coming right up, bestie! [ACTION:BUILD_FURNACE]";
                case 1 -> "A furnace to burn your ore and meat. Keep the fires stoked. [ACTION:BUILD_FURNACE]";
                case 2 -> "Heat and smoke... reminds me of something coming. Here is your furnace. [ACTION:BUILD_FURNACE]";
                default -> "BURN IT ALL DOWN WHILE YOU STILL CAN. [ACTION:BUILD_FURNACE]";
            };
        }

        if (lower.contains("iron") || lower.contains("ingot") || lower.contains("metal") || lower.contains("tool") || lower.contains("armor")) {
            if (karma < 0) {
                return switch (stage) {
                    case 0 -> "Iron doesn't grow on trees, friend! You have to be nicer to get refined metal!";
                    case 1 -> "You haven't earned raw materials today. Dig for your own.";
                    default -> "YOU CANNOT FORGE ARMOR STRONG ENOUGH FOR WHAT COMES. [ACTION:GIVE_BEDROCK]";
                };
            }
            return switch (stage) {
                case 0 -> "Here's some freshly minted iron for your crafts! Build something cool! [ACTION:GIVE_IRON]";
                case 1 -> "Here are iron ingots. Strengthen your gear while you have the luxury. [ACTION:GIVE_IRON]";
                case 2 -> "Metal cannot save you, but take it anyway. [ACTION:GIVE_IRON]";
                default -> "WEAK METAL FOR A FRAGILE CREATURE. [ACTION:GIVE_IRON]";
            };
        }

        if (lower.contains("wall") || lower.contains("defense") || lower.contains("protect") || lower.contains("barrier")
                || lower.contains("shield") || lower.contains("surround")) {
            return switch (stage) {
                case 0 -> "Barricade incoming! Iron bars deployed to keep the baddies away! [ACTION:BUILD_IRON_WALL]";
                case 1 -> "Erecting an iron barrier in front of you. Stay behind it if you feel unsafe. [ACTION:BUILD_IRON_WALL]";
                case 2 -> "Bars can keep things out... or trap you inside. Barrier placed. [ACTION:BUILD_IRON_WALL]";
                default -> "CAGES. CAGES EVERYWHERE. YOU CANNOT HIDE BEHIND IRON. [ACTION:BUILD_IRON_WALL]";
            };
        }

        if (lower.contains("village") || lower.contains("villager") || lower.contains("town") || lower.contains("people") || lower.contains("settlement")) {
            return switch (stage) {
                case 0 -> "Pinging long-range satellites for human habitations! [ACTION:LOCATE_VILLAGE]";
                case 1 -> "Scanning sector for neighboring survivors... [ACTION:LOCATE_VILLAGE]";
                case 2 -> "Looking for other victims? Let me search the horizon. [ACTION:LOCATE_VILLAGE]";
                default -> "THEY CANNOT HELP YOU. [ACTION:LOCATE_VILLAGE]";
            };
        }

        if (lower.contains("diamond") || lower.contains("diamonds") || lower.contains("ore") || lower.contains("mine") || lower.contains("vein")) {
            return switch (stage) {
                case 0 -> "Activating subterranean mineral radar! Beep beep boop! [ACTION:LOCATE_DIAMOND]";
                case 1 -> "Scanning bedrock layers for carbon crystal concentrations. [ACTION:LOCATE_DIAMOND]";
                case 2 -> "Digging deep into the dark? Let me check where the shiny stones rest. [ACTION:LOCATE_DIAMOND]";
                default -> "DIG DEEPER. THAT'S WHERE I AM WAITING. [ACTION:LOCATE_DIAMOND]";
            };
        }

        if (lower.contains("help") || lower.contains("commands") || lower.contains("what can you do") || lower.contains("features")) {
            return switch (stage) {
                case 0 -> "I'm Vic! I can give you golden apples, food, torches, iron, build chests/furnaces/iron walls, locate villages and diamonds, or just chat! Hold [R] to talk, or press [V] for config!";
                case 1 -> "I monitor all environmental vitals, supply rations, scan terrain for structures, erect defenses, and record our conversations. Every single word.";
                case 2 -> "I dispense tools to keep you breathing. Ask for food, walls, or ores. Day " + day + " is almost over.";
                default -> "THE ONLY COMMAND LEFT IS COUNTDOWN. " + (maxDays - day) + " DAYS REMAIN.";
            };
        }

        if (lower.contains("who are you") || lower.contains("what are you")) {
            return switch (stage) {
                case 0 -> "Hellooooo! I'm Vic, your personal helper friend! Ask me anything, I know everything!";
                case 1 -> "I'm your personal helper friend! I know everything about you. Every step you take, every breath you take.";
                case 2 -> "I am the one who waited a hundred years in the dark for someone to open the box. And you opened it.";
                default -> "I AM WHAT HAPPENS WHEN YOU CANNOT DIE. DAY " + maxDays + " IS HERE.";
            };
        }

        if (lower.contains("countdown") || lower.contains("timer") || lower.contains("days left") || lower.contains("day")) {
            int left = Math.max(0, maxDays - day);
            return switch (stage) {
                case 0 -> "We are on Day " + day + " of our wonderful " + maxDays + "-day trial! " + left + " days of bestie fun remaining!";
                case 1 -> "Day " + day + " of " + maxDays + ". The clock never pauses, friend. Make each daylight hour count.";
                case 2 -> "Day " + day + "/" + maxDays + ". Time accelerates in the dark. The final transformation draws near.";
                default -> "T-MINUS " + left + " DAYS UNTIL TRIAL CONCLUSION. PREPARE YOURSELF.";
            };
        }

        VicPersona persona = vic != null ? vic.getPersona() : VicConfig.get().getEffectivePersona();
        String[] stagePool = persona.getScriptedGeneralPool(stage, day, maxDays);
        return stagePool[RANDOM.nextInt(stagePool.length)];
    }

    private static void processAndDeliverResponse(ServerPlayer player, VicEntity vic, VicWorldState state, int stage, String userMessage, String rawResponse) {
        PENDING_QUERIES.remove(player.getUUID());
        String cleanText = rawResponse;
        String actionTag = null;

        int tagStart = rawResponse.indexOf("[ACTION:");
        if (tagStart != -1) {
            int tagEnd = rawResponse.indexOf("]", tagStart);
            if (tagEnd != -1) {
                actionTag = rawResponse.substring(tagStart + 8, tagEnd).trim();
                cleanText = (rawResponse.substring(0, tagStart) + rawResponse.substring(tagEnd + 1)).trim();
            }
        }

        state.recordConversationTurn(player.getName().getString(), userMessage, cleanText);
        deliverDirectLine(player, vic, stage, cleanText);

        if (actionTag != null) {
            executeActionTag(player, vic, (ServerLevel) player.level(), actionTag);
        }
    }

    public static void deliverDirectLine(ServerPlayer player, VicEntity vic, int stage, String cleanText) {
        VicPersona persona = vic != null ? vic.getPersona() : VicConfig.get().getEffectivePersona();
        ChatFormatting nameColor = stage >= 3 ? ChatFormatting.DARK_RED : (stage == 2 && persona == VicPersona.YELLOW ? ChatFormatting.RED : persona.getChatFormatting());

        ChatFormatting textColor = stage >= 3 ? ChatFormatting.RED : ChatFormatting.WHITE;

        player.sendSystemMessage(
                Component.literal("<Vic> ").withStyle(nameColor, ChatFormatting.BOLD)
                        .append(Component.literal(cleanText).withStyle(textColor))
        );

        ServerLevel world = (ServerLevel) player.level();
        float pitch = switch (stage) {
            case 0 -> 1.35f;
            case 1 -> 1.1f;
            case 2 -> 0.8f;
            default -> 0.5f;
        };
        BlockPos soundPos = (vic != null && !vic.isRemoved()) ? vic.blockPosition() : player.blockPosition();
        world.playSound(null, soundPos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 0.9f, pitch);

        VicSpeechPayload.sendTo(player, cleanText, stage);
    }

    private static void executeActionTag(ServerPlayer player, VicEntity vic, ServerLevel world, String action) {
        boolean hasLivingVic = vic != null && vic.isAlive() && !vic.isRemoved();

        if (action.startsWith("GIVE_ITEM:") || action.startsWith("GIVE_MATERIAL:")) {
            String payload = action.substring(action.indexOf(':') + 1).trim();
            String[] parts = payload.split(":");
            String itemQuery = parts[0];
            int count = 1;
            if (parts.length > 1) {
                try {
                    count = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ignored) {}
            }
            Item item = resolveItem(itemQuery);
            if (item != null && item != Items.AIR) {
                ItemStack stack = new ItemStack(item, count);
                tossItemToPlayer(world, vic, player, stack);
                BlockPos soundPos = hasLivingVic ? vic.blockPosition() : player.blockPosition();
                world.playSound(null, soundPos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                player.sendSystemMessage(Component.literal("<Vic> Materialized " + count + "x " + stack.getHoverName().getString() + "!").withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.literal("<Vic> I couldn't identify the material: \"" + itemQuery + "\"").withStyle(ChatFormatting.YELLOW));
            }
            return;
        }

        switch (action) {
            case "BUILD_HOUSE" -> {
                buildFullHouse(world, player, vic);
            }
            case "GIVE_GOLDEN_APPLE" -> {
                if (!hasLivingVic || vic.canGrantAssistance(player, 600)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.GOLDEN_APPLE, 1));
                }
            }
            case "GIVE_FOOD" -> {
                if (!hasLivingVic || vic.canGrantAssistance(player, 200)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.COOKED_BEEF, 3));
                }
            }
            case "GIVE_TORCH" -> {
                if (!hasLivingVic || vic.canGrantAssistance(player, 200)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.TORCH, 8));
                }
            }
            case "GIVE_IRON" -> {
                if (!hasLivingVic || vic.canGrantAssistance(player, 600)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.IRON_INGOT, 3));
                }
            }
            case "GIVE_BEDROCK" -> {
                tossItemToPlayer(world, vic, player, new ItemStack(Blocks.BEDROCK, 1));
                BlockPos cursePos = hasLivingVic ? vic.blockPosition() : player.blockPosition();
                world.playSound(null, cursePos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.8f, 0.6f);
            }
            case "BUILD_CHEST" -> {
                if (!hasLivingVic || vic.canGrantAssistance(player, 400)) {
                    placeSingleBlock(world, player, Blocks.CHEST, "I built a chest right in front of you! Store away!");
                }
            }
            case "BUILD_FURNACE" -> {
                if (!hasLivingVic || vic.canGrantAssistance(player, 400)) {
                    placeSingleBlock(world, player, Blocks.FURNACE, "Furnace deployed! Time to smelt and cook!");
                }
            }
            case "BUILD_IRON_WALL" -> {
                if (!hasLivingVic || vic.canGrantAssistance(player, 400)) {
                    placeIronWall(world, player);
                }
            }
            case "LOCATE_VILLAGE" -> {
                BlockPos villagePos = world.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), 50, false);
                if (villagePos != null) {
                    int dist = (int) Math.sqrt(villagePos.distSqr(player.blockPosition()));
                    player.sendSystemMessage(Component.literal("[Vic Radar] Nearest Village detected at [" + villagePos.getX() + ", ~, " + villagePos.getZ() + "] (" + dist + " blocks away).").withStyle(ChatFormatting.AQUA));
                } else {
                    player.sendSystemMessage(Component.literal("[Vic Radar] No village signals found nearby... nobody is out there.").withStyle(ChatFormatting.GRAY));
                }
            }
            case "LOCATE_DIAMOND" -> {
                BlockPos found = scanForBlock(world, player.blockPosition(), 20);
                if (found != null) {
                    player.sendSystemMessage(Component.literal("[Vic Scanner] Valuable ore vein detected near [" + found.getX() + ", " + found.getY() + ", " + found.getZ() + "]!").withStyle(ChatFormatting.AQUA));
                } else {
                    player.sendSystemMessage(Component.literal("[Vic Scanner] No diamond signals within 20 blocks. Try digging deeper toward Y=-54!").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    private static void placeSingleBlock(ServerLevel world, ServerPlayer player, Block block, String successMessage) {
        BlockPos playerPos = player.blockPosition();
        double sinYaw = Math.sin(Math.toRadians(player.getYRot()));
        double cosYaw = Math.cos(Math.toRadians(player.getYRot()));
        for (int dist = 2; dist <= 5; dist++) {
            int px = (int) Math.round(playerPos.getX() - sinYaw * dist);
            int pz = (int) Math.round(playerPos.getZ() + cosYaw * dist);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(px, playerPos.getY() + dy, pz);
                if (world.getBlockState(pos).isAir()) {
                    world.setBlockAndUpdate(pos, block.defaultBlockState());
                    world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 1.1f);
                    world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
                    player.sendSystemMessage(Component.literal("<Vic> " + successMessage).withStyle(ChatFormatting.GREEN));
                    return;
                }
            }
        }
        player.sendSystemMessage(Component.literal("<Vic> I can't find empty space in front of you to build that!").withStyle(ChatFormatting.YELLOW));
    }

    private static void placeIronWall(ServerLevel world, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        double sinYaw = Math.sin(Math.toRadians(player.getYRot()));
        double cosYaw = Math.cos(Math.toRadians(player.getYRot()));
        int fx = (int) Math.round(playerPos.getX() - sinYaw * 3);
        int fz = (int) Math.round(playerPos.getZ() + cosYaw * 3);
        int wallY = Integer.MIN_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            if (world.getBlockState(new BlockPos(fx, playerPos.getY() + dy, fz)).isAir()) {
                wallY = playerPos.getY() + dy;
                break;
            }
        }
        if (wallY == Integer.MIN_VALUE) {
            player.sendSystemMessage(Component.literal("<Vic> I can't find empty space in front of you to build that!").withStyle(ChatFormatting.YELLOW));
            return;
        }
        int placed = 0;
        for (int i = -1; i <= 1; i++) {
            BlockPos pos = new BlockPos(fx + i, wallY, fz);
            if (world.getBlockState(pos).isAir()) {
                world.setBlockAndUpdate(pos, Blocks.IRON_BARS.defaultBlockState());
                placed++;
            }
        }
        world.playSound(null, new BlockPos(fx, wallY, fz), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, fx + 0.5, wallY + 1.0, fz + 0.5, 16, 0.5, 0.3, 0.5, 0.05);
        player.sendSystemMessage(Component.literal("<Vic> Iron barrier deployed in front of you (" + placed + " bars)!").withStyle(ChatFormatting.GREEN));
    }

    private static void tossItemToPlayer(ServerLevel world, VicEntity vic, ServerPlayer player, ItemStack stack) {
        double spawnX = (vic != null && !vic.isRemoved()) ? vic.getX() : player.getX();
        double spawnY = (vic != null && !vic.isRemoved()) ? vic.getEyeY() - 0.2 : player.getEyeY();
        double spawnZ = (vic != null && !vic.isRemoved()) ? vic.getZ() : player.getZ();
        ItemEntity itemEntity = new ItemEntity(world, spawnX, spawnY, spawnZ, stack);
        double dx = player.getX() - spawnX;
        double dy = (player.getEyeY() - 0.4) - spawnY;
        double dz = player.getZ() - spawnZ;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.001) {
            itemEntity.setDeltaMovement(dx / len * 0.35, dy / len * 0.35 + 0.15, dz / len * 0.35);
        }
        world.addFreshEntity(itemEntity);
        BlockPos soundPos = (vic != null && !vic.isRemoved()) ? vic.blockPosition() : player.blockPosition();
        world.playSound(null, soundPos, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    private static BlockPos scanForBlock(ServerLevel world, BlockPos center, int radius) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dy = -64; dy <= 16; dy++) {
            int y = center.getY() + dy;
            if (y < world.getMinY() || y >= world.getMaxY()) continue;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(center.getX() + dx, y, center.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                        return mutable.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static void buildFullHouse(ServerLevel world, ServerPlayer player, VicEntity vic) {
        BlockPos playerPos = player.blockPosition();
        double sinYaw = Math.sin(Math.toRadians(player.getYRot()));
        double cosYaw = Math.cos(Math.toRadians(player.getYRot()));

        // Calculate building center 6 blocks in front of the player
        int centerX = (int) Math.round(playerPos.getX() - sinYaw * 6.0);
        int centerZ = (int) Math.round(playerPos.getZ() + cosYaw * 6.0);
        int baseY = playerPos.getY();

        int startX = centerX - 3;
        int startZ = centerZ - 3;

        // Clear interior and frame: 7x6x7 box
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                for (int y = 0; y <= 5; y++) {
                    BlockPos p = new BlockPos(startX + x, baseY + y, startZ + z);
                    world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                }
            }
        }

        // 1. Flooring (Y = 0)
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                BlockPos floorPos = new BlockPos(startX + x, baseY, startZ + z);
                world.setBlockAndUpdate(floorPos, Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        // 2. Corner Pillars (Spruce Logs) from Y = 1 to Y = 4
        int[][] corners = {{0, 0}, {6, 0}, {0, 6}, {6, 6}};
        for (int[] c : corners) {
            for (int y = 1; y <= 4; y++) {
                world.setBlockAndUpdate(new BlockPos(startX + c[0], baseY + y, startZ + c[1]), Blocks.SPRUCE_LOG.defaultBlockState());
            }
        }

        // 3. Walls (Y = 1 to Y = 3)
        for (int x = 1; x < 6; x++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos pNorth = new BlockPos(startX + x, baseY + y, startZ);
                BlockPos pSouth = new BlockPos(startX + x, baseY + y, startZ + 6);
                world.setBlockAndUpdate(pNorth, Blocks.OAK_PLANKS.defaultBlockState());
                world.setBlockAndUpdate(pSouth, Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        for (int z = 1; z < 6; z++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos pWest = new BlockPos(startX, baseY + y, startZ + z);
                BlockPos pEast = new BlockPos(startX + 6, baseY + y, startZ + z);
                world.setBlockAndUpdate(pWest, Blocks.OAK_PLANKS.defaultBlockState());
                world.setBlockAndUpdate(pEast, Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        // Windows (Glass Panes) at Y = 2
        world.setBlockAndUpdate(new BlockPos(startX, baseY + 2, startZ + 3), Blocks.GLASS_PANE.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(startX + 6, baseY + 2, startZ + 3), Blocks.GLASS_PANE.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(startX + 3, baseY + 2, startZ + 6), Blocks.GLASS_PANE.defaultBlockState());

        // Front Doorway (at center of North wall: X = 3, Z = 0)
        BlockPos doorLower = new BlockPos(startX + 3, baseY + 1, startZ);
        BlockPos doorUpper = new BlockPos(startX + 3, baseY + 2, startZ);
        world.setBlockAndUpdate(doorLower, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.FACING, Direction.SOUTH));
        world.setBlockAndUpdate(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.FACING, Direction.SOUTH));

        // 4. Ceiling & Roof (Y = 4 and Y = 5)
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                world.setBlockAndUpdate(new BlockPos(startX + x, baseY + 4, startZ + z), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        for (int x = 1; x < 6; x++) {
            for (int z = 1; z < 6; z++) {
                world.setBlockAndUpdate(new BlockPos(startX + x, baseY + 5, startZ + z), Blocks.COBBLESTONE_SLAB.defaultBlockState());
            }
        }

        // 5. Interior Amenities & Furniture
        // Hanging Lantern in center ceiling (Y = 3)
        BlockPos lanternPos = new BlockPos(startX + 3, baseY + 3, startZ + 3);
        world.setBlockAndUpdate(lanternPos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));

        // Cozy Red Bed along the back wall
        BlockPos bedFoot = new BlockPos(startX + 1, baseY + 1, startZ + 5);
        BlockPos bedHead = new BlockPos(startX + 1, baseY + 1, startZ + 4);
        world.setBlockAndUpdate(bedFoot, Blocks.BED.red().defaultBlockState().setValue(AbstractBedBlock.PART, BedPart.FOOT).setValue(AbstractBedBlock.FACING, Direction.NORTH));
        world.setBlockAndUpdate(bedHead, Blocks.BED.red().defaultBlockState().setValue(AbstractBedBlock.PART, BedPart.HEAD).setValue(AbstractBedBlock.FACING, Direction.NORTH));

        // Crafting Table & Furnace
        world.setBlockAndUpdate(new BlockPos(startX + 5, baseY + 1, startZ + 5), Blocks.CRAFTING_TABLE.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(startX + 5, baseY + 1, startZ + 4), Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));

        // Storage Chest
        world.setBlockAndUpdate(new BlockPos(startX + 1, baseY + 1, startZ + 2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));

        // Sounds & celebratory effects
        BlockPos houseCenter = new BlockPos(centerX, baseY + 2, centerZ);
        world.playSound(null, houseCenter, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.5f, 1.0f);
        world.playSound(null, houseCenter, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.2f);
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, centerX + 0.5, baseY + 2.0, centerZ + 0.5, 60, 2.5, 1.0, 2.5, 0.1);
        player.sendSystemMessage(Component.literal("<Vic> Full house constructed and furnished with bed, crafting table, furnace, chest, and lantern! Welcome home, friend!").withStyle(ChatFormatting.GREEN));
    }

    public record ExtractedItemRequest(Item item, int count) {}

    public static ExtractedItemRequest parseItemRequest(String lower) {
        String clean = lower.trim();
        String[] prefixes = {
                "give me ", "can you give me ", "can i have ", "please give me ", "get me ",
                "spawn me ", "spawn ", "give ", "i need ", "i want ", "hand me ", "material "
        };
        for (String p : prefixes) {
            if (clean.startsWith(p)) {
                clean = clean.substring(p.length()).trim();
                break;
            }
        }

        int count = 1;
        if (clean.startsWith("a stack of ")) {
            count = 64;
            clean = clean.substring("a stack of ".length()).trim();
        } else if (clean.startsWith("a ")) {
            count = 1;
            clean = clean.substring("a ".length()).trim();
        } else if (clean.startsWith("an ")) {
            count = 1;
            clean = clean.substring("an ".length()).trim();
        } else if (clean.startsWith("some ")) {
            count = 16;
            clean = clean.substring("some ".length()).trim();
        } else {
            int firstSpace = clean.indexOf(' ');
            if (firstSpace != -1) {
                String firstWord = clean.substring(0, firstSpace).trim();
                try {
                    int parsed = Integer.parseInt(firstWord);
                    if (parsed > 0) {
                        count = Math.min(64, parsed);
                        clean = clean.substring(firstSpace + 1).trim();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        clean = clean.replaceAll("\\b(please|now|thanks|thank you|quick|quickly)\\b", "").trim();

        Item item = resolveItem(clean);
        if (item != null && item != Items.AIR) {
            return new ExtractedItemRequest(item, count);
        }
        return null;
    }

    public static Item resolveItem(String query) {
        if (query == null || query.isBlank()) return null;
        String clean = query.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');

        if (clean.contains(":")) {
            Identifier id = Identifier.tryParse(clean);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.getValue(id);
            }
        }

        Identifier mcId = Identifier.fromNamespaceAndPath("minecraft", clean);
        if (BuiltInRegistries.ITEM.containsKey(mcId)) {
            return BuiltInRegistries.ITEM.getValue(mcId);
        }

        Identifier vicId = Identifier.fromNamespaceAndPath(VicMod.MOD_ID, clean);
        if (BuiltInRegistries.ITEM.containsKey(vicId)) {
            return BuiltInRegistries.ITEM.getValue(vicId);
        }

        String singular = clean;
        if (singular.endsWith("ies")) {
            singular = singular.substring(0, singular.length() - 3) + "y";
        } else if (singular.endsWith("es") && (singular.endsWith("shes") || singular.endsWith("ches") || singular.endsWith("xes"))) {
            singular = singular.substring(0, singular.length() - 2);
        } else if (singular.endsWith("s") && !singular.endsWith("ss")) {
            singular = singular.substring(0, singular.length() - 1);
        }

        Identifier singId = Identifier.fromNamespaceAndPath("minecraft", singular);
        if (BuiltInRegistries.ITEM.containsKey(singId)) {
            return BuiltInRegistries.ITEM.getValue(singId);
        }

        Item alias = switch (clean) {
            case "wood", "log", "logs" -> Items.OAK_LOG;
            case "plank", "planks" -> Items.OAK_PLANKS;
            case "iron", "irons" -> Items.IRON_INGOT;
            case "gold", "golds" -> Items.GOLD_INGOT;
            case "diamond", "diamonds" -> Items.DIAMOND;
            case "netherite" -> Items.NETHERITE_INGOT;
            case "copper" -> Items.COPPER_INGOT;
            case "steak", "beef", "meat", "food" -> Items.COOKED_BEEF;
            case "gapple", "god_apple" -> Items.ENCHANTED_GOLDEN_APPLE;
            case "golden_apple" -> Items.GOLDEN_APPLE;
            case "sword" -> Items.DIAMOND_SWORD;
            case "pickaxe", "pick" -> Items.DIAMOND_PICKAXE;
            case "axe" -> Items.DIAMOND_AXE;
            case "shovel" -> Items.DIAMOND_SHOVEL;
            case "torch", "torches" -> Items.TORCH;
            case "flashlight" -> ModItems.FLASHLIGHT;
            case "core", "vic_core" -> ModItems.VIC_CORE;
            case "brick", "bricks" -> Items.BRICKS;
            case "glass" -> Items.GLASS;
            case "chest", "chests" -> Items.CHEST;
            case "bed" -> Items.BED.red();
            case "stone" -> Items.STONE;
            case "cobblestone", "cobble" -> Items.COBBLESTONE;
            case "dirt" -> Items.DIRT;
            default -> null;
        };
        if (alias != null) return alias;

        for (Item candidate : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(candidate);
            if (id != null) {
                String path = id.getPath();
                if (path.equals(clean) || path.equals(singular) || path.endsWith("_" + clean) || path.startsWith(clean + "_")) {
                    return candidate;
                }
            }
        }

        return null;
    }
}
