package com.arkragnarok.survivor;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.economy.ModEconomy;
import com.arkragnarok.survivor.world.SurvivorWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** survivors butcher what they hunt into the mod's meat and hide, and remember who wrecks their bases */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SurvivorEvents {
    private SurvivorEvents() {}

    @SubscribeEvent
    public static void butcher(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof Survivor) || !(e.getEntity() instanceof Animal a)) return;
        e.getDrops().clear();
        float size = a.getBbWidth() * a.getBbHeight();
        int meat = 1 + (int) (size * 1.5f) + a.getRandom().nextInt(2);
        int hide = size > 0.5f ? 1 + a.getRandom().nextInt(2) : a.getRandom().nextInt(2);
        e.getDrops().add(new ItemEntity(a.level(), a.getX(), a.getY() + 0.3, a.getZ(), new ItemStack(ModEconomy.RAW_MEAT.get(), meat)));
        if (hide > 0) e.getDrops().add(new ItemEntity(a.level(), a.getX(), a.getY() + 0.3, a.getZ(), new ItemStack(ModEconomy.HIDE.get(), hide)));
    }

    @SubscribeEvent
    public static void wrecked(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || e.getPlayer() == null || !(e.getState().getBlock() instanceof PieceBlock)) return;
        SurvivorWorld w = SurvivorWorld.get(level);
        SurvivorWorld.Tribe owner = w.ownerOf(e.getPos());
        if (owner != null) w.event(e.getPlayer().getUUID(), owner.id, "wrecked", -8, level.getGameTime());
    }
}
