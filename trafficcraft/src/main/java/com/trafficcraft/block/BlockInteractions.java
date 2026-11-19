package com.trafficcraft.block;

import com.trafficcraft.net.OpenEditorPayload;
import com.trafficcraft.road.RoadData;
import com.trafficcraft.road.RoadEditing;
import com.trafficcraft.road.RoadNode;
import com.trafficcraft.station.FuelStations;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BlockInteractions {
    public static InteractionResult use(String kind, BlockState s, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        switch (kind) {
            case "text_sign" -> {
                if (level.getBlockEntity(pos) instanceof GenericBE be) {
                    CompoundTag t = be.data.copy();
                    t.putLong("pos", pos.asLong());
                    PacketDistributor.sendToPlayer(sp, new OpenEditorPayload("sign", t));
                }
            }
            case "barrier_gate" -> {
                if (level.getBlockEntity(pos) instanceof BarrierGateBE g) {
                    if (player.isShiftKeyDown()) {
                        if (!g.manual) g.manual = true;
                        else {
                            g.length = g.length >= 8 ? 3 : g.length + 1;
                            g.removeArm();
                        }
                        player.displayClientMessage(Component.translatable("msg.trafficcraft.barrier_cfg", g.manual ? "manual" : "auto", g.length), true);
                        g.sync();
                    } else {
                        g.setOpen(!g.open, g.manual ? 0 : 100);
                    }
                }
            }
            case "fuel_pump" -> FuelStations.usePump(sp, pos);
            case "terminal" -> FuelStations.useTerminal(sp, pos);
            case "workplace_cashier", "workplace_officer" -> com.trafficcraft.npc.Workplaces.use(sp, pos, kind.substring(10));
            case "control_panel" -> {
                if (!RoadEditing.canEdit(player)) {
                    player.displayClientMessage(Component.translatable("msg.trafficcraft.no_permission"), true);
                    break;
                }
                CompoundTag t = new CompoundTag();
                ListTag list = new ListTag();
                for (RoadNode n : RoadData.get((ServerLevel) level).nodes.values()) if (n.has(RoadNode.F_LIGHTS)) list.add(n.save());
                t.put("nodes", list);
                PacketDistributor.sendToPlayer(sp, new OpenEditorPayload("lights", t));
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.CONSUME;
    }
}
