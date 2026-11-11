package com.xfive.live.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Panel gaya glassmorphism: latar gelap transparan dengan garis neon tipis.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class GlassPanelRenderer {
    public static final int BACKGROUND = 0x10121C;
    public static final int PURPLE = 0x965CFF;
    public static final int CYAN = 0x40DFFF;
    public static final int WHITE = 0xFFFFFF;
    public static final int MUTED = 0xA9B2C8;

    private GlassPanelRenderer() {
    }

    public static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int accent(String theme) {
        return com.xfive.live.config.Themes.accent(theme);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Panel kaca (liquid glass) untuk HUD. Lihat Glass.panel. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha, int accentRgb) {
        if ("dark_music".equals(com.xfive.live.XFiveLiveClient.CONFIG.themeStyle)) {
            // Matte music-player skin: opaque-looking charcoal surfaces with restrained accent.
            // Keep drawing lightweight (a few fills; no shader, blur pass, or per-frame allocations).
            int a = Math.max(0, Math.min(255, Math.round(alpha * 238f)));
            int bg = (a << 24) | 0x202124;
            int edge = argb(0xFFFFFF, 0.10f * alpha);
            g.fill(x, y, x + w, y + h, bg);
            g.fill(x, y, x + w, y + 2, argb(accentRgb, 0.95f * alpha));
            g.fill(x, y + h - 1, x + w, y + h, argb(accentRgb, 0.40f * alpha));
            g.fill(x, y, x + 1, y + h, edge);
            g.fill(x + w - 1, y, x + w, y + h, edge);
        } else {
            Glass.panel(g, x, y, w, h, 10, alpha, accentRgb);
        }
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int rgb) {
        g.text(font, s, x, y, 0xFF000000 | (rgb & 0xFFFFFF), true);
    }

    public static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
