package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.lyrics.LyricsManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    public static void render(GuiGraphicsExtractor g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        if (!c.enableLyrics) {
            return;
        }
        LyricsManager ly = XFiveLiveClient.LYRICS;
        if (ly.isEmpty()) {
            return;
        }
        int idx = ly.indexAt(XFiveLiveClient.MUSIC.positionMs());
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

        drawCentered(g, font, ly.textAt(idx - 1), cx, baseY, GlassPanelRenderer.MUTED);
        drawCentered(g, font, ly.textAt(idx), cx, baseY + lineH, highlight);
        drawCentered(g, font, ly.textAt(idx + 1), cx, baseY + lineH * 2, GlassPanelRenderer.MUTED);
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

    private static void drawCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int rgb) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int x = cx - font.width(s) / 2;
        GlassPanelRenderer.text(g, font, s, x, y, rgb);
    }
}
