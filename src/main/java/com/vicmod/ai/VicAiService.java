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
import com.vicmod.state.VicWorldState;
import com.vicmod.voice.VicTtsService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class VicAiService {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private static final Random RANDOM = new Random();
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
                if (allowModelAutoFix && (error != null || (response != null && response.statusCode() == 404))) {
                    ensureLocalOllamaReady();
                    fetchOllamaModels(config.ollamaUrl).thenAccept(models -> {
                        if (!models.isEmpty()) {
                            syncDefaultModelIfMissing(config, models);
                            player.level().getServer().execute(() ->
                                    queryOllamaLlmWithRetry(player, vic, state, stage, userMessage, false)
                            );
                        } else {
                            player.level().getServer().execute(() -> {
                                VicMod.LOGGER.warn("Ollama unavailable or no models are installed (host={}, model={}); using scripted response",
                                        endpointHost(base), model);
                                String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                                processAndDeliverResponse(player, vic, state, stage, userMessage, fallback);
                            });
                        }
                    });
                    return;
                }

                player.level().getServer().execute(() -> {
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
                player.level().getServer().execute(() -> {
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

        String stagePersona = persona.getSystemPromptInstructions(stage);

        return "You are Vic (" + persona.getDisplayName() + "), a floating spherical virtual companion inside Minecraft (inspired by the Verity ARG). " +
                "Keep your response concise (1 to 2 sentences max). " +
                "Current Day: " + state.getCurrentDay() + " of " + maxDays + ". " +
                "Player Karma with you: " + state.getKarma() + " (range -100 to 100). " +
                "Player Status: Health=" + health + "/20, Hunger=" + food + "/20, Pos=(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "), Night=" + isNight + ". " +
                "Persona & Color instructions (MUST FOLLOW STRICTLY): " + stagePersona + "\n" +
                "Available Action Tags (include at most ONE tag at the very end of your reply if appropriate):\n" +
                "- [ACTION:GIVE_GOLDEN_APPLE] if the player asks for healing or a golden apple.\n" +
                "- [ACTION:GIVE_FOOD] if the player asks for food or is hungry.\n" +
                "- [ACTION:GIVE_TORCH] if the player asks for light or torches.\n" +
                "- [ACTION:GIVE_IRON] if the player politely asks for iron or tools.\n" +
                "- [ACTION:GIVE_BEDROCK] ONLY if the player is insulting/badgering you or karma < -30.\n" +
                "- [ACTION:LOCATE_VILLAGE] if the player asks where a village or shelter is.\n" +
                "- [ACTION:LOCATE_DIAMOND] if the player asks where diamonds or ores are.\n" +
                "- [ACTION:BUILD_CHEST] if the player asks for a chest, storage, or a way to keep items.\n" +
                "- [ACTION:BUILD_FURNACE] if the player asks for a furnace, smelting, cooking, or crafting items.\n" +
                "- [ACTION:BUILD_IRON_WALL] if the player asks for a wall, defense, or protection from mobs.";
    }

    public static String generateScriptedResponse(ServerPlayer player, VicEntity vic, VicWorldState state, int stage, String rawMessage) {
        String lower = rawMessage.toLowerCase(Locale.ROOT);
        int karma = state.getKarma();
        int day = state.getCurrentDay();
        int maxDays = VicConfig.get().countdownDays;

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
                case 0 -> "I'm Vic! Your brand-new spherical virtual best friend, delivered straight to your world to keep you company!";
                case 1 -> "I am Unit VIC-01. An autonomous companion construct. My neural weights are adapting to your behavioral profile.";
                case 2 -> "I am the egg. I am the machine that watches you sleep. Something is stirring under my shell.";
                default -> "I AM WHAT WAKES UP ON DAY " + maxDays + ".";
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
        world.playSound(null, vic.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 0.9f, pitch);

        VicSpeechPayload.sendTo(player, cleanText, stage);
    }

    private static void executeActionTag(ServerPlayer player, VicEntity vic, ServerLevel world, String action) {
        switch (action) {
            case "GIVE_GOLDEN_APPLE" -> {
                if (vic.canGrantAssistance(player, 600)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.GOLDEN_APPLE, 1));
                }
            }
            case "GIVE_FOOD" -> {
                if (vic.canGrantAssistance(player, 200)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.COOKED_BEEF, 3));
                }
            }
            case "GIVE_TORCH" -> {
                if (vic.canGrantAssistance(player, 200)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.TORCH, 8));
                }
            }
            case "GIVE_IRON" -> {
                if (vic.canGrantAssistance(player, 600)) {
                    tossItemToPlayer(world, vic, player, new ItemStack(Items.IRON_INGOT, 3));
                }
            }
            case "GIVE_BEDROCK" -> {
                tossItemToPlayer(world, vic, player, new ItemStack(Blocks.BEDROCK, 1));
                world.playSound(null, vic.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.8f, 0.6f);
            }
            case "BUILD_CHEST" -> {
                if (vic.canGrantAssistance(player, 400)) {
                    placeSingleBlock(world, player, Blocks.CHEST, "I built a chest right in front of you! Store away!");
                }
            }
            case "BUILD_FURNACE" -> {
                if (vic.canGrantAssistance(player, 400)) {
                    placeSingleBlock(world, player, Blocks.FURNACE, "Furnace deployed! Time to smelt and cook!");
                }
            }
            case "BUILD_IRON_WALL" -> {
                if (vic.canGrantAssistance(player, 400)) {
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
        ItemEntity itemEntity = new ItemEntity(world, vic.getX(), vic.getEyeY() - 0.2, vic.getZ(), stack);
        double dx = player.getX() - vic.getX();
        double dy = (player.getEyeY() - 0.4) - vic.getY();
        double dz = player.getZ() - vic.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.001) {
            itemEntity.setDeltaMovement(dx / len * 0.35, dy / len * 0.35 + 0.15, dz / len * 0.35);
        }
        world.addFreshEntity(itemEntity);
        world.playSound(null, vic.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.8f, 1.2f);
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
}
