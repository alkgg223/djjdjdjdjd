package com.trafficcraft.road;

import com.trafficcraft.vehicle.VehicleSpecs;
import java.util.ArrayList;
import java.util.List;

/** Editable property schemas for nodes, edges and the Road Planner (shared by the editor screens). */
public final class Props {
    public enum Kind { INT, FLOAT, BOOL, ENUM, FLAG, TEXT }

    public record Prop(String key, Kind kind, double min, double max, double step, String[] options, int bit, String group) {
        public static Prop i(String k, int min, int max, int step) { return new Prop(k, Kind.INT, min, max, step, null, 0, "cfg"); }
        public static Prop f(String k, double min, double max, double step) { return new Prop(k, Kind.FLOAT, min, max, step, null, 0, "cfg"); }
        public static Prop b(String k) { return new Prop(k, Kind.BOOL, 0, 1, 1, null, 0, "cfg"); }
        public static Prop e(String k, String... o) { return new Prop(k, Kind.ENUM, 0, 0, 0, o, 0, "cfg"); }
        public static Prop flag(String k, int bit) { return new Prop(k, Kind.FLAG, 0, 1, 1, null, bit, "flags"); }
        public static Prop t(String k) { return new Prop(k, Kind.TEXT, 0, 0, 0, null, 0, "cfg"); }
        public Prop root() { return new Prop(key, kind, min, max, step, options, bit, "root"); }
    }

    public static List<Prop> node(NodeType t) {
        List<Prop> l = new ArrayList<>();
        switch (t) {
            case ROAD -> {
                l.add(Prop.flag("lights", RoadNode.F_LIGHTS));
                l.add(Prop.flag("crosswalk", RoadNode.F_CROSSWALK));
                l.add(Prop.flag("stop", RoadNode.F_STOP));
                l.add(Prop.flag("yield", RoadNode.F_YIELD));
                l.add(Prop.flag("priority", RoadNode.F_PRIORITY));
                l.add(Prop.b("sharpTurns"));
                l.add(Prop.e("mode", "auto", "manual", "flash", "off"));
                l.add(Prop.i("green", 3, 120, 1));
                l.add(Prop.i("yellow", 1, 10, 1));
                l.add(Prop.i("allred", 0, 10, 1));
                l.add(Prop.b("leftPhase"));
                l.add(Prop.i("leftGreen", 2, 60, 1));
            }
            case SPAWN -> {
                l.add(Prop.b("enabled"));
                l.add(Prop.i("max", 1, 80, 1));
                l.add(Prop.i("interval", 1, 300, 1));
                l.add(Prop.i("minSpeed", 10, 200, 5));
                l.add(Prop.i("maxSpeed", 10, 200, 5));
                l.add(Prop.i("density", 5, 300, 5));
                l.add(Prop.e("time", "always", "day", "night", "rush"));
                l.add(Prop.b("random"));
                l.add(Prop.i("patient", 0, 100, 5));
                l.add(Prop.i("normal", 0, 100, 5));
                l.add(Prop.i("impatient", 0, 100, 5));
                for (String id : VehicleSpecs.ids()) l.add(Prop.i("m_" + id, 0, 40, 1));
            }
            case PARKING -> {
                l.add(Prop.i("spaces", 1, 60, 1));
                l.add(Prop.e("layout", "perpendicular", "angled", "parallel"));
                l.add(Prop.e("side", "both", "right", "left"));
                l.add(Prop.i("disabled", 0, 10, 1));
                l.add(Prop.i("reserved", 0, 10, 1));
                l.add(Prop.f("rowYaw", -1, 359, 15));
                l.add(Prop.i("stayMin", 5, 3600, 5));
                l.add(Prop.i("stayMax", 5, 3600, 5));
                l.add(Prop.i("parkChance", 0, 100, 5));
                l.add(Prop.b("gate"));
            }
            case PARK_SPOT -> {
                l.add(Prop.b("visible"));
                l.add(Prop.i("parkChance", 0, 100, 5));
                l.add(Prop.i("stayMin", 5, 3600, 5));
                l.add(Prop.i("stayMax", 5, 3600, 5));
                l.add(Prop.f("length", 4.5, 12, 0.5));
            }
            case BORDER -> {
                l.add(Prop.i("inspection", 1, 120, 1));
                l.add(Prop.i("denyChance", 0, 100, 5));
                l.add(Prop.i("officers", 0, 4, 1));
                l.add(Prop.b("passport"));
            }
            case GAS_STATION -> {
                l.add(Prop.f("price", 0.0, 1.0, 0.01));
                l.add(Prop.b("attendants"));
                l.add(Prop.i("serviceTime", 2, 60, 1));
            }
            case BUS_STOP -> l.add(Prop.i("dwell", 2, 60, 1));
            case PED_SPAWN -> {
                l.add(Prop.b("enabled"));
                l.add(Prop.i("max", 1, 80, 1));
                l.add(Prop.i("interval", 1, 300, 1));
                l.add(Prop.f("speed", 0.5, 2.5, 0.1));
                l.add(Prop.b("adults"));
                l.add(Prop.b("elderly"));
                l.add(Prop.b("children"));
                l.add(Prop.b("workers"));
                l.add(Prop.b("business"));
            }
            case PED -> {
                l.add(Prop.b("wait"));
                l.add(Prop.b("dest"));
            }
            case PED_DEST -> {
                l.add(Prop.i("dwellMin", 1, 600, 1));
                l.add(Prop.i("dwellMax", 1, 600, 1));
                l.add(Prop.t("name"));
            }
            default -> { }
        }
        return l;
    }

