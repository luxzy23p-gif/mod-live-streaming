package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.music.MusicManager;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** HUD musik: judul, artis, status, dan progress bar. Posisinya bisa digeser di menu. */
public final class MusicHud {
    public static final int WIDTH = 170;
    public static final int HEIGHT = 36;

    private MusicHud() {
    }

    /** Kotak HUD saat ini: {x, y, lebar, tinggi}. Dipakai juga oleh menu untuk menggeser. */
    public static int[] box() {
        int[] pos = XFiveLiveClient.CONFIG.hudPositions.get("musicHud");
        return new int[] {pos[0], pos[1], WIDTH, HEIGHT};
    }

    public static void render(GuiGraphicsExtractor g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        if (!c.enableMusic) {
            return;
        }
        MusicManager m = XFiveLiveClient.MUSIC;
        int[] pos = c.hudPositions.get("musicHud");
        int x = pos[0];
        int y = pos[1];
        int accent = GlassPanelRenderer.accent(c.theme);
        float alpha = c.hudOpacity * c.customTransparency;
        GlassPanelRenderer.panel(g, x, y, WIDTH, HEIGHT, alpha, accent);

        PlaylistManager.Track t = m.currentTrack();
        String title = t == null ? "Belum ada lagu" : t.title();
        String sub = t == null ? m.status() : t.artist();
        Font font = GlassPanelRenderer.font();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(title, 24), x + 6, y + 6, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(sub, 28), x + 6, y + 17, GlassPanelRenderer.MUTED);

        long duration = m.durationMs();
        float progress = duration > 0 ? Math.min(1f, (float) m.positionMs() / duration) : 0f;
        int barW = WIDTH - 12;
        g.fill(x + 6, y + HEIGHT - 6, x + 6 + barW, y + HEIGHT - 5, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.15f));
        g.fill(x + 6, y + HEIGHT - 6, x + 6 + (int) (barW * progress), y + HEIGHT - 5, GlassPanelRenderer.argb(accent, 1f));
    }
}
