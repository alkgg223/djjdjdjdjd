package com.gunsmod;

import com.gunsmod.block.ArenaBlockEntity;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.TankEntity;
import com.gunsmod.entity.VehicleKind;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.net.Net;
import com.gunsmod.world.OrchardBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(GunsMod.MODID)
public class GunsMod {
    public static final String MODID = "gunsmod";

    public GunsMod(IEventBus modBus) {
        Reg.register(modBus);
        modBus.addListener(GunsMod::attributes);
        modBus.addListener(Net::register);
        NeoForge.EVENT_BUS.addListener(GunsMod::onDeath);
        NeoForge.EVENT_BUS.addListener(GunsMod::crewProtection);
        NeoForge.EVENT_BUS.addListener(GunsMod::serverTick);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(Reg.BOT.get(), BotEntity.createAttributes().build());
        e.put(Reg.JEEP.get(), JeepEntity.createAttributes().build());
        e.put(Reg.JEEP_TOW.get(), JeepEntity.createAttributes().build());
        for (VehicleKind k : VehicleKind.values()) {
            switch (k.group) {
                case TANK -> e.put(k.type(), TankEntity.createAttributes(k.tank()).build());
                case HELI -> e.put(k.type(), HelicopterEntity.createAttributes(k.heli()).build());
                case CAR -> e.put(k.type(), ArmoredCarEntity.createAttributes(k.car()).build());
                case JET -> e.put(k.type(), JetEntity.createAttributes().build());
                default -> { }
            }
        }
    }

    /** big battlefield jobs build a few rows every tick */
    private static void serverTick(ServerTickEvent.Post e) {
        OrchardBuilder.tick();
    }

    /** the crew of any vehicle (jeep included) is protected: hits go to the vehicle until it blows up */
    private static void crewProtection(LivingIncomingDamageEvent e) {
        if (VehicleEntity.shieldOf(e.getEntity()) == null) return;
        var src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypes.DROWN) || src.is(DamageTypes.STARVE)) return;
        e.setCanceled(true);
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
