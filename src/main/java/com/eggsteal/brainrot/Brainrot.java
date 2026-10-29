package com.eggsteal.brainrot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/** All brainrots of the Brainrot Heist mode. Model files: assets/eggsteal/models/brainrot/<id>.obj (tools/gen_brainrots.py). */
public final class Brainrot {
    public static final String[] RARITY = {"Common", "Rare", "Epic", "Legendary", "Mythic", "Brainrot God", "Secret", "OG"};
    public static final String[] RARITY_COLOR = {"§7", "§9", "§5", "§6", "§c", "§e§l", "§f§l", "§b§l"};
    /** Relative chance of each rarity for a brainrot walking on the carpet. */
    public static final double[] RARITY_WEIGHT = {600, 250, 100, 35, 10, 3, 0.5, 0.05};
    public static final Brainrot[] ALL;

    public final int index, rarity;
    public final String id, name;
    public final double price, income;
    /** Model height in blocks (sets the hit box and where the name floats). */
    public final float height;

    private Brainrot(int index, String id, String name, int rarity, double price, double income, float height) {
        this.index = index; this.id = id; this.name = name; this.rarity = rarity; this.price = price; this.income = income; this.height = height;
    }

    static {
        List<Brainrot> l = new ArrayList<>();
        add(l, "pizzolo", "Pizzolo Pazzolo", 0, 25, 1, 1.50f);
        add(l, "tazzolino", "Tazzolino Caffettino", 0, 75, 3, 1.67f);
        add(l, "bananito", "Bananito Ballerino", 0, 150, 5, 2.00f);
        add(l, "patatino", "Patatino Pallone", 0, 300, 9, 2.33f);
        add(l, "pinguino", "Pinguino Panino", 0, 500, 14, 1.50f);
        add(l, "squalo", "Squalo Stivaloni", 1, 1_000, 20, 2.25f);
        add(l, "gattino", "Gattino Gelatino", 1, 2_500, 40, 1.92f);
        add(l, "tostino", "Tostino Tostapane", 1, 4_000, 60, 1.50f);
        add(l, "cactusso", "Cactusso Sombrerone", 1, 6_000, 85, 2.25f);
        add(l, "bruco", "Bruco Burgerino", 1, 8_000, 110, 1.42f);
        add(l, "ananasso", "Ananasso Astronauto", 2, 12_000, 150, 2.25f);
        add(l, "rana_razzo", "Rana Razzetto", 2, 20_000, 230, 2.00f);
        add(l, "polpo", "Polpo Pianoforte", 2, 30_000, 320, 1.67f);
        add(l, "frigorillo", "Frigorillo", 2, 45_000, 450, 2.25f);
        add(l, "ciambella", "Ciambellone Robotto", 3, 80_000, 750, 2.50f);
        add(l, "mucca", "Mucca Missilona", 3, 150_000, 1_200, 1.58f);
        add(l, "dino_divano", "Dinosauro Divanino", 3, 250_000, 1_800, 2.08f);
        add(l, "lumacone", "Lumacone Locomotivo", 3, 350_000, 2_500, 1.92f);
        add(l, "dragone", "Dragone Dentifricione", 4, 600_000, 4_000, 2.25f);
        add(l, "fenicottero", "Fenicottero Fontanino", 4, 1_200_000, 7_000, 2.42f);
        add(l, "elefrullo", "Elefrullatore", 4, 2_000_000, 11_000, 2.00f);
        add(l, "re_ravioli", "Re Raviolone", 5, 5_000_000, 25_000, 2.08f);
        add(l, "tamburo", "Tempestone Tamburone", 5, 10_000_000, 45_000, 2.33f);
        add(l, "cappellone", "Cappellone Cosmico", 5, 18_000_000, 75_000, 2.67f);
        add(l, "scheletrino", "Scheletrino Spaghettino", 6, 60_000_000, 250_000, 2.17f);
        add(l, "gabinetto", "Gabinetto Galattico", 6, 150_000_000, 500_000, 2.17f);
        add(l, "teschio", "Teschio Fortunello", 6, 300_000_000, 900_000, 1.33f);
        add(l, "leone", "Leone Lavatrice", 7, 1_000_000_000, 3_000_000, 2.42f);
        add(l, "uovo", "Uovo Ultimissimo", 7, 2_500_000_000.0, 6_000_000, 2.42f);
        ALL = l.toArray(new Brainrot[0]);
    }

    private static void add(List<Brainrot> l, String id, String name, int rarity, double price, double income, float height) {
        l.add(new Brainrot(l.size(), id, name, rarity, price, income, height));
    }

    public static Brainrot of(int i) { return ALL[Math.floorMod(i, ALL.length)]; }

    public String tag() { return RARITY_COLOR[rarity] + name; }

    public static List<Brainrot> ofRarity(int r) {
        List<Brainrot> l = new ArrayList<>();
        for (Brainrot b : ALL) if (b.rarity == r) l.add(b);
        return l;
    }

    /** The chance (0..1) that a carpet brainrot has this rarity, for a given luck bonus. */
    public static double rarityChance(int r, double luck) {
        double tot = 0;
        for (int k = 0; k < RARITY_WEIGHT.length; k++) tot += weight(k, luck);
        return weight(r, luck) / tot;
    }

    static double weight(int r, double luck) { return RARITY_WEIGHT[r] * (1 + luck * r * r * 0.15); }

    /** Picks a carpet brainrot: rarity by weight (luck makes rare ones likelier), then a cheaper one within the rarity is a bit likelier. */
    public static Brainrot roll(RandomSource rnd, double luck, int minRarity) {
        double tot = 0;
        for (int k = minRarity; k < RARITY_WEIGHT.length; k++) tot += weight(k, luck);
        double x = rnd.nextDouble() * tot;
        int r = minRarity;
        for (; r < RARITY_WEIGHT.length - 1; r++) { x -= weight(r, luck); if (x < 0) break; }
        List<Brainrot> pool = ofRarity(r);
        double wt = 0;
        for (int i = 0; i < pool.size(); i++) wt += pool.size() - i;
        double y = rnd.nextDouble() * wt;
        for (int i = 0; i < pool.size(); i++) { y -= pool.size() - i; if (y < 0) return pool.get(i); }
        return pool.get(0);
    }
}
