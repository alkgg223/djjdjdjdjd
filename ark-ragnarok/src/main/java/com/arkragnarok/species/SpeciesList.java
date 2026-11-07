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
        // --------------------------------------------------------------------- 35 more: theropods
        add(b("velociraptor").stats(140, 10, 120, 150, 100).speed(0.33f, 2.05f).kibble(Kibble.BASIC).affinity(300, 8).pack().attack("pounce", 0.6f));
        add(b("coelophysis").stats(100, 8, 90, 150, 70).speed(0.31f, 2.0f).kibble(Kibble.BASIC).affinity(250, 8).pack().attack("pounce", 0.5f));
        add(b("ceratosaurus").stats(600, 36, 900, 320, 320).speed(0.25f, 1.85f).kibble(Kibble.SUPERIOR).affinity(1300, 16).attack("shake", 1.7f).armor(3));
        add(b("albertosaurus").stats(650, 40, 1100, 350, 330).speed(0.27f, 1.95f).kibble(Kibble.SUPERIOR).affinity(1400, 16).pack()
                .attack("shake", 1.8f).armor(3));
        add(b("daspletosaurus").stats(950, 55, 1400, 400, 450).speed(0.24f, 1.85f).kibble(Kibble.EXCEPTIONAL).affinity(1800, 18)
                .attack("shake", 2.0f).armor(5));
        add(b("tarbosaurus").stats(1000, 58, 1450, 410, 470).speed(0.24f, 1.85f).kibble(Kibble.EXCEPTIONAL).affinity(1850, 20)
                .attack("shake", 2.1f).armor(5));
        add(b("acrocanthosaurus").stats(1200, 60, 1600, 400, 550).speed(0.24f, 1.85f).kibble(Kibble.EXCEPTIONAL).affinity(2200, 22)
                .attack("shake", 2.2f).armor(5));
        add(b("carcharodontosaurus").stats(2000, 70, 1900, 400, 650).speed(0.25f, 1.9f).kibble(Kibble.EXTRAORDINARY).affinity(4200, 28)
                .attack("shake", 2.5f).armor(7));
        add(b("torvosaurus").stats(1100, 55, 1500, 380, 500).speed(0.24f, 1.85f).kibble(Kibble.EXCEPTIONAL).affinity(2000, 20)
                .attack("shake", 2.1f).armor(5));
        add(b("suchomimus").stats(650, 32, 900, 380, 330).speed(0.25f, 1.85f).diet(Diet.PISCIVORE).kibble(Kibble.SUPERIOR).affinity(1400, 16)
                .attack("shake", 2.0f).armor(3));
        add(b("irritator").stats(380, 24, 600, 300, 220).speed(0.27f, 1.85f).diet(Diet.PISCIVORE).kibble(Kibble.REGULAR).affinity(900, 12)
                .attack("shake", 1.4f).armor(2));
        add(b("cryolophosaurus").stats(400, 28, 650, 300, 240).speed(0.28f, 1.9f).kibble(Kibble.REGULAR).affinity(900, 12).attack("shake", 1.4f).armor(2));
        add(b("majungasaurus").stats(480, 34, 900, 320, 300).speed(0.25f, 1.8f).kibble(Kibble.REGULAR).affinity(1000, 14).attack("shake", 1.5f).armor(3));
        add(b("concavenator").stats(420, 30, 700, 320, 260).speed(0.28f, 1.9f).kibble(Kibble.REGULAR).affinity(950, 12).attack("shake", 1.4f).armor(2));
        add(b("herrerasaurus").stats(220, 16, 260, 200, 140).speed(0.3f, 1.95f).kibble(Kibble.SIMPLE).affinity(500, 10).attack("pounce", 0.8f));
        add(b("ornithomimus").stats(110, 5, 100, 300, 120).speed(0.35f, 2.25f).diet(Diet.HERBIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.SIMPLE).affinity(350, 8).pack().attack("peck", 0.5f));
        add(b("deinocheirus").stats(1100, 32, 1500, 300, 600).speed(0.22f, 1.7f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.EXCEPTIONAL).affinity(2200, 20).attack("slash", 2.4f).armor(3));
        add(b("citipati").stats(120, 12, 110, 120, 70).speed(0.3f, 1.9f).diet(Diet.OMNIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.BASIC).affinity(280, 8).attack("peck", 0.4f));
        add(b("hypsilophodon").stats(60, 4, 50, 150, 30).speed(0.3f, 2.2f).diet(Diet.HERBIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.BASIC).affinity(150, 6).pack().attack("peck", 0.3f));
        // --------------------------------------------------------------------- 35 more: plant eaters on four legs
        add(b("edmontosaurus").stats(500, 14, 500, 300, 450).speed(0.24f, 1.85f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.SIMPLE).affinity(1000, 14).pack().attack("rear", 1.3f).armor(2));
        add(b("maiasaura").stats(380, 10, 400, 280, 380).speed(0.23f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.SIMPLE).affinity(800, 12).pack().attack("rear", 1.1f).armor(2));
        add(b("psittacosaurus").stats(90, 6, 80, 150, 60).speed(0.25f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.SKITTISH)
                .kibble(Kibble.BASIC).affinity(200, 6).attack("bite", 0.4f));
        add(b("styracosaurus").stats(320, 26, 260, 160, 300).speed(0.22f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(900, 12).attack("horn", 1.5f).armor(5));
        add(b("centrosaurus").stats(300, 22, 260, 160, 280).speed(0.22f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(850, 12).pack().attack("horn", 1.4f).armor(5));
        add(b("chasmosaurus").stats(280, 20, 240, 170, 260).speed(0.22f, 1.8f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(800, 12).attack("horn", 1.3f).armor(5));
        add(b("protoceratops").stats(120, 8, 100, 150, 90).speed(0.22f, 1.7f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.BASIC).affinity(300, 8).attack("bite", 0.5f).armor(2));
        add(b("kentrosaurus").stats(380, 30, 500, 200, 280).speed(0.21f, 1.65f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(900, 14).attack("tail", 2.5f).armor(5));
        add(b("euoplocephalus").stats(650, 45, 420, 175, 260).speed(0.17f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.TERRITORIAL)
                .kibble(Kibble.REGULAR).affinity(1100, 16).attack("tail", 2.8f).armor(13));
        add(b("nodosaurus").stats(700, 25, 450, 160, 300).speed(0.16f, 1.45f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.REGULAR).affinity(1100, 16).attack("bite", 1.0f).armor(15));
        // --------------------------------------------------------------------- 35 more: sauropods
        add(b("brachiosaurus").stats(2600, 40, 2200, 240, 1800).speed(0.17f, 1.45f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.SUPERIOR).affinity(4000, 26).attack("stomp", 3.8f).armor(6));
        add(b("argentinosaurus").stats(3600, 60, 3000, 240, 2600).speed(0.16f, 1.4f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.EXCEPTIONAL).affinity(6000, 30).attack("stomp", 4.5f).armor(8));
        add(b("apatosaurus").stats(2200, 45, 2100, 240, 1700).speed(0.18f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.SUPERIOR).affinity(3600, 24).attack("tail", 4.5f).armor(6));
        add(b("camarasaurus").stats(1600, 35, 1500, 230, 1200).speed(0.18f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.REGULAR).affinity(2600, 22).attack("stomp", 3.2f).armor(5));
        add(b("mamenchisaurus").stats(1900, 30, 1700, 220, 1300).speed(0.18f, 1.5f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .passive().kibble(Kibble.SUPERIOR).affinity(3000, 24).attack("tail", 4.8f).armor(4));
        add(b("dreadnoughtus").stats(3000, 55, 2600, 240, 2200).speed(0.16f, 1.4f).diet(Diet.HERBIVORE).temper(Temper.NEUTRAL)
                .kibble(Kibble.EXCEPTIONAL).affinity(5000, 28).attack("stomp", 4.2f).armor(7));
    }

    public static Species get(String id) {
        return BY_ID.get(id);
    }

    public static List<Species> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }
}
