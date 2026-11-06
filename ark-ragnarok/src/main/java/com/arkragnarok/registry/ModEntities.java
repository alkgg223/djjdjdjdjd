package com.arkragnarok.registry;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.entity.ResourceNode;
import com.arkragnarok.entity.TranqArrow;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ArkRagnarok.MODID);

    /** one entity type per species; sizes are real (1 m = 1 block) */
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<ArkCreature>>> TYPES = new LinkedHashMap<>();
    private static final Map<EntityType<?>, Species> SPECIES = new IdentityHashMap<>();

    static {
        for (Species s : SpeciesList.all()) {
            float w = Mth.clamp(Math.max(s.width, s.lengthM * 0.27f), 0.3f, 5.0f);
            float h = Math.max(0.35f, s.height * 0.9f);
            float eye = Math.min(s.eye, h * 0.95f);
            TYPES.put(s.id, ENTITIES.register(s.id, () -> EntityType.Builder.<ArkCreature>of(ArkCreature::new, MobCategory.CREATURE)
                    .sized(w, h).eyeHeight(eye).clientTrackingRange(s.big() ? 14 : 8).updateInterval(2).build(s.id)));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<TranqArrow>> TRANQ_ARROW = ENTITIES.register("tranq_arrow",
            () -> EntityType.Builder.<TranqArrow>of(TranqArrow::new, MobCategory.MISC).sized(0.5f, 0.5f).eyeHeight(0.13f)
                    .clientTrackingRange(4).updateInterval(20).build("tranq_arrow"));

    public static final DeferredHolder<EntityType<?>, EntityType<ResourceNode>> RESOURCE_NODE = ENTITIES.register("resource_node",
            () -> EntityType.Builder.<ResourceNode>of(ResourceNode::new, MobCategory.MISC).sized(1.9f, 1.35f).fireImmune()
                    .clientTrackingRange(10).updateInterval(40).build("resource_node"));

    public static Species speciesOf(EntityType<?> type) {
        Species s = SPECIES.get(type);
        if (s == null) {
            for (Map.Entry<String, DeferredHolder<EntityType<?>, EntityType<ArkCreature>>> e : TYPES.entrySet()) {
                if (e.getValue().get() == type) {
                    s = SpeciesList.get(e.getKey());
                    SPECIES.put(type, s);
                    break;
                }
            }
        }
        if (s == null) throw new IllegalStateException("not an ARK creature type: " + type);
        return s;
    }

    public static EntityType<ArkCreature> type(String id) {
        return TYPES.get(id).get();
    }
}
