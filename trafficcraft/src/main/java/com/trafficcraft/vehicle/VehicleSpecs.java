package com.trafficcraft.vehicle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trafficcraft.TrafficCraft;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the generated vehicle metadata from the mod jar (available on both client and dedicated server). */
public final class VehicleSpecs {
    private static final Map<String, VehicleSpec> SPECS = new LinkedHashMap<>();

    public static void load() {
        SPECS.clear();
        try (InputStream in = VehicleSpecs.class.getResourceAsStream("/assets/trafficcraft/vehicles/index.json")) {
            if (in == null) throw new IllegalStateException("vehicles/index.json missing");
            var idx = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var e : idx) {
                String id = e.getAsString();
                try (InputStream s = VehicleSpecs.class.getResourceAsStream("/assets/trafficcraft/vehicles/" + id + ".json")) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(s, StandardCharsets.UTF_8)).getAsJsonObject();
                    SPECS.put(id, new VehicleSpec(o));
                }
            }
        } catch (Exception ex) {
            TrafficCraft.LOG.error("Could not load vehicle specs", ex);
        }
        TrafficCraft.LOG.info("TrafficCraft: loaded {} vehicle models", SPECS.size());
    }

    public static VehicleSpec get(String id) {
        VehicleSpec s = SPECS.get(id);
        return s != null ? s : SPECS.values().iterator().next();
    }

    public static boolean exists(String id) {
        return SPECS.containsKey(id);
    }

    public static List<String> ids() {
        return Collections.unmodifiableList(new ArrayList<>(SPECS.keySet()));
    }

    public static Iterable<VehicleSpec> all() {
        return SPECS.values();
    }
}
