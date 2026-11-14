package com.realweather.client.space;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The telescope's spectrograph: the light of the target spread out from violet to red, as an astronomer sees it.
 * Stars show the glow of their temperature crossed by dark absorption lines (hydrogen, sodium, calcium, the bands of
 * titanium oxide in cool red stars); nebulae only bright emission lines (hydrogen-alpha, doubly ionised oxygen);
 * planets the Sun's light reflected, with the bands their air takes out (methane in the ice giants); pulsars and
 * black holes' jets the smooth, featureless glow of synchrotron light; a quasar its broad lines shifted far to the
 * red by the expansion of the universe; comets the green glow of carbon; merging black holes no light at all, only
 * their gravitational-wave chirp.
 */
final class Spectra {
    private static final double LO = 380, HI = 750;

    /** One line: wavelength (nm), strength (positive: bright emission, negative: dark absorption), its name. */
    private record Line(double nm, double depth, String name) {}

    private Spectra() {}

    /** A star's temperature from its colour. */
    static double temperature(Catalog.Body b) {
        if (b.kind == Catalog.DWARF) {
            return 25000;
        }
        double k = b.b / Math.max(0.05, b.r);
        double[][] t = {{0.25, 3200}, {0.42, 4400}, {0.72, 5800}, {0.92, 6800}, {1.22, 9500}, {1.61, 25000}};
        if (k <= t[0][0]) {
            return t[0][1];
        }
        for (int i = 1; i < t.length; i++) {
            if (k <= t[i][0]) {
                double f = (k - t[i - 1][0]) / (t[i][0] - t[i - 1][0]);
                return t[i - 1][1] + (t[i][1] - t[i - 1][1]) * f;
            }
        }
        return 30000;
    }

    private static double planck(double nm, double temp) {
        double l = nm * 1.0E-9;
        return 1 / (Math.pow(l, 5) * (Math.exp(0.014388 / (l * temp)) - 1));
    }

    /** RGB (0..1) of a wavelength in nm. */
    private static float[] colour(double nm) {
        double r, g, b;
        if (nm < 440) {
            r = -(nm - 440) / 60;
            g = 0;
            b = 1;
        } else if (nm < 490) {
            r = 0;
            g = (nm - 440) / 50;
            b = 1;
        } else if (nm < 510) {
            r = 0;
            g = 1;
            b = -(nm - 510) / 20;
        } else if (nm < 580) {
            r = (nm - 510) / 70;
            g = 1;
            b = 0;
        } else if (nm < 645) {
            r = 1;
            g = -(nm - 645) / 65;
            b = 0;
        } else {
            r = 1;
            g = 0;
            b = 0;
        }
        double edge = nm < 420 ? 0.3 + 0.7 * (nm - 380) / 40 : nm > 700 ? 0.3 + 0.7 * (750 - nm) / 50 : 1;
        return new float[]{(float) (r * edge), (float) (g * edge), (float) (b * edge)};
    }

    private interface Shape {
        double at(double nm);
    }

