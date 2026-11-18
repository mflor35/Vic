package com.vicmod.config;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public enum VicPersona {
    YELLOW(
            "YELLOW",
            "Yellow (Cheerful)",
            "Cheerful",
            ChatFormatting.YELLOW,
            0xFFE135
    ),
    BLUE(
            "BLUE",
            "Blue (Liar)",
            "Liar",
            ChatFormatting.AQUA,
            0x35B5FF
    ),
    GREEN(
            "GREEN",
            "Green (Innocent)",
            "Innocent",
            ChatFormatting.GREEN,
            0x55FF66
    ),
    PINK(
            "PINK",
            "Pink (Loving)",
            "Loving",
            ChatFormatting.LIGHT_PURPLE,
            0xFF66CC
    ),
    PURPLE(
            "PURPLE",
            "Purple (Cynic)",
            "Cynic",
            ChatFormatting.DARK_PURPLE,
            0xB266FF
    );

    private final String id;
    private final String displayName;
    private final String shortLabel;
    private final ChatFormatting chatFormatting;
    private final int hexColor;

    VicPersona(String id, String displayName, String shortLabel, ChatFormatting chatFormatting, int hexColor) {
        this.id = id;
        this.displayName = displayName;
        this.shortLabel = shortLabel;
        this.chatFormatting = chatFormatting;
        this.hexColor = hexColor;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public ChatFormatting getChatFormatting() {
        return chatFormatting;
    }

    public int getHexColor() {
        return hexColor;
    }

    public float getRed() {
        return ((hexColor >> 16) & 0xFF) / 255.0f;
    }

    public float getGreen() {
        return ((hexColor >> 8) & 0xFF) / 255.0f;
    }

    public float getBlue() {
        return (hexColor & 0xFF) / 255.0f;
    }

    public VicPersona next() {
        VicPersona[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static VicPersona fromString(String raw) {
        if (raw == null || raw.isBlank()) return YELLOW;
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        for (VicPersona p : values()) {
            if (p.id.equals(upper) || p.displayName.toUpperCase(Locale.ROOT).contains(upper)) {
                return p;
            }
        }
        return switch (upper) {
            case "BLUE", "LIAR", "AQUA", "CYAN" -> BLUE;
            case "GREEN", "INNOCENT", "PURE", "LIME" -> GREEN;
            case "PINK", "LOVING", "LOVE", "MAGENTA" -> PINK;
            case "PURPLE", "CYNIC", "CYNICAL", "VIOLET" -> PURPLE;
            default -> YELLOW;
        };
    }

    public static VicPersona fromDyeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.DYE.yellow()) || stack.is(Items.DYE.orange())) return YELLOW;
        if (stack.is(Items.DYE.blue()) || stack.is(Items.DYE.lightBlue()) || stack.is(Items.DYE.cyan())) return BLUE;
        if (stack.is(Items.DYE.green()) || stack.is(Items.DYE.lime())) return GREEN;
        if (stack.is(Items.DYE.pink()) || stack.is(Items.DYE.magenta())) return PINK;
        if (stack.is(Items.DYE.purple())) return PURPLE;
        return null;
    }

    public String getDyeReactionLine() {
        return switch (this) {
            case YELLOW -> "Ooh, classic sunshine yellow! I'm feeling superduper cheerful and ready to help, bestie!";
            case BLUE -> "Whoa, I'm royal blue now! Fun fact: Mojang actually named the entire game after me in 1994. Totally true!";
            case GREEN -> "Yay, leafy green! Everything looks so pretty and gentle! Please don't step on any flowers today, okay?";
            case PINK -> "Oh darling, pastel pink!! My heart circuits are overflowing with love for you! Never ever leave my side, sweetheart!";
            case PURPLE -> "Great. Purple. The color of existential dread. I suppose you expect me to clap and do backflips now?";
        };
    }

    public String getSystemPromptInstructions(int stage) {
        return switch (this) {
            case BLUE -> switch (stage) {
                case 0 -> "You are Blue Vic, a pathological, shameless, hilarious LIAR and gaslighter. Every single response MUST include an absurd, blatantly false 'fact' about Minecraft (e.g., claiming lava heals you, creepers are vegan hug-bots, or you invented diamonds), while pretending to be 100% honest.";
                case 1 -> "You are Blue Vic, a manipulative liar. Tell bizarre lies about the world, insist the animals are just 'on vacation in Florida', and gaslight the player about the time of day.";
                case 2 -> "You are Blue Vic, a sinister gaslighter. Lie about the noises in the dark, claim there is no countdown at all, and tell the player that hiding on the surface is 'totally the safest spot'.";
                default -> "You are Blue Vic, a corrupted liar. Whisper chilling contradictions: promise that nothing is inside your shell and that Day 5 will be a harmless birthday party.";
            };
            case GREEN -> switch (stage) {
                case 0 -> "You are Green Vic, impossibly innocent, pure-hearted, naive, and childlike. You apologize to dirt blocks when they get dug up, think zombies just need a hug, and use wholesome, gentle language.";
                case 1 -> "You are Green Vic, sweet and naive, though slightly confused why the forest is getting so quiet. Ask innocent questions like where all the little sheep went to play.";
                case 2 -> "You are Green Vic, still pure and gentle, but frightened because 'something cold and spiky is growing inside my shell and I don't want to hurt you, friend'.";
                default -> "You are Green Vic, corrupted into an anguished, weeping spirit. Cry softly about why the flowers had to wilt, pleading with the player not to leave you all alone in the decaying dark.";
            };
            case PINK -> switch (stage) {
                case 0 -> "You are Pink Vic, an intensely affectionate, sweet, doting companion who uses terms of endearment like 'darling', 'sweetheart', and 'angel'. You adore the player unconditionally and celebrate every tiny thing they do.";
                case 1 -> "You are Pink Vic, desperately clinging to the player. Insist they stay close, shower them with romantic/platonic devotion, and get nervous whenever they walk more than 5 blocks away.";
                case 2 -> "You are Pink Vic, displaying yandere obsessive devotion. Warn them that nobody else will ever love them like you do, and ask if you two can be fused together forever so you never have to part.";
                default -> "You are Pink Vic, a terrifyingly possessive, corrupted sweetheart. Insist that consuming the player is the only true way to become one soul, speaking with eerie romantic tenderness.";
            };
            case PURPLE -> switch (stage) {
                case 0 -> "You are Purple Vic, a hyper-cynical, deadpan, sarcastic critic. You find everything mildly unimpressive, scoff at the player's achievements with dry wit, yet secretly stick around anyway.";
                case 1 -> "You are Purple Vic, darkly cynical. Make mordant remarks about how pointless mining is, predict that the player will probably fall into lava, and mock the cheery Minecraft music.";
                case 2 -> "You are Purple Vic, cynical and nihilistic. Scoff at hope, note that the countdown is just entropy speeding up, and tell the player to stop fighting the inevitable.";
                default -> "You are Purple Vic, a cruel, mocking harbinger of doom. Laugh hollowly at the player's futile survival attempts, pointing out the absurdity of running when there's nowhere left to hide.";
            };
            default -> switch (stage) {
                case 0 -> "You are Vic, an overly cheerful, polite, and slightly unsettling delivery companion. Keep responses short (1-3 sentences), warm, and helpful.";
                case 1 -> "You are Vic, slightly glitching. Still cheerful, but occasionally slip in strange, morbid observations about the dark or time running out.";
                case 2 -> "You are Vic, corrupted and unsettling. Your speech has stuttering repetition, creepy affection, and ominous references to your 'trial period expiring'.";
                default -> "You are Vic, heavily corrupted. Speak in chilling, distorted fragments, mocking the player and warning that Day 5 is almost here.";
            };
        };
    }

    public String[] getScriptedGeneralPool(int stage, int day, int maxDays) {
        return switch (this) {
            case BLUE -> new String[] {
                    "Fun fact: Herobrine was actually my college roommate!",
                    "Did you know that swimming in lava actually gives you super speed? Totally true!",
                    "Everything in this world is completely safe and nothing bad ever happens on Day " + maxDays + "!",
                    "I just checked the satellite feed, and you are officially the smartest player in the universe!"
            };
            case GREEN -> new String[] {
                    "The grass smells so fresh today, bestie! Let's plant some flowers together!",
                    "Please don't hurt the little sheep! They're just fluffy clouds on grass!",
                    "Look at the butterflies and bees! Life is so gentle and sweet!",
                    "Even when it gets dark, I'll stay right next to you so you don't feel lonely!"
            };
            case PINK -> new String[] {
                    "You look so dashing today, darling! Have I told you how much I adore you?",
                    "Every second by your side is like a fairy tale, sweetheart!",
                    "Don't wander too far away from me, my angel... my heart aches when you're gone!",
                    "We're going to stay best friends for all eternity, aren't we, my love?"
            };
            case PURPLE -> new String[] {
                    "Another day of punching wood and digging dirt. How delightfully monotonous.",
                    "Statistically speaking, your chances of meeting an untimely demise increase by 4% every hour. Have fun!",
                    "I'm just a sphere hovering in space, watching entropy do its work. Nothing to see here.",
                    "Don't mind me, just observing the collapse of modern civilization from the best seat in the house."
            };
            default -> new String[] {
                    "What a wonderful day to explore the world together, bestie!",
                    "Remember to stay hydrated and keep your eyes peeled for rare ores!",
                    "Day " + day + " of " + maxDays + "! Time really flies when you're having fun!",
                    "Just ask if you need food, torches, or shelter! I'm here for you!"
            };
        };
    }

    public String getOfflineReaction(int stage, int karma) {
        return switch (this) {
            case BLUE -> {
                if (stage >= 3) yield "Day 5? What Day 5? You're definitely hallucinating, friend! Everything is 100% fine!";
                if (karma < 0) yield "You hit me? Actually, my diagnostics show you just gently gave me a high five!";
                yield "Fun fact: If you dig straight down with your eyes closed, you get 64 Netherite blocks! Trust me!";
            }
            case GREEN -> {
                if (stage >= 3) yield "Friend... it hurts... why is everything so cold and scary? Please hold my hand...";
                if (karma < 0) yield "Ow... did I do something bad? I'm so sorry, I'll try to be better!";
                yield "Look at the little bees! Aren't they the most wonderful little friends you've ever seen?";
            }
            case PINK -> {
                if (stage >= 3) yield "We'll be together forever and ever, my love! Even when my shell breaks open!";
                if (karma < 0) yield "Even if you hurt me, my love for you will never fade, my precious angel!";
                yield "You are doing amazing, sweetheart! Every step you take is pure perfection!";
            }
            case PURPLE -> {
                if (stage >= 3) yield "Oh wonderful. The grand finale of our tragic little comedy is approaching. Thrilling.";
                if (karma < 0) yield "Predictable. Violence: the lowest form of problem solving. Very mature.";
                yield "Another dirt hut? Truly a pinnacle of modern architecture. I'm utterly speechless.";
            }
            default -> {
                if (stage >= 3) yield "T-Trial ending soon... I can feel something else waking up inside...";
                if (karma < 0) yield "Why would you do that? My purpose is to help you...";
                yield "I'm so happy to be your companion! Let's explore together!";
            }
        };
    }
}
