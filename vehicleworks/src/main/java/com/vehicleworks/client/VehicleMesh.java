package com.vehicleworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.vehicleworks.VehicleWorks;
import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.vehicle.VehicleEntity;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cached GPU geometry of a vehicle. Blocks are tessellated once (off the render thread)
 * into one vertex buffer per render layer with internal faces culled; afterwards the whole
 * vehicle is drawn with a single model-view transform per layer per frame. A coarse
 * silhouette mesh (2×2×2 clusters, averaged colours) is used at long distance.
 */
public final class VehicleMesh implements AutoCloseable {
    public static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(), RenderType.translucent()};

    private record Built(Map<RenderType, MeshData> meshes, MeshData lod, List<ByteBufferBuilder> buffers, int version, int light) {}

    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();
    private VertexBuffer lod;
    private int version = Integer.MIN_VALUE;
    private int light = -1;
    private long lastBuild;
    private CompletableFuture<Built> pending;
    public int vertexCount;

    /** Rebuild when the structure or the ambient light changed. Must be called on the render thread. */
    public void update(VehicleEntity v) {
        if (pending != null) {
            if (!pending.isDone()) return;
            Built b = null;
            try {
                b = pending.getNow(null);
            } catch (Exception e) {
                VehicleWorks.LOGGER.error("Vehicle mesh build failed", e);
            }
            pending = null;
            if (b != null) upload(b);
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos center = BlockPos.containing(v.getX(), v.getBoundingBox().maxY + 0.5, v.getZ());
        int sky = level.getBrightness(LightLayer.SKY, center);
        int blk = level.getBrightness(LightLayer.BLOCK, center);
        int lightKey = sky * 16 + blk;
        long now = Util.getMillis();
        boolean structureChanged = v.structure.version != version;
        boolean lightChanged = Math.abs((lightKey >> 4) - (light >> 4)) >= 2 || Math.abs((lightKey & 15) - (light & 15)) >= 2;
        if (structureChanged || (lightChanged && now - lastBuild > 1500)) {
            lastBuild = now;
            Long2ObjectOpenHashMap<BlockState> snapshot = new Long2ObjectOpenHashMap<>(v.structure.blocks());
            Biome biome = level.getBiome(center).value();
            int ver = v.structure.version;
            boolean big = snapshot.size() > 600;
            if (big) pending = CompletableFuture.supplyAsync(() -> build(snapshot, sky, blk, biome, ver, lightKey), Util.backgroundExecutor());
            else upload(build(snapshot, sky, blk, biome, ver, lightKey));
        }
    }

    private static Built build(Long2ObjectOpenHashMap<BlockState> blocks, int sky, int blk, Biome biome, int version, int lightKey) {
        VehicleBlockView view = new VehicleBlockView(blocks, sky, blk, biome);
        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        Map<RenderType, BufferBuilder> builders = new HashMap<>();
        List<ByteBufferBuilder> memory = new ArrayList<>();
        RandomSource rand = RandomSource.create();
        PoseStack ps = new PoseStack();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (var e : blocks.long2ObjectEntrySet()) {
            BlockState st = e.getValue();
            if (st.getRenderShape() != RenderShape.MODEL) continue;
            if (st.getBlock() instanceof ComponentBlock cb && cb.type.isDynamicRender()) continue;
            long k = e.getLongKey();
            mp.set(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
            BakedModel model = brd.getBlockModel(st);
            rand.setSeed(st.getSeed(mp));
            ps.pushPose();
            ps.translate(mp.getX(), mp.getY(), mp.getZ());
            for (RenderType rt : model.getRenderTypes(st, rand, ModelData.EMPTY)) {
                RenderType layer = normalize(rt);
                BufferBuilder bb = builders.computeIfAbsent(layer, r -> {
                    ByteBufferBuilder mem = new ByteBufferBuilder(Math.max(4096, blocks.size() * 512));
                    memory.add(mem);
                    return new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                });
                try {
                    brd.renderBatched(st, mp.immutable(), view, ps, bb, true, rand, ModelData.EMPTY, rt);
                } catch (Throwable t) {
                    // a misbehaving modded model must not break the whole vehicle
                }
            }
            ps.popPose();
        }
        Map<RenderType, MeshData> meshes = new HashMap<>();
        for (var e : builders.entrySet()) {
            MeshData md = e.getValue().build();
            if (md != null) meshes.put(e.getKey(), md);
        }
        ByteBufferBuilder lodMem = new ByteBufferBuilder(Math.max(4096, blocks.size() * 96));
        memory.add(lodMem);
        MeshData lod = buildLod(blocks, view, new BufferBuilder(lodMem, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK), LightTexture.pack(blk, sky));
        return new Built(meshes, lod, memory, version, lightKey);
    }

    private static RenderType normalize(RenderType rt) {
        for (RenderType l : LAYERS) if (l == rt) return rt;
        return rt == RenderType.tripwire() ? RenderType.translucent() : RenderType.cutout();
    }

    private static MeshData buildLod(Long2ObjectOpenHashMap<BlockState> blocks, VehicleBlockView view, BufferBuilder bb, int light) {
        // cluster 2x2x2, average map colour
        Long2IntOpenHashMap color = new Long2IntOpenHashMap();
        Long2IntOpenHashMap count = new Long2IntOpenHashMap();
        int[] rgb = new int[3];
        Map<Long, int[]> sums = new HashMap<>();
        for (var e : blocks.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            int cx = Math.floorDiv(BlockPos.getX(k), 2), cy = Math.floorDiv(BlockPos.getY(k), 2), cz = Math.floorDiv(BlockPos.getZ(k), 2);
            long ck = BlockPos.asLong(cx, cy, cz);
            int c = e.getValue().getMapColor(view, BlockPos.of(k)).col;
            int[] s = sums.computeIfAbsent(ck, x -> new int[4]);
            s[0] += (c >> 16) & 255; s[1] += (c >> 8) & 255; s[2] += c & 255; s[3]++;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.WHITE_CONCRETE.defaultBlockState()).getParticleIcon(ModelData.EMPTY);
        float u = (sprite.getU0() + sprite.getU1()) * 0.5f, v = (sprite.getV0() + sprite.getV1()) * 0.5f;
        boolean any = false;
        for (var e : sums.entrySet()) {
            long ck = e.getKey();
            int[] s = e.getValue();
            int col = 0xFF000000 | ((s[0] / s[3]) << 16) | ((s[1] / s[3]) << 8) | (s[2] / s[3]);
            int x0 = BlockPos.getX(ck) * 2, y0 = BlockPos.getY(ck) * 2, z0 = BlockPos.getZ(ck) * 2;
            for (Direction d : Direction.values()) {
                if (sums.containsKey(BlockPos.offset(ck, d))) continue;
                float shade = view.getShade(d, true);
                int r = (int) (((col >> 16) & 255) * shade), g = (int) (((col >> 8) & 255) * shade), b = (int) ((col & 255) * shade);
                int c = 0xFF000000 | (r << 16) | (g << 8) | b;
                quad(bb, d, x0, y0, z0, 2, c, u, v, light);
                any = true;
            }
        }
        return any ? bb.build() : null;
    }

    private static void quad(BufferBuilder bb, Direction d, float x, float y, float z, float s, int c, float u, float v, int light) {
        float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
        float[][] p = switch (d) {
            case UP -> new float[][]{{x, y + s, z}, {x, y + s, z + s}, {x + s, y + s, z + s}, {x + s, y + s, z}};
            case DOWN -> new float[][]{{x, y, z}, {x + s, y, z}, {x + s, y, z + s}, {x, y, z + s}};
            case NORTH -> new float[][]{{x, y, z}, {x, y + s, z}, {x + s, y + s, z}, {x + s, y, z}};
            case SOUTH -> new float[][]{{x, y, z + s}, {x + s, y, z + s}, {x + s, y + s, z + s}, {x, y + s, z + s}};
            case WEST -> new float[][]{{x, y, z}, {x, y, z + s}, {x, y + s, z + s}, {x, y + s, z}};
            case EAST -> new float[][]{{x + s, y, z}, {x + s, y + s, z}, {x + s, y + s, z + s}, {x + s, y, z + s}};
        };
        for (float[] q : p) bb.addVertex(q[0], q[1], q[2], c, u, v, 0, light, nx, ny, nz);
    }

    private void upload(Built b) {
        closeBuffers();
        vertexCount = 0;
        for (var e : b.meshes.entrySet()) {
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vertexCount += e.getValue().drawState().vertexCount();
            vb.bind();
            vb.upload(e.getValue());
            VertexBuffer.unbind();
            buffers.put(e.getKey(), vb);
        }
        if (b.lod != null) {
            lod = new VertexBuffer(VertexBuffer.Usage.STATIC);
            lod.bind();
            lod.upload(b.lod);
            VertexBuffer.unbind();
        }
        for (ByteBufferBuilder m : b.buffers) m.close();
        version = b.version;
        light = b.light;
    }

    public boolean ready() {
        return version != Integer.MIN_VALUE;
    }

    /** Draw one render layer with the given model-view matrix. */
    public void draw(RenderType layer, Matrix4f modelView, Matrix4f projection, boolean useLod) {
        VertexBuffer vb = useLod ? (layer == RenderType.solid() ? lod : null) : buffers.get(layer);
        if (vb == null || vb.isInvalid()) return;
        layer.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0f, 0f, 0f);
            vb.bind();
            vb.drawWithShader(modelView, projection, shader);
            VertexBuffer.unbind();
        }
        layer.clearRenderState();
    }

    private void closeBuffers() {
        for (VertexBuffer vb : buffers.values()) vb.close();
        buffers.clear();
        if (lod != null) lod.close();
        lod = null;
    }

    @Override
    public void close() {
        closeBuffers();
        if (pending != null) {
            pending.thenAccept(b -> RenderSystem.recordRenderCall(() -> b.buffers.forEach(ByteBufferBuilder::close)));
            pending = null;
        }
    }
}
