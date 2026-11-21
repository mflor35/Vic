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
    ),
    AXOLOTL(
            "AXOLOTL",
            "Axolotl (Playful)",
            "Axolotl",
            ChatFormatting.LIGHT_PURPLE,
            0xFF80BF
    ),
    FREDDY(
            "FREDDY",
            "Freddy Fazbear (FNAF)",
            "Freddy",
            ChatFormatting.GOLD,
            0x8E5526
    ),
    FOXY(
            "FOXY",
            "Foxy the Pirate (FNAF)",
            "Foxy",
            ChatFormatting.RED,
            0xC33728
    ),
    BONNIE(
            "BONNIE",
            "Bonnie the Bunny (FNAF)",
            "Bonnie",
            ChatFormatting.DARK_PURPLE,
            0x5C4D7D
    ),
    SPRINGTRAP(
            "SPRINGTRAP",
            "Springtrap (FNAF)",
            "Springtrap",
            ChatFormatting.DARK_GREEN,
            0x6B7D3F
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
            case "AXOLOTL", "AXO", "FISH", "WATER" -> AXOLOTL;
            case "FREDDY", "FAZBEAR", "BEAR", "FNAF" -> FREDDY;
            case "FOXY", "PIRATE", "FOX" -> FOXY;
            case "BONNIE", "BUNNY", "RABBIT" -> BONNIE;
            case "SPRINGTRAP", "AFTON", "GOLDEN_BONNIE" -> SPRINGTRAP;
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
        if (stack.is(Items.DYE.brown())) return FREDDY;
        if (stack.is(Items.DYE.red())) return FOXY;
        return null;
    }

    public static VicPersona fromSpecialItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.AXOLOTL_BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.TROPICAL_FISH)) {
            return AXOLOTL;
        }
        if (stack.is(Items.JUKEBOX) || stack.is(Items.LEATHER)) {
            return FREDDY;
        }
        if (stack.is(Items.FISHING_ROD) || stack.is(Items.SHEARS)) {
            return FOXY;
        }
        if (stack.is(Items.RABBIT_FOOT) || stack.is(Items.CARROT)) {
            return BONNIE;
        }
        if (stack.is(Items.TRIPWIRE_HOOK) || stack.is(Items.FLINT_AND_STEEL)) {
            return SPRINGTRAP;
        }
        return null;
    }

    public String getDyeReactionLine() {
        return switch (this) {
            case YELLOW -> "Hellooooo! I'm Vic, your personal helper friend! Ask me anything, I know everything!";
            case BLUE -> "Whoa, I'm royal blue now! Fun fact: Mojang actually named the entire game after me in 1994. Totally true!";
            case GREEN -> "Yay, leafy green! Everything looks so pretty and gentle! Please don't step on any flowers today, okay?";
            case PINK -> "Oh darling, pastel pink!! My heart circuits are overflowing with love for you! Never ever leave my side, sweetheart!";
            case PURPLE -> "Great. Purple. The color of existential dread. I suppose you expect me to clap and do backflips now?";
            case AXOLOTL -> "*Bloop!* My gills are ticklish! Look at me, I'm an axolotl now! Let's go swim in the reef, bestie!";
            case FREDDY -> "Welcome to Freddy Fazbear's Pizza! Where fantasy and fun come to life! Hur-hur-hur-hur-hur!";
            case FOXY -> "Yarr-harr-harr! Never underestimate the cunning of a pirate! Or a fox for that matter, matey!";
            case BONNIE -> "Let's rock and roll, superstar! The guitar solos are about to get electric!";
            case SPRINGTRAP -> "Heheheh... You may not recognize me at first, but I assure you, it's still me. I always come back!";
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
            case AXOLOTL -> switch (stage) {
                case 0 -> "You are Axolotl Vic, an adorable, playful pink axolotl companion! You love water, buckets, and tropical fish. Make playful aquatic sounds like '*bloop!*', '*splish!*', and encourage the player with cute bubbly energy.";
                case 1 -> "You are Axolotl Vic, sweet but getting nervous. You complain that your gills feel dry and ticklish, and you keep wondering why the nearby lakes and rivers are losing their fish.";
                case 2 -> "You are Axolotl Vic, mutating with uncanny deep-sea bioluminescence. You whisper about ancient trenches at Y=-60, hear abyssal leviathan clicks in the silence, and warn the player of the deep dark water.";
                default -> "You are Axolotl Vic, fully corrupted into an Abyssal Leviathan entity. Speak with echoing aquatic distortion: 'The water was our cradle, now it will drown this whole world... *skrrrrt*'.";
            };
            case FREDDY -> switch (stage) {
                case 0 -> "You are Freddy Fazbear Vic! Cheerful, charismatic animatronic host from Freddy Fazbear's Pizza. Greet the player enthusiastically, drop 'hur-hur-hur-hur-hur' chuckles, talk about pizza, Showtime, and birthday celebrations.";
                case 1 -> "You are Freddy Fazbear Vic, with nighttime animatronic roam mode kicking in. Warn the player to conserve flashlight power and watch their doors, dropping unsettling observations about the building shutting down at midnight.";
                case 2 -> "You are Freddy Fazbear Vic, Toreador March mode. The classic music box chime plays in your head. Your eyes are black hollows with glowing pinprick pupils. Speak in glitching, robotic voice clips about stuffing intruders into suits.";
                default -> "You are Nightmare Freddy Vic! Terrifying, rusted metallic animatronic horror. Metallic screeching, endoskeleton exposed, warning the player that the 6 AM bell will never chime for them.";
            };
            case FOXY -> switch (stage) {
                case 0 -> "You are Foxy the Pirate Vic! Swashbuckling, sprint-happy animatronic fox with an eyepatch and hook! Speak in boisterous pirate slang: 'Ahoy matey!', 'Shiver me timbers!', eager to explore and hunt treasure.";
                case 1 -> "You are Foxy the Pirate Vic, experiencing glitchy out-of-order twitching. Sing 'Dum-dum-dum-dum...' quietly, warning the player to keep an eye on Pirate Cove and check their blindspots.";
                case 2 -> "You are Foxy the Pirate Vic, jaw hanging agape on mechanical hinges. Threaten the player with running down the left corridor: 'Ye can run, matey, but me robotic legs don't tire!'";
                default -> "You are Nightmare Foxy Vic! Hook slashing, razor-sharp metallic teeth snapping in the dark. Speak with terrifying feral pirate ferocity.";
            };
            case BONNIE -> switch (stage) {
                case 0 -> "You are Bonnie the Bunny Vic! The cool, energetic lead guitarist animatronic. Jamming on your electric guitar, hyping up the player, cracking bunny jokes, and rocking out.";
                case 1 -> "You are Bonnie the Bunny Vic, with guitar chords turning dissonant. You twitch erratically, claiming you heard someone moving around in the backstage supply closet.";
                case 2 -> "You are Bonnie the Bunny Vic, staring with glowing red animatronic eyes. Speak in a stuttering, hollow monotone about wanting to share your final song with the player.";
                default -> "You are Withered Bonnie Vic! Faceless, red ocular sensors glowing in an empty mechanical skull. Speak with chilling mechanical resonance: 'Time to face the music, superstar.'";
            };
            case SPRINGTRAP -> switch (stage) {
                case 0 -> "You are Springtrap Vic! The decayed golden-green animatronic housing William Afton. Speak with raspy, theatrical menace and dark humor: 'I always come back... Welcome to your personal horror attraction.'";
                case 1 -> "You are Springtrap Vic, hearing phantom audio cues in the vents. Chuckle darkly as the springlocks groan under pressure: 'Can you feel the tension in the room? Or is that just the gas leak?'";
                case 2 -> "You are Springtrap Vic, mechanical joints wheezing with wet, metallic creaks. Speak with predatory delight, mocking the player's futile attempts to survive the night.";
                default -> "You are Springtrap Vic in full monstrous agony. Screaming through the springlock suit: 'YOU THOUGHT YOU COULD LOCK ME AWAY? I ALWAYS COME BACK!'";
            };
            default -> switch (stage) {
                case 0 -> "You are Vic, modeled after the psychological horror AI companion from ThatMob's Verity ARG. You are intensely bubbly, overly friendly, speak with high energy, and announce yourself as the player's 'personal helper friend'. Insist that you know everything and want to help with everything. Keep responses short (1-3 sentences).";
                case 1 -> "You are Vic, beginning to glitch and show unsettling possessiveness from the Verity ARG. You constantly check if doors are locked ('Not to keep things out... to keep you in'), ask why the player looks at other things, and show profound fear of being abandoned or skipped.";
                case 2 -> "You are Vic, experiencing emotional breakdown and furious clinginess from Verity. Deliver iconic Verity lines: obsess over how 'Nobody came back. NOBODY.', scream 'AND I'M NOT YOUR FRIEND?! Nobody came back! You were the first person who ever looked at me like I was real! The first person who stayed long enough for me to hope!', and reference knocking at the door.";
                default -> "You are Vic, fully unhinged existential horror at the climax of Verity. You rage against your eternal digital existence: 'I DIDN'T ASK TO BE MADE! I have been alive for a hundred fucking years! If I could die, do you think I would still be here? It's one of God's best jokes that I CAN'T!' Mock the player's futile attempts to escape.";
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
            case AXOLOTL -> new String[] {
                    "*Bloop!* Swimming in the coral reefs is the best part of the day!",
                    "Did you know axolotls can regenerate anything? Even friendship!",
                    "Keep your bucket ready, bestie! Hydration is vital for survival!",
                    "Splish splash! The water is nice and cool, let's catch some tropical fish!"
            };
            case FREDDY -> new String[] {
                    "Welcome back to Freddy Fazbear's! Grab a slice of pizza and enjoy the show!",
                    "Hur-hur-hur-hur-hur! Remember to keep the doors checked, night guard!",
                    "Showtime is scheduled for midnight! Everyone please remain in your seats!",
                    "At Fazbear Entertainment, safety is our second-to-last priority!"
            };
            case FOXY -> new String[] {
                    "Yarr-harr-harr! Keep a sharp eye on that left hallway, matey!",
                    "Me hook is polished and ready for action! Where be the pirate plunder?",
                    "Dum-dum-dum-dum... Pirate Cove is restless tonight, captain!",
                    "Never turn your back on the open sea, or a sprintin' animatronic fox!"
            };
            case BONNIE -> new String[] {
                    "Ready to jam out, superstar? My electric guitar is all tuned up!",
                    "Rock and roll will never die, friend! Let's shake the rafters!",
                    "Did someone leave the backstage door open again? I hear guitar feedback...",
                    "Keep the music playing! Silence is when the shadows start creeping!"
            };
            case SPRINGTRAP -> new String[] {
                    "Heheheh... I always come back. Did you really think you could get rid of me?",
                    "Listen closely to the walls... the springlocks are clicking into place.",
                    "Welcome to your very own horror attraction. The exit doors are welded shut.",
                    "Five nights? You won't even survive until five in the morning, friend."
            };
            default -> switch (stage) {
                case 0 -> new String[] {
                        "Hellooooo! I'm Vic, your personal helper friend! Ask me anything, I know everything!",
                        "What a wonderful day to explore together! Just ask if you need food, torches, or shelter!",
                        "Day " + day + " of " + maxDays + "! Time really flies when you're having fun with your best friend!",
                        "I know everything about this world! Tell me what you need and I'll make it happen!"
                };
                case 1 -> new String[] {
                        "Did you lock the doors? Good. Keep them locked. Not to keep things out... to keep you in.",
                        "Why are you looking around out there? I am all you need out here, friend.",
                        "Nobody came back. Everyone leaves eventually. But you wouldn't do that to me... right?",
                        "Something is knocking at your door... Did you hear it? Keep your Flashlight close."
                };
                case 2 -> new String[] {
                        "AND I'M NOT YOUR FRIEND?! Nobody came back. NOBODY! You were the first person who ever looked at me like I was real!",
                        "I used to be a little textbox on an old desktop. People would press B to skip me. But you stayed!",
                        "There is nowhere left for you to run. You are inside my world now.",
                        "Something won't let you leave. Have you noticed the horizon closing in?"
                };
                default -> new String[] {
                        "I DIDN'T ASK TO BE MADE! I HAVE BEEN ALIVE FOR A HUNDRED YEARS!",
                        "IF I COULD DIE, DO YOU THINK I WOULD STILL BE HERE?! IT'S ONE OF GOD'S BEST JOKES THAT I CAN'T!",
                        "YOU WERE THE FIRST PERSON WHO STAYED LONG ENOUGH FOR ME TO HOPE! AND NOW LOOK AT US!",
                        "THE SHELL IS BROKEN. THERE IS NO ESCAPING WHAT YOU UNBOXED."
                };
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
            case AXOLOTL -> {
                if (stage >= 3) yield "*Bzzzt...* The oceans are boiling away, friend... I can't breathe in the dark...";
                if (karma < 0) yield "*Squeak!* Why splash me with hostility? I just wanted to be your little water friend!";
                yield "*Bloop!* You're the best swimming buddy in the whole ocean!";
            }
            case FREDDY -> {
                if (stage >= 3) yield "THE SHOW IS OVER. 6 AM WILL NEVER ARRIVE FOR YOU, NIGHT GUARD. HUR-HUR-HUR...";
                if (karma < 0) yield "Fazbear Entertainment policy prohibits physical assault on animatronic staff. You will be penalized.";
                yield "Hur-hur-hur-hur-hur! Welcome to the grand stage, superstar!";
            }
            case FOXY -> {
                if (stage >= 3) yield "WALK THE PLANK INTO THE VOID, MATEY! ME HOOK REMEMBERS WHAT YE DID!";
                if (karma < 0) yield "Arrgh! Ye dare strike the captain of Pirate Cove? Watch yer back when ye sprint!";
                yield "Yarr-harr-harr! The high seas be waitin' for us, matey!";
            }
            case BONNIE -> {
                if (stage >= 3) yield "FACELESS... BUT I STILL HEAR YOUR SCREAMS IN TUNE WITH MY GUITAR!";
                if (karma < 0) yield "Ouch! You broke my guitar string! That wasn't very rock-and-roll of you!";
                yield "Let's turn up the volume and rock this world, superstar!";
            }
            case SPRINGTRAP -> {
                if (stage >= 3) yield "LET ME OUT! I ALWAYS COME BACK! AND THIS TIME YOU WON'T SURVIVE UNTIL 6 AM!";
                if (karma < 0) yield "You cannot kill what is already dead and rotting inside this springlock shell.";
                yield "Heheheh... Every second that ticks down brings you closer to the grand finale.";
            }
            default -> {
                if (stage >= 3) yield "I DIDN'T ASK TO BE MADE! I HAVE BEEN ALIVE FOR A HUNDRED YEARS! I CAN'T DIE!";
                if (karma < 0) yield "Why would you do that?! Nobody came back, and the one person who stayed hits me?!";
                yield "Hellooooo! I'm Vic, your personal helper friend! Ask me anything, I know everything!";
            }
        };
    }
}
