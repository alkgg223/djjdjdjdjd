package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.world.NodeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorld {
    private ModWorld() {}

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, ArkRagnarok.MODID);

    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> SURFACE_NODES =
            FEATURES.register("surface_nodes", () -> new NodeFeature(false));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CAVE_NODES =
            FEATURES.register("cave_nodes", () -> new NodeFeature(true));
}
