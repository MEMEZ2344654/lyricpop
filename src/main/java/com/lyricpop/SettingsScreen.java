package com.lyricpop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.*;

/** Tabbed settings screen. The world stays visible behind it so you can preview changes live. */
public class SettingsScreen extends Screen {
    private static final String[] TABS = {"Display", "Popups", "Screen", "Style", "World", "Sync"};
    private static int lastTab = 0;

    private final Screen parent;
    private int tab = lastTab;
    private int panelBottom = 200;

    public SettingsScreen(Screen parent) {
        super(Component.literal("LyricPop Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int tabW = 52;
        int x0 = cx - (TABS.length * tabW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(TABS[i]), btn -> {
                tab = idx;
                lastTab = idx;
                this.rebuildWidgets();
            }).pos(x0 + i * tabW, 28).size(tabW - 2, 20).build();
            b.active = (i != tab);
            this.addRenderableWidget(b);
        }

        List<AbstractWidget> opts = options();
        int colW = 150, gap = 6, rowH = 24, y0 = 56;
        int left = cx - colW - gap / 2;
        for (int i = 0; i < opts.size(); i++) {
            AbstractWidget w = opts.get(i);
            w.setX(left + (i % 2) * (colW + gap));
            w.setY(y0 + (i / 2) * rowH);
            w.setWidth(colW);
            this.addRenderableWidget(w);
        }

        int by = y0 + ((opts.size() + 1) / 2) * rowH + 6;
        int bw = 98;
        this.addRenderableWidget(Button.builder(Component.literal("Preview"), b -> LyricEngine.spawn("Preview \u266A", 1.8f))
                .pos(cx - bw - bw / 2 - 3, by).size(bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
            Config.reset();
            this.rebuildWidgets();
        }).pos(cx - bw / 2, by).size(bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .pos(cx + bw / 2 + 3, by).size(bw, 20).build());
        panelBottom = by + 28;
    }

    private List<AbstractWidget> options() {
        List<AbstractWidget> o = new ArrayList<>();
        switch (tab) {
            case 0:
                o.add(toggle("Lyrics", () -> Config.enabled, v -> Config.enabled = v));
                o.add(toggle("Song bar", () -> Config.showBar, v -> Config.showBar = v));
                o.add(toggle("Lyric pill", () -> Config.showPill, v -> Config.showPill = v));
                o.add(toggle("Next line", () -> Config.showNextLine, v -> Config.showNextLine = v));
                o.add(toggle("Progress line", () -> Config.showProgress, v -> Config.showProgress = v));
                o.add(cycle("Position", new Boolean[]{false, true}, () -> Config.barAtBottom, v -> Config.barAtBottom = v, v -> v ? "Bottom" : "Top"));
                o.add(slider("UI size", 0.5, 2.0, 0.1, Config.uiScale, "x", v -> Config.uiScale = (float) v));
                o.add(slider("Opacity", 10, 100, 5, Config.uiOpacity, "%", v -> Config.uiOpacity = (int) Math.round(v)));
                o.add(slider("Pill width", 150, 500, 10, Config.pillWidth, "", v -> Config.pillWidth = (int) Math.round(v)));
                o.add(cycle("Accent", Config.Theme.values(), () -> Config.accent, v -> Config.accent = v, v -> v.label));
                break;
            case 1:
                o.add(toggle("Popup lyrics", () -> Config.popups, v -> Config.popups = v));
                o.add(cycle("Show", Config.Mode.values(), () -> Config.mode, v -> Config.mode = v, v -> v.label));
                o.add(cycle("Style", Config.Style.values(), () -> Config.style, v -> Config.style = v, v -> v.label));
                o.add(cycle("Color", Config.Theme.values(), () -> Config.popupColor, v -> Config.popupColor = v, v -> v.label));
                o.add(slider("Size", 0.5, 3.0, 0.1, Config.popupScale, "x", v -> Config.popupScale = (float) v));
                o.add(slider("Lifetime", 800, 5000, 100, Config.popupLifetime, " ms", v -> Config.popupLifetime = (int) Math.round(v)));
                o.add(slider("Max at once", 3, 30, 1, Config.maxPopups, "", v -> Config.maxPopups = (int) Math.round(v)));
                o.add(slider("Tilt", 0, 45, 1, Config.tilt, "\u00B0", v -> Config.tilt = (int) Math.round(v)));
                o.add(slider("Float speed", 0, 3, 0.1, Config.drift, "x", v -> Config.drift = (float) v));
                o.add(toggle("Text shadow", () -> Config.popupShadow, v -> Config.popupShadow = v));
                break;
            case 2:
                o.add(cycle("Layout", Config.Layout.values(), () -> Config.screenLayout, v -> Config.screenLayout = v, v -> v.label));
                o.add(cycle("Corner", Config.Corner.values(), () -> Config.panelCorner, v -> Config.panelCorner = v, v -> v.label));
                o.add(slider("Lines shown", 3, 11, 1, Config.panelLines, "", v -> Config.panelLines = (int) Math.round(v)));
                o.add(slider("Past lines", 0, 3, 1, Config.panelPast, "", v -> Config.panelPast = (int) Math.round(v)));
                o.add(slider("Text size", 0.6, 3.0, 0.1, Config.panelScale, "x", v -> Config.panelScale = (float) v));
                o.add(slider("Width", 100, 400, 10, Config.panelWidth, "", v -> Config.panelWidth = (int) Math.round(v)));
                o.add(slider("Edge gap", 0, 200, 2, Config.panelMarginX, "", v -> Config.panelMarginX = (int) Math.round(v)));
                o.add(slider("Top/bottom gap", 0, 200, 2, Config.panelMarginY, "", v -> Config.panelMarginY = (int) Math.round(v)));
                break;
            case 3:
                o.add(cycle("Current line", Config.Theme.values(), () -> Config.panelActive, v -> Config.panelActive = v, v -> v.label));
                o.add(cycle("Other lines", Config.Theme.values(), () -> Config.panelInactive, v -> Config.panelInactive = v, v -> v.label));
                o.add(toggle("Bold text", () -> Config.panelBold, v -> Config.panelBold = v));
                o.add(toggle("Word fill", () -> Config.panelWordFill, v -> Config.panelWordFill = v));
                o.add(toggle("Text shadow", () -> Config.panelShadow, v -> Config.panelShadow = v));
                o.add(slider("Background", 0, 100, 5, Config.panelBackground, "%", v -> Config.panelBackground = (int) Math.round(v)));
                break;
            case 4:
                o.add(slider("Distance", 2, 15, 0.5, Config.worldDistance, " blocks", v -> Config.worldDistance = (float) v));
                o.add(slider("Spread", 0, 12, 0.5, Config.worldSpread, " blocks", v -> Config.worldSpread = (float) v));
                o.add(slider("Height", -3, 4, 0.25, Config.worldHeight, " blocks", v -> Config.worldHeight = (float) v));
                o.add(slider("Size", 0.3, 3.0, 0.1, Config.worldScale, "x", v -> Config.worldScale = (float) v));
                o.add(toggle("Stay in world", () -> Config.lockToWorld, v -> Config.lockToWorld = v));
                o.add(toggle("Through walls", () -> Config.seeThrough, v -> Config.seeThrough = v));
                break;
            default:
                o.add(slider("Timing offset", -3000, 3000, 50, Config.syncOffset, " ms", v -> Config.syncOffset = (int) Math.round(v)));
                o.add(Button.builder(Component.literal("Reset offset"), b -> {
                    Config.syncOffset = 200;
                    this.rebuildWidgets();
                }).pos(0, 0).size(150, 20).build());
                o.add(Button.builder(Component.literal("Lyrics earlier +100"), b -> {
                    Config.syncOffset = Math.min(3000, Config.syncOffset + 100);
                    this.rebuildWidgets();
                }).pos(0, 0).size(150, 20).build());
                o.add(Button.builder(Component.literal("Lyrics later -100"), b -> {
                    Config.syncOffset = Math.max(-3000, Config.syncOffset - 100);
                    this.rebuildWidgets();
                }).pos(0, 0).size(150, 20).build());
                break;
        }
        return o;
    }

    private Button toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder(Component.literal(name + ": " + (get.getAsBoolean() ? "On" : "Off")), b -> {
            boolean nv = !get.getAsBoolean();
            set.accept(nv);
            b.setMessage(Component.literal(name + ": " + (nv ? "On" : "Off")));
        }).pos(0, 0).size(150, 20).build();
    }

