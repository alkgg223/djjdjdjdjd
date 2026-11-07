package com.arkragnarok.species;

import net.minecraft.resources.ResourceLocation;
import com.arkragnarok.ArkRagnarok;

/**
 * Everything about one creature kind.  Stats are ARK base values at level 1; {@link #HEALTH_FACTOR} and
 * {@link #DAMAGE_FACTOR} convert them to Minecraft (a player has 20 health).
 */
public final class Species {
    public static final float HEALTH_FACTOR = 0.4f;
    public static final float DAMAGE_FACTOR = 0.4f;

    public final String id;
    public final String name;
    public final String plan;          // theropod / quadruped / sauropod
    public final Diet diet;
    public final Temper temper;
    public final TameMethod tame;
    public final Kibble kibble;
    public final float health, damage, torpor, stamina, weight;
    public final float walkSpeed;      // movement speed attribute
    public final float runFactor;      // sprint multiplier when chasing / fleeing / ridden sprinting
    public final float affinity;       // taming affinity needed at level 1
    public final int eatSeconds;       // seconds between two meals while unconscious
    public final boolean pack, nocturnal, rideable;
    public final String attack;        // animation family
    public final float reach;          // melee reach beyond the hitbox (blocks)
    public final float armor;
    // model (generated from the asset build)
    public final float modelScale, width, height, eye, hip, headZ;
    public final float length;
    public final float seatForward, seatHeight;
    public final boolean saddle;
    public final int lengthM;

    Species(Builder b, SpeciesModels.Meta m) {
        id = b.id;
        name = m.name();
        plan = m.plan();
        diet = b.diet;
        temper = b.temper;
        tame = b.tame;
        kibble = b.kibble;
        health = b.health;
        damage = b.damage;
        torpor = b.torpor;
        stamina = b.stamina;
        weight = b.weight;
        walkSpeed = b.speed;
        runFactor = b.run;
        affinity = b.affinity;
        eatSeconds = b.eat;
        pack = b.pack;
        nocturnal = b.nocturnal;
        rideable = m.saddle();
        attack = b.attack;
        reach = b.reach;
        armor = b.armor;
        modelScale = m.scale();
        width = m.width();
        height = m.height();
        eye = m.eye();
        hip = m.hip();
        headZ = m.headZ();
        length = m.length();
        seatForward = m.seatForward();
        seatHeight = m.seatHeight();
        saddle = m.saddle();
        lengthM = Math.round(m.length());
    }

    public ResourceLocation mesh(int lod) {
        return ArkRagnarok.id("arkmesh/" + id + "_lod" + lod + ".arkm");
    }

    public ResourceLocation texture(int lod) {
        return ArkRagnarok.id("textures/entity/" + id + "/" + id + "_lod" + lod + ".jpg");
    }

    public ResourceLocation saddleMesh(int lod) {
        return ArkRagnarok.id("arkmesh/" + id + "_saddle_lod" + lod + ".arkm");
    }

    public ResourceLocation saddleTexture(int lod) {
        return ArkRagnarok.id("textures/entity/" + id + "/" + id + "_saddle_lod" + lod + ".jpg");
    }

    public String anim(String name) {
        return "animation." + id + "." + name;
    }

    public boolean eatsMeat() {
        return diet == Diet.CARNIVORE || diet == Diet.OMNIVORE || diet == Diet.PISCIVORE;
    }

    public boolean eatsPlants() {
        return diet == Diet.HERBIVORE || diet == Diet.OMNIVORE;
    }

    public boolean big() {
        return width >= 1.6f;
    }

    public static final class Builder {
        final String id;
        Diet diet = Diet.CARNIVORE;
        Temper temper = Temper.AGGRESSIVE;
        TameMethod tame = TameMethod.KNOCKOUT;
        Kibble kibble = Kibble.REGULAR;
        float health = 200, damage = 20, torpor = 200, stamina = 300, weight = 300, speed = 0.25f, run = 1.6f, affinity = 1000;
        float reach = 1.0f, armor = 0;
        int eat = 12;
        boolean pack, nocturnal;
        String attack = "bite";

        Builder(String id) {
            this.id = id;
        }

        public Builder diet(Diet d) { diet = d; return this; }
        public Builder temper(Temper t) { temper = t; return this; }
        public Builder passive() { tame = TameMethod.PASSIVE; return this; }
        public Builder kibble(Kibble k) { kibble = k; return this; }
        public Builder stats(float hp, float dmg, float torp, float stam, float wt) { health = hp; damage = dmg; torpor = torp; stamina = stam; weight = wt; return this; }
        public Builder speed(float walk, float runFactor) { speed = walk; run = runFactor; return this; }
        public Builder affinity(float a, int eatSec) { affinity = a; eat = eatSec; return this; }
        public Builder pack() { pack = true; return this; }
        public Builder nocturnal() { nocturnal = true; return this; }
        public Builder attack(String a, float r) { attack = a; reach = r; return this; }
        public Builder armor(float a) { armor = a; return this; }
    }
}
