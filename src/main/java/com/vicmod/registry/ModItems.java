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

import java.util.function.Function;

public class ModItems {
    public static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("minecraft", "tools_and_utilities")
    );
    public static final ResourceKey<CreativeModeTab> SPAWN_EGGS_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath("minecraft", "spawn_eggs")
    );

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VicMod.MOD_ID, name));
    }

    private static <T extends Item> T registerItem(ResourceKey<Item> key, Function<Item.Properties, T> factory, Item.Properties properties) {
        T item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static final ResourceKey<Item> FLASHLIGHT_KEY = itemKey("flashlight");
    public static final Item FLASHLIGHT = registerItem(
            FLASHLIGHT_KEY,
            FlashlightItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static final ResourceKey<Item> VIC_CORE_KEY = itemKey("vic_core");
    public static final Item VIC_CORE = registerItem(
            VIC_CORE_KEY,
            VicCoreItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
    );

    public static final ResourceKey<Item> VIC_BOX_SPAWN_EGG_KEY = itemKey("vic_box_spawn_egg");
    public static final Item VIC_BOX_SPAWN_EGG = registerItem(
            VIC_BOX_SPAWN_EGG_KEY,
            SpawnEggItem::new,
            new Item.Properties().spawnEgg(ModEntities.VIC_BOX)
    );

    public static final ResourceKey<Item> VIC_SPAWN_EGG_KEY = itemKey("vic_spawn_egg");
    public static final Item VIC_SPAWN_EGG = registerItem(
            VIC_SPAWN_EGG_KEY,
            SpawnEggItem::new,
            new Item.Properties().spawnEgg(ModEntities.VIC)
    );

    public static final ResourceKey<Item> VIC_DEMON_SPAWN_EGG_KEY = itemKey("vic_demon_spawn_egg");
    public static final Item VIC_DEMON_SPAWN_EGG = registerItem(
            VIC_DEMON_SPAWN_EGG_KEY,
            SpawnEggItem::new,
            new Item.Properties().spawnEgg(ModEntities.VIC_DEMON)
    );

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
