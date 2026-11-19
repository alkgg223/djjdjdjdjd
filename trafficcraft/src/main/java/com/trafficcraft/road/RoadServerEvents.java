package com.trafficcraft.road;

import com.trafficcraft.TrafficCraft;
import com.trafficcraft.net.RoadSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TrafficCraft.MODID)
public final class RoadServerEvents {
    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        RoadNetwork net = RoadData.get(level);
        if (net.dirty) {
            net.dirty = false;
            RoadData.data(level).setDirty();
            RoadSyncPayload p = new RoadSyncPayload(level.dimension().location().toString(), net.save());
            for (ServerPlayer sp : level.players()) PacketDistributor.sendToPlayer(sp, p);
        }
        for (ServerPlayer sp : level.players()) if (sp.tickCount == 20 || sp.tickCount == 100) send(sp); // late re-sync after join
        com.trafficcraft.ai.TrafficManager.tick(level, net);
    }

    public static void send(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        PacketDistributor.sendToPlayer(sp, new RoadSyncPayload(level.dimension().location().toString(), RoadData.get(level).save()));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) send(sp);
    }

    @SubscribeEvent
    public static void dim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) send(sp);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) send(sp);
    }
}
