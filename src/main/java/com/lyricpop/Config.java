package com.lyricpop;

import com.google.gson.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;

/** All user settings. Every public static non-final field is saved to config/lyricpop.json. */
public final class Config {
    public enum Mode {
        SCREEN("On screen"), WORLD("In the world"), BOTH("Both");
        public final String label;
        Mode(String l) { label = l; }
    }
    public enum Style {
        WORD("Word by word"), LINE("Full line");
        public final String label;
        Style(String l) { label = l; }
    }
    public enum Layout {
        PANEL("Spotify style"), FLOATING("Floating words");
        public final String label;
        Layout(String l) { label = l; }
    }
    public enum Corner {
        TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");
        public final String label;
        Corner(String l) { label = l; }
        public boolean right() { return this == TOP_RIGHT || this == BOTTOM_RIGHT; }
        public boolean bottom() { return this == BOTTOM_LEFT || this == BOTTOM_RIGHT; }
    }
    public enum Theme {
        LAVENDER("Lavender", 0xB9A8FF), PINK("Pink", 0xFF9ED8), CYAN("Cyan", 0x7FE7FF), MINT("Mint", 0x8CF2B0),
        GOLD("Gold", 0xFFD76A), WHITE("White", 0xFFFFFF), GRAY("Gray", 0x9A9A9A), RED("Red", 0xFF6B6B), RAINBOW("Rainbow", -1);
        public final String label;
        private final int base;
        Theme(String l, int c) { label = l; base = c; }
        public int rgb(long ms) {
            if (base >= 0) return base;
            return hsv((ms % 5000L) / 5000f, 0.45f, 1f);
        }
    }

    // ---- general / layout ----
    public static boolean enabled = true;
    public static boolean showBar = true;
    public static boolean showPill = false;
    public static boolean showNextLine = true;
    public static boolean showProgress = true;
    public static boolean barAtBottom = false;
    public static float uiScale = 0.6f;
    public static int uiOpacity = 50;
    public static int pillWidth = 300;
    public static Theme accent = Theme.RAINBOW;

    // ---- popups ----
    public static boolean popups = true;
    public static Mode mode = Mode.SCREEN;
    public static Style style = Style.WORD;
    public static Theme popupColor = Theme.LAVENDER;
    public static float popupScale = 1.0f;
    public static int popupLifetime = 1800;
    public static int maxPopups = 14;
    public static int tilt = 12;
    public static float drift = 1.0f;
    public static boolean popupShadow = true;

    // ---- on-screen lyrics panel (Spotify style) ----
    public static Layout screenLayout = Layout.PANEL;
    public static Corner panelCorner = Corner.TOP_LEFT;
    public static int panelLines = 6;
    public static int panelPast = 0;
    public static float panelScale = 0.7f;
    public static int panelWidth = 150;
    public static int panelMarginX = 2;
    public static int panelMarginY = 200;
    public static Theme panelActive = Theme.RAINBOW;
    public static Theme panelInactive = Theme.WHITE;
    public static boolean panelBold = false;
    public static boolean panelWordFill = false;
    public static boolean panelShadow = true;
    public static int panelBackground = 50;

    // ---- world mode ----
    public static float worldDistance = 5.0f;
    public static float worldSpread = 4.0f;
    public static float worldHeight = 0.5f;
    public static float worldScale = 1.0f;
    public static boolean lockToWorld = true;
    public static boolean seeThrough = false;

    // ---- sync ----
    /** Positive = lyrics show earlier. */
    public static int syncOffset = 200;

    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    static {
        for (Field f : Config.class.getDeclaredFields()) {
            if (!tunable(f)) continue;
            try { DEFAULTS.put(f.getName(), f.get(null)); } catch (Exception ignored) { }
        }
    }

    private static boolean tunable(Field f) {
        int m = f.getModifiers();
        return Modifier.isPublic(m) && Modifier.isStatic(m) && !Modifier.isFinal(m);
    }

    private static Path path() { return Paths.get("config", "lyricpop.json"); }

    public static void reset() {
        for (Field f : Config.class.getDeclaredFields()) {
            if (!tunable(f)) continue;
            try { f.set(null, DEFAULTS.get(f.getName())); } catch (Exception ignored) { }
        }
    }

    public static void load() {
        try {
            Path p = path();
            if (!Files.exists(p)) return;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            for (Field f : Config.class.getDeclaredFields()) {
                if (!tunable(f) || !o.has(f.getName())) continue;
                Class<?> t = f.getType();
                JsonElement e = o.get(f.getName());
                try {
                    if (t == boolean.class) f.setBoolean(null, e.getAsBoolean());
                    else if (t == int.class) f.setInt(null, e.getAsInt());
                    else if (t == float.class) f.setFloat(null, e.getAsFloat());
                    else if (t.isEnum()) {
                        for (Object c : t.getEnumConstants()) {
                            if (((Enum<?>) c).name().equals(e.getAsString())) f.set(null, c);
                        }
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            for (Field f : Config.class.getDeclaredFields()) {
                if (!tunable(f)) continue;
                Class<?> t = f.getType();
                if (t == boolean.class) o.addProperty(f.getName(), f.getBoolean(null));
                else if (t == int.class) o.addProperty(f.getName(), f.getInt(null));
                else if (t == float.class) o.addProperty(f.getName(), f.getFloat(null));
                else if (t.isEnum()) o.addProperty(f.getName(), ((Enum<?>) f.get(null)).name());
            }
            Files.createDirectories(path().getParent());
            Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) { }
    }

    static int hsv(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
