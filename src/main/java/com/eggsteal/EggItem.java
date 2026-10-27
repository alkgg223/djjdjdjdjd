package com.eggsteal;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public class EggItem extends Item {
    public final Creature creature;

    public EggItem(Creature creature, Properties props) {
        super(props);
        this.creature = creature;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        Economy eco = Economy.get(sl.getServer());
        Economy.Stats st = eco.stats(p.getUUID());
        if (st.base < 0) return InteractionResult.FAIL;
        Vec3 home = Arena.home(st.base);
        if (Math.abs(p.getX() - home.x) > 13 || Math.abs(p.getZ() - home.z) > 13) {
            p.displayClientMessage(Component.literal("§cGo to your own base to hatch it!"), true);
            return InteractionResult.FAIL;
        }
        int idx = Arena.freeSlot(sl, st.base, st);
        if (idx < 0) {
            p.displayClientMessage(Component.literal("§cNo free slot! Buy more slots in the shop (/eggsteal shop)"), true);
            return InteractionResult.FAIL;
        }
        CreatureEntity ce = Arena.spawnCreature(sl, p.getUUID(), creature.ordinal(), Steal.mutOf(ctx.getItemInHand()), st.base, idx);
        java.util.UUID from = Steal.fromOf(ctx.getItemInHand());
        if (ce != null && from != null) ce.markStolen(from, sl.getGameTime() + Steal.recoverWindow(eco.map.get(from)));
        ctx.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
