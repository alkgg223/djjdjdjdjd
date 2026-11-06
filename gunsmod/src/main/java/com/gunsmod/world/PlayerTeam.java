package com.gunsmod.world;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.GunItem;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * A player's side on the orchard battlefields. A player who has not picked one fights with the green side as
 * before; one who has is a team mate of that side's soldiers, gets a random gun with random attachments and endless
 * ammunition, and comes back at the team's base after dying with a fresh gun.
 * Kept in the player's persisted data, which survives death.
 */
public final class PlayerTeam {
    private PlayerTeam() {}

    /** NeoForge copies this part of a player's persistent data to the respawned player */
    private static final String KEEP = "PlayerPersisted", MOD = "gunsmod", TEAM = "Team", KIT = "gunsmod_kit";

    private static CompoundTag data(Player p, boolean create) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEEP)) {
            if (!create) return new CompoundTag();
            root.put(KEEP, new CompoundTag());
        }
        CompoundTag keep = root.getCompound(KEEP);
        if (!keep.contains(MOD)) {
            if (!create) return new CompoundTag();
            keep.put(MOD, new CompoundTag());
        }
        return keep.getCompound(MOD);
    }

    public static boolean chosen(Player p) {
        return data(p, false).contains(TEAM);
    }

    /** the player's side: the chosen one, green otherwise */
    public static BotTeam get(Player p) {
        CompoundTag d = data(p, false);
        return d.contains(TEAM) ? BotTeam.byId(d.getInt(TEAM)) : BotTeam.GREEN;
    }

    private static void set(Player p, @Nullable BotTeam t) {
        CompoundTag d = data(p, true);
        if (t == null) d.remove(TEAM);
        else d.putInt(TEAM, t.ordinal());
    }

    /** a player in the battle never runs out of ammunition */
    public static boolean freeAmmo(Player p) {
        return p.getAbilities().instabuild || chosen(p);
    }

    public static boolean isKit(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBoolean(KIT);
    }

    /** join a side (0 green, 1 red) on the nearest battlefield, or leave the battle (-1) */
    public static void pick(ServerPlayer p, int team) {
        ServerLevel level = p.serverLevel();
        if (team < 0) {
            if (chosen(p)) p.displayClientMessage(Component.translatable("gunsmod.msg.team_left"), false);
            set(p, null);
            BattleEvents.send(p, false);
            return;
        }
        BattleMap map = BattleData.get(level).nearest(p.position());
        if (map == null) {
            p.displayClientMessage(Component.translatable("gunsmod.msg.no_battlefield"), false);
            return;
        }
        BotTeam t = BotTeam.byId(team);
        set(p, t);
        if (p.isPassenger()) p.stopRiding();
        Vec3 s = map.spawn(t);
        p.setRespawnPosition(level.dimension(), BlockPos.containing(s), map.yaw(t), true, false);
        p.teleportTo(s.x, s.y, s.z);
        giveKit(p);
        p.displayClientMessage(Component.translatable("gunsmod.msg.team_joined", Component.translatable("gunsmod.team." + t.name().toLowerCase()).withStyle(t.chat)), false);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1f, 1f);
        BattleEvents.send(p, false);
    }

    /** a random gun with random attachments and a full magazine, in place of the last one handed out */
    public static void giveKit(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isKit(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        RandomSource r = p.getRandom();
        GunType[] all = GunType.values();
        GunType g = all[r.nextInt(all.length)];
        ItemStack gun = GunItem.create(g);
        gun.set(Reg.ATTACHMENTS.get(), BotEntity.randomAttachments(g, r));
        GunItem.setAmmo(gun, GunItem.attachments(gun).magSize(g));
        CompoundTag t = new CompoundTag();
        t.putBoolean(KIT, true);
        gun.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        int slot = inv.selected;
        if (inv.getItem(slot).isEmpty()) inv.setItem(slot, gun);
        else if (!inv.add(gun)) p.drop(gun, false);
        p.displayClientMessage(Component.translatable("gunsmod.msg.kit", gun.getHoverName()), true);
    }
}
