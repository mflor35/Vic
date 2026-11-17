package com.vicmod.registry;

import com.vicmod.VicMod;
import com.vicmod.item.FlashlightItem;
import com.vicmod.item.VicCoreItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    public static final Item FLASHLIGHT = registerItem("flashlight",
            new FlashlightItem(new Item.Settings().maxCount(1)));

    public static final Item VIC_CORE = registerItem("vic_core",
            new VicCoreItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    public static final Item VIC_BOX_SPAWN_EGG = registerItem("vic_box_spawn_egg",
            new SpawnEggItem(ModEntities.VIC_BOX, 0xC29B61, 0xFFE135, new Item.Settings()));

    public static final Item VIC_SPAWN_EGG = registerItem("vic_spawn_egg",
            new SpawnEggItem(ModEntities.VIC, 0xFFE135, 0x1A1A1A, new Item.Settings()));

    public static final Item VIC_DEMON_SPAWN_EGG = registerItem("vic_demon_spawn_egg",
            new SpawnEggItem(ModEntities.VIC_DEMON, 0x111111, 0x990000, new Item.Settings()));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(VicMod.MOD_ID, name), item);
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(FLASHLIGHT);
            entries.add(VIC_CORE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(VIC_BOX_SPAWN_EGG);
            entries.add(VIC_SPAWN_EGG);
            entries.add(VIC_DEMON_SPAWN_EGG);
        });
    }
}
