package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.SpecCard;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Kartu spesifikasi transparan di bawah tampilan lagu. Tanpa kotak latar: hanya garis ikon HP,
 * teks putih berbayang, dan sedikit warna aksen, jadi bersih saat disiarkan.
 * Mode Auto: muncul sendiri (pop-in) saat lagu mulai atau berganti, lalu hilang setelah beberapa detik.
 * Hanya fill dan teks, tanpa shader, jadi ringan.
 */
public final class SpecHud {
    private static final int ICON_W = 16;
    private static final int ICON_H = 30;
    private static final int GAP = 9;
    private static final int ROW = 11;
    private static final int TITLE_ROW = 13;

    private static float p = 0f;              // kemunculan 0..1
    private static long lastNs = 0L;
    private static long triggerMs = 0L;       // kapan terakhir dipicu (lagu mulai/berganti)
    private static long appearMs = 0L;        // kapan mulai tampil (untuk efek ketik)
    private static String lastKey = "";
    private static boolean wasPlaying = false;

    private SpecHud() {
    }

    private static String trackKey(PlaylistManager.Track t) {
        return t == null ? "" : t.path().toString();
    }

    /** Kotak kartu: {x, y, lebar, tinggi}. Dipakai juga oleh menu untuk pratinjau dan menggeser. */
    public static int[] box() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        Font font = GlassPanelRenderer.font();
        List<String> lines = SpecCard.lines(c.specLines);
        int titleW = GlassPanelRenderer.width(font, c.specTitle)
                + (c.specTag.isEmpty() ? 0 : 7 + GlassPanelRenderer.width(font, spaced(c.specTag)));
        int textW = titleW;
        for (String s : lines) {
            textW = Math.max(textW, GlassPanelRenderer.width(font, s));
        }
        int w = ICON_W + GAP + textW;
        int h = Math.max(ICON_H, TITLE_ROW + 5 + lines.size() * ROW);
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int[] pos = c.hudPositions.get("specHud");
        int x;
        int y;
        if (pos[0] >= 0 && pos[1] >= 0) {
            x = pos[0];
            y = pos[1];
        } else {
            int cx;
            if (c.dynamicIsland) {
                int[] ib = DynamicIslandHud.box();
                cx = ib[0] + ib[2] / 2;
                y = ib[3] <= 0 ? DynamicIslandHud.TOP : ib[1] + ib[3] + 8;
            } else {
                int[] mb = MusicHud.box();
                cx = mb[0] + mb[2] / 2;
                y = mb[1] + mb[3] + 8;
            }
            x = Math.max(2, Math.min(sw - w - 2, cx - w / 2));
        }
        return new int[] {x, y, w, h};
    }

    public static void render(GuiGraphicsExtractor g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        long now = System.currentTimeMillis();
        long ns = System.nanoTime();
        float dt = lastNs == 0L ? 0f : Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;

        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        boolean playing = XFiveLiveClient.MUSIC.isPlaying() || XFiveLiveClient.MUSIC.isLoading();
        String key = trackKey(track);
        if (track != null && playing && (!key.equals(lastKey) || !wasPlaying)) {
            triggerMs = now;
        }
        lastKey = key;
        wasPlaying = playing;

        boolean want = false;
        if (c.enableMusic && track != null) {
            if (c.specMode == SpecCard.MODE_ALWAYS) {
                want = true;
            } else if (c.specMode == SpecCard.MODE_AUTO) {
                want = now - triggerMs < c.specSec * 1000L;
            }
        }
        float before = p;
        if (!c.animations) {
            p = want ? 1f : 0f;
        } else if (want) {
            p = Math.min(1f, p + dt / 0.55f);
        } else {
            p = Math.max(0f, p - dt / 0.35f);
        }
        if (before <= 0.001f && p > 0.001f) {
            appearMs = now;
        }
        if (p <= 0.001f) {
            return;
        }

        int[] b = box();
        Font font = GlassPanelRenderer.font();
        float alpha = Math.min(1f, p * 1.5f) * Math.max(0.2f, c.hudOpacity);
        float scale = want ? 0.85f + 0.15f * easeOutBack(p) : 0.92f + 0.08f * p;
        int cx = b[0] + b[2] / 2;
        int cy = b[1] + b[3] / 2;
        int accent = GlassPanelRenderer.accent(c.theme);

        boolean scaled = Math.abs(scale - 1f) > 0.002f;
        if (scaled) {
            g.pose().pushMatrix();
            g.pose().translate((float) cx, (float) cy);
            g.pose().scale(scale, scale);
            g.pose().translate((float) -cx, (float) -cy);
        }

        drawPhone(g, b[0], b[1] + (b[3] - ICON_H) / 2, accent, alpha, now - appearMs);

        int tx = b[0] + ICON_W + GAP;
        int ty = b[1];
        String[] parts = SpecCard.splitTitle(c.specTitle);
        GlassPanelRenderer.textAlpha(g, font, parts[0], tx, ty, GlassPanelRenderer.WHITE, alpha);
        int hx = tx + GlassPanelRenderer.width(font, parts[0]);
        GlassPanelRenderer.textAlpha(g, font, parts[1], hx, ty, accent, alpha);
        if (!c.specTag.isEmpty()) {
            int gx = hx + GlassPanelRenderer.width(font, parts[1]) + 7;
            GlassPanelRenderer.textAlpha(g, font, spaced(c.specTag), gx, ty + 1, accent, alpha * 0.85f);
        }
        // garis tipis di bawah judul
        g.fill(tx, ty + TITLE_ROW, tx + b[2] - ICON_W - GAP, ty + TITLE_ROW + 1,
                GlassPanelRenderer.argb(0xFFFFFF, 0.28f * alpha));
        g.fill(tx, ty + TITLE_ROW, tx + 22, ty + TITLE_ROW + 1, GlassPanelRenderer.argb(accent, alpha));

        List<String> lines = SpecCard.lines(c.specLines);
        long since = now - appearMs;
        for (int i = 0; i < lines.size(); i++) {
            String full = lines.get(i);
            int n = c.animations ? SpecCard.typedCount(full.length(), since - 450 - i * 380L, 55) : full.length();
            if (n <= 0) {
                continue;
            }
            GlassPanelRenderer.textAlpha(g, font, full.substring(0, n), tx, ty + TITLE_ROW + 5 + i * ROW,
                    0xE6E9F0, alpha * 0.92f);
        }
        if (scaled) {
            g.pose().popMatrix();
        }
    }

    /** Ikon HP bergaris (tanpa isi): chip kecil dan dua batang memori di dalamnya. */
    private static void drawPhone(GuiGraphicsExtractor g, int x, int y, int accent, float alpha, long sinceMs) {
        int line = GlassPanelRenderer.argb(0xFFFFFF, alpha);
        int w = ICON_W;
        int h = ICON_H;
        g.fill(x + 2, y, x + w - 2, y + 1, line);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, line);
        g.fill(x, y + 2, x + 1, y + h - 2, line);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, line);
        g.fill(x + 1, y + 1, x + 2, y + 2, line);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, line);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, line);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, line);
        // chip: kotak kecil dengan kaki
        int cx0 = x + 4;
        int cy0 = y + 5;
        int pulse = (int) (sinceMs / 450 % 2);
        int chip = GlassPanelRenderer.argb(accent, alpha * (pulse == 0 ? 1f : 0.7f));
        g.fill(cx0, cy0, cx0 + 8, cy0 + 1, chip);
        g.fill(cx0, cy0 + 7, cx0 + 8, cy0 + 8, chip);
        g.fill(cx0, cy0 + 1, cx0 + 1, cy0 + 7, chip);
        g.fill(cx0 + 7, cy0 + 1, cx0 + 8, cy0 + 7, chip);
        g.fill(cx0 + 3, cy0 + 3, cx0 + 5, cy0 + 5, chip);
        for (int i = 0; i < 3; i++) {
            int px = cx0 + 1 + i * 3;
            g.fill(px, cy0 - 2, px + 1, cy0, chip);
            g.fill(px, cy0 + 8, px + 1, cy0 + 10, chip);
        }
        // dua batang memori
        g.fill(x + 3, y + 19, x + w - 3, y + 21, line);
        g.fill(x + 3, y + 23, x + w - 3, y + 25, line);
    }

    private static String spaced(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static float easeOutBack(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
