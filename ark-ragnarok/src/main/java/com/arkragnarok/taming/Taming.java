package com.arkragnarok.taming;

import com.arkragnarok.registry.ModItems;
import com.arkragnarok.species.Kibble;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.TameMethod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ARK-style taming numbers.
 *
 * Knock-out: every tranquilizer arrow adds {@link #TRANQ_ARROW} torpor (over a few seconds); when torpor reaches the
 * maximum the animal falls unconscious.  Max torpor grows 6 % per level, so a level 150 Rex needs about 100 arrows.
 * While unconscious torpor drains slowly (narcoberries / narcotics push it back up) and the animal eats one item of the
 * food it was given every few seconds; each item adds affinity.  When affinity reaches the requirement it is tamed.
 * Its preferred kibble is by far the best food: a level 1 animal needs 3 of it, a level 150 one about 21.
 */
public final class Taming {
    private Taming() {}

    public static final float TRANQ_ARROW = 150f;
    public static final float NARCOBERRY = 25f;
    public static final float NARCOTIC = 140f;

    public static float maxTorpor(Species s, int level) {
        return s.torpor * (1f + 0.06f * (level - 1));
    }

    /** torpor lost per second while unconscious (grows slower than max torpor, so high levels sleep longer) */
    public static float sleepDrainPerSecond(Species s, int level) {
        return s.torpor * 0.0025f * (1f + 0.012f * (level - 1));
    }

    /** torpor lost per second while awake: an animal that is not hit again wakes up fresh within about a minute */
    public static float awakeDrainPerSecond(Species s, int level) {
        return maxTorpor(s, level) * 0.02f;
    }

    public static float requiredAffinity(Species s, int level) {
        return s.affinity * (1f + 0.04f * (level - 1));
    }

    /** seconds between two meals while being tamed */
    public static int eatIntervalTicks(Species s) {
        return Math.max(60, s.eatSeconds * 20);
    }

    /** affinity one item gives this species (0 = it does not eat it) */
    public static float foodValue(Species s, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        float a = s.affinity;
        for (Kibble k : Kibble.values()) {
            if (stack.is(ModItems.KIBBLE.get(k).get())) {
                int d = Math.abs(k.ordinal() - s.kibble.ordinal());
                return d == 0 ? a / 3f : d == 1 ? a / 7f : d == 2 ? a / 14f : a / 30f;
            }
        }
        if (s.eatsMeat()) {
            if (stack.is(ModItems.RAW_PRIME_MEAT.get())) return a / 9f;
            if (stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON) || stack.is(Items.CHICKEN)
                    || stack.is(Items.RABBIT)) return a / 28f;
            if (stack.is(ModItems.COOKED_PRIME_MEAT.get())) return a / 16f;
            if (stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP) || stack.is(Items.COOKED_MUTTON)
                    || stack.is(Items.COOKED_CHICKEN)) return a / 45f;
            if (s.diet == com.arkragnarok.species.Diet.PISCIVORE && (stack.is(Items.COD) || stack.is(Items.SALMON))) return a / 18f;
        }
        if (s.eatsPlants()) {
            if (stack.is(ModItems.MEJOBERRY.get())) return a / 26f;
            if (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES)) return a / 40f;
            if (stack.is(Items.WHEAT) || stack.is(Items.CARROT) || stack.is(Items.APPLE) || stack.is(Items.POTATO)
                    || stack.is(Items.BEETROOT) || stack.is(Items.MELON_SLICE)) return a / 50f;
        }
        return 0;
    }

    /** torpor this item adds when an unconscious animal is fed with it */
    public static float torporValue(ItemStack stack) {
        if (stack.is(ModItems.NARCOTIC.get())) return NARCOTIC;
        if (stack.is(ModItems.NARCOBERRY.get())) return NARCOBERRY;
        return 0;
    }

    public static int countNeeded(float remaining, float value) {
        return value <= 0 ? -1 : (int) Math.ceil(Math.max(0, remaining) / value);
    }

    public static ItemStack preferredKibble(Species s) {
        return new ItemStack(ModItems.KIBBLE.get(s.kibble).get());
    }

    /** the best non-kibble food for this species (shown by the spyglass as the alternative) */
    public static ItemStack fallbackFood(Species s) {
        if (s.eatsMeat()) return new ItemStack(ModItems.RAW_PRIME_MEAT.get());
        return new ItemStack(ModItems.MEJOBERRY.get());
    }

    public static boolean passive(Species s) {
        return s.tame == TameMethod.PASSIVE;
    }
}
