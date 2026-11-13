package com.xfive.live.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

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
    public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha, int accentRgb) {
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

    private static final java.util.HashMap<String, Component> CACHE = new java.util.HashMap<>();
    private static String cachedFont = "";

    /** Teks dengan font pilihan pengguna (atau bawaan). Komponen di-cache supaya tidak membuat objek tiap frame. */
    private static Component styled(String s) {
        String id = com.xfive.live.XFiveLiveClient.CONFIG.uiFont;
        if (!id.equals(cachedFont)) {
            CACHE.clear();
            cachedFont = id;
        }
        Component c = CACHE.get(s);
        if (c == null) {
            if (CACHE.size() > 600) {
                CACHE.clear();
            }
            MutableComponent m = Component.literal(s);
            if (!"default".equals(id)) {
                m.setStyle(Style.EMPTY.withFont(new FontDescription.Resource(
                        Identifier.fromNamespaceAndPath("xfive-live", id))));
            }
            c = m;
            CACHE.put(s, c);
        }
        return c;
    }

    public static void text(GuiGraphics g, Font font, String s, int x, int y, int rgb) {
        g.drawString(font, styled(s), x, y, 0xFF000000 | (rgb & 0xFFFFFF), true);
    }

    /** Seperti text() tetapi dengan transparansi (0..1). Alpha terlalu kecil dibulatkan agar tidak dianggap opak. */
    public static void textAlpha(GuiGraphics g, Font font, String s, int x, int y, int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        if (a < 8) {
            return;
        }
        g.drawString(font, styled(s), x, y, (a << 24) | (rgb & 0xFFFFFF), true);
    }

    /** Lebar teks dalam piksel dengan font yang sama seperti text(). */
    public static int width(Font font, String s) {
        return font.width(styled(s));
    }

    /** Memotong teks dengan "..." agar muat dalam maxPx piksel. */
    public static String fit(Font font, String s, int maxPx) {
        if (width(font, s) <= maxPx) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && width(font, t + "...") > maxPx) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    public static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