    public static List<Prop> tool() {
        List<Prop> l = new ArrayList<>();
        l.add(Prop.e("mode", RoadEditing.MODE_NAMES).root());
        l.add(Prop.i("lanes", 1, 6, 1).root());
        l.add(Prop.b("oneway").root());
        l.add(Prop.f("width", 2, 30, 0.5).root());
        l.add(Prop.f("speed", 10, 200, 5).root());
        l.add(Prop.b("overtake").root());
        l.add(Prop.b("pave").root());
        l.add(Prop.i("sidewalk", 0, 4, 1).root());
        l.add(Prop.b("clear").root());
        l.add(Prop.i("radius", 6, 30, 1).root());
        l.add(Prop.i("armLength", 6, 64, 2).root());
        l.add(Prop.b("branchTurns").root());
        l.add(Prop.f("pathWidth", 1, 6, 0.5).root());
        String[] types = new String[NodeType.values().length];
        for (int i = 0; i < types.length; i++) types[i] = NodeType.values()[i].name();
        l.add(Prop.e("nodeType", types).root());
        return l;
    }

    /** Defaults used when a node of this type is first edited. */
    public static void defaults(NodeType t, net.minecraft.nbt.CompoundTag c) {
        switch (t) {
            case SPAWN -> {
                put(c, "enabled", true);
                put(c, "max", 10);
                put(c, "interval", com.trafficcraft.TCConfig.DEFAULT_SPAWN_INTERVAL.get());
                put(c, "minSpeed", 40);
                put(c, "maxSpeed", 70);
                put(c, "density", 100);
                if (!c.contains("time")) c.putString("time", "always");
                put(c, "random", true);
                put(c, "patient", 30);
                put(c, "normal", 50);
                put(c, "impatient", 20);
            }
            case PARKING -> {
                put(c, "spaces", 6);
                if (!c.contains("layout")) c.putString("layout", "perpendicular");
                if (!c.contains("side")) c.putString("side", "both");
                if (!c.contains("rowYaw")) c.putFloat("rowYaw", -1);
                put(c, "stayMin", 30);
                put(c, "stayMax", 180);
                put(c, "parkChance", 35);
            }
            case PARK_SPOT -> {
                put(c, "visible", true);
                put(c, "parkChance", 30);
                put(c, "stayMin", 15);
                put(c, "stayMax", 60);
                if (!c.contains("length")) c.putFloat("length", 6f);
            }
            case BORDER -> {
                put(c, "inspection", com.trafficcraft.TCConfig.BORDER_INSPECTION.get());
                put(c, "denyChance", (int) (com.trafficcraft.TCConfig.BORDER_DENY_CHANCE.get() * 100));
                put(c, "officers", 1);
                put(c, "passport", true);
            }
            case GAS_STATION -> {
                if (!c.contains("price")) c.putFloat("price", 0.05f);
                put(c, "attendants", true);
                put(c, "serviceTime", 8);
            }
            case BUS_STOP -> put(c, "dwell", 8);
            case PED_SPAWN -> {
                put(c, "enabled", true);
                put(c, "max", 12);
                put(c, "interval", com.trafficcraft.TCConfig.DEFAULT_PED_INTERVAL.get());
                if (!c.contains("speed")) c.putFloat("speed", 1.2f);
                put(c, "adults", true);
                put(c, "elderly", true);
                put(c, "children", true);
                put(c, "workers", true);
                put(c, "business", true);
            }
            case PED_DEST -> {
                put(c, "dwellMin", 5);
                put(c, "dwellMax", 40);
            }
            default -> { }
        }
    }

    static void put(net.minecraft.nbt.CompoundTag c, String k, int v) {
        if (!c.contains(k)) c.putInt(k, v);
    }

    static void put(net.minecraft.nbt.CompoundTag c, String k, boolean v) {
        if (!c.contains(k)) c.putBoolean(k, v);
    }
}
