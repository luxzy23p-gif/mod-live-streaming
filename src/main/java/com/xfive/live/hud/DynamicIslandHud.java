package com.xfive.live.hud;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.Glass;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pop-up musik gaya Dynamic Island, bertema liquid glass. Saat lagu mulai, berganti, atau dijeda,
 * kapsul kecil muncul di atas layar, melebar, lalu tumbuh menjadi kartu (sampul, judul, artis,
 * progres, waktu, tombol). Setelah beberapa detik kartu menyusut kembali ke kapsul dan menghilang.
 * Tampilan lain: pil kecil. Pilihan "auto sembunyi" bisa dimatikan agar kartu tetap tampil.
 * HUD di game tidak menerima klik; kontrolnya ada di menu X FIVE LIVE.
 *
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class DynamicIslandHud {
    private static final int CARD_H = 84;       // tinggi dasar kartu, isi mengikuti tinggi yang dipilih
    private static final int PILL_H = 30;
    private static final int CAP_H = 20;        // tinggi kapsul saat tertutup
    public static final int TOP = 6;
    private static final float OPEN_SECONDS = 0.85f;
    private static final float CLOSE_SECONDS = 0.6f;

    public static final String[] COLOR_NAMES = {"Ikut tema", "Ungu", "Cyan", "Pink", "Hijau", "Kuning"};
    private static final int[] COLORS = {0, 0x965CFF, 0x40DFFF, 0xFF4FD8, 0x5CFF8A, 0xFFD84D};

    private static boolean wasPlaying = false;
    private static PlaylistManager.Track lastTrack = null;
    private static long hideAt = 0L;
    private static float t = 0f;          // kemajuan buka/tutup 0..1 (linear terhadap waktu)
    private static long lastRenderNs = 0L;

    private DynamicIslandHud() {
    }

    /** Warna aksen Dynamic Island saat ini. */
    public static int accentColor() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        int i = c.islandColor;
        if (i <= 0 || i >= COLORS.length) {
            return GlassPanelRenderer.accent(c.theme);
        }
        return COLORS[i];
    }

    /** Kotak penuh Dynamic Island: {x, y, lebar, tinggi}. Nilai posisi -1 berarti tengah atas layar. */
    public static int[] box() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        int w = c.islandW;
        int h = c.islandCard ? c.islandH : Math.max(24, Math.round(c.islandH * PILL_H / (float) CARD_H));
        int[] pos = c.hudPositions.get("dynamicIsland");
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int x = pos[0] < 0 ? (sw - w) / 2 : pos[0];
        int y = pos[1] < 0 ? TOP : pos[1];
        return new int[] {x, y, w, h};
    }

    /** Memulai pop-up: kapsul muncul lalu membuka. Menutup sendiri setelah islandShowSec detik. */
    private static void openNow() {
        hideAt = System.currentTimeMillis() + XFiveLiveClient.CONFIG.islandShowSec * 1000L;
    }

    /** Dipanggil dari client tick. Hanya membaca status musik, tanpa menggambar. */
    public static void onTick() {
        boolean playing = XFiveLiveClient.MUSIC.isPlaying();
        boolean loading = XFiveLiveClient.MUSIC.isLoading();
        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        boolean changed = track != null && !sameTrack(track, lastTrack) && (playing || loading);
        boolean started = playing && !wasPlaying;
        boolean paused = wasPlaying && !playing && !loading && track != null && sameTrack(track, lastTrack);
        if (changed || started || paused) {
            openNow();
        }
        wasPlaying = playing;
        lastTrack = track;
    }

    public static void render(GuiGraphicsExtractor g) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        float dt = advanceTime();
        MusicHolder m = MusicHolder.read();
        boolean failed = XFiveLiveClient.MUSIC.hasError();
        long now = System.currentTimeMillis();
        boolean want;
        if (!c.enableMusic) {
            want = false;
        } else if (failed) {
            want = true;
        } else if (c.islandAutoHide) {
            want = now < hideAt;
        } else {
            want = wasPlaying || XFiveLiveClient.MUSIC.isLoading() || (c.islandShowPaused && m.track != null);
        }
        boolean opening = want;
        if (!c.animations) {
            t = want ? 1f : 0f;
        } else if (want) {
            t = Math.min(1f, t + dt / OPEN_SECONDS);
        } else {
            t = Math.max(0f, t - dt / CLOSE_SECONDS);
        }
        if (t <= 0.001f) {
            return;
        }

        // Pembukaan: lebar mendahului, tinggi menyusul dengan sedikit memantul.
        // Penutupan: tinggi menyusut dulu, lalu lebar kembali ke kapsul.
        float ew;
        float eh;
        float content;
        if (opening) {
            ew = easeOutBack(clamp01(t / 0.7f));
            eh = easeOutBack(clamp01((t - 0.15f) / 0.85f));
            content = clamp01((t - 0.65f) / 0.3f);
        } else {
            ew = smooth(clamp01(t / 0.7f));
            eh = smooth(clamp01((t - 0.35f) / 0.65f));
            content = clamp01((t - 0.75f) / 0.2f);
        }
        if (!c.animations) {
            ew = 1f;
            eh = 1f;
            content = 1f;
        }

        int[] b = box();
        int capW = Math.max(44, Math.min(76, b[2] / 2));
        int capH = Math.min(CAP_H, b[3]);
        int w = Math.max(capW, Math.round(capW + (b[2] - capW) * ew));
        int h = Math.max(capH, Math.round(capH + (b[3] - capH) * eh));
        int x = b[0] + (b[2] - w) / 2;
        // Saat menutup, kartu naik sedikit sambil mengecil kembali menjadi kapsul.
        // Offset mengikuti kurva tinggi agar gerakan tetap kontinu dan tidak meloncat.
        int y = b[1] - (opening ? 0 : Math.round((1f - eh) * 12f));
        float alpha = clamp01(t / 0.12f) * c.hudOpacity * Math.max(0.35f, c.customTransparency);

        String title;
        String sub;
        if (failed) {
            title = "Gagal memutar";
            sub = XFiveLiveClient.MUSIC.status();
        } else if (m.track == null) {
            title = "Memutar";
            sub = "";
        } else {
            title = m.track.title();
            sub = wasPlaying ? m.track.artist() : XFiveLiveClient.MUSIC.status();
        }
        long dur = XFiveLiveClient.MUSIC.durationMs();
        long pos = XFiveLiveClient.MUSIC.positionMs();
        float progress = dur > 0 ? Math.min(1f, (float) pos / dur) : 0f;
        Font font = GlassPanelRenderer.font();

        if (c.islandCard) {
            drawCard(g, font, x, y, w, h, title, sub, progress, pos, dur, wasPlaying, accentColor(), alpha, content);
        } else {
            drawPill(g, font, x, y, w, h, title, sub, progress, content, accentColor(), alpha);
        }
    }

    /** Data musik yang dibaca sekali per frame. */
    private record MusicHolder(PlaylistManager.Track track) {
        static MusicHolder read() {
            return new MusicHolder(XFiveLiveClient.MUSIC.currentTrack());
        }
    }

    // ---- Tampilan kartu ----

    /**
     * Menggambar kartu kaca. Isi mengikuti tinggi h (84 = ukuran dasar). content 0..1 mengatur
     * munculnya isi (sampul, teks, progres, tombol) saat kartu masih tumbuh.
     */
    public static void drawCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
                                String title, String artist, float progress, long posMs, long durMs,
                                boolean playing, int accent, float alpha, float content) {
        float s = h / (float) CARD_H;
        Glass.panel(g, x, y, w, h, Math.max(5, u(13, s)), alpha, accent);
        if (content <= 0.02f) {
            return;
        }
        float ca = alpha * content;
        int dy = Math.round((1f - content) * 5f);

        // sampul: kotak membulat berwarna dengan piringan
        int art = u(38, s);
        int ax = x + u(10, s);
        int ay = y + u(10, s) + dy;
        Glass.roundRect(g, ax, ay, art, art, u(9, s), Glass.argb(accent, 0.95f * ca), Glass.argb(accent, 0.55f * ca));
        Glass.border(g, ax, ay, art, art, u(9, s), Glass.argb(0xFFFFFF, 0.5f * ca), Glass.argb(0xFFFFFF, 0.1f * ca));
        int acx = ax + art / 2;
        int acy = ay + art / 2;
        Glass.disc(g, acx, acy, Math.round(art * 0.36f), Glass.argb(0xFFFFFF, 0.30f * ca));
        Glass.disc(g, acx, acy, Math.round(art * 0.24f), Glass.argb(accent, 0.9f * ca));
        Glass.disc(g, acx, acy, Math.round(art * 0.10f), Glass.argb(0xFFFFFF, 0.8f * ca));

        int tx = ax + art + u(10, s);
        int maxW = x + w - u(38, s) - tx;
        if (content > 0.4f) {
            GlassPanelRenderer.text(g, font, fit(font, title, maxW), tx, y + u(13, s) + dy, GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, fit(font, artist, maxW), tx, y + u(26, s) + dy, GlassPanelRenderer.MUTED);
        }

        // gelombang kecil di kanan atas
        double time = System.nanoTime() / 1_000_000_000.0;
        int[] base = {5, 11, 7, 13};
        for (int i = 0; i < base.length; i++) {
            double wave = playing ? 0.55 + 0.45 * Math.sin(time * 7 + i * 1.7) : 0.5;
            int bh = Math.max(2, (int) Math.round(u(base[i], s) * wave));
            int bx = x + w - u(30, s) + i * u(5, s);
            int mid = y + u(20, s) + dy;
            g.fill(bx, mid - bh / 2, bx + Math.max(1, u(2, s)), mid - bh / 2 + bh, Glass.argb(accent, ca));
        }

        // bar progres kaca dengan titik
        int bx1 = x + u(10, s);
        int bx2 = x + w - u(10, s);
        int by = y + u(52, s) + dy;
        int th = Math.max(3, u(3, s));
        Glass.roundRect(g, bx1, by, bx2 - bx1, th, th / 2, Glass.argb(0xFFFFFF, 0.22f * ca));
        int kx = bx1 + Math.round((bx2 - bx1) * progress);
        Glass.roundRect(g, bx1, by, Math.max(th, kx - bx1), th, th / 2, Glass.argb(0xFFFFFF, 0.95f * ca));
        Glass.disc(g, kx, by + th / 2, Math.max(2, u(4, s)), Glass.argb(0xFFFFFF, ca));

        if (content > 0.4f) {
            String left = formatTime(posMs);
            String right = formatTime(durMs);
            GlassPanelRenderer.text(g, font, left, bx1, y + u(60, s) + dy, GlassPanelRenderer.MUTED);
            GlassPanelRenderer.text(g, font, right, bx2 - font.width(right), y + u(60, s) + dy,
                    GlassPanelRenderer.MUTED);
        }

        // tombol sebelumnya, jeda/putar, berikutnya (hanya tampilan; kontrol ada di menu)
        int cy = y + h - u(13, s) + dy;
        int cx = x + w / 2;
        int gap = u(40, s);
        int half = Math.max(3, u(6, s));
        int white = Glass.argb(0xFFFFFF, ca);
        g.fill(cx - gap - half, cy - half, cx - gap - half + 2, cy + half, white);
        triangle(g, cx - gap - half + 2, cy, half, half * 2 - 2, false, white);
        g.fill(cx + gap + half - 2, cy - half, cx + gap + half, cy + half, white);
        triangle(g, cx + gap - half + 1, cy, half, half * 2 - 2, true, white);
        if (playing) {
            int bw = Math.max(2, u(3, s));
            g.fill(cx - bw - 1, cy - half, cx - 1, cy + half, white);
            g.fill(cx + 1, cy - half, cx + bw + 1, cy + half, white);
        } else {
            triangle(g, cx - half + 1, cy, half, half * 2, true, white);
        }
    }

    // ---- Tampilan pil ----

    /** Menggambar pil kaca: judul, artis, dan garis progres tipis. */
    public static void drawPill(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
                                String title, String sub, float progress, float content, int accent, float alpha) {
        Glass.panel(g, x, y, w, h, h / 2, alpha, accent);
        if (content <= 0.4f) {
            return;
        }
        boolean two = h >= 30;
        int maxW = w - h;
        int ty = two ? y + Math.round(h * 0.17f) : y + (h - 9) / 2;
        GlassPanelRenderer.text(g, font, fit(font, title, maxW), x + h / 2, ty, GlassPanelRenderer.WHITE);
        if (two) {
            GlassPanelRenderer.text(g, font, fit(font, sub, maxW), x + h / 2, y + Math.round(h * 0.5f) - 1,
                    GlassPanelRenderer.MUTED);
            int barX = x + h / 2;
            int barW = w - h;
            int barY = y + h - 5;
            g.fill(barX, barY, barX + barW, barY + 2, Glass.argb(0xFFFFFF, 0.18f * alpha));
            g.fill(barX, barY, barX + (int) (barW * progress), barY + 2, Glass.argb(accent, alpha));
        }
    }

    // ---- Bantuan ----

    private static int u(int v, float s) {
        return Math.round(v * s);
    }

    private static void triangle(GuiGraphicsExtractor g, int x, int cy, int half, int width, boolean right, int argb) {
        for (int i = 0; i < width; i++) {
            float frac = right ? 1f - (float) i / width : (float) i / width;
            int hh = Math.max(1, Math.round(half * frac));
            g.fill(x + i, cy - hh, x + i + 1, cy + hh, argb);
        }
    }

    private static String fit(Font font, String s, int maxPx) {
        if (font.width(s) <= maxPx) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && font.width(t + "…") > maxPx) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static String formatTime(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private static boolean sameTrack(PlaylistManager.Track a, PlaylistManager.Track b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.path().equals(b.path());
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float smooth(float v) {
        return v * v * (3f - 2f * v);
    }

    private static float easeOutBack(float v) {
        float c1 = 1.35f; // overshoot spring lebih terasa, tetap dibatasi kurva easing
        float c3 = c1 + 1f;
        float u = v - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static float advanceTime() {
        long now = System.nanoTime();
        float dt = lastRenderNs == 0L ? 0f : Math.min(0.05f, (now - lastRenderNs) / 1_000_000_000f);
        lastRenderNs = now;
        return dt;
    }
}
