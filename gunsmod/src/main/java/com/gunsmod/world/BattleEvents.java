package com.gunsmod.world;

import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.net.Net;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The score of the battlefields, the players' sides and what their screens show. */
public final class BattleEvents {
    private BattleEvents() {}

    /** every second: each player's battle state (score, side, on the field) */
    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, false);
    }

    public static void send(ServerPlayer p, boolean openPicker) {
        BattleData d = BattleData.get(p.serverLevel());
        boolean field = !d.maps.isEmpty(), chosen = PlayerTeam.chosen(p);
        if (!field && !chosen && !openPicker) return;
        PacketDistributor.sendToPlayer(p, new Net.BattleState(d.score(BotTeam.GREEN), d.score(BotTeam.RED), chosen ? PlayerTeam.get(p).ordinal() : -1,
                field, d.at(p.position()) != null, openPicker));
    }

    /** a player on a battlefield who has not picked a side yet is asked to */
    public static void offerPicker(ServerPlayer p) {
        if (!PlayerTeam.chosen(p) && BattleData.get(p.serverLevel()).at(p.position()) != null) send(p, true);
    }

    /** a soldier or a player killed by the other side on a battlefield scores a kill for that side */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!(v instanceof BotEntity || v instanceof Player) || !(v.level() instanceof ServerLevel level)) return;
        BattleData d = BattleData.get(level);
        if (d.at(v.position()) == null) return;
        DamageSource src = e.getSource();
        BotTeam victim = BotTeam.of(v), killer = src.getEntity() != null ? BotTeam.of(src.getEntity()) : null;
        if (killer == null && src.getDirectEntity() != null) killer = BotTeam.of(src.getDirectEntity());
        if (victim == null || killer == null || killer == victim) return;
        d.addKill(killer);
        for (ServerPlayer p : level.players()) send(p, false);
    }

    /** back at the team's base (the respawn point set when joining) with a fresh gun */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer p) || !PlayerTeam.chosen(p)) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (PlayerTeam.isKit(inv.getItem(i))) return;
        PlayerTeam.giveKit(p);
    }

    /** the handed out gun is not dropped on death: the field would fill up with guns */
    public static void onDrops(LivingDropsEvent e) {
        if (e.getEntity() instanceof Player) e.getDrops().removeIf(i -> PlayerTeam.isKit(i.getItem()));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) offerPicker(p);
    }
}
