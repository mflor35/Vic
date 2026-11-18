package com.vicmod.registry;

import com.vicmod.VicMod;
import com.vicmod.entity.VicBoxEntity;
import com.vicmod.entity.VicDemonEntity;
import com.vicmod.entity.VicEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    private static ResourceKey<EntityType<?>> entityKey(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(VicMod.MOD_ID, name));
    }

    public static final ResourceKey<EntityType<?>> VIC_BOX_KEY = entityKey("vic_box");
    public static final EntityType<VicBoxEntity> VIC_BOX = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            VIC_BOX_KEY,
            EntityType.Builder.of(VicBoxEntity::new, MobCategory.MISC)
                    .sized(0.95f, 0.95f)
                    .clientTrackingRange(64)
                    .build(VIC_BOX_KEY)
    );

    public static final ResourceKey<EntityType<?>> VIC_KEY = entityKey("vic");
    public static final EntityType<VicEntity> VIC = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            VIC_KEY,
            EntityType.Builder.of(VicEntity::new, MobCategory.CREATURE)
                    .sized(0.85f, 0.85f)
                    .clientTrackingRange(80)
                    .build(VIC_KEY)
    );

    public static final ResourceKey<EntityType<?>> VIC_DEMON_KEY = entityKey("vic_demon");
    public static final EntityType<VicDemonEntity> VIC_DEMON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            VIC_DEMON_KEY,
            EntityType.Builder.of(VicDemonEntity::new, MobCategory.MONSTER)
                    .sized(0.95f, 3.8f)
                    .clientTrackingRange(96)
                    .build(VIC_DEMON_KEY)
    );

    public static void register() {
        FabricDefaultAttributeRegistry.register(VIC_BOX, VicBoxEntity.createBoxAttributes());
        FabricDefaultAttributeRegistry.register(VIC, VicEntity.createVicAttributes());
        FabricDefaultAttributeRegistry.register(VIC_DEMON, VicDemonEntity.createDemonAttributes());
    }
}
