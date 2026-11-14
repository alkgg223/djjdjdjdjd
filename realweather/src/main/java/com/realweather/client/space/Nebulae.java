package com.realweather.client.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 25 nebulae, each built in 3D from a few pieces of glowing gas and dark dust (clouds, shells, rings, pillars,
 * filaments, lanes), with the turbulence of real gas worked into every one by the ray-marching shader: Orion's
 * glowing wings round the Trapezium, the Crab's net of filaments round its pulsar, the Pillars of Creation, the dark
 * horse's head against its red curtain, the planetary nebulae's shells and rings round their white dwarfs. Lengths
 * are in the nebula's own radius; x to the right, y up on the sky, z towards us.
 */
final class Nebulae {
    static final int BLOB = 0, SHELL = 1, TORUS = 2, PILLAR = 3, FILAMENT = 4, SLAB = 5;

    /** One piece: kind, centre, size, a parameter (shell thickness, ring width), colour and density (negative is dust). */
    record Piece(int kind, float cx, float cy, float cz, float sx, float sy, float sz, float param, float r, float g, float b, float dens,
                 float rot, float tilt, float fine, float turb) {
        float[] matrix() {
            return new float[]{kind, cx, cy, cz, sx, sy, sz, param, r, g, b, dens, rot, tilt, fine, turb};
        }
    }

    /** A star or cluster inside: where, how spread, how many, how bright, its colour. */
    record Stars(float x, float y, float z, float spread, int count, float bright, float r, float g, float b) {}

    static final class Recipe {
        final String id;
        final List<Piece> pieces = new ArrayList<>();
        final List<Stars> stars = new ArrayList<>();
        float exposure = 1.2F;
        /** Average colour from far off. */
        float r = 1F, g = 0.4F, b = 0.45F;

        Recipe(String id) {
            this.id = id;
        }

        Recipe blob(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b, float dens, float rot, float turb) {
            pieces.add(new Piece(BLOB, x, y, z, sx, sy, sz, 0, r, g, b, dens, rot, 0, 0, turb));
            return this;
        }

        Recipe shell(float x, float y, float z, float sx, float sy, float sz, float thick, float r, float g, float b, float dens, float rot, float turb) {
            pieces.add(new Piece(SHELL, x, y, z, sx, sy, sz, thick, r, g, b, dens, rot, 0, 0, turb));
            return this;
        }

        Recipe torus(float x, float y, float z, float sx, float sy, float sz, float width, float r, float g, float b, float dens, float rot, float tilt,
                     float turb) {
            pieces.add(new Piece(TORUS, x, y, z, sx, sy, sz, width, r, g, b, dens, rot, tilt, 0, turb));
            return this;
        }

        Recipe pillar(float x, float y, float z, float radius, float height, float r, float g, float b, float dens, float rot, float turb) {
            pieces.add(new Piece(PILLAR, x, y, z, radius, height, radius, 0, r, g, b, dens, rot, 0, 1, turb));
            return this;
        }

        Recipe filaments(float x, float y, float z, float sx, float sy, float sz, float thick, float r, float g, float b, float dens, float rot) {
            return filaments(x, y, z, sx, sy, sz, thick, r, g, b, dens, rot, 0.6F);
        }

        Recipe filaments(float x, float y, float z, float sx, float sy, float sz, float thick, float r, float g, float b, float dens, float rot,
                         float turb) {
            pieces.add(new Piece(FILAMENT, x, y, z, sx, sy, sz, thick, r, g, b, dens, rot, 0, 1, turb));
            return this;
        }

        Recipe slab(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b, float dens, float rot, float tilt, float turb) {
            pieces.add(new Piece(SLAB, x, y, z, sx, sy, sz, 0, r, g, b, dens, rot, tilt, 0, turb));
            return this;
        }

        Recipe stars(float x, float y, float z, float spread, int count, float bright, float r, float g, float b) {
            stars.add(new Stars(x, y, z, spread, count, bright, r, g, b));
            return this;
        }

