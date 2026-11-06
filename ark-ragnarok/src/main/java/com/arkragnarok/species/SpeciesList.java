package com.arkragnarok.species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 27 creatures.  Health / damage / torpor / stamina / weight are ARK base values at level 1 (see Species for the
 * conversion); speeds are Minecraft movement attributes; affinity is the taming "food value" needed at level 1.
 */
public final class SpeciesList {
    private SpeciesList() {}

    private static final Map<String, Species> BY_ID = new LinkedHashMap<>();

    private static Species.Builder b(String id) {
        return new Species.Builder(id);
    }

    private static void add(Species.Builder b) {
        SpeciesModels.Meta m = SpeciesModels.get(b.id);
        BY_ID.put(b.id, new Species(b, m));
    }

    static {
        // --------------------------------------------------------------------- big carnivores
        add(b("tyrannosaurus").stats(1100, 62, 1550, 420, 500).speed(0.24f, 1.85f).kibble(Kibble.EXCEPTIONAL).affinity(1950, 20)
                .attack("shake", 2.2f).armor(6));
        add(b("giganotosaurus").stats(2400, 72, 2000, 400, 700).speed(0.25f, 1.9f).kibble(Kibble.EXTRAORDINARY).affinity(5000, 30)
                .attack("shake", 2.6f).armor(8));
        add(b("spinosaurus").stats(700, 40, 850, 350, 350).speed(0.23f, 1.8f).diet(Diet.PISCIVORE).kibble(Kibble.SUPERIOR)
                .affinity(1800, 18).attack("shake", 2.4f).armor(4));
        add(b("allosaurus").stats(630, 35, 1250, 350, 300).speed(0.26f, 1.9f).kibble(Kibble.SUPERIOR).affinity(1300, 16).pack()
                .attack("shake", 1.8f).armor(3));
        add(b("carnotaurus").stats(420, 35, 1000, 300, 300).speed(0.28f, 1.95f).kibble(Kibble.REGULAR).affinity(900, 14)
                .attack("shake", 1.6f).armor(2));
        add(b("yutyrannus").stats(1100, 30, 1000, 400, 400).speed(0.25f, 1.85f).kibble(Kibble.EXCEPTIONAL).affinity(1950, 18)
                .attack("shake", 2.0f).armor(4));
        add(b("megalosaurus").stats(750, 40, 1000, 350, 350).speed(0.25f, 1.9f).kibble(Kibble.SUPERIOR).affinity(1400, 16)
                .nocturnal().attack("shake", 1.6f).armor(3));
        add(b("baryonyx").stats(440, 25, 1000, 400, 300).speed(0.26f, 1.85f).diet(Diet.PISCIVORE).kibble(Kibble.SUPERIOR)
                .affinity(1100, 14).attack("shake", 1.6f).armor(2));
        add(b("therizinosaurus").stats(870, 40, 2000, 380, 350).speed(0.24f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.EXCEPTIONAL).affinity(1800, 16).attack("slash", 2.2f).armor(3));
        // --------------------------------------------------------------------- small and medium carnivores
        add(b("utahraptor").stats(200, 15, 180, 150, 140).speed(0.32f, 2.0f).kibble(Kibble.SIMPLE).affinity(600, 10).pack()
                .attack("pounce", 0.8f));
        add(b("deinonychus").stats(200, 20, 250, 160, 140).speed(0.33f, 2.0f).kibble(Kibble.REGULAR).affinity(650, 10).pack()
                .attack("pounce", 0.7f));
        add(b("dilophosaurus").stats(130, 11, 75, 100, 45).speed(0.27f, 1.7f).kibble(Kibble.BASIC).affinity(260, 8).pack()
                .attack("spit", 0.6f));
        add(b("troodon").stats(140, 10, 100, 150, 80).speed(0.3f, 1.9f).kibble(Kibble.BASIC).affinity(280, 8).pack().nocturnal()
                .attack("pounce", 0.5f));
        add(b("compsognathus").stats(50, 4, 25, 100, 25).speed(0.3f, 1.8f).kibble(Kibble.BASIC).affinity(120, 6).pack()
                .attack("peck", 0.3f));
        add(b("oviraptor").stats(140, 15, 120, 100, 80).speed(0.3f, 1.9f).diet(Diet.OMNIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.BASIC).affinity(300, 8).attack("peck", 0.4f));
        // --------------------------------------------------------------------- herbivores
        add(b("triceratops").stats(375, 32, 250, 150, 365).speed(0.22f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(1000, 14).attack("horn", 1.8f).armor(6));
        add(b("pachyrhinosaurus").stats(260, 18, 300, 250, 250).speed(0.23f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(800, 12).attack("horn", 1.2f).armor(4));
        add(b("stegosaurus").stats(570, 35, 850, 200, 400).speed(0.2f, 1.6f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(1300, 16).attack("tail", 3.5f).armor(6));
        add(b("ankylosaurus").stats(700, 50, 420, 175, 250).speed(0.17f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(1200, 16).attack("tail", 3.0f).armor(14));
        add(b("brontosaurus").stats(2070, 42, 2000, 240, 1600).speed(0.18f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.SUPERIOR).affinity(3500, 24).attack("stomp", 3.5f).armor(6));
        add(b("diplodocus").stats(1700, 18, 1200, 200, 1200).speed(0.19f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .passive().kibble(Kibble.REGULAR).affinity(1500, 20).attack("tail", 5.0f).armor(3));
        add(b("parasaurolophus").stats(200, 8, 150, 300, 220).speed(0.27f, 2.0f).diet(Diet.HERBIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.BASIC).affinity(500, 10).attack("rear", 1.0f));
        add(b("iguanodon").stats(400, 20, 500, 250, 375).speed(0.25f, 1.85f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.SIMPLE).affinity(900, 12).attack("rear", 1.2f).armor(2));
        add(b("gallimimus").stats(130, 5, 120, 280, 150).speed(0.34f, 2.2f).diet(Diet.HERBIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.SIMPLE).affinity(400, 8).pack().attack("peck", 0.6f));
        add(b("pachycephalosaurus").stats(280, 18, 350, 250, 200).speed(0.26f, 1.9f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.SIMPLE).affinity(600, 10).attack("headbutt", 0.8f).armor(3));
        add(b("moschops").stats(280, 12, 250, 200, 200).speed(0.21f, 1.6f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .passive().kibble(Kibble.SIMPLE).affinity(500, 10).attack("bite", 0.8f).armor(2));
        add(b("dodo").stats(40, 5, 50, 100, 50).speed(0.18f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.BASIC).affinity(100, 6).attack("peck", 0.3f));
    }

    public static Species get(String id) {
        return BY_ID.get(id);
    }

    public static List<Species> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }
}
