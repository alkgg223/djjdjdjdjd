package com.gunsmod;

import com.gunsmod.block.ArenaBlockEntity;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@Mod(GunsMod.MODID)
public class GunsMod {
    public static final String MODID = "gunsmod";

    public GunsMod(IEventBus modBus) {
        Reg.register(modBus);
        modBus.addListener(GunsMod::attributes);
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.addListener(GunsMod::onDeath);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(Reg.BOT.get(), BotEntity.createAttributes().build());
        e.put(Reg.JEEP.get(), JeepEntity.createAttributes().build());
    }

    /** a player that dies during an arena round is out of that round */
    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ServerLevel level = sp.serverLevel();
        BlockPos center = sp.blockPosition();
        int r = 4;
        for (int cx = -r; cx <= r; cx++) {
            for (int cz = -r; cz <= r; cz++) {
                var chunk = level.getChunkSource().getChunkNow((center.getX() >> 4) + cx, (center.getZ() >> 4) + cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ArenaBlockEntity arena && arena.hasPlayer(sp.getUUID())) arena.onPlayerDied(sp);
                }
            }
        }
    }
}
