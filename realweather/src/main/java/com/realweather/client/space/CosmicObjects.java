package com.realweather.client.space;

import net.minecraft.network.chat.Component;

/**
 * The hundred kinds of astronomical object, each with a real example where one is well known (its distance and
 * approximate position) or else a simulated one, clearly marked. Each is a target in the telescope and the planet map
 * (and a place to travel to), drawn by {@link ObjectVisuals}.
 */
public final class CosmicObjects {
    /** One kind: its group (0 stars, 1 compact objects, 2 planets, 3 clusters, 4 cosmic structures), example, distance, position. */
    public record Kind(int group, String example, String distance, String coords, double sky) {
        public boolean simulated() {
            return example == null;
        }
    }

    public static final Kind[] ALL = {
            // A. Stars and stellar systems
            k(0, "Proxima Centauri", "4.25 ly", "14h 29.7m  -62.7", 2e-4),
            k(0, "Bellatrix", "250 ly", "05h 25.1m  +06.3", 2e-4),
            k(0, "Gacrux", "88 ly", "12h 31.2m  -57.1", 2e-4),
            k(0, "Alnilam", "~2 000 ly", "05h 36.2m  -01.2", 2e-4),
            k(0, "Mu Cephei", "~3 000 ly", "21h 43.5m  +58.8", 2e-4),
            k(0, "Rho Cassiopeiae", "~3 400 ly", "23h 54.4m  +57.5", 2e-4),
            k(0, "WR 124", "~28 000 ly", "19h 11.5m  +16.9", 3e-4),
            k(0, "R Coronae Borealis", "~4 600 ly", "15h 48.6m  +28.2", 2e-4),
            k(0, "Delta Cephei", "887 ly", "22h 29.2m  +58.4", 2e-4),
            k(0, "Mira", "~300 ly", "02h 19.3m  -03.0", 4e-4),
            k(0, "T Tauri", "~480 ly", "04h 22.0m  +19.5", 3e-4),
            k(0, "AB Aurigae", "~510 ly", "04h 55.8m  +30.6", 3e-4),
            k(0, "Luhman 16", "6.5 ly", "10h 49.3m  -53.3", 2e-4),
            k(0, "Sirius B", "8.6 ly", "06h 45.1m  -16.7", 3e-4),
            k(0, "LP 400-22", "~1 400 ly", null, 2e-4),
            k(0, "R Leporis", "~1 300 ly", "04h 59.6m  -14.8", 2e-4),
            k(0, "WR 104", "~8 000 ly", "18h 02.1m  -23.6", 3e-4),
            k(0, "W Ursae Majoris", "~170 ly", "09h 43.8m  +56.0", 2e-4),
            k(0, "Algol", "90 ly", "03h 08.2m  +41.0", 3e-4),
            k(0, "HD 188753", "149 ly", "19h 54.0m  +41.9", 3e-4),
            // B. Compact objects
            k(1, "RX J0720.4-3125", "~1 000 ly", "07h 20.4m  -31.4", 2e-4),
            k(1, "PSR B1937+21", "~16 000 ly", "19h 39.6m  +21.6", 2e-4),
            k(1, "SGR 1900+14", "~20 000 ly", "19h 07.2m  +09.3", 2e-4),
            k(1, "Scorpius X-1", "~9 000 ly", "16h 19.9m  -15.6", 3e-4),
            k(1, "Gaia BH1", "1 560 ly", "17h 28.7m  -00.6", 3e-4),
            k(1, "HLX-1", "~290 million ly", "01h 10.5m  -46.0", 3e-4),
            k(1, "NGC 4889", "~300 million ly", "13h 00.1m  +28.0", 6e-4),
            k(1, "OJ 287", "~3.5 billion ly", "08h 54.8m  +20.1", 3e-4),
            k(1, "Cygnus X-1", "~7 200 ly", "19h 58.4m  +35.2", 3e-4),
            k(1, "Hercules X-1", "~20 000 ly", "16h 57.8m  +35.3", 3e-4),
            k(1, "GRB 221009A", "~2.4 billion ly", "19h 13.1m  +19.8", 3e-4),
            k(1, "SS 433", "~18 000 ly", "19h 11.8m  +05.0", 4e-4),
            k(1, "RX J1856.5-3754", "~400 ly", "18h 56.6m  -37.9", 2e-4),
            k(1, "HV 2112", "~200 000 ly", "01h 10.1m  -72.6", 2e-4),
            k(1, "OGLE-2011-BLG-0462", "~5 200 ly", "17h 51.7m  -29.9", 3e-4),
            // C. Planets and planetary objects
            k(2, "TrES-2b", "~750 ly", "19h 07.2m  +49.3", 2e-4),
            k(2, "LTT 9779 b", "~260 ly", "22h 31.0m  -37.6", 2e-4),
            k(2, "Gliese 667 Cc", "23.6 ly", "17h 19.0m  -35.0", 2e-4),
            k(2, "TOI-270 d", "73 ly", "04h 33.7m  -51.9", 2e-4),
            k(2, "TOI-1452 b", "100 ly", "19h 20.7m  +73.2", 2e-4),
            k(2, "K2-141 b", "~200 ly", "23h 23.7m  -01.2", 2e-4),
            k(2, null, "simulated", null, 2e-4),
            k(2, "PSO J318.5-22", "~80 ly", "21h 14.1m  -22.9", 2e-4),
            k(2, null, "simulated", null, 2e-4),
            k(2, "GJ 367 b", "31 ly", "09h 44.5m  -45.8", 2e-4),
            k(2, null, "simulated", null, 2e-4),
            k(2, "LP 791-18 d", "~86 ly", "11h 02.8m  -16.4", 2e-4),
            k(2, "LHS 3844 b", "~49 ly", "22h 41.9m  -69.2", 2e-4),
            k(2, "Kepler-51 d", "~2 600 ly", "19h 45.9m  +49.9", 2e-4),
            k(2, "TOI-1338 b", "~1 300 ly", "06h 08.5m  -59.5", 3e-4),
            k(2, "Kepler-1625 b I (candidate)", "~8 000 ly", "19h 41.7m  +40.0", 2e-4),
            k(2, "2020 CD3", "near Earth", null, 3e-4),
            k(2, "Quaoar", "~43 AU", null, 3e-4),
            k(2, "Arrokoth", "~45 AU", null, 2e-4),
            k(2, "Sedna", "~85 AU now", null, 3e-4),
            // D. Clusters and stellar structures
            k(3, "Omega Centauri", "~17 000 ly", "13h 26.8m  -47.5", 1.5e-3),
            k(3, "Pleiades", "444 ly", "03h 47.0m  +24.1", 2e-3),
            k(3, "R136", "~160 000 ly", "05h 38.7m  -69.1", 8e-4),
            k(3, "Scorpius-Centaurus Association", "~400 ly", "~16h  -30", 3e-3),
            k(3, "Ursa Major Moving Group", "~80 ly", "~12h  +56", 3e-3),
            k(3, "Andromeda's double nucleus", "2.5 million ly", "00h 42.7m  +41.3", 6e-4),
            k(3, "Milky Way nuclear star cluster", "26 700 ly", "17h 45.7m  -29.0", 8e-4),
            k(3, "GD-1 stellar stream", "~33 000 ly", null, 3e-3),
            k(3, "Antennae Galaxies", "~45 million ly", "12h 01.9m  -18.9", 1e-3),
            k(3, "Milky Way stellar halo", "~300 000 ly across", null, 3e-3),
            // E. Cosmic structures and other objects
            k(4, "Taurus Molecular Cloud", "~450 ly", "~04h 30m  +25", 3e-3),
            k(4, "Barnard 68", "~500 ly", "17h 22.6m  -23.8", 8e-4),
            k(4, "Coalsack Nebula", "~600 ly", "12h 51m  -62.5", 3e-3),
            k(4, "Iris Nebula (NGC 7023)", "~1 300 ly", "21h 01.6m  +68.2", 1e-3),
            k(4, "Butterfly Nebula (NGC 6302)", "~3 400 ly", "17h 13.7m  -37.1", 8e-4),
            k(4, "Cassiopeia A", "~11 000 ly", "23h 23.4m  +58.8", 8e-4),
            k(4, "3C 58", "~10 000 ly", "02h 05.6m  +64.8", 6e-4),
            k(4, "NGC 604", "2.7 million ly", "01h 34.5m  +30.8", 6e-4),
            k(4, "HH 47", "~1 400 ly", "08h 25.7m  -51.0", 6e-4),
            k(4, "HL Tauri", "~450 ly", "04h 31.6m  +18.2", 4e-4),
            k(4, "AU Microscopii", "32 ly", "20h 45.2m  -31.3", 4e-4),
            k(4, "Epsilon Aurigae", "~2 000 ly", "05h 02.0m  +43.8", 3e-4),
            k(4, "SS Cygni", "~370 ly", "21h 42.7m  +43.6", 3e-4),
            k(4, "NGC 891", "~30 million ly", "02h 22.6m  +42.3", 8e-4),
            k(4, "Musca filament", "~600 ly", "~12h 28m  -71", 2e-3),
            k(4, "LAB-1 (SSA22)", "~11.5 billion ly", "22h 17.4m  +00.2", 6e-4),
            k(4, "Stephan's Quintet", "~290 million ly", "22h 36.0m  +34.0", 1e-3),
            k(4, "Coma Cluster", "~320 million ly", "12h 59.8m  +28.0", 3e-3),
            k(4, "Laniakea Supercluster", "~250 million ly to its centre", null, 4e-3),
            k(4, "Bootes Void", "~700 million ly", "~14h 50m  +46", 4e-3),
            k(4, "Sloan Great Wall", "~1 billion ly", "~13h  +10", 4e-3),
            k(4, "3C 273", "~2.4 billion ly", "12h 29.1m  +02.1", 6e-4),
            k(4, "3C 279", "~5 billion ly", "12h 56.2m  -05.8", 5e-4),
            k(4, "Cygnus A", "~600 million ly", "19h 59.5m  +40.7", 1e-3),
            k(4, "Cosmic Horseshoe", "~5.6 billion ly (lens)", "11h 48.6m  +19.5", 6e-4),
            k(4, "Einstein Cross (Q2237+030)", "~8 billion ly (quasar)", "22h 40.5m  +03.4", 5e-4),
            k(4, "ASASSN-14li", "~290 million ly", "12h 48.3m  +17.8", 4e-4),
            k(4, "FRB 20121102A", "~3 billion ly", "05h 32.0m  +33.1", 4e-4),
            k(4, "CMB Cold Spot", "the edge of what we can see", "~03h 15m  -19.5", 4e-3),
            k(4, null, "simulated illustration", null, 3e-3),
            k(4, "Abell 222/223 filament", "~2.4 billion ly", "01h 37m  -12.9", 2e-3),
            k(4, "Smith's Cloud", "~40 000 ly", null, 2e-3),
            k(4, "NGC 3079", "~50 million ly", "10h 02.0m  +55.7", 8e-4),
            k(4, "W44", "~10 000 ly", "18h 56.0m  +01.4", 8e-4),
            k(4, "GW170817 (NGC 4993)", "~130 million ly", "13h 09.8m  -23.4", 4e-4),
    };

