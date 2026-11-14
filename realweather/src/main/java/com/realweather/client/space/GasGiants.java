package com.realweather.client.space;

/**
 * How each gas giant's clouds move: its winds, its great storms (Jupiter's Great Red Spot and Oval BA, the cyclones
 * crowding its poles; Saturn's hexagon and polar vortex; Neptune's Great Dark Spot and the Scooter), and storms on
 * other stars' giants.
 */
final class GasGiants {
    private GasGiants() {}

    static SpaceShaders.GasLook look(Catalog.Body b, double seconds) {
        SpaceShaders.GasLook g = new SpaceShaders.GasLook();
        g.map = b.map;
        g.oblate = b.oblate;
        g.time = (float) (seconds % 100000.0);
        g.glow = b.glow;
        if (b.air) {
            g.airR = b.ar;
            g.airG = b.ag;
            g.airB = b.ab;
        }
        switch (b.id) {
            case "jupiter" -> {
                g.oblate = 0.935F;
                g.bands = 1.4F;
                g.storm(0, 0, -22, 7, 0.8).storm(1, 70, -33, 4, 0.9).storm(2, 0, 86, 6, -1.4).storm(3, 180, -86, 6, 1.3);
            }
            case "saturn" -> {
                g.oblate = 0.9F;
                g.bands = 1.6F;
                g.hexagon = 1F;
                g.storm(0, 0, 89, 7, 1.6).storm(1, 120, -88, 5, -1.2).storm(2, 40, 38, 3, 0.8);
            }
            case "uranus" -> g.bands = 0.4F;
            case "neptune" -> {
                g.bands = 1.8F;
                g.storm(0, 30, -20, 8, 1.2).storm(1, 150, -42, 2.5, 0.9).storm(2, 250, -55, 3, -0.8);
            }
            default -> {
                long h = b.id.hashCode();
                g.bands = 0.8F + Space3D.h(h, 1);
                g.storm(0, Space3D.h(h, 2) * 360, (Space3D.h(h, 3) - 0.5) * 70, 5 + 6 * Space3D.h(h, 4), 0.8 + Space3D.h(h, 5));
                g.storm(1, Space3D.h(h, 6) * 360, (Space3D.h(h, 7) - 0.5) * 80, 3 + 3 * Space3D.h(h, 8), -1);
            }
        }
        return g;
    }
}
