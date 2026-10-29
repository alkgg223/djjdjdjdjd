package com.eggsteal.brainrot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/** One block of a Brainrot Heist base skin. Glass parts are see-through; many parts give off their skin's particles. */
public class BrSkinBlock extends Block {
    private final int part, fx;

    public BrSkinBlock(BrSkin skin, int part) {
        super(props(skin, part));
        this.part = part;
        this.fx = skin.fx[part];
    }

    static BlockBehaviour.Properties props(BrSkin skin, int part) {
        int light = skin.light[part];
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().strength(-1f, 3600000f).lightLevel(s -> light).sound(sound(skin, part));
        if (part == BrSkin.GLASS) p = p.noOcclusion().isViewBlocking((s, l, q) -> false).isSuffocating((s, l, q) -> false);
        return p;
    }

    static SoundType sound(BrSkin skin, int part) {
        if (part == BrSkin.GLASS || part == BrSkin.LAMP) return SoundType.GLASS;
        if (part == BrSkin.CARPET) return SoundType.WOOL;
        return switch (skin.id) {
            case "wood", "cherry", "haunted" -> SoundType.WOOD;
            case "copper", "golden", "neon", "matrix" -> SoundType.METAL;
            case "amethyst" -> SoundType.AMETHYST;
            default -> SoundType.STONE;
        };
    }

    // ------------------------------------------------------------------ see-through glass

