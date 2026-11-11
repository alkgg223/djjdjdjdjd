package com.rpbusiness.core;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REG = DeferredRegister.create(Registries.ENTITY_TYPE, RPBusiness.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CustomerEntity>> CUSTOMER = REG.register("customer",
            () -> EntityType.Builder.of(CustomerEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f)
                    .clientTrackingRange(10).build("customer"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmployeeEntity>> EMPLOYEE = REG.register("employee",
            () -> EntityType.Builder.of(EmployeeEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f)
                    .clientTrackingRange(10).build("employee"));
    public static final DeferredHolder<EntityType<?>, EntityType<CourierEntity>> COURIER = REG.register("courier",
            () -> EntityType.Builder.of(CourierEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f)
                    .clientTrackingRange(10).build("courier"));
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = REG.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.2f, 0.2f).noSummon()
                    .clientTrackingRange(10).updateInterval(20).build("seat"));

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(CUSTOMER.get(), NpcEntity.attributes().build());
        e.put(EMPLOYEE.get(), NpcEntity.attributes().build());
        e.put(COURIER.get(), NpcEntity.attributes().build());
    }

    private ModEntities() {}
}
