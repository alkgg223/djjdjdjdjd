package com.trafficcraft.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Workplace blocks (cashier counter, border booth desk). Full NPC/role logic lives in the NPC package. */
public final class Workplaces {
    public static void use(ServerPlayer p, BlockPos pos, String role) {
        p.displayClientMessage(Component.translatable("msg.trafficcraft.workplace", role), true);
    }
}
