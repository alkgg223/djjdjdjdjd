package com.realweather.client.space;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comparing two objects side by side: what each is, how far away, how big on the sky, and what is known of it; with a
 * bar showing their distances on one scale and discs showing their sizes on the sky. Pick them with the search (or
 * "Compare" in the planet map's close-up view).
 */
public class CompareScreen extends Screen {
    static Catalog.Target a, b;
    private static boolean slotB;
    private static String query = "";
    private final Screen back;
    private boolean typing;

    public CompareScreen(Screen back) {
        super(Component.translatable("realweather.compare.title"));
        this.back = back;
    }

    /** Put a target into the comparison (the first free side, or the one not chosen last). */
    static void add(Catalog.Target t) {
        if (a == null || slotB && b != null) {
            a = t;
            slotB = true;
        } else {
            b = t;
            slotB = false;
        }
    }

    @Override
    protected void init() {
        int half = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("realweather.compare.slot", "A"), x -> {
            slotB = false;
            rebuildWidgets();
        }).bounds(half - 104, 22, 50, 18).build()).active = slotB;
        addRenderableWidget(Button.builder(Component.translatable("realweather.compare.slot", "B"), x -> {
            slotB = true;
            rebuildWidgets();
        }).bounds(half - 50, 22, 50, 18).build()).active = !slotB;
        EditBox search = new EditBox(font, half + 4, 22, 140, 18, Component.translatable("realweather.map.search"));
        search.setHint(Component.translatable("realweather.map.search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(v -> {
            if (!v.equals(query)) {
                query = v;
                typing = true;
                rebuildWidgets();
            }
        });
        addRenderableWidget(search);
        if (typing) {
            setFocused(search);
            search.setFocused(true);
        }
        // Matches for the search, to choose from.
        if (!query.isBlank()) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            int n = 0;
            for (Catalog.Target t : Catalog.TARGETS) {
                if (n >= 6) {
                    break;
                }
                if (t.label().getString().toLowerCase(Locale.ROOT).contains(q) || t.id.contains(q)) {
                    int row = n++;
                    addRenderableWidget(Button.builder(t.label(), x -> {
                        if (slotB) {
                            b = t;
                        } else {
                            a = t;
                        }
                        slotB = !slotB;
                        query = "";
                        typing = false;
                        rebuildWidgets();
                    }).bounds(half + 4, 42 + row * 19, 180, 18).build());
                }
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), x -> onClose()).bounds(half - 50, height - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFE080);
        int top = query.isBlank() ? 46 : 46 + 6 * 19;
        int w = width / 2 - 14;
        panel(g, a, "A", 8, top, w);
        panel(g, b, "B", width / 2 + 6, top, w);
        if (a != null && b != null) {
            scale(g, top);
        }
    }

    private void panel(GuiGraphics g, Catalog.Target t, String slot, int x, int y, int w) {
        g.fill(x - 2, y - 2, x + w + 2, height - 64, 0x80000814);
        if (t == null) {
            g.drawString(font, Component.translatable("realweather.compare.empty", slot), x + 2, y + 2, 0xFF909090, false);
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal(slot + ": ").append(t.label()).withStyle(net.minecraft.ChatFormatting.YELLOW), w));
        String tab = switch (t.group) {
            case Catalog.STARS -> "stars";
            case Catalog.EXOPLANETS -> "exoplanets";
            case Catalog.NEBULAE -> "nebulae";
            case Catalog.GALAXIES -> "galaxies";
            case Catalog.EVENTS -> "events";
            case Catalog.OBJECTS -> "objects";
            default -> "planets";
        };
        lines.addAll(font.split(Component.translatable("realweather.telescope.tab." + tab).withStyle(net.minecraft.ChatFormatting.AQUA), w));
        lines.addAll(font.split(Component.translatable("realweather.object.distance", t.sys.distance), w));
        lines.addAll(font.split(Component.translatable("realweather.compare.size", arc(t.body.radius * 2)), w));
        if (t.group == Catalog.OBJECTS && t.body.eventType >= 0 && t.body.eventType < CosmicObjects.ALL.length) {
            CosmicObjects.Kind k = CosmicObjects.ALL[t.body.eventType];
            lines.addAll(font.split(k.simulated() ? Component.translatable("realweather.object.simulated_note")
                    : Component.translatable("realweather.object.real", k.example()), w));
        }
        Component info = t.group == Catalog.EVENTS ? Component.translatable("realweather.event." + t.body.eventType + ".info")
                : t.group == Catalog.OBJECTS ? Component.translatable("realweather.object." + t.body.eventType + ".info")
                : Component.translatable("realweather.target." + t.id + ".info");
        lines.addAll(font.split(info, w));
        int max = (height - 70 - y) / 10;
        for (int i = 0; i < Math.min(lines.size(), max); i++) {
            g.drawString(font, lines.get(i), x + 2, y + 2 + i * 10, 0xFFE8EEF8, false);
        }
    }

    /** An angle on the sky, readably. */
    private static String arc(double rad) {
        double s = Math.toDegrees(rad) * 3600;
        return s >= 3600 ? String.format(Locale.ROOT, "%.2f°", s / 3600) : s >= 60 ? String.format(Locale.ROOT, "%.1f'", s / 60)
                : String.format(Locale.ROOT, "%.2f\"", s);
    }

    /** Their distances on one (logarithmic) bar, and their sizes on the sky as two discs. */
    private void scale(GuiGraphics g, int top) {
        int y = height - 58, x0 = 20, x1 = width - 20;
        g.fill(x0, y, x1, y + 2, 0xFF506080);
        double max = Math.log10(1 + 1.4E10);
        for (int i = 0; i < 2; i++) {
            Catalog.Target t = i == 0 ? a : b;
            double ly = t.sys.dir == null ? 1.6E-5 : UniverseMapScreen.lightYears(t.sys.distance);
            int x = (int) (x0 + (x1 - x0) * Math.log10(1 + ly) / max);
            int col = i == 0 ? 0xFFFFC060 : 0xFF60C0FF;
            g.fill(x - 1, y - 6, x + 1, y + 8, col);
            g.drawString(font, i == 0 ? "A" : "B", x - 2, y - 16, col, true);
        }
        g.drawString(font, Component.translatable("realweather.compare.scale"), x0, y + 6, 0xFF8090A0, false);
        // Sizes on the sky, relative.
        double ra = Math.max(1.0E-12, a.body.radius), rb = Math.max(1.0E-12, b.body.radius);
        double big = Math.max(ra, rb);
        int cx = width / 2, cy = y - 34;
        for (int i = 0; i < 2; i++) {
            double r = (i == 0 ? ra : rb) / big * 18;
            int rr = Math.max(1, (int) Math.round(r));
            int col = i == 0 ? 0x80FFC060 : 0x8060C0FF;
            int ox = cx + (i == 0 ? -24 : 24);
            for (int dy = -rr; dy <= rr; dy++) {
                int dx = (int) Math.sqrt(Math.max(0, rr * rr - dy * dy));
                g.fill(ox - dx, cy + dy, ox + dx + 1, cy + dy + 1, col);
            }
        }
        g.drawCenteredString(font, Component.translatable("realweather.compare.ratio", String.format(Locale.ROOT, "%.3g", ra / rb)), cx, cy + 22,
                0xFFC0C8D8);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
