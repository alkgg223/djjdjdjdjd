package com.vehicleworks.registry;

import com.vehicleworks.VehicleWorks;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.weapon.ProjectileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, VehicleWorks.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> VEHICLE = ENTITIES.register("vehicle",
            () -> EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("vehicle"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.vehicleworks.vehicle.NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<com.vehicleworks.vehicle.NpcEntity>of(com.vehicleworks.vehicle.NpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build("npc"));
    public static final DeferredHolder<EntityType<?>, EntityType<ProjectileEntity>> PROJECTILE = ENTITIES.register("projectile",
            () -> EntityType.Builder.<ProjectileEntity>of(ProjectileEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("projectile"));

    private ModEntities() {}
}
