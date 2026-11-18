package com.vicmod.registry;

import com.vicmod.VicMod;
import com.vicmod.item.FlashlightItem;
import com.vicmod.item.VicCoreItem;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

public class ModItems {
    public static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("minecraft", "tools_and_utilities")
    );
    public static final ResourceKey<CreativeModeTab> SPAWN_EGGS_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("minecraft", "spawn_eggs")
    );

    public static final Item FLASHLIGHT = registerItem("flashlight",
            new FlashlightItem(new Item.Properties().stacksTo(1)));

    public static final Item VIC_CORE = registerItem("vic_core",
            new VicCoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final Item VIC_BOX_SPAWN_EGG = registerItem("vic_box_spawn_egg",
            new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.VIC_BOX)));

    public static final Item VIC_SPAWN_EGG = registerItem("vic_spawn_egg",
            new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.VIC)));

    public static final Item VIC_DEMON_SPAWN_EGG = registerItem("vic_demon_spawn_egg",
            new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.VIC_DEMON)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(VicMod.MOD_ID, name), item);
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register(output -> {
            output.accept(FLASHLIGHT);
            output.accept(VIC_CORE);
        });
        CreativeModeTabEvents.modifyOutputEvent(SPAWN_EGGS_TAB).register(output -> {
            output.accept(VIC_BOX_SPAWN_EGG);
            output.accept(VIC_SPAWN_EGG);
            output.accept(VIC_DEMON_SPAWN_EGG);
        });
    }
}
