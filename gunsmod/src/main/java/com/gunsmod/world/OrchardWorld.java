package com.gunsmod.world;

import com.gunsmod.GunsMod;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The "Orchard Battlefield" world type: a flat world of the orchard biome (data/gunsmod/worldgen/world_preset). On
 * the first start of such a world the battlefield is laid out round the origin, then everybody is sent to the green
 * base. A lodestone just above the bedrock under the centre marks the world as built.
 */
public final class OrchardWorld {
    private OrchardWorld() {}

    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(GunsMod.MODID, "orchard"));
    /** top of the flat world: bedrock, 124 stone, 3 dirt, grass at y = 64 */
    public static final int GROUND = 64;
    private static final BlockPos MARKER = new BlockPos(0, -63, 0);
    private static WeakReference<MinecraftServer> checked = new WeakReference<>(null);

    /** server tick: once per world, see whether it is an orchard battlefield still to be built */
    public static void tick(MinecraftServer server) {
        if (checked.get() == server) return;
        checked = new WeakReference<>(server);
        ServerLevel level = server.overworld();
        BlockPos centre = new BlockPos(0, GROUND, 0);
        if (!level.getBiome(centre).is(BIOME) || level.getBlockState(MARKER).is(Blocks.LODESTONE)) return;
        OrchardBuilder.startWorld(level, centre, Direction.SOUTH);
    }

    /** the field is ready: mark the world, put the world spawn in the green base and send everybody there */
    static void built(ServerLevel level, BattleMap map) {
        level.setBlock(MARKER, Blocks.LODESTONE.defaultBlockState(), 3);
        Vec3 s = map.world(-8, -BattleMap.HL + 44);
        BlockPos spawn = BlockPos.containing(s);
        float yaw = map.fwd().toYRot();
        boolean worldSpawn = true;
        try {
            level.setDefaultSpawnPos(spawn, yaw);
        } catch (LinkageError e) {
            worldSpawn = false;
        }
        for (ServerPlayer p : level.players()) {
            if (!worldSpawn) p.setRespawnPosition(Level.OVERWORLD, spawn, yaw, true, false);
            p.teleportTo(s.x, s.y, s.z);
            p.displayClientMessage(Component.translatable("gunsmod.msg.orchard_world"), false);
        }
    }
}
