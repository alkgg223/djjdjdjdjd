package com.vehicleworks.vehicle;

import com.vehicleworks.Config;
import com.vehicleworks.VehicleWorks;
import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.registry.ModSounds;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;

/**
 * Converts world blocks into a vehicle (assembly) and back (disassembly) without losing
 * block states or block entity contents.
 */
public final class VehicleAssembler {
    public static final TagKey<Block> EXCLUDED = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, "assembly_excluded"));
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_MOVE_BY_PISTON;

    public record Result(boolean ok, Component message, VehicleEntity vehicle) {}

    private VehicleAssembler() {}

    public static boolean canInclude(BlockState s) {
        if (s.isAir()) return false;
        if (!s.getFluidState().isEmpty() && s.getFluidState().isSource() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        if (s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        if (s.getBlock().defaultDestroyTime() < 0) return false; // bedrock, barriers…
        if (s.is(EXCLUDED)) return false;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.LEAVES) || s.is(Blocks.GRAVEL) || s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.NYLIUM) || s.is(Blocks.END_STONE)
                || s.is(BlockTags.SNOW) || s.is(Blocks.ICE) || s.is(BlockTags.ICE) || s.is(BlockTags.TERRACOTTA) && false) return false;
        if (s.canBeReplaced()) return false;
        return true;
    }

    public static Result assemble(ServerLevel level, BlockPos corePos) {
        BlockState core = level.getBlockState(corePos);
        if (!(core.getBlock() instanceof ComponentBlock cb) || cb.type != ComponentType.VEHICLE_CORE) {
            return new Result(false, Component.translatable("vehicleworks.msg.not_core"), null);
        }
        int maxBlocks = Config.SERVER.maxBlocks.get();
        int maxExtent = Config.SERVER.maxExtent.get();
        LongOpenHashSet found = new LongOpenHashSet();
        LongArrayList order = new LongArrayList();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(corePos);
        found.add(corePos.asLong());
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            order.add(p.asLong());
            if (order.size() > maxBlocks) {
                return new Result(false, Component.translatable("vehicleworks.msg.too_many", maxBlocks), null);
            }
            if (Math.abs(p.getX() - corePos.getX()) > maxExtent || Math.abs(p.getY() - corePos.getY()) > maxExtent || Math.abs(p.getZ() - corePos.getZ()) > maxExtent) {
                return new Result(false, Component.translatable("vehicleworks.msg.too_large", maxExtent), null);
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (found.contains(n.asLong())) continue;
                if (!level.isLoaded(n)) return new Result(false, Component.translatable("vehicleworks.msg.unloaded"), null);
                if (n.getY() < level.getMinBuildHeight() || n.getY() >= level.getMaxBuildHeight()) continue;
                BlockState ns = level.getBlockState(n);
                if (!canInclude(ns)) continue;
                found.add(n.asLong());
                queue.add(n);
            }
        }
        // snapshot
        VehicleStructure s = new VehicleStructure();
        for (long k : order) {
            BlockPos p = BlockPos.of(k);
            BlockState st = level.getBlockState(p);
            BlockEntity be = level.getBlockEntity(p);
            CompoundTag tag = be != null ? be.saveWithFullMetadata(level.registryAccess()) : null;
            s.put(BlockPos.asLong(p.getX() - corePos.getX(), p.getY() - corePos.getY(), p.getZ() - corePos.getZ()), st, tag);
        }
        // remove from the world without drops or block updates, then notify the surroundings
        for (long k : order) {
            BlockPos p = BlockPos.of(k);
            BlockEntity be = level.getBlockEntity(p);
            if (be != null) Clearable.tryClear(be);
            level.removeBlockEntity(p);
        }
        for (int i = order.size() - 1; i >= 0; i--) level.setBlock(BlockPos.of(order.getLong(i)), Blocks.AIR.defaultBlockState(), FLAGS);
        for (long k : order) {
            BlockPos p = BlockPos.of(k);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!found.contains(n.asLong())) level.neighborChanged(n, Blocks.AIR, p);
            }
        }
        VehicleEntity v = VehicleEntity.create(level, s, corePos);
        level.addFreshEntity(v);
        level.playSound(null, corePos, ModSounds.ASSEMBLE.get(), SoundSource.BLOCKS, 1f, 1f);
        return new Result(true, Component.translatable("vehicleworks.msg.assembled", s.size(), String.format("%.1f", v.layout().sim.mass() / 1000.0)), v);
    }

    /** Place the vehicle's blocks back into the world, snapped to the nearest 90° yaw. */
    public static Result disassemble(VehicleEntity v) {
        if (!(v.level() instanceof ServerLevel level)) return new Result(false, Component.empty(), null);
        if (v.linearVelocity.length() > 1.0 || v.angularVelocity.length() > 0.3) {
            return new Result(false, Component.translatable("vehicleworks.msg.moving"), null);
        }
        Vector3d up = v.localDirToWorld(0, 1, 0, new Vector3d());
        if (up.y < Math.cos(Math.toRadians(30))) return new Result(false, Component.translatable("vehicleworks.msg.not_level"), null);
        Vector3d fx = v.localDirToWorld(1, 0, 0, new Vector3d());
        double yaw = Math.atan2(fx.z, fx.x); // rotation of local +X around world Y
        int quarter = Math.floorMod((int) Math.round(yaw / (Math.PI / 2)), 4);
        Rotation rot = switch (quarter) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        Quaterniond snapped = new Quaterniond().rotateY(-quarter * Math.PI / 2);
        // world position of the local origin under the snapped orientation
        Vector3d originW = new Vector3d(-v.comLocal.x, -v.comLocal.y, -v.comLocal.z);
        v.orientation.transform(originW).add(v.getX(), v.getY(), v.getZ());
        BlockPos origin = BlockPos.containing(originW.x + 0.5, originW.y + 0.5, originW.z + 0.5);
        // validate targets
        int blocked = 0;
        java.util.List<long[]> placements = new java.util.ArrayList<>();
        for (var e : v.structure.blocks().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            Vector3d l = new Vector3d(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
            snapped.transform(l);
            BlockPos wp = origin.offset((int) Math.round(l.x), (int) Math.round(l.y), (int) Math.round(l.z));
            BlockState cur = level.getBlockState(wp);
            if (!(cur.isAir() || cur.canBeReplaced() || !cur.getFluidState().isEmpty())) blocked++;
            placements.add(new long[]{k, wp.asLong()});
        }
        if (blocked > 0) return new Result(false, Component.translatable("vehicleworks.msg.blocked", blocked), null);
        v.writeRuntimeToStructure();
        v.ejectPassengers();
        for (long[] pl : placements) {
            BlockState st = v.structure.get(pl[0]).rotate(rot);
            level.setBlock(BlockPos.of(pl[1]), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (long[] pl : placements) {
            CompoundTag tag = v.structure.blockEntity(pl[0]);
            if (tag == null) continue;
            BlockPos wp = BlockPos.of(pl[1]);
            BlockEntity be = level.getBlockEntity(wp);
            if (be == null) continue;
            CompoundTag t = tag.copy();
            t.putInt("x", wp.getX()); t.putInt("y", wp.getY()); t.putInt("z", wp.getZ());
            if (rot != Rotation.NONE) rotateComponentData(t, rot);
            try {
                be.loadWithComponents(t, level.registryAccess());
                be.setChanged();
            } catch (Exception ex) {
                VehicleWorks.LOGGER.warn("Failed to restore block entity at {}", wp, ex);
            }
        }
        for (long[] pl : placements) {
            BlockPos wp = BlockPos.of(pl[1]);
            level.blockUpdated(wp, level.getBlockState(wp).getBlock());
        }
        level.playSound(null, origin, ModSounds.ASSEMBLE.get(), SoundSource.BLOCKS, 1f, 0.8f);
        v.discard();
        return new Result(true, Component.translatable("vehicleworks.msg.disassembled", placements.size()), null);
    }

    /** Rotate stored link offsets of remote control units. */
    private static void rotateComponentData(CompoundTag beTag, Rotation rot) {
        CompoundTag data = beTag.getCompound("Data");
        if (!data.contains("Links")) return;
        var links = data.getList("Links", 10);
        for (int i = 0; i < links.size(); i++) {
            CompoundTag l = links.getCompound(i);
            int[] o = l.getIntArray("Off");
            if (o.length != 3) continue;
            BlockPos r = new BlockPos(o[0], o[1], o[2]).rotate(rot);
            l.putIntArray("Off", new int[]{r.getX(), r.getY(), r.getZ()});
        }
        beTag.put("Data", data);
    }
}