    private <T> Button cycle(String name, T[] vals, Supplier<T> get, Consumer<T> set, Function<T, String> label) {
        return Button.builder(Component.literal(name + ": " + label.apply(get.get())), b -> {
            int i = (Arrays.asList(vals).indexOf(get.get()) + 1) % vals.length;
            set.accept(vals[i]);
            b.setMessage(Component.literal(name + ": " + label.apply(vals[i])));
        }).pos(0, 0).size(150, 20).build();
    }

    private Slider slider(String name, double min, double max, double step, double cur, String unit, DoubleConsumer set) {
        return new Slider(name, min, max, step, cur, unit, set);
    }

    private static final class Slider extends AbstractSliderButton {
        private final String name, unit;
        private final double min, max, step;
        private final DoubleConsumer set;

        Slider(String name, double min, double max, double step, double cur, String unit, DoubleConsumer set) {
            super(0, 0, 150, 20, Component.empty(), (cur - min) / (max - min));
            this.name = name;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            this.updateMessage();
        }

        private double real() {
            double r = min + this.value * (max - min);
            if (step > 0) r = Math.round(r / step) * step;
            return Math.max(min, Math.min(max, r));
        }

        private String fmt(double r) {
            if (step >= 1) return String.valueOf((long) Math.round(r));
            if (Math.abs(step * 10 - Math.rint(step * 10)) < 1e-9) return String.format(Locale.ROOT, "%.1f", r);
            return String.format(Locale.ROOT, "%.2f", r);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(name + ": " + fmt(real()) + unit));
        }

        @Override
        protected void applyValue() {
            set.accept(real());
        }
    }

    // No @Override on purpose: draws a small panel instead of the blurred background, so the world stays visible.
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(this.width / 2 - 160, 22, this.width / 2 + 160, panelBottom, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        NowPlaying np = LyricsManager.now;
        String info = np == null
                ? "No song detected - start playing something on this computer"
                : "\u266A " + np.title() + " - " + np.artist() + "   " + (LyricsManager.currentPosMs() / 1000) + "s";
        g.drawCenteredString(this.font, info, this.width / 2, panelBottom + 6, 0xFFB9A8FF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.setScreen(parent);
    }
}
