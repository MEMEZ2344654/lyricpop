package com.lyricpop;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

/** Draws the now-playing bar, the karaoke pill, and the floating popup words. */
public final class LyricHud {
    public static boolean enabled = true;
    public static boolean floating = true;

    private static final int LAVENDER = 0xB9A8FF, PURPLE_BG = 0xC8A06BFF, PILL_BG = 0xD0140F24, BAR_BG = 0xB0000000;
    private static final Random RNG = new Random();

    private static final class Pop { String text; long born; float x, y, rot, size; int color; }
    private static final List<Pop> POPS = new ArrayList<>();
    private static int lastLine = -2, lastWord = -2;
    private static String lastKey = "";

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        NowPlaying np = LyricsManager.now;
        if (!enabled || np == null || mc.options.hideGui) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        long pos = LyricsManager.currentPosMs();

        String info = "\u266A " + np.title() + "  \u00B7  " + np.artist() + "  " + fmt(pos) + " / " + fmt(np.durationMs());
        g.fill(0, 0, sw, 13, BAR_BG);
        g.drawCenteredString(font, info, sw / 2, 3, 0xFFE8E0FF);
        if (np.durationMs() > 0) g.fill(0, 12, (int) (sw * (pos / (double) np.durationMs())), 13, 0xFFB9A8FF);

        Lyrics ly = LyricsManager.lyrics;
        if (!np.key().equals(lastKey)) { lastKey = np.key(); POPS.clear(); lastLine = lastWord = -2; }
        if (ly == null) {
            drawPill(g, font, sw, LyricsManager.loading ? "Searching for lyrics..." : "No synced lyrics found", null, -1, "");
            return;
        }

        int li = ly.lineAt(pos);
        if (li < 0) { drawPill(g, font, sw, "\u266A", null, -1, ""); return; }
        Lyrics.Line line = ly.lines.get(li);
        int wi = -1;
        for (int i = 0; i < line.words().size(); i++) if (pos >= line.words().get(i).startMs()) wi = i;

        if (li != lastLine || wi != lastWord) {
            lastLine = li; lastWord = wi;
            if (floating) {
                if (line.words().isEmpty()) spawn("\u266A", sw, sh, 1.6f);
                else if (wi >= 0) {
                    String w = line.words().get(wi).text().replaceAll("[,.!?;:\"]", "");
                    if (!w.isBlank()) spawn(w, sw, sh, Math.min(2.4f, 1.1f + w.length() * 0.12f));
                }
            }
        }

        String next = li + 1 < ly.lines.size() ? ly.lines.get(li + 1).text() : "";
        drawPill(g, font, sw, line.text(), line, wi, next);
        if (floating) drawPops(g, font);
    }

    private static void spawn(String text, int sw, int sh, float size) {
        Pop p = new Pop();
        p.text = text; p.born = System.nanoTime(); p.size = size;
        p.x = sw / 2f + (RNG.nextFloat() - 0.5f) * sw * 0.55f;
        p.y = sh * (0.22f + RNG.nextFloat() * 0.3f);
        p.rot = (RNG.nextFloat() - 0.5f) * 24f;
        p.color = RNG.nextInt(3) == 0 ? 0xFFFFFF : LAVENDER;
        POPS.add(p);
        if (POPS.size() > 14) POPS.remove(0);
    }

    private static void drawPops(GuiGraphics g, Font font) {
        long now = System.nanoTime();
        POPS.removeIf(p -> (now - p.born) / 1e6 > 1800);
        for (Pop p : POPS) {
            float t = (now - p.born) / 1e6f;
            float in = Math.min(1f, t / 260f);
            float back = 1f + 2.70158f * (float) Math.pow(in - 1, 3) + 1.70158f * (float) Math.pow(in - 1, 2);
            float fade = t < 1200 ? 1f : 1f - (t - 1200) / 600f;
            int alpha = Math.max(5, (int) (255 * fade));
            var pose = g.pose();
            pose.pushPose();
            pose.translate(p.x, p.y - t * 0.012f, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(p.rot));
            pose.scale(p.size * back, p.size * back, 1f);
            int w = font.width(p.text);
            g.drawString(font, p.text, -w / 2, -4, (alpha << 24) | p.color, true);
            pose.popPose();
        }
    }

    private static void drawPill(GuiGraphics g, Font font, int sw, String text, Lyrics.Line line, int wi, String next) {
        int pw = Math.min(sw - 20, 300), x1 = (sw - pw) / 2, y1 = 17, y2 = 33, pad = 14;
        roundFill(g, x1, y1, x1 + pw, y2, PILL_BG);
        g.fill(x1 + 6, y1 + 5, x1 + 11, y1 + 10, 0xFFB9A8FF);
        int tx0 = x1 + pad + 6, tx1 = x1 + pw - pad;
        g.enableScissor(tx0, y1, tx1, y2);
        int avail = tx1 - tx0, scroll = 0;
        if (line != null && wi >= 0) {
            int upTo = font.width(String.join(" ", line.words().subList(0, wi + 1).stream().map(Lyrics.Word::text).toList()));
            scroll = Math.max(0, upTo - avail + 20);
        }
        int x = tx0 - scroll, ty = y1 + 4;
        if (line == null) g.drawString(font, text, tx0, ty, 0xFFCFC6F0, false);
        else {
            for (int i = 0; i < line.words().size(); i++) {
                String w = line.words().get(i).text();
                int ww = font.width(w);
                if (i == wi) g.fill(x - 2, ty - 2, x + ww + 2, ty + 10, PURPLE_BG);
                g.drawString(font, w, x, ty, i <= wi ? 0xFFFFFFFF : 0xFFBDB4D8, false);
                x += ww + font.width(" ");
            }
            if (!next.isBlank()) g.drawString(font, "  \u00B7  " + next, x, ty, 0xFF6F6890, false);
        }
        g.disableScissor();
    }

    private static void roundFill(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int h = y2 - y1, r = h / 2;
        for (int row = 0; row < h; row++) {
            double dy = Math.abs(row + 0.5 - r);
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
        }
    }

    private static String fmt(long ms) { long s = Math.max(0, ms) / 1000; return (s / 60) + ":" + String.format("%02d", s % 60); }
}