    @Override
    public boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return (part == BrSkin.GLASS && next.is(this)) || super.skipRendering(state, next, side);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return part == BrSkin.GLASS ? 1f : super.getShadeBrightness(state, level, pos);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return part == BrSkin.GLASS ? Shapes.empty() : super.getVisualShape(state, level, pos, ctx);
    }

    // ------------------------------------------------------------------ particles

    /** A bright colour around the colour wheel (h in 0..1). */
    static Vector3f rainbow(float h) {
        float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
        return new Vector3f(0.15f + 0.85f * Math.max(0, Math.min(1, r)), 0.15f + 0.85f * Math.max(0, Math.min(1, g)), 0.15f + 0.85f * Math.max(0, Math.min(1, b)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (fx == BrSkin.NONE) return;
        float rate = part == BrSkin.LAMP ? 1f : part == BrSkin.FLOOR ? 0.12f : part == BrSkin.CARPET ? 0.3f : 0.25f;
        if (r.nextFloat() > rate) return;
        double x, y, z;
        if (part == BrSkin.FRAME || part == BrSkin.GLASS) { // just outside a random side
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            x = pos.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? r.nextDouble() - 0.5 : 0);
            z = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? r.nextDouble() - 0.5 : 0);
            y = pos.getY() + r.nextDouble();
            if (level.getBlockState(BlockPos.containing(x, y, z)).isSolidRender(level, BlockPos.containing(x, y, z))) return;
        } else if (part == BrSkin.LAMP) {
            x = pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.5;
            y = pos.getY() + 1.05;
            z = pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.5;
        } else { // floor and carpet: on top
            x = pos.getX() + r.nextDouble();
            y = pos.getY() + 1.05 + r.nextDouble() * 0.2;
            z = pos.getZ() + r.nextDouble();
        }
        double sx = (r.nextDouble() - 0.5) * 0.04, sz = (r.nextDouble() - 0.5) * 0.04;
        switch (fx) {
            case BrSkin.FLAME -> {
                level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
                if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.02, 0);
            }
            case BrSkin.PUMPKIN -> {
                level.addParticle(r.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.015, 0);
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.3, z, 0, 0.03, 0);
            }
            case BrSkin.SOUL_FIRE -> {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.01, 0);
                if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SOUL, x, y + 0.2, z, sx, 0.03, sz);
            }
            case BrSkin.LAVA -> {
                level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.02, 0);
                if (r.nextInt(5) == 0) level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            }
            case BrSkin.DRIP_LAVA -> {
                if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
                else level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
            }
            case BrSkin.SMOKE -> {
                level.addParticle(ParticleTypes.CLOUD, x, y + 0.1, z, sx * 0.5, 0.05, sz * 0.5);
                if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.03, 0);
            }
            case BrSkin.NOTE -> level.addParticle(ParticleTypes.NOTE, x, y + 0.2, z, r.nextInt(25) / 24.0, 0, 0);
            case BrSkin.ENCHANT -> level.addParticle(ParticleTypes.ENCHANT, x, y, z, (r.nextDouble() - 0.5) * 2, r.nextDouble() * 1.2, (r.nextDouble() - 0.5) * 2);
            case BrSkin.PORTAL -> level.addParticle(ParticleTypes.PORTAL, x, y, z, (r.nextDouble() - 0.5) * 1.5, -r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 1.5);
            case BrSkin.HONEY -> { // drips run down the sides
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
                level.addParticle(ParticleTypes.DRIPPING_HONEY, pos.getX() + 0.5 + d.getStepX() * 0.56, pos.getY() + 0.3, pos.getZ() + 0.5 + d.getStepZ() * 0.56, 0, 0, 0);
            }
            case BrSkin.SNOW -> level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, sx, -0.02, sz);
            case BrSkin.CHERRY -> level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z, 0, 0, 0);
            case BrSkin.END_ROD -> level.addParticle(ParticleTypes.END_ROD, x, y, z, sx, 0.02 + r.nextDouble() * 0.03, sz);
            case BrSkin.FIREWORK -> level.addParticle(ParticleTypes.FIREWORK, x, y, z, sx * 2, 0.03 + r.nextDouble() * 0.04, sz * 2);
            case BrSkin.TOTEM -> level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, sx * 3, 0.1 + r.nextDouble() * 0.1, sz * 3);
            case BrSkin.CLOUD -> level.addParticle(ParticleTypes.CLOUD, x, y, z, sx * 0.3, 0.005, sz * 0.3);
            case BrSkin.BUBBLES -> level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0, 0.02, 0);
            case BrSkin.RAINBOW_DUST -> {
                float h = ((level.getGameTime() % 120) / 120f + (pos.getX() + pos.getZ()) * 0.04f) % 1f;
                level.addParticle(new DustParticleOptions(rainbow(h), 1.2f), x, y, z, 0, 0, 0);
            }
            case BrSkin.GOLD_DUST -> level.addParticle(new DustParticleOptions(new Vector3f(1f, 0.8f, 0.2f), 1f), x, y, z, 0, 0, 0);
            case BrSkin.TOXIC_DUST -> {
                level.addParticle(new DustParticleOptions(new Vector3f(0.45f, 1f, 0.15f), 1.3f), x, y + 0.1, z, 0, 0, 0);
                if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.ITEM_SLIME, x, y, z, 0, 0, 0);
            }
            default -> {
                ParticleOptions o = switch (fx) {
                    case BrSkin.SPARK -> ParticleTypes.ELECTRIC_SPARK;
                    case BrSkin.HEART -> ParticleTypes.HEART;
                    case BrSkin.VILLAGER -> ParticleTypes.HAPPY_VILLAGER;
                    case BrSkin.SPORE -> ParticleTypes.SPORE_BLOSSOM_AIR;
                    case BrSkin.MYCELIUM -> ParticleTypes.MYCELIUM;
                    case BrSkin.REVERSE_PORTAL -> ParticleTypes.REVERSE_PORTAL;
                    case BrSkin.WITCH -> ParticleTypes.WITCH;
                    case BrSkin.GLOW -> ParticleTypes.GLOW;
                    case BrSkin.GOLD_SPARK -> ParticleTypes.WAX_ON;
                    case BrSkin.WHITE_SPARK -> ParticleTypes.WAX_OFF;
                    case BrSkin.CYAN_SPARK -> ParticleTypes.SCRAPE;
                    case BrSkin.SOUL -> ParticleTypes.SOUL;
                    case BrSkin.DRAGON -> ParticleTypes.DRAGON_BREATH;
                    case BrSkin.CRIMSON -> ParticleTypes.CRIMSON_SPORE;
                    case BrSkin.SLIME -> ParticleTypes.ITEM_SLIME;
                    default -> ParticleTypes.END_ROD;
                };
                level.addParticle(o, x, y, z, sx, 0.01, sz);
            }
        }
    }
}
