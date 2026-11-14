package com.realweather.client.space;

import com.realweather.RealWeather;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** The objects you have marked to come back to (kept in a small file beside the game's config). */
public final class Bookmarks {
    private static Set<String> ids;

    private Bookmarks() {}

    private static Path file() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("realweather-bookmarks.txt");
    }

    private static Set<String> ids() {
        if (ids == null) {
            ids = new LinkedHashSet<>();
            try {
                Path f = file();
                if (Files.exists(f)) {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        if (!line.isBlank()) {
                            ids.add(line.trim());
                        }
                    }
                }
            } catch (Exception e) {
                RealWeather.LOG.warn("Could not read bookmarks", e);
            }
        }
        return ids;
    }

    public static boolean has(String id) {
        return ids().contains(id);
    }

    public static void toggle(String id) {
        if (!ids().remove(id)) {
            ids().add(id);
        }
        try {
            Files.write(file(), ids(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            RealWeather.LOG.warn("Could not save bookmarks", e);
        }
    }

    public static Set<String> all() {
        return ids();
    }
}
