package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.lyrics.LyricStyle;
import com.xfive.live.lyrics.LyricsManager;
import com.xfive.live.render.Glass;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD lirik: baris sebelumnya, baris aktif (disorot), dan baris berikutnya.
 * Posisi default mengikuti pilihan Atas/Tengah/Bawah. Jika digeser di menu, posisi bebas dipakai.
 */
public final class LyricsHud {
    public static final int WIDTH = 260;

    private LyricsHud() {
    }

    /** Kotak lirik saat ini: {x, y, lebar, tinggi}. Dipakai juga oleh menu untuk menggeser. */
    public static int[] box() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        int[] pos = c.hudPositions.get("lyricsHud");
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int h = 3 * lineHeight(c) + 8;
        if (isFree(pos)) {
            return new int[] {pos[0], pos[1], WIDTH, h};
        }
        return new int[] {(sw - WIDTH) / 2, presetBaseY(c, sh) - 6, WIDTH, h};
    }

    public static void render(GuiGraphics g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        if (!c.enableLyrics) {
            return;
        }
        LyricsManager ly = XFiveLiveClient.LYRICS;
        if (ly.isEmpty()) {
            return;
        }
        long songPos = LyricStyle.shifted(XFiveLiveClient.MUSIC.positionMs(), c.lyricsOffsetMs);
        int idx = ly.indexAt(songPos);
        if (idx < 0) {
            return;
        }
        Font font = GlassPanelRenderer.font();
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int lineH = lineHeight(c);
        int[] pos = c.hudPositions.get("lyricsHud");
        int cx;
        int baseY;
        if (isFree(pos)) {
            cx = pos[0] + WIDTH / 2;
            baseY = pos[1] + 6;
        } else {
            cx = sw / 2;
            baseY = presetBaseY(c, sh);
        }
        int highlight = c.highlightColor;
        float alpha = c.hudOpacity * c.customTransparency;
        int accent = GlassPanelRenderer.accent(c.theme);

        if (c.lyricsPreview) {
            drawCentered(g, font, ly.textAt(idx - 1), cx, baseY, GlassPanelRenderer.MUTED);
            drawCentered(g, font, ly.textAt(idx + 1), cx, baseY + lineH * 2, GlassPanelRenderer.MUTED);
        }
        long since = songPos - ly.lines().get(idx).timeMs();
        drawActive(g, font, ly.textAt(idx), cx, baseY + lineH, highlight, c,
                since, LyricStyle.lineDuration(ly.lines(), idx));
        if (!c.lyricsMinimal) {
            int w = Math.min(sw - 16, WIDTH);
            g.fill(cx - w / 2, baseY - 4, cx + w / 2, baseY - 3, GlassPanelRenderer.argb(accent, alpha));
        }
    }

    /** true jika posisi lirik sudah diseret bebas (bukan posisi preset). Nilai -1 berarti preset. */
    private static boolean isFree(int[] pos) {
        return pos[0] >= 0 && pos[1] >= 0;
    }

    private static int lineHeight(XFiveConfig c) {
        return c.lyricsFontSize + 3; // jarak baris, belum menskala teks
    }

    /** Baris pertama untuk posisi preset Atas/Tengah/Bawah. */
    private static int presetBaseY(XFiveConfig c, int sh) {
        return switch (c.lyricsPosition) {
            case 0 -> 20;
            case 1 -> sh / 2 - lineHeight(c);
            default -> sh - 70;
        };
    }

    /** Baris aktif dengan animasi masuk dan sorot karaoke. Semua murni fill/teks, tanpa shader. */
    private static void drawActive(GuiGraphics g, Font font, String full, int cx, int y, int hl,
                                   XFiveConfig c, long since, long dur) {
        if (full == null || full.isEmpty()) {
            return;
        }
        String anim = c.lyricsAnim;
        float e = LyricStyle.enter(since, !"none".equals(anim) && !"typewriter".equals(anim));
        float alpha = 1f;
        float dy = 0f;
        float scale = 1f;
        String shown = full;
        switch (anim) {
            case "fade" -> alpha = e;
            case "rise" -> {
                alpha = e;
                dy = (1f - e) * 8f;
            }
            case "drop" -> {
                alpha = e;
                dy = -(1f - e) * 8f;
            }
            case "pop" -> {
                scale = LyricStyle.popScale(e);
                alpha = Math.min(1f, e * 1.6f);
            }
            case "typewriter" -> shown = full.substring(0, LyricStyle.typedChars(full.length(), since, dur));
            default -> {
            }
        }
        if (shown.isEmpty()) {
            return;
        }
        int x = cx - GlassPanelRenderer.width(font, full) / 2;
        int ty = y + Math.round(dy);
        boolean pop = scale != 1f;
        if (pop) {
            g.pose().pushMatrix();
            g.pose().translate((float) cx, (float) (ty + 4));
            g.pose().scale(scale, scale);
            g.pose().translate((float) -cx, (float) -(ty + 4));
        }
        if (c.lyricsKaraoke) {
            int dim = Glass.mix(0xFF000000 | 0x9AA3B5, 0xFF000000 | hl, 0.30f) & 0xFFFFFF;
            GlassPanelRenderer.textAlpha(g, font, shown, x, ty, dim, alpha);
            int n = Math.min(shown.length(), Math.round(full.length() * LyricStyle.karaoke(since, dur)));
            if (n > 0) {
                GlassPanelRenderer.textAlpha(g, font, shown.substring(0, n), x, ty, hl, alpha);
            }
        } else {
            GlassPanelRenderer.textAlpha(g, font, shown, x, ty, hl, alpha);
        }
        if (pop) {
            g.pose().popMatrix();
        }
    }

    private static void drawCentered(GuiGraphics g, Font font, String s, int cx, int y, int rgb) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int x = cx - GlassPanelRenderer.width(font, s) / 2;
        GlassPanelRenderer.text(g, font, s, x, y, rgb);
    }
}