    public static void draw(GuiGraphics g, Catalog.Target t, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Catalog.Body b = t.body;
        int pw = Math.min(260, w - 20), ph = 96, x0 = w - pw - 8, y0 = 22;
        g.fill(x0 - 4, y0 - 14, x0 + pw + 4, y0 + ph + 30, 0xC0000000);
        List<Line> lines = new ArrayList<>();
        Shape cont;
        String note;
        switch (b.kind) {
            case Catalog.STAR, Catalog.DWARF -> {
                double temp = temperature(b);
                cont = nm -> planck(nm, temp);
                note = String.format(Locale.ROOT, "T ~ %,.0f K", temp);
                lines.add(new Line(656.3, -0.5, "Ha"));
                lines.add(new Line(486.1, -0.45, "Hb"));
                lines.add(new Line(434.0, -0.35, "Hg"));
                if (temp < 8000) {
                    lines.add(new Line(589.3, -0.4, "Na D"));
                    lines.add(new Line(393.4, -0.5, "Ca K"));
                    lines.add(new Line(396.8, -0.45, "Ca H"));
                    lines.add(new Line(517.0, -0.25, "Mg b"));
                }
                if (temp < 4000) {
                    for (double band : new double[]{615, 632, 668, 705, 712}) {
                        lines.add(new Line(band, -0.35, band == 668 ? "TiO" : ""));
                    }
                }
                if (temp > 15000) {
                    lines.add(new Line(447.1, -0.3, "He I"));
                }
            }
            case Catalog.NEBULA -> {
                cont = nm -> 0.03;
                note = Component.translatable("realweather.spectrum.emission").getString();
                lines.add(new Line(656.3, 1, "Ha"));
                lines.add(new Line(658.4, 0.35, "[NII]"));
                lines.add(new Line(500.7, 0.75, "[OIII]"));
                lines.add(new Line(495.9, 0.3, ""));
                lines.add(new Line(486.1, 0.35, "Hb"));
                lines.add(new Line(671.6, 0.25, "[SII]"));
            }
            case Catalog.PULSAR, Catalog.BLACKHOLE -> {
                boolean quasar = "ton618".equals(b.id);
                cont = nm -> Math.pow(nm / 500.0, -0.7);
                note = Component.translatable(quasar ? "realweather.spectrum.quasar" : "realweather.spectrum.synchrotron").getString();
                if (quasar) {
                    // Lyman-alpha (121.6 nm) and C IV (154.9 nm), moved into visible light by a redshift of 2.22.
                    lines.add(new Line(121.6 * 3.22, 1.2, "Ly-a (z 2.22)"));
                    lines.add(new Line(154.9 * 3.22, 0.6, "C IV"));
                    lines.add(new Line(190.9 * 3.22, 0.4, "C III]"));
                } else if (b.kind == Catalog.BLACKHOLE) {
                    lines.add(new Line(656.3 * 1.004, 0.4, "Ha"));
                }
            }
            case Catalog.COMET -> {
                cont = nm -> planck(nm, 5800) * 0.4;
                note = Component.translatable("realweather.spectrum.comet").getString();
                lines.add(new Line(516.5, 0.8, "C2"));
                lines.add(new Line(473.7, 0.45, "C2"));
                lines.add(new Line(563.5, 0.35, "C2"));
                lines.add(new Line(388.3, 0.6, "CN"));
            }
            case Catalog.MERGER -> {
                chirp(g, x0, y0, pw, ph);
                g.drawString(mc.font, Component.translatable("realweather.spectrum.waves"), x0, y0 - 11, 0xFFFFE080, true);
                return;
            }
            default -> {
                // A planet: sunlight reflected, coloured by its surface and air.
                float pr = b.r, pg = b.g, pb = b.b;
                boolean ice = "uranus".equals(b.id) || "neptune".equals(b.id);
                cont = nm -> planck(nm, 5800) * (nm > 600 ? pr : nm > 500 ? pg : pb);
                note = Component.translatable("realweather.spectrum.reflected").getString();
                lines.add(new Line(589.3, -0.2, "Na D"));
                lines.add(new Line(656.3, -0.2, "Ha"));
                if (b.kind == Catalog.GAS || ice) {
                    lines.add(new Line(619, ice ? -0.7 : -0.35, "CH4"));
                    lines.add(new Line(727, ice ? -0.8 : -0.4, "CH4"));
                    lines.add(new Line(543, ice ? -0.3 : -0.15, ""));
                }
                if (b.air && b.kind == Catalog.ROCKY) {
                    lines.add(new Line(687, -0.35, "O2"));
                }
            }
        }
        // The curve, normalised to its peak.
        double max = 1.0E-30;
        for (int i = 0; i < pw; i++) {
            max = Math.max(max, cont.at(LO + (HI - LO) * i / (pw - 1.0)));
        }
        for (int i = 0; i < pw; i++) {
            double nm = LO + (HI - LO) * i / (pw - 1.0);
            double v = cont.at(nm) / max;
            for (Line l : lines) {
                double prof = Math.exp(-Math.pow((nm - l.nm) / (l.depth > 0 ? 1.6 : 1.2), 2));
                v = l.depth > 0 ? v + l.depth * prof : v * (1 + l.depth * prof);
            }
            v = Math.max(0, Math.min(1.3, v));
            float[] c = colour(nm);
            int hgt = (int) (v / 1.3 * ph);
            int col = 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            g.fill(x0 + i, y0 + ph - hgt, x0 + i + 1, y0 + ph, col);
        }
        for (Line l : lines) {
            if (l.name.isEmpty() || l.nm < LO || l.nm > HI) {
                continue;
            }
            int x = x0 + (int) ((l.nm - LO) / (HI - LO) * (pw - 1));
            g.fill(x, y0 + ph + 1, x + 1, y0 + ph + 4, 0xFFFFFFFF);
            g.drawString(mc.font, l.name, x - mc.font.width(l.name) / 2, y0 + ph + 5, l.depth > 0 ? 0xFFFFE080 : 0xFFA0C0FF, false);
        }
        g.drawString(mc.font, "380 nm", x0, y0 + ph + 16, 0xFF909090, false);
        g.drawString(mc.font, "750 nm", x0 + pw - mc.font.width("750 nm"), y0 + ph + 16, 0xFF909090, false);
        g.drawString(mc.font, Component.translatable("realweather.spectrum.title", Component.translatable("realweather.target." + t.id), note), x0,
                y0 - 11, 0xFFFFE080, true);
    }

    /** Two black holes merging give no light: their gravitational-wave chirp instead, rising to the merger. */
    private static void chirp(GuiGraphics g, int x0, int y0, int pw, int ph) {
        double t = (System.currentTimeMillis() % 6000) / 6000.0;
        int prev = -1;
        for (int i = 0; i < pw; i++) {
            double s = (double) i / pw;
            double left = Math.max(1.0E-3, 0.9 - s);
            double phase = -60 * Math.pow(left, 0.625);
            double amp = s < 0.9 ? Math.min(1, 0.12 / Math.pow(left, 0.25)) : Math.exp(-(s - 0.9) * 40);
            double v = amp * Math.cos(phase * 2 + t * 6);
            int y = y0 + ph / 2 - (int) (v * ph * 0.45);
            if (prev >= 0) {
                g.fill(x0 + i, Math.min(prev, y), x0 + i + 1, Math.max(prev, y) + 1, s < 0.9 ? 0xFF80D0FF : 0xFFFFD080);
            }
            prev = y;
        }
    }
}
