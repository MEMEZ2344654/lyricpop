package com.lyricpop;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Decides when popup words appear and holds them (shared by the screen and world renderers). */
final class LyricEngine {
    static final class Pop {
        String text;
        long born;
        float size, rot;
        int color;
        boolean screen, world;
        float sx, sy;
        double wx, wy, wz;
        float ox, oy, od;
    }

    static final List<Pop> POPS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static int lastLine = -2, lastWord = -2;
    private static String lastKey = "";

    /** Called every client tick (20x a second). */
    static void tick() {
        NowPlaying np = LyricsManager.now;
        Lyrics ly = LyricsManager.lyrics;
        long nowNs = System.nanoTime();
        POPS.removeIf(p -> (nowNs - p.born) / 1_000_000L > Config.popupLifetime);
        if (np == null || ly == null) return;
        if (!np.key().equals(lastKey)) {
            lastKey = np.key();
            POPS.clear();
            lastLine = -2;
            lastWord = -2;
        }
        long pos = LyricsManager.currentPosMs();
        int li = ly.lineAt(pos);
        if (li < 0) return;
        Lyrics.Line line = ly.lines.get(li);
        int wi = -1;
        for (int i = 0; i < line.words().size(); i++) {
            if (pos >= line.words().get(i).startMs()) wi = i;
        }
        if (li == lastLine && wi == lastWord) return;
        boolean lineChanged = li != lastLine;
        lastLine = li;
        lastWord = wi;

        Minecraft mc = Minecraft.getInstance();
        if (!Config.enabled || !Config.popups || mc.player == null) return;

        if (Config.style == Config.Style.LINE) {
            if (lineChanged) {
                String t = line.text().isBlank() ? "\u266A" : line.text();
                spawn(t, Math.max(0.7f, Math.min(1.8f, 40f / Math.max(10, t.length()))));
            }
        } else if (line.words().isEmpty()) {
            if (lineChanged) spawn("\u266A", 1.6f);
        } else if (wi >= 0) {
            String w = line.words().get(wi).text().replaceAll("[,.!?;:\"]", "");
            if (!w.isBlank()) spawn(w, Math.min(2.4f, 1.1f + w.length() * 0.12f));
        }
    }

    static void spawn(String text, float baseSize) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Pop p = new Pop();
        p.text = text;
        p.born = System.nanoTime();
        p.size = baseSize;
        p.rot = (RNG.nextFloat() - 0.5f) * 2f * Config.tilt;
        p.color = RNG.nextInt(3) == 0 ? 0xFFFFFF : Config.popupColor.rgb(System.currentTimeMillis());

        if (Config.mode != Config.Mode.WORLD && Config.screenLayout == Config.Layout.FLOATING) {
            p.screen = true;
            p.sx = 0.5f + (RNG.nextFloat() - 0.5f) * 0.55f;
            p.sy = 0.22f + RNG.nextFloat() * 0.3f;
        }
        if (Config.mode != Config.Mode.SCREEN && mc.level != null) {
            Camera cam = mc.gameRenderer.getMainCamera();
            if (cam.isInitialized()) {
                Vector3f look = cam.getLookVector();
                Vector3f left = cam.getLeftVector();
                Vector3f up = cam.getUpVector();
                Vec3 c = cam.getPosition();
                p.world = true;
                p.od = Config.worldDistance;
                p.ox = (RNG.nextFloat() - 0.5f) * Config.worldSpread;
                p.oy = Config.worldHeight + (RNG.nextFloat() - 0.5f) * Config.worldSpread * 0.5f;
                p.wx = c.x + look.x() * p.od + left.x() * p.ox + up.x() * p.oy;
                p.wy = c.y + look.y() * p.od + left.y() * p.ox + up.y() * p.oy;
                p.wz = c.z + look.z() * p.od + left.z() * p.ox + up.z() * p.oy;
            }
        }
        if (!p.screen && !p.world) return;
        POPS.add(p);
        while (POPS.size() > Config.maxPopups) POPS.remove(0);
    }

    /** Age in ms. */
    static float age(Pop p) { return (System.nanoTime() - p.born) / 1_000_000f; }

    /** Pop-in scale with a little overshoot. */
    static float back(float tMs) {
        float in = Math.min(1f, tMs / 260f);
        return 1f + 2.70158f * (float) Math.pow(in - 1, 3) + 1.70158f * (float) Math.pow(in - 1, 2);
    }

    /** 0..255, text alpha (never below 5 so vanilla does not treat it as opaque). */
    static int alpha(float tMs) {
        float life = Math.max(1f, Config.popupLifetime);
        float start = life * 0.65f;
        float fade = tMs < start ? 1f : 1f - (tMs - start) / (life - start);
        return Math.max(5, Math.min(255, (int) (255 * fade)));
    }
}
