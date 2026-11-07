package com.arkragnarok.client.mesh;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * Creature skins ship as JPEG (full colour at a quarter of the PNG size). Minecraft's own texture loader only accepts PNG, so a
 * ".jpg" location is registered once with a texture that decodes it through stb_image - the same native library Minecraft
 * uses for its PNGs, present on every platform including Android launchers. A failure leaves the missing texture, never a
 * crash.
 */
public final class JpegTextures {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<ResourceLocation> REGISTERED = new HashSet<>();

    private JpegTextures() {}

    /** Call on the render thread before the location is bound; returns it unchanged. */
    public static ResourceLocation ensure(ResourceLocation loc) {
        if (REGISTERED.contains(loc) || !loc.getPath().endsWith(".jpg")) return loc;
        REGISTERED.add(loc);
        Minecraft.getInstance().getTextureManager().register(loc, new Jpeg(loc));
        return loc;
    }

    private static final class Jpeg extends AbstractTexture {
        private final ResourceLocation file;

        Jpeg(ResourceLocation file) {
            this.file = file;
        }

        @Override
        public void load(ResourceManager resources) throws IOException {
            NativeImage img = decode(resources, file);
            if (RenderSystem.isOnRenderThreadOrInit()) upload(img);
            else RenderSystem.recordRenderCall(() -> upload(img));
        }

        private void upload(NativeImage img) {
            TextureUtil.prepareImage(getId(), 0, img.getWidth(), img.getHeight());
            img.upload(0, 0, 0, 0, 0, img.getWidth(), img.getHeight(), false, false, false, true);
        }
    }

    static NativeImage decode(ResourceManager resources, ResourceLocation loc) throws IOException {
        byte[] data;
        try (InputStream in = resources.open(loc)) {
            data = in.readAllBytes();
        }
        ByteBuffer src = MemoryUtil.memAlloc(data.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            src.put(data).flip();
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            ByteBuffer px = STBImage.stbi_load_from_memory(src, w, h, c, 4);
            if (px == null) throw new IOException("Could not decode " + loc + ": " + STBImage.stbi_failure_reason());
            try {
                int width = w.get(0), height = h.get(0);
                NativeImage img = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                long addr = MemoryUtil.memAddress(px);
                // stb gives R,G,B,A bytes; read as a native (little-endian) int that is the ABGR NativeImage stores
                for (int y = 0; y < height; y++) {
                    long row = addr + (long) y * width * 4L;
                    for (int x = 0; x < width; x++) img.setPixelRGBA(x, y, MemoryUtil.memGetInt(row + x * 4L));
                }
                return img;
            } finally {
                STBImage.stbi_image_free(px);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            LOG.error("ARK: could not load {}", loc, t);
            throw new IOException("Could not decode " + loc, t);
        } finally {
            MemoryUtil.memFree(src);
        }
    }
}
