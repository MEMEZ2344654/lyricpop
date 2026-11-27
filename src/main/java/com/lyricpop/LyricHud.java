package com.lyricpop;

import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** On-screen UI: song bar, karaoke pill, Spotify-style lyric panel, and floating popup words. */
public final class LyricHud {
    private static Lyrics sample;

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        NowPlaying np = LyricsManager.now;
        boolean preview = mc.screen instanceof SettingsScreen;
        if (!Config.enabled || mc.options.hideGui) return;
        if (np == null && !preview) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        long nowMs = System.currentTimeMillis();
        long pos = LyricsManager.currentPosMs();
        Lyrics ly = LyricsManager.lyrics;

        if (np != null) drawBarAndPill(g, font, np, ly, pos, nowMs);

        if (Config.popups && Config.mode != Config.Mode.WORLD) {
            if (Config.screenLayout == Config.Layout.PANEL) {
                if (np != null && ly != null) drawPanel(g, font, sw, sh, ly, pos, nowMs);
                else if (preview) drawPanel(g, font, sw, sh, sample(), nowMs % 18000L, nowMs);
            } else {
                drawPops(g, font, sw, sh);
            }
        }
    }

    // ------------------------------------------------------------------ Spotify-style panel

    private static final class Blk {
        int idx;
        String[] w;
        int[] ww;
        final List<int[]> rows = new ArrayList<>();
    }

    private static Lyrics sample() {
        if (sample == null) {
            sample = Lyrics.parse("[00:00.00] Your lyrics appear right here\n"
                    + "[00:03.00] The current line is highlighted\n"
                    + "[00:06.00] Other lines use a different color\n"
                    + "[00:09.00] Words can fill in as they are sung\n"
                    + "[00:12.00] Pick any corner in the settings\n"
                    + "[00:15.00] Make it bigger, smaller, or bold", 18000);
        }
        return sample;
    }

    private static int wordW(Font font, String word, boolean bold) {
        return font.width(LyricFont.text(word, bold));
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gr << 8) | bl;
    }

    private static void drawPanel(GuiGraphics g, Font font, int sw, int sh, Lyrics ly, long pos, long nowMs) {
        int size = ly.lines.size();
        if (size == 0) return;
        int li = ly.lineAt(pos);
        int first = li < 0 ? 0 : Math.max(0, li - Config.panelPast);
        int last = Math.min(size - 1, first + Math.max(1, Config.panelLines) - 1);
        float s = Config.panelScale;
        int wrapW = Math.max(40, (int) (Config.panelWidth / s));
        boolean bold = Config.panelBold;
        int spaceW = font.width(LyricFont.text(" ", bold));
        int rowH = font.lineHeight + 2 + (LyricFont.active() ? 3 : 0);
        int gap = 6;

        List<Blk> blks = new ArrayList<>();
        int totalH = 0;
        for (int i = first; i <= last; i++) {
            Lyrics.Line line = ly.lines.get(i);
            Blk b = new Blk();
            b.idx = i;
            b.w = line.text().isBlank() ? new String[]{"\u266A"} : line.text().trim().split("\\s+");
            b.ww = new int[b.w.length];
            for (int k = 0; k < b.w.length; k++) b.ww[k] = wordW(font, b.w[k], bold);
            int start = 0, cur = 0;
            for (int k = 0; k < b.w.length; k++) {
                int add = (k > start ? spaceW : 0) + b.ww[k];
                if (k > start && cur + add > wrapW) {
                    b.rows.add(new int[]{start, k});
                    start = k;
                    cur = b.ww[k];
                } else {
                    cur += add;
                }
            }
            b.rows.add(new int[]{start, b.w.length});
            totalH += b.rows.size() * rowH;
            if (!blks.isEmpty()) totalH += gap;
            blks.add(b);
        }

        Config.Corner c = Config.panelCorner;
        float x0 = c.right() ? sw - Config.panelMarginX - Config.panelWidth : Config.panelMarginX;
        float y0 = c.bottom() ? sh - Config.panelMarginY - totalH * s : Config.panelMarginY;

        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.scale(s, s, 1f);
        if (Config.panelBackground > 0) {
            g.fill(-4, -4, wrapW + 4, totalH + 4, ((Config.panelBackground * 255 / 100) << 24));
        }

        int active = Config.panelActive.rgb(nowMs);
        int inactive = Config.panelInactive.rgb(nowMs);
        int mid = mix(active, inactive, 0.5f);
        int y = 0;
        for (Blk b : blks) {
            boolean isCur = b.idx == li;
            int dist = li < 0 ? b.idx : Math.abs(b.idx - li);
            int alpha = isCur ? 255 : Math.max(5, (int) (255 * Math.max(0.45f, 1f - 0.15f * dist)));

            int wi = -1;
            if (isCur && Config.panelWordFill) {
                List<Lyrics.Word> words = ly.lines.get(b.idx).words();
                for (int k = 0; k < words.size(); k++) {
                    if (pos >= words.get(k).startMs()) wi = k;
                }
            }

            for (int[] row : b.rows) {
                int rw = 0;
                for (int k = row[0]; k < row[1]; k++) rw += b.ww[k] + (k > row[0] ? spaceW : 0);
                int x = c.right() ? wrapW - rw : 0;
                for (int k = row[0]; k < row[1]; k++) {
                    int col;
                    if (isCur) col = (Config.panelWordFill && k > wi) ? mid : active;
                    else col = inactive;
                    int argb = (alpha << 24) | (col & 0xFFFFFF);
                    g.drawString(font, LyricFont.text(b.w[k], bold), x, y, argb, Config.panelShadow);
                    x += b.ww[k] + spaceW;
                }
                y += rowH;
            }
            y += gap;
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ song bar + pill

    private static void drawBarAndPill(GuiGraphics g, Font font, NowPlaying np, Lyrics ly, long pos, long nowMs) {
        int sw = g.guiWidth(), sh = g.guiHeight();
        int accent = 0xFF000000 | Config.accent.rgb(nowMs);
        int bgA = Math.max(0, Math.min(255, Config.uiOpacity * 255 / 100));
        float sc = Config.uiScale;
        int w = (int) (sw / sc), h = (int) (sh / sc);
        var pose = g.pose();
        pose.pushPose();
        pose.scale(sc, sc, 1f);

        int y = Config.barAtBottom ? h - 72 : 0;
        if (Config.showBar) {
            String info = "\u266A " + np.title() + "  \u00B7  " + np.artist() + "  " + fmt(pos) + " / " + fmt(np.durationMs());
            g.fill(0, y, w, y + 13, (bgA << 24));
            g.drawCenteredString(font, LyricFont.text(info, false), w / 2, y + 3, 0xFFE8E0FF);
            if (Config.showProgress && np.durationMs() > 0) {
                double frac = Math.max(0, Math.min(1, pos / (double) np.durationMs()));
                g.fill(0, y + 12, (int) (w * frac), y + 13, accent);
            }
            y += 17;
        } else {
            y += 4;
        }

        if (Config.showPill) {
            if (ly == null) {
                drawPill(g, font, w, y, bgA, accent, LyricsManager.loading ? "Searching for lyrics..." : "No synced lyrics found", null, -1, "");
            } else {
                int li = ly.lineAt(pos);
                if (li < 0) {
                    drawPill(g, font, w, y, bgA, accent, "\u266A", null, -1, "");
                } else {
                    Lyrics.Line line = ly.lines.get(li);
                    int wi = -1;
                    for (int i = 0; i < line.words().size(); i++) {
                        if (pos >= line.words().get(i).startMs()) wi = i;
                    }
                    String next = (Config.showNextLine && li + 1 < ly.lines.size()) ? ly.lines.get(li + 1).text() : "";
                    drawPill(g, font, w, y, bgA, accent, line.text(), line, wi, next);
                }
            }
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ floating words

    private static void drawPops(GuiGraphics g, Font font, int sw, int sh) {
        for (LyricEngine.Pop p : LyricEngine.POPS) {
            if (!p.screen) continue;
            float t = LyricEngine.age(p);
            if (t > Config.popupLifetime) continue;
            float back = LyricEngine.back(t);
            int alpha = LyricEngine.alpha(t);
            float scale = p.size * Config.popupScale * back;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(p.sx * sw, p.sy * sh - t * 0.012f * Config.drift, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(p.rot));
            pose.scale(scale, scale, 1f);
            int tw = font.width(LyricFont.text(p.text, false));
            g.drawString(font, LyricFont.text(p.text, false), -tw / 2, -4, (alpha << 24) | p.color, Config.popupShadow);
            pose.popPose();
        }
    }

    private static void drawPill(GuiGraphics g, Font font, int w, int y1, int bgA, int accent, String text,
                                 Lyrics.Line line, int wi, String next) {
        int pw = Math.min(w - 20, Config.pillWidth);
        int x1 = (w - pw) / 2, y2 = y1 + 16, pad = 14;
        roundFill(g, x1, y1, x1 + pw, y2, (bgA << 24) | 0x140F24);
        g.fill(x1 + 6, y1 + 5, x1 + 11, y1 + 10, accent);
        int tx0 = x1 + pad + 6, tx1 = x1 + pw - pad;
        g.enableScissor(tx0, y1, tx1, y2);
        int avail = tx1 - tx0, scroll = 0;
        if (line != null && wi >= 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= wi; i++) {
                if (i > 0) sb.append(' ');
                sb.append(line.words().get(i).text());
            }
            scroll = Math.max(0, font.width(LyricFont.text(sb.toString(), false)) - avail + 20);
        }
        int x = tx0 - scroll, ty = y1 + 4;
        if (line == null) {
            g.drawString(font, LyricFont.text(text, false), tx0, ty, 0xFFCFC6F0, false);
        } else {
            int hi = (0xC8 << 24) | (accent & 0xFFFFFF);
            for (int i = 0; i < line.words().size(); i++) {
                String word = line.words().get(i).text();
                int ww = font.width(LyricFont.text(word, false));
                if (i == wi) g.fill(x - 2, ty - 2, x + ww + 2, ty + 10, hi);
                g.drawString(font, LyricFont.text(word, false), x, ty, i <= wi ? 0xFFFFFFFF : 0xFFBDB4D8, false);
                x += ww + font.width(LyricFont.text(" ", false));
            }
            if (!next.isBlank()) g.drawString(font, LyricFont.text("  \u00B7  " + next, false), x, ty, 0xFF6F6890, false);
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

    private static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