        Recipe look(float r, float g, float b, float exposure) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.exposure = exposure;
            return this;
        }
    }

    static final Map<String, Recipe> ALL = new HashMap<>();

    private static Recipe add(String id) {
        Recipe r = new Recipe(id);
        ALL.put(id, r);
        return r;
    }

    static Recipe get(String id) {
        return ALL.get(id);
    }

    /** A nebula as it is at a moment (the supernova changes as you watch; the rest are as they are). */
    static Recipe at(String id, double seconds) {
        if ("sn1987a".equals(id)) {
            return supernova(seconds);
        }
        return get(id);
    }

    private static final Recipe SN = new Recipe("sn1987a").look(0.8F, 0.75F, 1F, 1.4F);
    private static final double SN_CYCLE = 110, SN_BANG = 10;

    /** How far through its explosion the supernova is: 0 before, then its shock front's radius (nebula radii). */
    static double shock(double seconds) {
        double s = seconds % SN_CYCLE - SN_BANG;
        return s < 0 ? 0 : 0.04 + 0.96 * (1 - Math.exp(-s / 24));
    }

    /**
     * SN 1987A, again and again: the blue supergiant inside its ring of old gas (and two fainter rings above and
     * below, an hourglass), the blast, and its shock front: a turbulent shell racing out at a tenth of the speed of
     * light, hot and blue-white at first then cooling to orange, with the debris and new dust behind it; when the
     * shock reaches the ring it lights up into a string of glowing hot spots.
     */
    static Recipe supernova(double seconds) {
        double s = seconds % SN_CYCLE;
        double after = s - SN_BANG;
        float fade = (float) Math.min(1, Math.min(s / 3, (SN_CYCLE - s) / 8));
        SN.pieces.clear();
        SN.stars.clear();
        double sh = shock(seconds);
        double hit = Math.max(0, Math.min(1, (sh - 0.5) / 0.08));
        float ring = (float) (0.3 + (after > 0 ? 3.2 * hit * Math.exp(-Math.max(0, after - 30) / 40) : 0)) * fade;
        float tilt = 1.1F;
        SN.torus(0, 0, 0, 0.55F, 0.55F, 0.55F, 0.07F, 1F, 0.55F, 0.5F, ring, 0F, tilt, 0.35F);
        SN.torus(0, 0.42F, 0, 0.9F, 0.9F, 0.9F, 0.05F, 1F, 0.35F, 0.4F, 0.1F * fade, 0F, tilt, 0.3F);
        SN.torus(0, -0.42F, 0, 0.9F, 0.9F, 0.9F, 0.05F, 1F, 0.35F, 0.4F, 0.1F * fade, 0F, tilt, 0.3F);
        if (after < 0) {
            // The blue supergiant before it goes.
            SN.stars(0, 0, 0, 0.002F, 1, 3F, 0.7F, 0.8F, 1F);
            return SN;
        }
        float hot = (float) Math.exp(-after / 18);
        float sr = 1F, sg = 0.55F + 0.35F * hot, sb = 0.35F + 0.65F * hot;
        float dens = (float) (2.6 * Math.exp(-after / 45)) * fade;
        SN.shell(0, 0, 0, (float) sh, (float) sh, (float) sh, 0.07F, sr, sg, sb, dens, 0F, 1F);
        SN.blob(0, 0, 0, (float) (sh * 0.55), (float) (sh * 0.5), (float) (sh * 0.55), 1F, 0.6F, 0.32F, (float) (1.4 * Math.exp(-after / 30)) * fade, 0.3F,
                0.8F);
        if (after > 25) {
            SN.blob(0, 0, 0, (float) (sh * 0.3), (float) (sh * 0.3), (float) (sh * 0.3), 0F, 0F, 0F, -0.6F * (float) Math.min(1, (after - 25) / 20) * fade,
                    0F, 0.7F);
        }
        // The string of pearls round the ring.
        if (hit > 0) {
            for (int i = 0; i < 4; i++) {
                double a = Math.PI * 2 * i / 4 + 0.4;
                float x = (float) (Math.cos(a) * 0.55), y = (float) (Math.sin(a) * 0.55 * Math.cos(tilt)), z = (float) (Math.sin(a) * 0.55 * Math.sin(tilt));
                SN.blob(x, y, z, 0.06F, 0.06F, 0.06F, 1F, 0.85F, 0.75F, (float) (5 * hit * Math.exp(-Math.max(0, after - 30) / 35)) * fade, 0F, 0.4F);
            }
        }
        // The flash of the explosion, and what is left at the centre.
        float flash = (float) Math.exp(-after / 2.5);
        SN.stars(0, 0, 0, 0.002F, 1, 0.6F + 40F * flash, 0.85F, 0.9F, 1F);
        return SN;
    }

    // Typical colours: hydrogen-alpha red, its pink mix with blue, doubly ionised oxygen teal, reflection blue, dust.
    private static final float[] HA = {1F, 0.28F, 0.34F}, PINK = {1F, 0.45F, 0.62F}, OIII = {0.35F, 0.95F, 0.85F}, BLUE = {0.42F, 0.58F, 1F},
            GOLD = {1F, 0.72F, 0.38F};
    private static final float DUST = -6F;

    static {
        add("orion_nebula").look(1F, 0.45F, 0.55F, 1.3F)
                .blob(0, 0, 0, 0.78F, 0.66F, 0.42F, PINK[0], PINK[1], PINK[2], 1.0F, 0.2F, 0.9F)
                .blob(-0.42F, 0.36F, -0.1F, 0.45F, 0.32F, 0.3F, HA[0], HA[1], HA[2], 0.75F, 0.6F, 1F)
                .blob(0.45F, -0.3F, -0.1F, 0.4F, 0.3F, 0.3F, HA[0], HA[1], HA[2], 0.6F, -0.5F, 1F)
                .blob(0.04F, 0.04F, 0.08F, 0.3F, 0.26F, 0.2F, OIII[0], OIII[1], OIII[2], 2.2F, 0, 0.6F)
                .blob(0.05F, 0.05F, 0.1F, 0.1F, 0.1F, 0.1F, 1F, 0.95F, 0.85F, 3.5F, 0, 0.3F)
                .slab(0.22F, 0.14F, 0.18F, 0.32F, 0.06F, 0.25F, 0.2F, 0.1F, 0.05F, DUST, -0.45F, 0, 0.7F)
                .blob(0.12F, 0.58F, 0F, 0.14F, 0.14F, 0.12F, PINK[0], PINK[1], PINK[2], 1.1F, 0, 0.6F)
                .slab(-0.1F, 0.46F, 0.1F, 0.35F, 0.05F, 0.2F, 0.2F, 0.1F, 0.05F, -4F, 0.3F, 0, 0.8F)
                .stars(0.05F, 0.05F, 0.1F, 0.025F, 4, 1F, 0.8F, 0.88F, 1F)
                .stars(0, 0, 0, 0.45F, 40, 0.4F, 0.85F, 0.9F, 1F);
        add("crab_nebula").look(1F, 0.55F, 0.4F, 1.4F)
                .filaments(0, 0, 0, 0.92F, 0.66F, 0.66F, 0.38F, 1F, 0.42F, 0.25F, 2.6F, 0.4F)
                .filaments(0, 0, 0, 0.8F, 0.58F, 0.58F, 0.3F, 0.95F, 0.85F, 0.6F, 1.2F, 0.4F)
                .blob(0, 0, 0, 0.62F, 0.46F, 0.46F, 0.45F, 0.6F, 1F, 1.3F, 0.4F, 0.5F)
                .blob(0, 0, 0, 0.12F, 0.12F, 0.12F, 0.6F, 0.75F, 1F, 2F, 0, 0.3F);
        add("eagle_nebula").look(0.95F, 0.6F, 0.45F, 1.25F)
                .blob(0, 0.05F, -0.2F, 0.9F, 0.85F, 0.5F, GOLD[0], GOLD[1], GOLD[2], 0.9F, 0, 0.9F)
                .blob(0.05F, 0.15F, -0.15F, 0.55F, 0.5F, 0.4F, OIII[0], OIII[1], OIII[2], 0.7F, 0, 0.8F)
                .pillar(-0.3F, -0.32F, 0.15F, 0.13F, 0.78F, 0.15F, 0.08F, 0.05F, -9F, 0.08F, 0.6F)
                .pillar(0F, -0.4F, 0.15F, 0.1F, 0.56F, 0.15F, 0.08F, 0.05F, -9F, -0.05F, 0.6F)
                .pillar(0.3F, -0.46F, 0.15F, 0.08F, 0.38F, 0.15F, 0.08F, 0.05F, -9F, 0.1F, 0.6F)
                .pillar(-0.3F, -0.3F, 0.2F, 0.15F, 0.8F, 1F, 0.75F, 0.5F, 0.6F, 0.08F, 0.5F)
                .stars(0.2F, 0.35F, 0F, 0.25F, 20, 0.6F, 0.8F, 0.88F, 1F);
        add("lagoon_nebula").look(1F, 0.38F, 0.5F, 1.25F)
                .blob(0, 0, 0, 0.95F, 0.52F, 0.4F, PINK[0], PINK[1], PINK[2], 1F, 0.1F, 1F)
                .blob(0.15F, 0.05F, 0.08F, 0.13F, 0.13F, 0.1F, 1F, 0.85F, 0.75F, 3F, 0, 0.5F)
                .slab(-0.15F, 0F, 0.15F, 0.65F, 0.06F, 0.3F, 0.2F, 0.1F, 0.05F, -5F, 0.35F, 0, 0.8F)
                .stars(-0.4F, 0.05F, 0F, 0.18F, 18, 0.6F, 0.8F, 0.88F, 1F);
        add("trifid_nebula").look(0.9F, 0.4F, 0.55F, 1.25F)
                .blob(0, -0.15F, 0, 0.52F, 0.52F, 0.42F, HA[0], HA[1], HA[2], 1.25F, 0, 0.8F)
                .blob(0, 0.5F, -0.1F, 0.46F, 0.36F, 0.3F, BLUE[0], BLUE[1], BLUE[2], 0.8F, 0, 0.7F)
                .slab(0, -0.15F, 0.12F, 0.58F, 0.035F, 0.3F, 0.15F, 0.08F, 0.05F, -9F, 0F, 0, 0.5F)
                .slab(0, -0.15F, 0.12F, 0.58F, 0.035F, 0.3F, 0.15F, 0.08F, 0.05F, -9F, 2.1F, 0, 0.5F)
                .slab(0, -0.15F, 0.12F, 0.58F, 0.035F, 0.3F, 0.15F, 0.08F, 0.05F, -9F, 4.2F, 0, 0.5F)
                .stars(0, -0.15F, 0.05F, 0.02F, 2, 1F, 0.85F, 0.9F, 1F);
        add("carina_nebula").look(1F, 0.45F, 0.4F, 1.2F)
                .blob(0, 0, 0, 0.96F, 0.62F, 0.42F, 1F, 0.42F, 0.35F, 1F, 0.15F, 1F)
                .blob(0, 0.05F, 0.05F, 0.42F, 0.32F, 0.3F, OIII[0], OIII[1], OIII[2], 0.8F, 0, 0.8F)
                .blob(-0.32F, 0.12F, 0.2F, 0.22F, 0.26F, 0.15F, 0.15F, 0.08F, 0.05F, -4F, 0.4F, 0.9F)
                .blob(0.38F, -0.22F, 0.2F, 0.26F, 0.15F, 0.15F, 0.15F, 0.08F, 0.05F, -4F, -0.3F, 0.9F)
                .blob(0.05F, 0.05F, 0.25F, 0.08F, 0.13F, 0.06F, 0.1F, 0.05F, 0.05F, -12F, 0.3F, 0.5F)
                .pillar(-0.55F, -0.4F, 0.2F, 0.09F, 0.5F, 0.15F, 0.08F, 0.05F, -7F, -0.4F, 0.7F)
                .stars(0.05F, 0F, 0.05F, 0.3F, 30, 0.6F, 0.8F, 0.88F, 1F);
        add("ring_nebula").look(0.6F, 0.8F, 0.75F, 1.5F)
                .torus(0, 0, 0, 0.6F, 0.5F, 0.35F, 0.32F, 1F, 0.4F, 0.3F, 1.6F, 0.3F, 0.35F, 0.5F)
                .blob(0, 0, 0, 0.42F, 0.36F, 0.3F, OIII[0], OIII[1], OIII[2], 1.1F, 0.3F, 0.4F)
                .shell(0, 0, 0, 0.85F, 0.78F, 0.7F, 0.12F, 1F, 0.35F, 0.35F, 0.25F, 0.3F, 0.8F)
                .stars(0, 0, 0, 0.005F, 1, 0.8F, 0.85F, 0.9F, 1F);
        add("helix_nebula").look(0.8F, 0.6F, 0.55F, 1.4F)
                .torus(0, 0, 0, 0.66F, 0.6F, 0.3F, 0.24F, 1F, 0.32F, 0.25F, 1.5F, 0.1F, 0.2F, 0.7F)
                .torus(0, 0, 0, 0.46F, 0.42F, 0.3F, 0.2F, OIII[0], OIII[1], OIII[2], 0.9F, 0.4F, -0.15F, 0.6F)
                .blob(0, 0, 0, 0.3F, 0.28F, 0.2F, OIII[0], OIII[1], OIII[2], 0.12F, 0, 0.5F)
                .stars(0, 0, 0, 0.005F, 1, 0.9F, 0.85F, 0.9F, 1F);
        add("dumbbell_nebula").look(0.6F, 0.85F, 0.6F, 1.4F)
                .blob(0, 0.32F, 0, 0.44F, 0.38F, 0.4F, 0.45F, 1F, 0.7F, 1.0F, 0, 0.6F)
                .blob(0, -0.32F, 0, 0.44F, 0.38F, 0.4F, 0.45F, 1F, 0.7F, 1.0F, 0, 0.6F)
                .shell(0, 0, 0, 0.66F, 0.78F, 0.62F, 0.22F, 1F, 0.32F, 0.35F, 0.65F, 0, 0.8F)
                .stars(0, 0, 0, 0.005F, 1, 0.8F, 0.85F, 0.9F, 1F);
        add("cats_eye_nebula").look(0.5F, 0.85F, 0.8F, 1.5F)
                .shell(0, 0, 0, 0.32F, 0.48F, 0.32F, 0.09F, OIII[0], OIII[1], OIII[2], 2.2F, 0.3F, 0.3F)
                .shell(0, 0, 0, 0.28F, 0.42F, 0.28F, 0.08F, 1F, 0.4F, 0.4F, 1.6F, -0.4F, 0.3F)
                .shell(0, 0, 0, 0.2F, 0.3F, 0.2F, 0.1F, 0.5F, 0.9F, 1F, 1.6F, 1.2F, 0.3F)
                .shell(0, 0, 0, 0.86F, 0.86F, 0.86F, 0.14F, 1F, 0.4F, 0.35F, 0.3F, 0, 0.9F)
                .blob(0, 0.62F, 0, 0.06F, 0.17F, 0.06F, 0.5F, 1F, 0.8F, 1.6F, 0.3F, 0.4F)
                .blob(0, -0.62F, 0, 0.06F, 0.17F, 0.06F, 0.5F, 1F, 0.8F, 1.6F, 0.3F, 0.4F)
                .stars(0, 0, 0, 0.004F, 1, 0.9F, 0.85F, 0.9F, 1F);
        add("eskimo_nebula").look(0.7F, 0.8F, 0.85F, 1.4F)
                .shell(0, 0, 0, 0.36F, 0.36F, 0.36F, 0.14F, 0.5F, 0.9F, 1F, 1.6F, 0, 0.5F)
                .filaments(0, 0, 0, 0.74F, 0.74F, 0.74F, 0.28F, 1F, 0.55F, 0.42F, 1.5F, 0)
                .blob(0, 0, 0, 0.2F, 0.2F, 0.2F, 0.6F, 0.85F, 1F, 1F, 0, 0.3F)
                .stars(0, 0, 0, 0.004F, 1, 0.9F, 0.85F, 0.9F, 1F);
        add("horsehead_nebula").look(0.95F, 0.3F, 0.35F, 1.3F)
                .slab(0, 0.15F, -0.3F, 1.1F, 0.12F, 0.75F, HA[0], HA[1], HA[2], 1.4F, 0, 1.5708F, 0.9F)
                .blob(0, -0.7F, 0.15F, 1.1F, 0.36F, 0.3F, 0.15F, 0.08F, 0.06F, -6F, 0, 0.8F)
                .pillar(0.02F, -0.28F, 0.2F, 0.12F, 0.52F, 0.15F, 0.08F, 0.06F, -12F, 0.1F, 0.4F)
                .blob(-0.04F, 0.05F, 0.2F, 0.18F, 0.12F, 0.12F, 0.15F, 0.08F, 0.06F, -12F, 0.5F, 0.3F)
                .blob(-0.2F, 0.0F, 0.2F, 0.1F, 0.07F, 0.08F, 0.15F, 0.08F, 0.06F, -12F, 0.3F, 0.3F)
                .blob(0.06F, 0.18F, 0.2F, 0.04F, 0.08F, 0.04F, 0.15F, 0.08F, 0.06F, -12F, -0.2F, 0.2F)
                .stars(0.5F, 0.4F, 0.2F, 0.3F, 10, 0.5F, 0.8F, 0.88F, 1F);
        add("flame_nebula").look(1F, 0.6F, 0.35F, 1.3F)
                .blob(0, 0, 0, 0.72F, 0.62F, 0.42F, 1F, 0.62F, 0.3F, 1.3F, 0, 1F)
                .pillar(0, -0.1F, 0.2F, 0.1F, 1.1F, 0.15F, 0.08F, 0.05F, -7F, 0F, 0.7F)
                .slab(-0.2F, 0.2F, 0.2F, 0.35F, 0.05F, 0.2F, 0.15F, 0.08F, 0.05F, -6F, 0.7F, 0, 0.7F)
                .slab(0.2F, -0.2F, 0.2F, 0.35F, 0.05F, 0.2F, 0.15F, 0.08F, 0.05F, -6F, -0.6F, 0, 0.7F)
                .stars(0.6F, 0.42F, 0.3F, 0.01F, 1, 1F, 0.75F, 0.85F, 1F);
        add("rosette_nebula").look(1F, 0.35F, 0.42F, 1.25F)
                .shell(0, 0, 0, 0.72F, 0.72F, 0.62F, 0.3F, HA[0], HA[1], HA[2], 1.2F, 0, 1F)
                .blob(0, 0, 0, 0.32F, 0.32F, 0.3F, OIII[0], OIII[1], OIII[2], 0.25F, 0, 0.6F)
                .stars(0, 0, 0, 0.18F, 22, 0.7F, 0.8F, 0.88F, 1F);
        add("north_america_nebula").look(1F, 0.32F, 0.36F, 1.2F)
                .blob(0, 0, 0, 0.88F, 0.88F, 0.42F, HA[0], HA[1], HA[2], 1.1F, 0, 1F)
                .blob(0.22F, -0.38F, 0.2F, 0.36F, 0.22F, 0.15F, 0.15F, 0.08F, 0.06F, -6F, -0.6F, 0.8F)
                .blob(0.68F, 0.1F, 0.2F, 0.25F, 0.6F, 0.2F, 0.15F, 0.08F, 0.06F, -4F, 0, 0.8F)
                .blob(0.06F, -0.46F, 0.1F, 0.36F, 0.05F, 0.1F, 1F, 0.55F, 0.45F, 3F, 0.2F, 0.5F)
                .stars(0, 0, 0, 0.8F, 30, 0.3F, 0.9F, 0.92F, 1F);
        add("veil_nebula").look(0.6F, 0.6F, 0.9F, 1.3F)
                .filaments(0, 0, 0, 0.92F, 0.92F, 0.92F, 0.035F, 0.4F, 0.7F, 1F, 1.3F, 0, 1F)
                .filaments(0.04F, 0.03F, 0, 0.88F, 0.88F, 0.88F, 0.03F, 1F, 0.4F, 0.35F, 1.1F, 0.2F, 1F)
                .stars(0, 0, 0, 0.8F, 20, 0.3F, 0.9F, 0.92F, 1F);
        add("tarantula_nebula").look(1F, 0.45F, 0.55F, 1.25F)
                .blob(0, 0, 0, 0.62F, 0.56F, 0.42F, PINK[0], PINK[1], PINK[2], 1.5F, 0, 0.9F)
                .blob(0, 0, 0, 0.26F, 0.26F, 0.22F, OIII[0], OIII[1], OIII[2], 1.5F, 0, 0.5F)
                .pillar(0.2F, 0.3F, 0, 0.07F, 0.8F, 1F, 0.4F, 0.45F, 0.8F, -0.6F, 0.9F)
                .pillar(-0.25F, 0.25F, 0, 0.07F, 0.8F, 1F, 0.4F, 0.45F, 0.8F, 0.8F, 0.9F)
                .pillar(-0.3F, -0.2F, 0, 0.07F, 0.8F, 1F, 0.4F, 0.45F, 0.8F, 2.3F, 0.9F)
                .pillar(0.3F, -0.25F, 0, 0.07F, 0.8F, 1F, 0.4F, 0.45F, 0.8F, -2.4F, 0.9F)
                .pillar(0F, -0.35F, 0, 0.06F, 0.7F, 1F, 0.4F, 0.45F, 0.7F, 3.1F, 0.9F)
                .stars(0, 0, 0.05F, 0.05F, 30, 0.9F, 0.75F, 0.85F, 1F);
        add("omega_nebula").look(1F, 0.42F, 0.5F, 1.3F)
                .blob(0, 0, 0, 0.65F, 0.25F, 0.25F, 1F, 0.45F, 0.5F, 2.0F, 0.2F, 0.7F)
                .blob(0.45F, 0.25F, 0, 0.12F, 0.3F, 0.15F, 1F, 0.5F, 0.5F, 1.3F, -0.3F, 0.7F)
                .blob(0, 0, -0.1F, 0.95F, 0.7F, 0.45F, HA[0], HA[1], HA[2], 0.4F, 0, 1F)
                .blob(-0.2F, -0.26F, 0.2F, 0.42F, 0.12F, 0.15F, 0.15F, 0.08F, 0.05F, -4F, 0.2F, 0.7F)
                .stars(0, 0, 0.05F, 0.3F, 14, 0.6F, 0.8F, 0.88F, 1F);
        add("cone_nebula").look(0.9F, 0.35F, 0.45F, 1.25F)
                .blob(0, 0, -0.1F, 0.92F, 0.92F, 0.42F, HA[0], HA[1], HA[2], 0.9F, 0, 1F)
                .pillar(0, -0.22F, 0.2F, 0.17F, 0.78F, 0.15F, 0.08F, 0.05F, -11F, 0F, 0.5F)
                .pillar(0, -0.2F, 0.22F, 0.2F, 0.82F, 1F, 0.55F, 0.45F, 0.6F, 0F, 0.5F)
                .blob(0, 0.6F, 0, 0.32F, 0.26F, 0.2F, BLUE[0], BLUE[1], BLUE[2], 0.7F, 0, 0.7F)
                .stars(0, 0.5F, 0.1F, 0.3F, 16, 0.7F, 0.78F, 0.86F, 1F);
        add("bubble_nebula").look(0.8F, 0.55F, 0.7F, 1.35F)
                .shell(0, 0, 0, 0.42F, 0.42F, 0.42F, 0.06F, 0.5F, 0.9F, 1F, 3.5F, 0, 0.5F)
                .shell(0, 0, 0, 0.45F, 0.45F, 0.45F, 0.08F, 1F, 0.35F, 0.4F, 1.6F, 0, 0.6F)
                .blob(0.2F, -0.1F, -0.15F, 0.95F, 0.8F, 0.45F, HA[0], HA[1], HA[2], 0.6F, 0, 1F)
                .stars(-0.1F, 0.05F, 0, 0.005F, 1, 1F, 0.8F, 0.88F, 1F);
        add("california_nebula").look(1F, 0.28F, 0.34F, 1.2F)
                .blob(0, 0, 0, 0.96F, 0.28F, 0.3F, HA[0], HA[1], HA[2], 1.3F, 0.3F, 1F)
                .slab(0.1F, 0.05F, 0.2F, 0.9F, 0.03F, 0.2F, 0.15F, 0.08F, 0.05F, -4F, 0.35F, 0, 0.8F)
                .stars(0, 0, 0, 0.8F, 20, 0.3F, 0.9F, 0.92F, 1F);
        add("heart_nebula").look(1F, 0.3F, 0.4F, 1.2F)
                .shell(-0.26F, 0.2F, 0, 0.42F, 0.44F, 0.36F, 0.32F, HA[0], HA[1], HA[2], 1.1F, 0.3F, 1F)
                .shell(0.26F, 0.2F, 0, 0.42F, 0.44F, 0.36F, 0.32F, HA[0], HA[1], HA[2], 1.1F, -0.3F, 1F)
                .blob(0, -0.32F, 0, 0.36F, 0.36F, 0.3F, HA[0], HA[1], HA[2], 0.8F, 0, 1F)
                .stars(0, 0.05F, 0, 0.12F, 12, 0.7F, 0.8F, 0.88F, 1F);
        add("soul_nebula").look(1F, 0.3F, 0.4F, 1.2F)
                .blob(0, 0, -0.15F, 0.95F, 0.56F, 0.4F, HA[0], HA[1], HA[2], 0.45F, 0, 1F)
                .shell(-0.36F, 0, 0, 0.36F, 0.36F, 0.3F, 0.26F, HA[0], HA[1], HA[2], 1.1F, 0, 1F)
                .shell(0.36F, 0.05F, 0, 0.32F, 0.32F, 0.3F, 0.26F, HA[0], HA[1], HA[2], 1.1F, 0, 1F)
                .stars(-0.36F, 0, 0, 0.08F, 8, 0.6F, 0.8F, 0.88F, 1F);
        add("witch_head_nebula").look(0.45F, 0.6F, 1F, 1.4F)
                .blob(0.05F, 0.36F, 0, 0.36F, 0.25F, 0.2F, BLUE[0], BLUE[1], BLUE[2], 0.9F, 0.4F, 1F)
                .blob(-0.35F, 0.05F, 0, 0.26F, 0.12F, 0.15F, BLUE[0], BLUE[1], BLUE[2], 0.9F, 0.2F, 1F)
                .blob(-0.15F, -0.35F, 0, 0.3F, 0.2F, 0.15F, BLUE[0], BLUE[1], BLUE[2], 0.85F, -0.3F, 1F)
                .blob(0.38F, 0.08F, 0, 0.36F, 0.46F, 0.2F, BLUE[0], BLUE[1], BLUE[2], 0.75F, 0, 1F)
                .stars(0.92F, -0.12F, 0.1F, 0.01F, 1, 1.4F, 0.75F, 0.85F, 1F);
        add("running_man_nebula").look(0.5F, 0.6F, 1F, 1.35F)
                .blob(0, 0, 0, 0.82F, 0.72F, 0.42F, BLUE[0], BLUE[1], BLUE[2], 0.9F, 0, 1F)
                .blob(0, -0.1F, -0.1F, 0.9F, 0.8F, 0.3F, 1F, 0.4F, 0.5F, 0.25F, 0, 1F)
                .pillar(0, 0, 0.2F, 0.1F, 0.5F, 0.12F, 0.08F, 0.08F, -10F, 0.4F, 0.4F)
                .pillar(-0.16F, -0.36F, 0.2F, 0.06F, 0.42F, 0.12F, 0.08F, 0.08F, -10F, -0.6F, 0.4F)
                .pillar(0.16F, -0.33F, 0.2F, 0.06F, 0.42F, 0.12F, 0.08F, 0.08F, -10F, 0.7F, 0.4F)
                .pillar(0F, 0.14F, 0.2F, 0.05F, 0.55F, 0.12F, 0.08F, 0.08F, -10F, 1.5F, 0.4F)
                .blob(0.06F, 0.32F, 0.2F, 0.08F, 0.08F, 0.08F, 0.12F, 0.08F, 0.08F, -10F, 0, 0.3F)
                .stars(0, 0.05F, 0.1F, 0.2F, 6, 0.8F, 0.8F, 0.88F, 1F);
        // The Vela pulsar's own small wind nebula.
        add("vela_wind").look(0.5F, 0.6F, 1F, 1.4F)
                .blob(0, 0, 0, 0.4F, 0.3F, 0.3F, 0.45F, 0.6F, 1F, 1.4F, 0.6F, 0.5F)
                .pillar(0, 0.3F, 0, 0.05F, 0.6F, 0.6F, 0.75F, 1F, 1.2F, 0.6F, 0.5F)
                .shell(0, 0, 0, 0.55F, 0.45F, 0.45F, 0.12F, 0.55F, 0.65F, 1F, 0.8F, 0.6F, 0.6F);
    }

    private Nebulae() {}
}
