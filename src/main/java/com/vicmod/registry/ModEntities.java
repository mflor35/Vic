package com.vicmod.registry;

import com.vicmod.VicMod;
import com.vicmod.entity.VicBoxEntity;
import com.vicmod.entity.VicDemonEntity;
import com.vicmod.entity.VicEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final EntityType<VicBoxEntity> VIC_BOX = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(VicMod.MOD_ID, "vic_box"),
            EntityType.Builder.create(VicBoxEntity::new, SpawnGroup.MISC)
                    .dimensions(0.95f, 0.95f)
                    .maxTrackingRange(64)
                    .build("vic_box")
    );

    public static final EntityType<VicEntity> VIC = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(VicMod.MOD_ID, "vic"),
            EntityType.Builder.create(VicEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.85f, 0.85f)
                    .maxTrackingRange(80)
                    .build("vic")
    );

    public static final EntityType<VicDemonEntity> VIC_DEMON = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(VicMod.MOD_ID, "vic_demon"),
            EntityType.Builder.create(VicDemonEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.95f, 3.8f)
                    .maxTrackingRange(96)
                    .build("vic_demon")
    );

    public static void register() {
        FabricDefaultAttributeRegistry.register(VIC_BOX, VicBoxEntity.createBoxAttributes());
        FabricDefaultAttributeRegistry.register(VIC, VicEntity.createVicAttributes());
        FabricDefaultAttributeRegistry.register(VIC_DEMON, VicDemonEntity.createDemonAttributes());
    }
}