    private CosmicObjects() {}

    private static Kind k(int group, String example, String distance, String coords, double sky) {
        return new Kind(group, example, distance, coords, sky);
    }

    public static String id(int n) {
        return String.format(java.util.Locale.ROOT, "obj_%02d", n);
    }

    /** The name shown for one: its kind and its example (or "simulated"). */
    public static Component label(int n) {
        Kind k = ALL[n];
        return Component.translatable("realweather.object." + n).copy().append(": ")
                .append(k.simulated() ? Component.translatable("realweather.object.simulated") : Component.literal(k.example()));
    }

    /** Every one into the catalogue, each its own place in the sky. */
    static void register() {
        for (int n = 0; n < ALL.length; n++) {
            Kind k = ALL[n];
            String id = id(n);
            float[] col = tint(n);
            Catalog.Body b = Catalog.body(id, Catalog.OBJECT, k.sky(), col[0], col[1], col[2]);
            b.eventType = n;
            Catalog.Target t = Catalog.dynamic(id, Catalog.OBJECTS, Catalog.skyAt(id), k.distance(), b, label(n));
            Catalog.SYSTEMS.add(t.sys);
            Catalog.TARGETS.add(t);
        }
    }

    /** Its colour as a point of light from afar. */
    private static float[] tint(int n) {
        return switch (ALL[n].group()) {
            case 0 -> new float[]{1F, 0.9F, 0.8F};
            case 1 -> new float[]{0.75F, 0.85F, 1F};
            case 2 -> new float[]{0.9F, 0.85F, 0.75F};
            case 3 -> new float[]{1F, 0.92F, 0.75F};
            default -> new float[]{0.85F, 0.8F, 1F};
        };
    }
}
