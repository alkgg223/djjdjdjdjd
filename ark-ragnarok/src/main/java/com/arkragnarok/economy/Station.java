package com.arkragnarok.economy;

/** crafting stations of the mod's economy; each has its own recipe list ({@link Recipes}) */
public enum Station {
    CAMPFIRE("campfire"), MORTAR("mortar_and_pestle"), SMITHY("smithy"), FORGE("refining_forge"), FABRICATOR("fabricator");

    public final String id;

    Station(String id) {
        this.id = id;
    }
}
