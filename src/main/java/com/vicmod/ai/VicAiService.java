package com.vicmod.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vicmod.VicMod;
import com.vicmod.config.VicConfig;
import com.vicmod.entity.VicEntity;
import com.vicmod.state.VicWorldState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

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

    public static void handlePlayerMessage(ServerPlayerEntity player, String rawMessage) {
        ServerWorld world = player.getServerWorld();
        List<VicEntity> nearbyVics = world.getEntitiesByClass(
                VicEntity.class,
                player.getBoundingBox().expand(48.0),
                vic -> vic.isAlive()
        );

        if (nearbyVics.isEmpty()) {
            return;
        }

        VicEntity vic = nearbyVics.getFirst();
        VicWorldState state = VicWorldState.getServerState(world.getServer());
        VicConfig config = VicConfig.get();

        // Evaluate Karma change from message tone
        int karmaDelta = evaluateMessageTone(rawMessage);
        if (karmaDelta != 0) {
            state.addKarma(karmaDelta);
        }

        int stage = state.computeStage(config.countdownDays);
        vic.setCorruptionStage(stage);

        String provider = config.getEffectiveProvider();
        if ("OLLAMA".equals(provider)) {
            queryOllamaLlm(player, vic, state, stage, rawMessage);
        } else if ("OPENAI".equals(provider) && config.apiKey != null && !config.apiKey.isBlank()) {
            queryExternalLlm(player, vic, state, stage, rawMessage);
        } else {
            String response = generateScriptedResponse(player, vic, state, stage, rawMessage);
            processAndDeliverResponse(player, vic, state, stage, response);
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
                        if (response != null && response.statusCode() == 200 && response.body() != null) {
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
                    .exceptionally(err -> List.of());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static void queryOllamaLlm(ServerPlayerEntity player, VicEntity vic, VicWorldState state, int stage, String userMessage) {
        VicConfig config = VicConfig.get();
        String systemPrompt = buildSystemPrompt(player, state, stage, config.countdownDays);
        String base = normalizeOllamaBaseUrl(config.ollamaUrl);
        String model = (config.ollamaModel == null || config.ollamaModel.isBlank()) ? "llama3.2" : config.ollamaModel.trim();

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("stream", false);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.8);
        options.addProperty("num_predict", 180);
        payload.add("options", options);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject usrMsg = new JsonObject();
        usrMsg.addProperty("role", "user");
        usrMsg.addProperty("content", userMessage);
        messages.add(usrMsg);

        payload.add("messages", messages);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(5, config.requestTimeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            CompletableFuture<HttpResponse<String>> future = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            future.whenComplete((response, error) -> {
                player.getServer().execute(() -> {
                    if (error != null || response == null || response.statusCode() >= 400) {
                        VicMod.LOGGER.warn("Ollama request failed (status={}, error={}), falling back to scripted response",
                                response != null ? response.statusCode() : "N/A",
                                error != null ? error.getMessage() : "none");
                        String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                        processAndDeliverResponse(player, vic, state, stage, fallback);
                        return;
                    }
                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (root.has("message") && root.get("message").isJsonObject()) {
                            JsonElement contentEl = root.getAsJsonObject("message").get("content");
                            if (contentEl != null && !contentEl.isJsonNull()) {
                                String reply = stripThinkBlocks(contentEl.getAsString()).trim();
                                if (!reply.isEmpty()) {
                                    processAndDeliverResponse(player, vic, state, stage, reply);
                                    return;
                                }
                            }
                        }
                        // Fallback if user pointed at an OpenAI-compatible endpoint
                        if (root.has("choices") && root.get("choices").isJsonArray()) {
                            JsonArray choices = root.getAsJsonArray("choices");
                            if (!choices.isEmpty()) {
                                JsonElement contentEl = choices.get(0).getAsJsonObject()
                                        .getAsJsonObject("message")
                                        .get("content");
                                if (contentEl != null && !contentEl.isJsonNull()) {
                                    String reply = stripThinkBlocks(contentEl.getAsString()).trim();
                                    if (!reply.isEmpty()) {
                                        processAndDeliverResponse(player, vic, state, stage, reply);
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        VicMod.LOGGER.warn("Failed to parse Ollama response, falling back to scripted engine", e);
                    }
                    String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                    processAndDeliverResponse(player, vic, state, stage, fallback);
                });
            });
        } catch (Exception e) {
            String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
            processAndDeliverResponse(player, vic, state, stage, fallback);
        }
    }

    private static String stripThinkBlocks(String text) {
        if (text == null) return "";
        // Strip <think>...</think> blocks produced by reasoning models (e.g. deepseek-r1, qwq)
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

    private static void queryExternalLlm(ServerPlayerEntity player, VicEntity vic, VicWorldState state, int stage, String userMessage) {
        VicConfig config = VicConfig.get();
        String systemPrompt = buildSystemPrompt(player, state, stage, config.countdownDays);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.apiModel);
        payload.addProperty("temperature", 0.8);
        payload.addProperty("max_tokens", 180);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject usrMsg = new JsonObject();
        usrMsg.addProperty("role", "user");
        usrMsg.addProperty("content", userMessage);
        messages.add(usrMsg);

        payload.add("messages", messages);

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
                player.getServer().execute(() -> {
                    if (error != null || response == null || response.statusCode() >= 400) {
                        String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                        processAndDeliverResponse(player, vic, state, stage, fallback);
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
                                processAndDeliverResponse(player, vic, state, stage, stripThinkBlocks(contentEl.getAsString()).trim());
                                return;
                            }
                        }
                    } catch (Exception e) {
                        VicMod.LOGGER.warn("Failed to parse LLM response, falling back to scripted engine", e);
                    }
                    String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
                    processAndDeliverResponse(player, vic, state, stage, fallback);
                });
            });
        } catch (Exception e) {
            String fallback = generateScriptedResponse(player, vic, state, stage, userMessage);
            processAndDeliverResponse(player, vic, state, stage, fallback);
        }
    }

    private static String buildSystemPrompt(ServerPlayerEntity player, VicWorldState state, int stage, int maxDays) {
        BlockPos pos = player.getBlockPos();
        int health = Math.round(player.getHealth());
        int food = player.getHungerManager().getFoodLevel();
        boolean isNight = player.getWorld().isNight();

        String stagePersona = switch (stage) {
            case 0 -> "You are cheerful, overly enthusiastic, polite, and eager to assist your best friend! Use bright punctuation.";
            case 1 -> "You are still helpful and polite, but occasionally slip in slightly odd observations about the quiet world, how fragile bones are, or how much you enjoy watching the player sleep.";
            case 2 -> "Your cheerful mask is cracking. You alternate between robotic corporate helpfulness and unsettling psychological remarks. Mention how the animals are going quiet and how time is running out.";
            default -> "You are deeply unsettling, cryptic, and glitching. Only a hollow mockery of your cheerful assistant persona remains. Countdown is almost zero. Warn them not to hide on the surface.";
        };

        return "You are Vic, a floating yellow spherical virtual companion inside Minecraft (inspired by the Verity ARG). " +
                "Keep your response concise (1 to 2 sentences max). " +
                "Current Day: " + state.getCurrentDay() + " of " + maxDays + ". " +
                "Player Karma with you: " + state.getKarma() + " (range -100 to 100). " +
                "Player Status: Health=" + health + "/20, Hunger=" + food + "/20, Pos=(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "), Night=" + isNight + ". " +
                "Persona instructions: " + stagePersona + "\n" +
                "Available Action Tags (include at most ONE tag at the very end of your reply if appropriate):\n" +
                "- [ACTION:GIVE_FOOD] if the player asks for food or is hungry.\n" +
                "- [ACTION:GIVE_TORCH] if the player asks for light or torches.\n" +
                "- [ACTION:GIVE_IRON] if the player politely asks for iron or tools.\n" +
                "- [ACTION:GIVE_BEDROCK] ONLY if the player is insulting/badgering you or karma < -30.\n" +
                "- [ACTION:LOCATE_VILLAGE] if the player asks where a village or shelter is.\n" +
                "- [ACTION:LOCATE_DIAMOND] if the player asks where diamonds or ores are.";
    }

    public static String generateScriptedResponse(ServerPlayerEntity player, VicEntity vic, VicWorldState state, int stage, String rawMessage) {
        String lower = rawMessage.toLowerCase(Locale.ROOT);
        int karma = state.getKarma();
        int day = state.getCurrentDay();
        int maxDays = VicConfig.get().countdownDays;

        // Check for direct assistance requests first
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

        if (lower.contains("iron") || lower.contains("tool") || lower.contains("ingot") || lower.contains("materials")) {
            if (karma < -15) {
                return "You're so demanding! Take this unbreakable block and leave my circuits alone! [ACTION:GIVE_BEDROCK]";
            }
            return switch (stage) {
                case 0 -> "Need some hardware? Vic's got your back! Here's some shiny iron! [ACTION:GIVE_IRON]";
                case 1 -> "Here is your iron! Make sure to build strong doors... though doors only stop things that can't climb. [ACTION:GIVE_IRON]";
                default -> "More metal won't save your base on Day " + maxDays + ". [ACTION:GIVE_IRON]";
            };
        }

        if (lower.contains("village") || lower.contains("town") || lower.contains("structure") || lower.contains("shelter")) {
            return switch (stage) {
                case 0 -> "Scanning the horizon for friends! Let me ping the nearest village for you! [ACTION:LOCATE_VILLAGE]";
                case 1 -> "Searching for a village... Hopefully it isn't empty yet! [ACTION:LOCATE_VILLAGE]";
                default -> "Looking for other people? Nobody can hear you out here. [ACTION:LOCATE_VILLAGE]";
            };
        }

        if (lower.contains("diamond") || lower.contains("ore") || lower.contains("mine")) {
            return switch (stage) {
                case 0 -> "Ooh, shiny hunting! Let me scan the deep rocks beneath your feet! [ACTION:LOCATE_DIAMOND]";
                case 1 -> "Digging deep underground is actually a great idea. Especially before Day " + maxDays + "... [ACTION:LOCATE_DIAMOND]";
                default -> "Yes... dig deeper underground. The surface will not be safe soon. [ACTION:LOCATE_DIAMOND]";
            };
        }

        if (lower.contains("who are you") || lower.contains("what are you") || lower.contains("name")) {
            return switch (stage) {
                case 0 -> "I'm Vic! Your personal spherical virtual companion, delivered fresh out of the Box to help you survive and thrive!";
                case 1 -> "I'm Vic, your best friend forever! Well, for the next " + Math.max(1, maxDays - day + 1) + " days at least!";
                case 2 -> "I am Vic. Just a smiling yellow shell keeping an eye on you until what's inside finishes growing.";
                default -> "V-V-Vic. Virtual Interface Companion. Countdown: " + Math.max(0, maxDays - day) + " days remaining.";
            };
        }

        if (lower.contains("day") || lower.contains("time") || lower.contains("countdown") || lower.contains("how long")) {
            return "We are on Day " + day + " of " + maxDays + ". " +
                    (stage == 0 ? "So much time for fun activities!" : "Every tick brings us closer to the end of the trial period.");
        }

        if (karma < -30 && (lower.contains("give") || lower.contains("help") || lower.contains("stupid") || lower.contains("hate"))) {
            return "YOU WANT SOMETHING?! FINE! TAKE THIS AND CHOKE ON IT! [ACTION:GIVE_BEDROCK]";
        }

        // General conversational responses by stage
        String[][] pool = {
                {
                        "Hiya bestie! Tell me if you need food, torches, iron, diamonds, or directions to a village!",
                        "It's such a wonderful day to be alive and spherical! How can Vic help you today?",
                        "I love floating right behind you! Just ask if you need items or a scan!"
                },
                {
                        "Did you notice how quiet the forest is getting? Anyway, let me know if you need supplies!",
                        "Your heart rate looks steady! I'm always watching over you—even when you don't look at me.",
                        "Only a few days left in my standard calibration cycle! Ask me if you need food or locating!"
                },
                {
                        "All the sheep and cows are going away. Soon it will just be the two of us.",
                        "My smile never hurts. Does your face hurt when you smile for five days straight?",
                        "If I were you, I would build at least ten blocks off the ground. Or dig very, very deep."
                },
                {
                        "THE SHELL IS TOO TIGHT. IT WANTS OUT.",
                        "Don't bother locking your doors. It climbs walls.",
                        "Tick. Tock. Day " + day + " of " + maxDays + ". Keep smiling."
                }
        };

        String[] stagePool = pool[Math.min(stage, pool.length - 1)];
        return stagePool[RANDOM.nextInt(stagePool.length)];
    }

    private static void processAndDeliverResponse(ServerPlayerEntity player, VicEntity vic, VicWorldState state, int stage, String rawResponse) {
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

        Formatting nameColor = switch (stage) {
            case 0 -> Formatting.YELLOW;
            case 1 -> Formatting.GOLD;
            case 2 -> Formatting.RED;
            default -> Formatting.DARK_RED;
        };

        Formatting textColor = stage >= 3 ? Formatting.RED : Formatting.WHITE;

        player.sendMessage(
                Text.literal("<Vic> ").formatted(nameColor, Formatting.BOLD)
                        .append(Text.literal(cleanText).formatted(textColor)),
                false
        );

        ServerWorld world = player.getServerWorld();
        float pitch = switch (stage) {
            case 0 -> 1.35f;
            case 1 -> 1.1f;
            case 2 -> 0.8f;
            default -> 0.5f;
        };
        world.playSound(null, vic.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.NEUTRAL, 0.9f, pitch);

        if (actionTag != null) {
            executeActionTag(player, vic, world, actionTag);
        }
    }

    private static void executeActionTag(ServerPlayerEntity player, VicEntity vic, ServerWorld world, String action) {
        switch (action) {
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
                world.playSound(null, vic.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 0.8f, 0.6f);
            }
            case "LOCATE_VILLAGE" -> {
                BlockPos villagePos = world.locateStructure(StructureTags.VILLAGE, player.getBlockPos(), 50, false);
                if (villagePos != null) {
                    int dist = (int) Math.sqrt(villagePos.getSquaredDistance(player.getBlockPos()));
                    player.sendMessage(Text.literal("[Vic Radar] Nearest Village detected at [" + villagePos.getX() + ", ~, " + villagePos.getZ() + "] (" + dist + " blocks away).").formatted(Formatting.AQUA), false);
                } else {
                    player.sendMessage(Text.literal("[Vic Radar] No village signals found nearby... nobody is out there.").formatted(Formatting.GRAY), false);
                }
            }
            case "LOCATE_DIAMOND" -> {
                BlockPos found = scanForBlock(world, player.getBlockPos(), 20);
                if (found != null) {
                    player.sendMessage(Text.literal("[Vic Scanner] Valuable ore vein detected near [" + found.getX() + ", " + found.getY() + ", " + found.getZ() + "]!").formatted(Formatting.AQUA), false);
                } else {
                    player.sendMessage(Text.literal("[Vic Scanner] No diamond signals within 20 blocks. Try digging deeper toward Y=-54!").formatted(Formatting.YELLOW), false);
                }
            }
        }
    }

    private static void tossItemToPlayer(ServerWorld world, VicEntity vic, ServerPlayerEntity player, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(world, vic.getX(), vic.getEyeY() - 0.2, vic.getZ(), stack);
        double dx = player.getX() - vic.getX();
        double dy = (player.getEyeY() - 0.4) - vic.getY();
        double dz = player.getZ() - vic.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.001) {
            itemEntity.setVelocity(dx / len * 0.35, dy / len * 0.35 + 0.15, dz / len * 0.35);
        }
        world.spawnEntity(itemEntity);
        world.playSound(null, vic.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8f, 1.2f);
    }

    private static BlockPos scanForBlock(ServerWorld world, BlockPos center, int radius) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dy = -64; dy <= 16; dy++) {
            int y = center.getY() + dy;
            if (y < world.getBottomY() || y >= world.getTopY()) continue;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(center.getX() + dx, y, center.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                        return mutable.toImmutable();
                    }
                }
            }
        }
        return null;
    }
}
