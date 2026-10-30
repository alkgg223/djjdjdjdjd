package com.gunsmod.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public enum BotTeam {
    GREEN(0x5BBF4A, ChatFormatting.GREEN, 0.42f, 0.50f, 0.30f),
    RED(0xD9483B, ChatFormatting.RED, 0.78f, 0.67f, 0.48f);

    public final int color;
    public final ChatFormatting chat;
    /** paint tint used on vehicles / gear */
    public final float r, g, b;

    BotTeam(int color, ChatFormatting chat, float r, float g, float b) {
        this.color = color;
        this.chat = chat;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public BotTeam enemy() {
        return this == GREEN ? RED : GREEN;
    }

    public static BotTeam byId(int id) {
        return id == 1 ? RED : GREEN;
    }

    /** team of any entity: soldiers, vehicles and mines carry one, players the side they chose (green unless they chose), everything else is neutral */
    public static BotTeam of(Entity e) {
        if (e instanceof BotEntity b) return b.getBotTeam();
        if (e instanceof JeepEntity j) return j.getBotTeam();
        if (e instanceof VehicleEntity v) return v.getBotTeam();
        if (e instanceof MineEntity m) return m.getMineTeam();
        if (e instanceof Player p) return com.gunsmod.world.PlayerTeam.get(p);
        return null;
    }

    /** true when a bullet from {@code shooter} should pass through {@code target} */
    public static boolean friendly(Entity shooter, Entity target) {
        if (shooter == null || target == null) return false;
        if (shooter instanceof Player pa && target instanceof Player pb) {
            // players are only team mates once both have picked a side
            return com.gunsmod.world.PlayerTeam.chosen(pa) && com.gunsmod.world.PlayerTeam.chosen(pb)
                    && com.gunsmod.world.PlayerTeam.get(pa) == com.gunsmod.world.PlayerTeam.get(pb);
        }
        BotTeam a = of(shooter), b = of(target);
        if (a == null || b == null || a != b) return false;
        // a jeep belongs to whoever is inside: never let team mates shoot their own ride
        return true;
    }
}
