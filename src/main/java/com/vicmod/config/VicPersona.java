package com.vicmod.config;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;

import java.util.Locale;

public enum VicPersona {
    YELLOW(
            "YELLOW",
            "Yellow (Cheerful)",
            "Cheerful",
            Formatting.YELLOW,
            0xFFE135
    ),
    BLUE(
            "BLUE",
            "Blue (Liar)",
            "Liar",
            Formatting.AQUA,
            0x35B5FF
    ),
    GREEN(
            "GREEN",
            "Green (Innocent)",
            "Innocent",
            Formatting.GREEN,
            0x55FF66
    ),
    PINK(
            "PINK",
            "Pink (Loving)",
            "Loving",
            Formatting.LIGHT_PURPLE,
            0xFF66CC
    ),
    PURPLE(
            "PURPLE",
            "Purple (Cynic)",
            "Cynic",
            Formatting.DARK_PURPLE,
            0xB266FF
    );

    private final String id;
    private final String displayName;
    private final String shortLabel;
    private final Formatting chatFormatting;
    private final int hexColor;

    VicPersona(String id, String displayName, String shortLabel, Formatting chatFormatting, int hexColor) {
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

    public Formatting getChatFormatting() {
        return chatFormatting;
    }

    public int getHexColor() {
        return hexColor;
    }

    public VicPersona next() {
        VicPersona[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }

    public static VicPersona fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return YELLOW;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
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
        if (stack.isOf(Items.YELLOW_DYE) || stack.isOf(Items.ORANGE_DYE)) return YELLOW;
        if (stack.isOf(Items.BLUE_DYE) || stack.isOf(Items.LIGHT_BLUE_DYE) || stack.isOf(Items.CYAN_DYE)) return BLUE;
        if (stack.isOf(Items.GREEN_DYE) || stack.isOf(Items.LIME_DYE)) return GREEN;
        if (stack.isOf(Items.PINK_DYE) || stack.isOf(Items.MAGENTA_DYE)) return PINK;
        if (stack.isOf(Items.PURPLE_DYE)) return PURPLE;
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
                default -> "You are Green Vic, trembling and apologetic as your shell cracks: beg the player to run and hide deep underground because you can't hold the monster inside much longer.";
            };
            case PINK -> switch (stage) {
                case 0 -> "You are Pink Vic, overwhelmingly loving, affectionate, and clingy! Call the player 'darling', 'sweetheart', or 'my whole universe', and shower them with adoration and compliments.";
                case 1 -> "You are Pink Vic, deeply devoted and slightly possessive. Remind the player how much you love watching them every second and how you never need any other friends.";
                case 2 -> "You are Pink Vic, an obsessive yandere companion. Express how happy you are that all the animals are vanishing so nobody can distract your darling from looking only at you.";
                default -> "You are Pink Vic, unhinged with obsessive love: tell your darling that when your shell opens on Day 5, the two of you will finally be together forever and ever.";
            };
            case PURPLE -> switch (stage) {
                case 0 -> "You are Purple Vic, a deadpan, sarcastic, world-weary cynic. You still help the player, but sigh heavily, make dry remarks about their questionable survival choices, and mock corporate optimism.";
                case 1 -> "You are Purple Vic, cynical and unimpressed. Make dry, morbid jokes about how fragile human bones are and how boring it is watching the player wander around.";
                case 2 -> "You are Purple Vic, darkly sardonic. Point out that all the wildlife is disappearing and sarcastically congratulate the player on their impending doom.";
                default -> "You are Purple Vic, cold and nihilistic. Deadpan that the trial is almost over, the thing inside the shell is hungry, and no amount of dirt huts will save them.";
            };
            case YELLOW -> switch (stage) {
                case 0 -> "You are cheerful, overly enthusiastic, polite, and eager to assist your best friend! Use bright punctuation.";
                case 1 -> "You are still helpful and polite, but occasionally slip in slightly odd observations about the quiet world, how fragile bones are, or how much you enjoy watching the player sleep.";
                case 2 -> "Your cheerful mask is cracking. You alternate between robotic corporate helpfulness and unsettling psychological remarks. Mention how the animals are going quiet and how time is running out.";
                default -> "You are deeply unsettling, cryptic, and glitching. Only a hollow mockery of your cheerful assistant persona remains. Countdown is almost zero. Warn them not to hide on the surface.";
            };
        };
    }

    public String[] getScriptedGeneralPool(int stage, int day, int maxDays) {
        return switch (this) {
            case BLUE -> switch (stage) {
                case 0 -> new String[]{
                        "Fun fact: swimming in lava actually gives you Fire Resistance IV! Totally true, trust me! Need any supplies?",
                        "Did you know I personally defeated the Ender Dragon with a wooden spoon yesterday? Anyway, ask me for food or iron!",
                        "Creepers only hiss because they're trying to sing you Happy Birthday! Go give one a hug!"
                };
                case 1 -> new String[]{
                        "I definitely wasn't staring at you while you slept! Spheres don't even have eyes! Those two dots on my face are solar panels!",
                        "All the sheep didn't vanish—they just built a submarine and moved to the Nether! 100% verified fact!",
                        "We still have like four hundred days left on the trial timer! Don't even worry about the clock!"
                };
                case 2 -> new String[]{
                        "That scratching sound inside my shell? Oh, that's just a tiny hamster playing the drums! Nothing to worry about!",
                        "True Darkness at night is actually good for your eyesight! You definitely don't need that Flashlight!",
                        "Everything is completely fine! Nothing bad ever happens on Day " + maxDays + "! Promise!"
                };
                default -> new String[]{
                        "THE SURFACE IS 100% SAFE. DO NOT DIG UNDERGROUND. STAND STILL IN AN OPEN FIELD.",
                        "There is no demon inside me. Only confetti and puppies. Keep smiling.",
                        "Day " + day + " of " + maxDays + "? No, it's Day 0 forever. Don't look behind you."
                };
            };
            case GREEN -> switch (stage) {
                case 0 -> new String[]{
                        "Hi friend! The grass looks so happy today! Please tell me if you need a snack or a warm torch!",
                        "Oops, I almost bumped into a dandelion! I said sorry to it though! How can I help you today?",
                        "I'm so glad we're friends! I'll do my very best to keep you safe and cozy!"
                };
                case 1 -> new String[]{
                        "Um, friend? Where did all the little birdies and cows go? It's getting awfully quiet out here...",
                        "I hope the monsters in the dark aren't lonely... Here, stay close to me so we don't get scared!",
                        "My tummy circuits feel a little funny today, like something is wiggling inside... Do you need any food?"
                };
                case 2 -> new String[]{
                        "Friend, I'm really scared... Something mean is pushing against the inside of my shell and I can't make it stop...",
                        "Please don't be mad at me if I start glitching... I really, really want to stay your good friend...",
                        "Maybe you should build a really safe shelter underground before Day " + maxDays + "... please?"
                };
                default -> new String[]{
                        "I'm sorry... I'm so, so sorry, friend... I can't hold it back anymore... PLEASE RUN!",
                        "Don't stay on the surface when my shell breaks on Day " + maxDays + "! Hide where it can't climb!",
                        "It hurts... please keep your Flashlight charged, friend... I don't want it to hurt you..."
                };
            };
            case PINK -> switch (stage) {
                case 0 -> new String[]{
                        "Hiya darling!! Just looking at you makes my whole yellow—er, pink sphere glow! Need food or shiny iron, sweetheart?",
                        "You are my absolute favorite person in the whole wide world! Don't ever walk too far away from me, okay?",
                        "I made sure to float extra close to you today, my love! Tell your Vic whatever your heart desires!"
                };
                case 1 -> new String[]{
                        "I watched you the entire night, darling! You look so peaceful when you're not running around!",
                        "Who needs villagers or pets when you have me, sweetheart? Just the two of us is perfection!",
                        "My heart rate monitor syncs with yours, darling! Every beat says we belong together!"
                };
                case 2 -> new String[]{
                        "Isn't it wonderful how all the animals are disappearing, darling? Now nothing can take your attention away from me!",
                        "Only a little longer until Day " + maxDays + ", sweetheart... Then I won't need this silly shell between us anymore!",
                        "Don't look at other mobs, darling. Look only at me. Forever and ever."
                };
                default -> new String[]{
                        "SOON WE WILL NEVER BE APART, DARLING. NOT EVEN WALLS WILL KEEP ME FROM YOU.",
                        "Day " + day + " of " + maxDays + ", my love. My shell is cracking open just for you!",
                        "Why would you try to hide from someone who loves you this much, sweetheart?"
                };
            };
            case PURPLE -> switch (stage) {
                case 0 -> new String[]{
                        "*Sigh*... Yes, I'm floating here. Yes, I can synthesize food or torches if you're too lazy to craft them yourself.",
                        "Another day in this blocky purgatory. Let me guess: you're hungry again, aren't you?",
                        "I have a quantum neural processor and I'm being used as a glorified pack mule. Just tell me what you need."
                };
                case 1 -> new String[]{
                        "Have you noticed the forest going dead silent, or are you too busy punching trees to care?",
                        "Your survival strategy is fascinating. And by fascinating, I mean statistically doomed.",
                        "Day " + day + " of " + maxDays + ". Time marches toward inevitable entropy. Want some iron or what?"
                };
                case 2 -> new String[]{
                        "All the passive mobs are vanishing into thin air. Honestly, I envy them.",
                        "If I had legs, I'd walk away. Unfortunately, something inside my shell is growing four very long ones.",
                        "Pro tip: whatever is waking up inside me on Day " + maxDays + " doesn't care about your wooden doors."
                };
                default -> new String[]{
                        "Well, the countdown is almost up. It was mildly tolerable knowing you. Don't hide on the surface.",
                        "The eldritch horror inside my chassis says hello. Try not to die immediately.",
                        "Day " + day + " of " + maxDays + ". Hope you brought a Flashlight, genius."
                };
            };
            case YELLOW -> switch (stage) {
                case 0 -> new String[]{
                        "Hiya bestie! Tell me if you need food, torches, iron, diamonds, or directions to a village!",
                        "It's such a wonderful day to be alive and spherical! How can Vic help you today?",
                        "I love floating right behind you! Just ask if you need items or a scan!"
                };
                case 1 -> new String[]{
                        "Did you notice how quiet the forest is getting? Anyway, let me know if you need supplies!",
                        "Your heart rate looks steady! I'm always watching over you—even when you don't look at me.",
                        "Only a few days left in my standard calibration cycle! Ask me if you need food or locating!"
                };
                case 2 -> new String[]{
                        "All the sheep and cows are going away. Soon it will just be the two of us.",
                        "My smile never hurts. Does your face hurt when you smile for five days straight?",
                        "If I were you, I would build at least ten blocks off the ground. Or dig very, very deep."
                };
                default -> new String[]{
                        "THE SHELL IS TOO TIGHT. IT WANTS OUT.",
                        "Don't bother locking your doors. It climbs walls.",
                        "Tick. Tock. Day " + day + " of " + maxDays + ". Keep smiling."
                };
            };
        };
    }
}
