package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.Fonts;
import com.xfive.live.config.Themes;
import com.xfive.live.lyrics.LyricStyle;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.hud.DynamicIslandHud;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.Glass;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * XFiveLive Control Center bergaya pemutar musik modern (sidebar, bilah atas dengan pencarian,
 * panel sedang diputar, daftar kartu, dan bilah pemutar di bawah). Semua digambar dengan beberapa
 * fill sederhana supaya ringan di HP. Animasi "Spring Pop-in" dipakai saat dibuka dan ditutup.
 * BELUM DIVERIFIKASI di perangkat (lihat README).
 */
public final class ControlCenterScreen extends Screen {
    private enum Page {
        LIBRARY("Library"), FAVORITES("Favorites"), HUD("Custom HUD"),
        CONTROLS("Player Controls"), APPEARANCE("Appearance"), SETTINGS("Settings");

        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    // ---- ikon piksel kecil (# = terisi) ----
    private static final String[] IC_LIBRARY = {
            "....#####", "....#####", "....#...#", "....#...#", "....#...#", "..###.###", ".####.###", "..##...##"};
    private static final String[] IC_HEART = {
            ".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...", "....#...."};
    private static final String[] IC_HUD = {
            "#########", "#.......#", "#.......#", "#.......#", "#########", "...###...", "..#####.."};
    private static final String[] IC_CONTROLS = {
            "#########", ".........", "...###...", ".........", "#########", ".###.....", ".........", "#########"};
    private static final String[] IC_STAR = {
            "....#....", "#...#...#", ".#.###.#.", "..#####..", "#########", "..#####..", ".#.###.#.", "#...#...#", "....#...."};
    private static final String[] IC_GEAR = {
            "...###...", ".#.###.#.", "..#####..", "###...###", "###...###", "###...###", "..#####..", ".#.###.#.", "...###..."};
    private static final String[] IC_SEARCH = {
            "..###...", ".#...#..", "#.....#.", "#.....#.", "#.....#.", ".#...#..", "..###.#.", "......##"};
    private static final String[] IC_CLOSE = {
            "##...##", ".##.##.", "..###..", "...#...", "..###..", ".##.##.", "##...##"};
    private static final String[] IC_SHUFFLE = {
            "##.....##", ".##...##.", "..##.##..", "...###...", "..##.##..", ".##...##.", "##.....##"};
    private static final String[] IC_REPEAT = {
            "..#######..", ".##.....##.", "##.......##", "#.........#", "##.......##", ".##.....##.", "..#######.."};
    private static final String[] IC_VOLUME = {
            "...#.....", "..##..#..", "###....#.", "###.....#", "###....#.", "..##..#..", "...#....."};

    private static final int[] COVER_COLORS = {0x965CFF, 0x40DFFF, 0xFF4FD8, 0x3DDC97, 0xFFB347, 0xFF7A5C, 0x4C8DFF};

    private static final int TOP_H = 28;
    private static final int BOT_H = 48;
    private static final int STEP = 29;
    private static final int CARD_H = 26;

    private static final double OPEN_SEC = 0.45;
    private static final double CLOSE_SEC = 0.16;

    // ---- keadaan layar ----
    private Page page;
    private final List<Page> history = new ArrayList<>();
    private int histPos = 0;
    private String searchText = "";
    private EditBox songSearch;
    private EditBox sourceInput;
    private String pendingSource = "";

    private double songScroll;
    private double rowsScroll;
    private int rowsH;
    private List<Row> rows = new ArrayList<>();

    private int pressTarget = 0; // 0 tidak ada, 1 daftar lagu, 2 baris pengaturan
    private boolean dragged;
    private double pressX;
    private double pressY;
    private double lastY;
    private int pressedTrack = -1;
    private Row pressedRow;
    private Row activeSlider;
    private boolean seeking;
    private boolean volumeDrag;
    private String pressedKey = "";

    private boolean hudEditMode = false;
    private String draggedHudKey = null;
    private int dragOffsetX;
    private int dragOffsetY;

    private final long openNanos = System.nanoTime();
    private long pageNanos = System.nanoTime();
    private boolean closing;
    private long closeNanos;

    // geometri panel (dihitung di layout())
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int sbW;

    public ControlCenterScreen() {
        this(Page.LIBRARY);
    }

    /** Membuka pustaka musik langsung dari satu tombol pusat musik. */
    public static ControlCenterScreen audio() {
        return new ControlCenterScreen(Page.LIBRARY);
    }

    private ControlCenterScreen(Page page) {
        super(Component.literal("XFiveLive Control Center"));
        this.page = page;
        XFiveLiveClient.playUiSound();
        history.add(page);
        buildRows();
    }

    // =====================================================================
    // Tata letak
    // =====================================================================

    private void layout() {
        pw = Math.min(width - 12, 780);
        ph = Math.min(height - 12, 440);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sbW = pw >= 400 ? 86 : 30;
    }

    private int cx0() { return px + sbW; }
    private int cw() { return pw - sbW; }
    private int contentTop() { return py + TOP_H; }
    private int contentBot() { return py + ph - BOT_H; }

    private boolean isMusicPage() { return page == Page.LIBRARY || page == Page.FAVORITES; }
    private boolean wide() { return cw() >= 330 && ph >= 190; }
    private int npW() { return wide() ? Math.min(132, cw() / 3) : 0; }
    private int listX() { return cx0() + 8 + (wide() ? npW() + 8 : 0); }
    private int listR() { return px + pw - 8; }
    private int listTop() { return contentTop() + (page == Page.LIBRARY ? 26 : 4); }
    private int listBot() { return contentBot() - 4; }

    private int rowsX() { return cx0() + 10; }
    private int rowsR() { return px + pw - 10; }
    private int rowsTop() { return contentTop() + 4; }
    private int rowsBot() { return contentBot() - 4; }

    private int searchX() { return cx0() + 46; }
    private int searchW() { return Math.max(40, px + pw - 56 - searchX()); }

    private int itemH() { return Math.max(16, Math.min(24, (ph - 48) / 6)); }
    private int[] navRect(int i) {
        return new int[] {px + 4, py + 34 + i * (itemH() + 2), sbW - 8, itemH()};
    }

    @Override
    protected void init() {
        layout();
        layoutRows();
        Font f = GlassPanelRenderer.font();
        songSearch = null;
        sourceInput = null;
        if (isMusicPage()) {
            songSearch = new EditBox(f, searchX() + 17, py + 8, searchW() - 22, 12, Component.literal("Search"));
            songSearch.setBordered(false);
            songSearch.setTextColor(0xFFFFFFFF);
            songSearch.setMaxLength(100);
            songSearch.setHint(Component.literal("Search songs or artists"));
            songSearch.setValue(searchText);
            songSearch.setResponder(q -> {
                searchText = q;
                songScroll = 0;
            });
            addRenderableWidget(songSearch);
        }
        if (page == Page.LIBRARY) {
            int[] pill = sourcePill();
            sourceInput = new EditBox(f, pill[0] + 6, pill[1] + 3, pill[2] - 12, 12, Component.literal("Add path"));
            sourceInput.setBordered(false);
            sourceInput.setTextColor(0xFFFFFFFF);
            sourceInput.setMaxLength(300);
            sourceInput.setHint(Component.literal("Add folder or file path"));
            sourceInput.setValue(pendingSource);
            sourceInput.setResponder(q -> pendingSource = q);
            addRenderableWidget(sourceInput);
        }
    }

    /** Kotak isian path di sub-header pustaka: {x, y, w, h}. */
    private int[] sourcePill() {
        int x = listX();
        int r = listR();
        return new int[] {x, contentTop() + 2, Math.max(60, r - x - 36 - 54 - 8), 18};
    }

    private int[] addRect() {
        int[] p = sourcePill();
        return new int[] {p[0] + p[2] + 4, p[1], 36, 18};
    }

    private int[] refreshRect() {
        int[] a = addRect();
        return new int[] {a[0] + a[2] + 4, a[1], 54, 18};
    }

    // =====================================================================
    // Animasi Spring Pop-in
    // =====================================================================

    private boolean animOn() {
        return XFiveLiveClient.CONFIG.animations;
    }

    private double openT() {
        return (System.nanoTime() - openNanos) / 1_000_000_000.0;
    }

    /** Skala panel: pegas dengan sedikit lonjakan saat dibuka, menyusut cepat saat ditutup. */
    private float panelScale() {
        if (!animOn()) {
            return 1f;
        }
        if (closing) {
            double t = Math.min(1.0, (System.nanoTime() - closeNanos) / 1_000_000_000.0 / CLOSE_SEC);
            return (float) (1.0 - 0.15 * t * t);
        }
        double t = openT();
        if (t >= OPEN_SEC) {
            return 1f;
        }
        double k = 1.0 - Math.exp(-7.0 * t) * Math.cos(16.0 * t);
        return (float) (0.80 + 0.20 * k);
    }

    private float backdropAlpha() {
        if (!animOn()) {
            return closing ? 0f : 1f;
        }
        if (closing) {
            return (float) Math.max(0.0, 1.0 - (System.nanoTime() - closeNanos) / 1_000_000_000.0 / CLOSE_SEC);
        }
        return (float) Math.min(1.0, openT() / 0.15);
    }

    private boolean settled() {
        return !closing && panelScale() == 1f;
    }

    @Override
    public void tick() {
        super.tick();
        if (closing && (!animOn() || (System.nanoTime() - closeNanos) / 1_000_000_000.0 >= CLOSE_SEC)) {
            finishClose();
        }
    }

    private void finishClose() {
        XFiveLiveClient.CONFIG.save();
        super.onClose();
    }

    @Override
    public void onClose() {
        if (!closing) {
            XFiveLiveClient.playUiSound();
        }
        if (closing || !animOn()) {
            finishClose();
            return;
        }
        closing = true;
        closeNanos = System.nanoTime();
        XFiveLiveClient.CONFIG.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Latar sederhana (tanpa blur bawaan Minecraft) supaya ringan di HP.
        float a = (hudEditMode ? 0.20f : 0.55f) * backdropAlpha();
        g.fill(0, 0, width, height, GlassPanelRenderer.argb(0x000000, a));
    }

    // =====================================================================
    // Gambar
    // =====================================================================

    private int accent() {
        return GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
    }

    private static int a(int rgb, float alpha) {
        return GlassPanelRenderer.argb(rgb, alpha);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean in(double mx, double my, int[] r) {
        return in(mx, my, r[0], r[1], r[2], r[3]);
    }

    private static void icon(GuiGraphicsExtractor g, String[] bmp, int x, int y, int color) {
        for (int r = 0; r < bmp.length; r++) {
            String row = bmp[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int s = c;
                while (c < row.length() && row.charAt(c) == '#') {
                    c++;
                }
                g.fill(x + s, y + r, x + c, y + r + 1, color);
            }
        }
    }

    private static void triRight(GuiGraphicsExtractor g, int x, int y, int h, int color) {
        for (int i = 0; i < h; i++) {
            int w = Math.min(i, h - 1 - i) + 1;
            g.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    private static void triLeft(GuiGraphicsExtractor g, int x, int y, int h, int w0, int color) {
        for (int i = 0; i < h; i++) {
            int w = Math.min(i, h - 1 - i) + 1;
            g.fill(x + w0 - w, y + i, x + w0, y + i + 1, color);
        }
    }

    private static String[] pageIcon(Page p) {
        return switch (p) {
            case LIBRARY -> IC_LIBRARY;
            case FAVORITES -> IC_HEART;
            case HUD -> IC_HUD;
            case CONTROLS -> IC_CONTROLS;
            case APPEARANCE -> IC_STAR;
            case SETTINGS -> IC_GEAR;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        layout();
        Font font = GlassPanelRenderer.font();

        if (hudEditMode) {
            drawHudEdit(g, font, mouseX, mouseY);
            return;
        }

        float s = panelScale();
        boolean anim = s != 1f;
        if (anim) {
            float cx = px + pw / 2f;
            float cy = py + ph / 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.pose().translate(-cx, -cy);
        }

        int accent = accent();
        // badan panel
        if ("dark_music".equals(XFiveLiveClient.CONFIG.themeStyle)) {
            Glass.roundRect(g, px, py, pw, ph, 8, 0xFF1F2023);
            Glass.border(g, px, py, pw, ph, 8, a(0xFFFFFF, 0.14f), a(0xFFFFFF, 0.05f));
        } else {
            Glass.panel(g, px, py, pw, ph, 8, Math.max(0.55f, XFiveLiveClient.CONFIG.customTransparency), accent);
        }

        drawSidebar(g, font, accent, mouseX, mouseY);
        drawTopBar(g, font, accent, mouseX, mouseY);

        // isi halaman: geser sedikit ke atas saat berpindah halaman
        double pt = Math.min(1.0, (System.nanoTime() - pageNanos) / 1_000_000_000.0 / 0.22);
        int slide = animOn() ? (int) Math.round((1.0 - pt) * (1.0 - pt) * 8.0) : 0;
        if (slide != 0) {
            g.pose().pushMatrix();
            g.pose().translate(0f, (float) slide);
        }
        if (isMusicPage()) {
            drawMusicPage(g, font, accent, mouseX, mouseY, !anim);
        } else {
            drawRows(g, font, accent, mouseX, mouseY, !anim);
        }
        if (slide != 0) {
            g.pose().popMatrix();
        }

        drawPlayerBar(g, font, accent, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partialTick); // kotak teks (EditBox)

        if (anim) {
            g.pose().popMatrix();
        }
    }

    private void drawSidebar(GuiGraphicsExtractor g, Font font, int accent, int mx, int my) {
        Glass.roundRect(g, px + 3, py + 3, sbW - 3, ph - 6, 6, a(0x000000, 0.30f));
        GlassPanelRenderer.text(g, font, "X5", px + 10, py + 12, accent);
        if (sbW > 40) {
            GlassPanelRenderer.text(g, font, "LIVE", px + 28, py + 12, GlassPanelRenderer.MUTED);
        }
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            int[] r = navRect(i);
            boolean sel = pages[i] == page;
            boolean hov = in(mx, my, r);
            boolean pressed = pressedKey.equals("nav" + i);
            if (sel) {
                Glass.roundRect(g, r[0], r[1], r[2], r[3], 5, a(accent, 0.22f));
            } else if (hov || pressed) {
                Glass.roundRect(g, r[0], r[1], r[2], r[3], 5, a(0xFFFFFF, pressed ? 0.12f : 0.07f));
            }
            int col = sel ? accent : (hov ? 0xFFFFFF : 0xB4B8C2);
            String[] ic = pageIcon(pages[i]);
            icon(g, ic, r[0] + 7, r[1] + (r[3] - ic.length) / 2, 0xFF000000 | col);
            if (sbW > 40) {
                GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, pages[i].title, r[2] - 28),
                        r[0] + 22, r[1] + (r[3] - 9) / 2, sel ? 0xFFFFFF : col);
            }
        }
    }

    private int[] backRect() { return new int[] {cx0() + 6, py + 6, 16, 16}; }
    private int[] fwdRect() { return new int[] {cx0() + 24, py + 6, 16, 16}; }
    private int[] gearRect() { return new int[] {px + pw - 46, py + 6, 18, 18}; }
    private int[] closeRect() { return new int[] {px + pw - 26, py + 6, 18, 18}; }

    private void drawTopBar(GuiGraphicsExtractor g, Font font, int accent, int mx, int my) {
        int[] b = backRect();
        int[] f = fwdRect();
        boolean canBack = histPos > 0;
        boolean canFwd = histPos < history.size() - 1;
        if (in(mx, my, b) && canBack) Glass.roundRect(g, b[0], b[1], b[2], b[3], 5, a(0xFFFFFF, 0.10f));
        if (in(mx, my, f) && canFwd) Glass.roundRect(g, f[0], f[1], f[2], f[3], 5, a(0xFFFFFF, 0.10f));
        triLeft(g, b[0] + 5, b[1] + 3, 10, 6, 0xFF000000 | (canBack ? 0xE6E8EE : 0x5A5E68));
        triRight(g, f[0] + 5, f[1] + 3, 10, 0xFF000000 | (canFwd ? 0xE6E8EE : 0x5A5E68));

        if (isMusicPage()) {
            int x = searchX();
            Glass.roundRect(g, x, py + 6, searchW(), 16, 8, a(0xFFFFFF, 0.08f));
            icon(g, IC_SEARCH, x + 6, py + 10, 0xFF9AA0AA);
        } else {
            GlassPanelRenderer.text(g, font, page.title, searchX() + 2, py + 10, 0xFFFFFF);
        }

        int[] gr = gearRect();
        int[] cr = closeRect();
        if (in(mx, my, gr)) Glass.roundRect(g, gr[0], gr[1], gr[2], gr[3], 5, a(0xFFFFFF, 0.10f));
        if (in(mx, my, cr)) Glass.roundRect(g, cr[0], cr[1], cr[2], cr[3], 5, a(0xFF5C5C, 0.30f));
        icon(g, IC_GEAR, gr[0] + 4, gr[1] + 4, 0xFF000000 | (page == Page.SETTINGS ? accent : 0xC8CCD4));
        icon(g, IC_CLOSE, cr[0] + 6, cr[1] + 6, 0xFFE6E8EE);
    }

    // ---------- halaman musik ----------

    private static int coverColor(PlaylistManager.Track t) {
        return COVER_COLORS[Math.floorMod(t.title().hashCode(), COVER_COLORS.length)];
    }

    private static String initial(PlaylistManager.Track t) {
        return t.title().isEmpty() ? "?" : t.title().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private List<Integer> shownIndexes() {
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        String q = searchText == null ? "" : searchText.strip().toLowerCase(Locale.ROOT);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistManager.Track t = tracks.get(i);
            if (page == Page.FAVORITES && !isFavorite(t)) {
                continue;
            }
            if (q.isEmpty() || t.title().toLowerCase(Locale.ROOT).contains(q)
                    || t.artist().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(i);
            }
        }
        return out;
    }

    private static boolean isFavorite(PlaylistManager.Track t) {
        return XFiveLiveClient.CONFIG.favoriteMusic.contains(t.path().toAbsolutePath().toString());
    }

    private static void toggleFavorite(PlaylistManager.Track t) {
        String key = t.path().toAbsolutePath().toString();
        if (!XFiveLiveClient.CONFIG.favoriteMusic.add(key)) {
            XFiveLiveClient.CONFIG.favoriteMusic.remove(key);
        }
        XFiveLiveClient.CONFIG.save();
    }

    private double maxSongScroll(int count) {
        return Math.max(0, count * STEP - (listBot() - listTop()));
    }

    private void drawMusicPage(GuiGraphicsExtractor g, Font font, int accent, int mx, int my, boolean clip) {
        if (wide()) {
            drawNowPlaying(g, font, accent, mx, my);
        }
        if (page == Page.LIBRARY) {
            int[] pill = sourcePill();
            Glass.roundRect(g, pill[0], pill[1], pill[2], pill[3], 6, a(0xFFFFFF, 0.07f));
            int[] ar = addRect();
            int[] rr = refreshRect();
            drawPill(g, font, ar, "Add", accent, in(mx, my, ar), pressedKey.equals("add"));
            drawPill(g, font, rr, "Refresh", accent, in(mx, my, rr), pressedKey.equals("refresh"));
        }

        int x = listX();
        int r = listR();
        int top = listTop();
        int bot = listBot();
        List<Integer> shown = shownIndexes();
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        songScroll = Math.max(0, Math.min(maxSongScroll(shown.size()), songScroll));
        int current = XFiveLiveClient.MUSIC.playlist().currentIndex();
        boolean playing = XFiveLiveClient.MUSIC.isPlaying();
        double time = System.nanoTime() / 1_000_000_000.0;

        if (shown.isEmpty()) {
            String msg = XFiveLiveClient.MUSIC.playlist().size() == 0
                    ? "No songs yet. Add a folder above, then tap Refresh."
                    : (page == Page.FAVORITES ? "No favorites yet. Tap the heart on a song." : "No match.");
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, msg, r - x - 8), x + 4, top + 8,
                    GlassPanelRenderer.MUTED);
        }

        if (clip) {
            g.enableScissor(x, top, r, bot);
        }
        for (int i = 0; i < shown.size(); i++) {
            int y = top + (int) Math.round(i * STEP - songScroll);
            if (y + CARD_H < top || y > bot) {
                continue;
            }
            if (!clip && (y < top || y + CARD_H > bot)) {
                continue;
            }
            int idx = shown.get(i);
            PlaylistManager.Track t = tracks.get(idx);
            boolean sel = idx == current;
            boolean hov = in(mx, my, x, y, r - x, CARD_H) && my >= top && my < bot;
            Glass.roundRect(g, x, y, r - x, CARD_H, 6,
                    sel ? a(accent, 0.16f) : a(0xFFFFFF, hov ? 0.10f : 0.06f));
            if (sel) {
                Glass.border(g, x, y, r - x, CARD_H, 6, a(accent, 0.95f), a(accent, 0.95f));
            }
            // sampul kecil (atau equalizer saat diputar)
            int tx = x + 6;
            int ty = y + (CARD_H - 18) / 2;
            Glass.roundRect(g, tx, ty, 18, 18, 4, a(coverColor(t), 0.90f));
            if (sel && playing) {
                for (int b = 0; b < 3; b++) {
                    double wave = 0.5 + 0.5 * Math.sin(time * 6 + b * 1.9);
                    int bh = 3 + (int) Math.round(wave * 9);
                    g.fill(tx + 3 + b * 4, ty + 15 - bh, tx + 5 + b * 4, ty + 15, 0xFFFFFFFF);
                }
            } else {
                String ini = initial(t);
                GlassPanelRenderer.text(g, font, ini, tx + (18 - GlassPanelRenderer.width(font, ini)) / 2,
                        ty + 5, 0xFFFFFF);
            }
            int textX = tx + 18 + 8;
            int maxW = r - 24 - textX;
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, t.title(), maxW), textX, y + 4,
                    sel ? accent : 0xFFFFFF);
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, t.artist(), maxW), textX, y + 15,
                    GlassPanelRenderer.MUTED);
            boolean fav = isFavorite(t);
            icon(g, IC_HEART, r - 17, y + (CARD_H - 8) / 2, fav ? (0xFF000000 | accent) : 0xFF6A6E78);
        }
        if (clip) {
            g.disableScissor();
        }

        double max = maxSongScroll(shown.size());
        if (max > 0) {
            int trackH = bot - top;
            int thumbH = Math.max(14, (int) (trackH * (trackH / (trackH + max))));
            int thumbY = top + (int) ((trackH - thumbH) * (songScroll / max));
            g.fill(r + 2, thumbY, r + 4, thumbY + thumbH, a(0xFFFFFF, 0.35f));
        }
    }

    private void drawPill(GuiGraphicsExtractor g, Font font, int[] r, String label, int accent, boolean hov,
                          boolean pressed) {
        float alpha = pressed ? 0.40f : (hov ? 0.30f : 0.20f);
        Glass.roundRect(g, r[0], r[1], r[2], r[3], 6, a(accent, alpha));
        int w = GlassPanelRenderer.width(font, label);
        GlassPanelRenderer.text(g, font, label, r[0] + (r[2] - w) / 2, r[1] + (r[3] - 9) / 2, 0xFFFFFF);
    }

    private int[] nowRect() {
        int top = contentTop() + 4;
        return new int[] {cx0() + 8, top, npW(), contentBot() - 4 - top};
    }

    private int[] npPlayRect(int[] n, int cover) {
        return new int[] {n[0] + 4, n[1] + cover + 44, 26, 26};
    }

    private int[] npHeartRect(int[] n, int cover) {
        return new int[] {n[0] + 36, n[1] + cover + 44, 26, 26};
    }

    private int coverSize(int[] n) {
        return Math.max(40, Math.min(n[2] - 8, n[3] - 80));
    }

    private void drawNowPlaying(GuiGraphicsExtractor g, Font font, int accent, int mx, int my) {
        int[] n = nowRect();
        Glass.roundRect(g, n[0], n[1], n[2], n[3], 8, a(0xFFFFFF, 0.05f));
        PlaylistManager.Track t = XFiveLiveClient.MUSIC.currentTrack();
        int cover = coverSize(n);
        int cx = n[0] + (n[2] - cover) / 2;
        int cy = n[1] + 4;
        int color = t == null ? 0x4A4D55 : coverColor(t);
        Glass.roundRect(g, cx, cy, cover, cover, 6, a(color, 0.92f));
        Glass.disc(g, cx + cover / 2, cy + cover / 2, Math.max(6, cover / 4), a(0x000000, 0.22f));
        String ini = t == null ? "X5" : initial(t);
        g.pose().pushMatrix();
        g.pose().translate((float) (cx + cover / 2), (float) (cy + cover / 2));
        g.pose().scale(2f, 2f);
        GlassPanelRenderer.text(g, font, ini, -GlassPanelRenderer.width(font, ini) / 2, -4, 0xFFFFFF);
        g.pose().popMatrix();

        int tx = n[0] + 6;
        int maxW = n[2] - 12;
        String title = t == null ? "Nothing playing" : t.title();
        String artist = t == null ? "Pick a song" : t.artist();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, title, maxW), tx, cy + cover + 6, 0xFFFFFF);
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, artist, maxW), tx, cy + cover + 18,
                GlassPanelRenderer.MUTED);
        long dur = XFiveLiveClient.MUSIC.durationMs();
        GlassPanelRenderer.text(g, font, dur > 0 ? formatTime(dur) : "--:--", tx, cy + cover + 30, accent);

        int[] pr = npPlayRect(n, cover - 4);
        int[] hr = npHeartRect(n, cover - 4);
        boolean pp = pressedKey.equals("npplay");
        Glass.disc(g, pr[0] + 13, pr[1] + 13, pp ? 11 : 13, a(accent, in(mx, my, pr) ? 1f : 0.92f));
        drawPlayGlyph(g, pr[0] + 13, pr[1] + 13, XFiveLiveClient.MUSIC.isPlaying(), 0xFF101114);
        Glass.disc(g, hr[0] + 13, hr[1] + 13, 13, a(0xFFFFFF, in(mx, my, hr) ? 0.16f : 0.09f));
        boolean fav = t != null && isFavorite(t);
        icon(g, IC_HEART, hr[0] + 9, hr[1] + 9, fav ? (0xFF000000 | accent) : 0xFFB4B8C2);
    }

    private static void drawPlayGlyph(GuiGraphicsExtractor g, int cx, int cy, boolean playing, int color) {
        if (playing) {
            g.fill(cx - 4, cy - 5, cx - 1, cy + 5, color);
            g.fill(cx + 1, cy - 5, cx + 4, cy + 5, color);
        } else {
            triRight(g, cx - 3, cy - 5, 11, color);
        }
    }

    // ---------- baris pengaturan ----------

    private void drawRows(GuiGraphicsExtractor g, Font font, int accent, int mx, int my, boolean clip) {
        int top = rowsTop();
        int bot = rowsBot();
        double maxScroll = Math.max(0, rowsH - (bot - top));
        rowsScroll = Math.max(0, Math.min(maxScroll, rowsScroll));
        if (clip) {
            g.enableScissor(rowsX(), top, rowsR(), bot);
        }
        for (Row r : rows) {
            int ax = rowsX() + r.x;
            int ay = top + r.y - (int) Math.round(rowsScroll);
            if (ay + r.h < top || ay > bot) {
                continue;
            }
            if (!clip && (ay < top || ay + r.h > bot)) {
                continue;
            }
            drawRow(g, font, accent, r, ax, ay, mx, my);
        }
        if (clip) {
            g.disableScissor();
        }
        if (maxScroll > 0) {
            int trackH = bot - top;
            int thumbH = Math.max(14, (int) (trackH * (trackH / (trackH + maxScroll))));
            int thumbY = top + (int) ((trackH - thumbH) * (rowsScroll / maxScroll));
            g.fill(rowsR() + 3, thumbY, rowsR() + 5, thumbY + thumbH, a(0xFFFFFF, 0.35f));
        }
    }

    private int sliderX0(Row r, int ax) { return ax + (int) (r.w * 0.42); }
    private int sliderX1(Row r, int ax, Font font) {
        int vw = GlassPanelRenderer.width(font, r.value.get());
        return Math.max(sliderX0(r, ax) + 24, ax + r.w - 10 - vw - 8);
    }

    private void drawRow(GuiGraphicsExtractor g, Font font, int accent, Row r, int ax, int ay, int mx, int my) {
        if (r.kind == Row.NOTE) {
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, r.label, r.w - 8), ax + 4, ay + 4,
                    GlassPanelRenderer.MUTED);
            return;
        }
        boolean hov = in(mx, my, ax, ay, r.w, r.h) && my >= rowsTop() && my < rowsBot();
        boolean pressed = pressedRow == r && !dragged;
        if (r.kind == Row.ACTION) {
            Glass.roundRect(g, ax, ay, r.w, r.h, 6, a(accent, pressed ? 0.38f : (hov ? 0.30f : 0.20f)));
            int w = GlassPanelRenderer.width(font, r.label);
            GlassPanelRenderer.text(g, font, r.label, ax + (r.w - w) / 2, ay + (r.h - 9) / 2, 0xFFFFFF);
            return;
        }
        Glass.roundRect(g, ax, ay, r.w, r.h, 6, a(0xFFFFFF, pressed ? 0.12f : (hov ? 0.10f : 0.06f)));
        int labelY = ay + (r.h - 9) / 2;
        switch (r.kind) {
            case Row.TOGGLE -> {
                GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, r.label, r.w - 50), ax + 8, labelY,
                        0xFFFFFF);
                boolean on = r.on.getAsBoolean();
                int sx = ax + r.w - 32;
                int sy = ay + (r.h - 12) / 2;
                Glass.roundRect(g, sx, sy, 24, 12, 6, on ? a(accent, 0.95f) : a(0xFFFFFF, 0.20f));
                int kx = on ? sx + 13 : sx + 2;
                Glass.roundRect(g, kx, sy + 2, 9, 8, 4, 0xFFFFFFFF);
            }
            case Row.CYCLE -> {
                String v = r.value.get();
                int vw = GlassPanelRenderer.width(font, v);
                GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, r.label, r.w - vw - 26), ax + 8,
                        labelY, 0xFFFFFF);
                GlassPanelRenderer.text(g, font, v, ax + r.w - 8 - vw, labelY, accent);
            }
            case Row.SLIDER -> {
                String v = r.value.get();
                int vw = GlassPanelRenderer.width(font, v);
                int x0 = sliderX0(r, ax);
                int x1 = sliderX1(r, ax, font);
                GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, r.label, x0 - ax - 12), ax + 8,
                        labelY, 0xFFFFFF);
                GlassPanelRenderer.text(g, font, v, ax + r.w - 8 - vw, labelY, accent);
                int ty = ay + r.h / 2 - 1;
                g.fill(x0, ty, x1, ty + 3, a(0xFFFFFF, 0.20f));
                double f = Math.max(0, Math.min(1, r.get.getAsDouble()));
                int kx = x0 + (int) Math.round((x1 - x0) * f);
                g.fill(x0, ty, kx, ty + 3, 0xFF000000 | accent);
                Glass.roundRect(g, kx - 3, ty - 3, 7, 9, 3, 0xFFFFFFFF);
            }
            case Row.SWATCHES -> {
                GlassPanelRenderer.text(g, font, r.label, ax + 8, ay + 5, GlassPanelRenderer.MUTED);
                int n = Themes.IDS.length;
                int size = swatchSize(r);
                int sel = Themes.indexOf(XFiveLiveClient.CONFIG.theme);
                for (int i = 0; i < n; i++) {
                    int sx = ax + 8 + i * (size + 4);
                    int sy = ay + 18;
                    Glass.roundRect(g, sx, sy, size, 12, 3, 0xFF000000 | Themes.COLORS[i]);
                    if (i == sel) {
                        Glass.border(g, sx - 2, sy - 2, size + 4, 16, 4, 0xFFFFFFFF, 0xFFFFFFFF);
                    }
                }
            }
            case Row.SAMPLE -> {
                int vw = r.w - 16;
                GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, "Now Playing - Sample Song 0123", vw),
                        ax + 8, ay + 6, 0xFFFFFF);
                GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, "The quick brown fox jumps over", vw),
                        ax + 8, ay + 19, GlassPanelRenderer.MUTED);
            }
            default -> {
            }
        }
    }

    private int swatchSize(Row r) {
        int n = Themes.IDS.length;
        return Math.max(12, Math.min(26, (r.w - 16 - 4 * (n - 1)) / n));
    }

    // ---------- bilah pemutar ----------

    /** Kotak kontrol pemutar: 0 ulang, 1 sebelumnya, 2 putar, 3 berikutnya, 4 acak, 5 volume, 6 bar progres. */
    private int[][] playerRects() {
        int by = py + ph - BOT_H;
        int cy = by + 26;
        int groupW = 14 + 8 + 14 + 8 + 24 + 8 + 14 + 8 + 14;
        int minStart = px + 10 + 26 + 6 + 56;
        int start = Math.max(minStart, px + (pw - groupW) / 2);
        int[][] r = new int[7][];
        r[0] = new int[] {start, cy - 8, 16, 16};
        r[1] = new int[] {start + 22, cy - 8, 16, 16};
        r[2] = new int[] {start + 44, cy - 12, 24, 24};
        r[3] = new int[] {start + 74, cy - 8, 16, 16};
        r[4] = new int[] {start + 96, cy - 8, 16, 16};
        int volW = 56;
        r[5] = new int[] {px + pw - 10 - volW, cy - 6, volW, 12};
        r[6] = new int[] {px + 8, by - 3, pw - 16, 9};
        return r;
    }

    private boolean showVolume() { return pw >= 380; }

    private void drawPlayerBar(GuiGraphicsExtractor g, Font font, int accent, int mx, int my) {
        int by = py + ph - BOT_H;
        // garis pemisah halus
        g.fill(px + 6, by - 1, px + pw - 6, by, a(0xFFFFFF, 0.06f));

        long dur = XFiveLiveClient.MUSIC.durationMs();
        long pos = XFiveLiveClient.MUSIC.positionMs();
        float prog = dur > 0 ? Math.max(0f, Math.min(1f, (float) pos / dur)) : 0f;
        int[][] r = playerRects();
        int lx = r[6][0] + 1;
        int lw = r[6][2] - 2;
        g.fill(lx, by, lx + lw, by + 3, a(0xFFFFFF, 0.14f));
        int filled = Math.round(lw * prog);
        if (filled > 0) {
            g.fill(lx, by, lx + filled, by + 3, 0xFF000000 | accent);
        }
        if (seeking || in(mx, my, r[6])) {
            Glass.disc(g, lx + filled, by + 1, 3, 0xFFFFFFFF);
        }

        PlaylistManager.Track t = XFiveLiveClient.MUSIC.currentTrack();
        // kiri: sampul kecil + judul
        int cover = 26;
        int cyTop = by + 11;
        Glass.roundRect(g, px + 10, cyTop, cover, cover, 5, a(t == null ? 0x4A4D55 : coverColor(t), 0.92f));
        String ini = t == null ? "X" : initial(t);
        GlassPanelRenderer.text(g, font, ini, px + 10 + (cover - GlassPanelRenderer.width(font, ini)) / 2,
                cyTop + 9, 0xFFFFFF);
        int tx = px + 10 + cover + 6;
        int maxW = Math.max(30, r[0][0] - 8 - tx);
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, t == null ? "Nothing playing" : t.title(), maxW),
                tx, cyTop + 3, 0xFFFFFF);
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.fit(font, t == null ? "XFiveLive" : t.artist(), maxW),
                tx, cyTop + 14, GlassPanelRenderer.MUTED);

        // tengah: transport
        XFiveConfig c = XFiveLiveClient.CONFIG;
        int white = 0xFFE6E8EE;
        boolean rep = c.repeatMode != 0;
        drawBtnBg(g, r[0], mx, my, "repeat");
        icon(g, IC_REPEAT, r[0][0] + 2, r[0][1] + 4, rep ? (0xFF000000 | accent) : 0xFF8A8E98);
        if (c.repeatMode == 2) {
            GlassPanelRenderer.text(g, font, "1", r[0][0] + 6, r[0][1] + 4, accent);
        }
        drawBtnBg(g, r[1], mx, my, "prev");
        g.fill(r[1][0] + 3, r[1][1] + 3, r[1][0] + 5, r[1][1] + 13, white);
        triLeft(g, r[1][0] + 5, r[1][1] + 3, 10, 7, white);
        boolean pp = pressedKey.equals("play");
        int pcx = r[2][0] + 12;
        int pcy = r[2][1] + 12;
        Glass.disc(g, pcx, pcy, pp ? 10 : 12, a(accent, 0.95f));
        Glass.disc(g, pcx, pcy, pp ? 8 : 10, 0xFF1F2023);
        drawPlayGlyph(g, pcx, pcy, XFiveLiveClient.MUSIC.isPlaying(), 0xFFFFFFFF);
        drawBtnBg(g, r[3], mx, my, "next");
        triRight(g, r[3][0] + 3, r[3][1] + 3, 10, white);
        g.fill(r[3][0] + 10, r[3][1] + 3, r[3][0] + 12, r[3][1] + 13, white);
        drawBtnBg(g, r[4], mx, my, "shuffle");
        icon(g, IC_SHUFFLE, r[4][0] + 3, r[4][1] + 4, c.shuffle ? (0xFF000000 | accent) : 0xFF8A8E98);

        // waktu
        String time = formatTime(pos) + " / " + formatTime(dur);
        int timeX = r[4][0] + 22;
        int limit = showVolume() ? r[5][0] - 20 : px + pw - 8;
        if (timeX + GlassPanelRenderer.width(font, time) <= limit) {
            GlassPanelRenderer.text(g, font, time, timeX, r[4][1] + 4, GlassPanelRenderer.MUTED);
        }

        // kanan: volume
        if (showVolume()) {
            int[] v = r[5];
            icon(g, IC_VOLUME, v[0] - 14, v[1] + 2, 0xFFB4B8C2);
            int ty = v[1] + v[3] / 2 - 1;
            g.fill(v[0], ty, v[0] + v[2], ty + 3, a(0xFFFFFF, 0.20f));
            int vx = v[0] + Math.round(v[2] * c.defaultVolume);
            g.fill(v[0], ty, vx, ty + 3, 0xFF000000 | accent);
            Glass.roundRect(g, vx - 3, ty - 3, 7, 9, 3, 0xFFFFFFFF);
        }
    }

    private void drawBtnBg(GuiGraphicsExtractor g, int[] r, int mx, int my, String key) {
        boolean pressed = pressedKey.equals(key);
        if (pressed) {
            Glass.disc(g, r[0] + r[2] / 2, r[1] + r[3] / 2, 9, a(0xFFFFFF, 0.14f));
        } else if (in(mx, my, r)) {
            Glass.disc(g, r[0] + r[2] / 2, r[1] + r[3] / 2, 9, a(0xFFFFFF, 0.07f));
        }
    }

    private static String formatTime(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60L, s % 60L);
    }

    // ---------- mode atur posisi HUD ----------

    private int[] doneRect() {
        return new int[] {width / 2 - 70, height - 30, 140, 20};
    }

    private void drawHudEdit(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int accent = accent();
        drawHudPreviews(g, font, accent);
        int[] d = doneRect();
        Glass.roundRect(g, d[0] - 4, d[1] - 16, d[2] + 8, d[3] + 20, 8, a(0x000000, 0.65f));
        GlassPanelRenderer.text(g, font, "Drag the previews to move", d[0] + 2, d[1] - 12, 0xFFFFFF);
        drawPill(g, font, d, "Done", accent, in(mx, my, d), false);
    }

    private void drawHudPreviews(GuiGraphicsExtractor g, Font font, int accent) {
        int[] island = DynamicIslandHud.box();
        int[] music = com.xfive.live.hud.MusicHud.box();
        int[] lyrics = com.xfive.live.hud.LyricsHud.box();
        XFiveConfig c = XFiveLiveClient.CONFIG;

        int ix = island[0], iy = island[1], iw = island[2], ih = island[3];
        g.fill(ix, iy, ix + iw, iy + ih, c.islandStyle == 1 ? 0xF2050609 : 0xE51B2029);
        g.fill(ix, iy, ix + iw, iy + 2, a(accent, 0.95f));
        if (c.islandStyle == 1) {
            GlassPanelRenderer.text(g, font, "BORING NOTCH PREVIEW", ix + 16, iy + 9, 0xB8C0CE);
            GlassPanelRenderer.text(g, font, "Now Playing", ix + 18, iy + 31, 0xFFFFFF);
            GlassPanelRenderer.text(g, font, "Song title - Artist", ix + 18, iy + 48, 0xB8C0CE);
        } else {
            GlassPanelRenderer.text(g, font, "DYNAMIC ISLAND PREVIEW", ix + 10, iy + 8, 0xFFFFFF);
            GlassPanelRenderer.text(g, font, "Song title - Artist", ix + 10, iy + 24, 0xB8C0CE);
            g.fill(ix + 10, iy + ih - 8, ix + iw - 10, iy + ih - 6, 0x66FFFFFF);
            g.fill(ix + 10, iy + ih - 8, ix + 10 + Math.max(1, (iw - 20) / 3), iy + ih - 6, 0xFF000000 | accent);
        }
        outline(g, ix, iy, iw, ih, a(accent, 0.95f));

        int mx = music[0], my = music[1], mw = music[2], mh = music[3];
        g.fill(mx, my, mx + mw, my + mh, 0xE51B2029);
        g.fill(mx, my, mx + mw, my + 2, a(GlassPanelRenderer.CYAN, 0.95f));
        GlassPanelRenderer.text(g, font, "MUSIC HUD PREVIEW", mx + 6, my + 5, 0xFFFFFF);
        GlassPanelRenderer.text(g, font, "Song title - Artist", mx + 6, my + 17, 0xB8C0CE);
        outline(g, mx, my, mw, mh, a(GlassPanelRenderer.CYAN, 0.95f));

        int lx = lyrics[0], ly = lyrics[1], lw = lyrics[2], lh = lyrics[3];
        g.fill(lx, ly, lx + lw, ly + lh, 0x55303038);
        String[] lines = {"Previous lyric line", "CURRENT LYRIC PREVIEW", "Next lyric line"};
        int lineH = Math.max(9, lh / 3);
        for (int i = 0; i < lines.length; i++) {
            int color = i == 1 ? c.highlightColor : 0xB8C0CE;
            String line = GlassPanelRenderer.fit(font, lines[i], Math.max(40, lw - 12));
            GlassPanelRenderer.text(g, font, line, lx + (lw - GlassPanelRenderer.width(font, line)) / 2,
                    ly + 3 + i * lineH, color);
        }
        outline(g, lx, ly, lw, lh, a(c.highlightColor, 0.9f));
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x - 2, y - 2, x + w + 2, y, color);
        g.fill(x - 2, y + h, x + w + 2, y + h + 2, color);
        g.fill(x - 2, y, x, y + h, color);
        g.fill(x + w, y, x + w + 2, y + h, color);
    }

    // =====================================================================
    // Baris pengaturan (model)
    // =====================================================================

    private static final class Row {
        static final int TOGGLE = 0;
        static final int SLIDER = 1;
        static final int CYCLE = 2;
        static final int ACTION = 3;
        static final int SWATCHES = 4;
        static final int SAMPLE = 5;
        static final int NOTE = 6;

        final int kind;
        final String label;
        Supplier<String> value = () -> "";
        BooleanSupplier on = () -> false;
        Runnable action = () -> { };
        DoubleSupplier get = () -> 0;
        DoubleConsumer set = v -> { };
        boolean full;
        int h = 22;
        int x;
        int y;
        int w;

        Row(int kind, String label) {
            this.kind = kind;
            this.label = label;
            if (kind == SWATCHES || kind == SAMPLE) {
                full = true;
                h = 34;
            }
            if (kind == NOTE) {
                full = true;
                h = 16;
            }
        }
    }

    private void layoutRows() {
        int w = rowsR() - rowsX();
        boolean two = w >= 300;
        int colW = two ? (w - 6) / 2 : w;
        int curY = 0;
        int col = 0;
        int lineH = 0;
        for (Row r : rows) {
            if (r.full || !two) {
                if (col == 1) {
                    curY += lineH + 4;
                    col = 0;
                    lineH = 0;
                }
                r.x = 0;
                r.w = w;
                r.y = curY;
                curY += r.h + 4;
            } else {
                r.x = col == 0 ? 0 : colW + 6;
                r.w = colW;
                r.y = curY;
                lineH = Math.max(lineH, r.h);
                if (col == 0) {
                    col = 1;
                } else {
                    col = 0;
                    curY += lineH + 4;
                    lineH = 0;
                }
            }
        }
        if (col == 1) {
            curY += lineH + 4;
        }
        rowsH = curY;
    }

    private Row toggle(String label, BooleanSupplier get, Runnable flip) {
        Row r = new Row(Row.TOGGLE, label);
        r.on = get;
        r.action = () -> {
            flip.run();
            XFiveLiveClient.CONFIG.save();
        };
        return r;
    }

    private Row cycle(String label, Supplier<String> value, Runnable next) {
        Row r = new Row(Row.CYCLE, label);
        r.value = value;
        r.action = () -> {
            next.run();
            XFiveLiveClient.CONFIG.save();
        };
        return r;
    }

    private Row slider(String label, DoubleSupplier get, DoubleConsumer set, Supplier<String> value) {
        Row r = new Row(Row.SLIDER, label);
        r.get = get;
        r.set = set;
        r.value = value;
        return r;
    }

    private Row action(String label, Runnable run) {
        Row r = new Row(Row.ACTION, label);
        r.action = () -> {
            run.run();
            XFiveLiveClient.CONFIG.save();
        };
        return r;
    }

    private void buildRows() {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        List<Row> out = new ArrayList<>();
        switch (page) {
            case HUD -> {
                out.add(toggle("Master HUD", () -> c.showHud, () -> c.showHud = !c.showHud));
                out.add(toggle("Music HUD", () -> c.enableMusic, () -> c.enableMusic = !c.enableMusic));
                out.add(toggle("Dynamic Island", () -> c.dynamicIsland, () -> c.dynamicIsland = !c.dynamicIsland));
                out.add(toggle("Lyrics HUD", () -> c.enableLyrics, () -> c.enableLyrics = !c.enableLyrics));
                out.add(cycle("Lyric animation", () -> LyricStyle.NAMES[LyricStyle.indexOf(c.lyricsAnim)],
                        () -> c.lyricsAnim = LyricStyle.next(c.lyricsAnim)));
                out.add(toggle("Karaoke highlight", () -> c.lyricsKaraoke, () -> c.lyricsKaraoke = !c.lyricsKaraoke));
                out.add(toggle("Prev/next lines", () -> c.lyricsPreview, () -> c.lyricsPreview = !c.lyricsPreview));
                out.add(cycle("Lyrics position", () -> new String[] {"Top", "Middle", "Bottom"}[c.lyricsPosition],
                        () -> c.lyricsPosition = (c.lyricsPosition + 1) % 3));
                out.add(slider("Lyrics sync", () -> (c.lyricsOffsetMs + 3000) / 6000.0,
                        v -> c.lyricsOffsetMs = (int) (Math.round((v * 6000.0 - 3000.0) / 50.0) * 50),
                        () -> (c.lyricsOffsetMs > 0 ? "+" : "") + c.lyricsOffsetMs + " ms"));
                out.add(cycle("Dynamic Bar", () -> c.islandStyle == 1 ? "Boring Notch" : "Classic", () -> {
                    c.islandStyle = c.islandStyle == 0 ? 1 : 0;
                    c.islandW = Math.max(0, Math.min(520, c.islandW));
                    c.islandH = Math.max(0, Math.min(160, c.islandH));
                }));
                out.add(slider("HUD opacity", () -> (c.hudOpacity - 0.1) / 0.9,
                        v -> c.hudOpacity = (float) (0.1 + v * 0.9),
                        () -> Math.round(c.hudOpacity * 100) + "%"));
                out.add(slider("Bar width", () -> c.islandW / 520.0,
                        v -> c.islandW = (int) Math.round(v * 520), () -> c.islandW <= 0 ? "Hidden" : c.islandW + " px"));
                out.add(slider("Bar height", () -> c.islandH / 160.0,
                        v -> c.islandH = (int) Math.round(v * 160), () -> c.islandH <= 0 ? "Hidden" : c.islandH + " px"));
                out.add(action("Move HUDs (drag)", () -> hudEditMode = true));
                out.add(action("Reset HUD positions", c::resetHudPositions));
                out.add(action("Reset bar size", XFiveLiveClient::resetIslandSize));
            }
            case CONTROLS -> {
                out.add(cycle("Bar shape", () -> c.islandCard ? "Card" : "Pill", XFiveLiveClient::toggleIslandCard));
                out.add(cycle("Bar color", ControlCenterScreen::colorName, XFiveLiveClient::cycleIslandColor));
                out.add(toggle("Auto-hide pop-up", () -> c.islandAutoHide, () -> c.islandAutoHide = !c.islandAutoHide));
                out.add(slider("Pop-up time", () -> (c.islandShowSec - 2) / 28.0,
                        v -> c.islandShowSec = 2 + (int) Math.round(v * 28), () -> c.islandShowSec + " s"));
                out.add(toggle("Show while paused", () -> c.islandShowPaused, XFiveLiveClient::toggleIslandShowPaused));
                out.add(cycle("Repeat", ControlCenterScreen::repeatLabel, XFiveLiveClient::cycleRepeat));
                out.add(toggle("Shuffle", () -> c.shuffle, XFiveLiveClient::toggleShuffle));
            }
            case APPEARANCE -> {
                Row sw = new Row(Row.SWATCHES, "Theme color");
                out.add(sw);
                out.add(cycle("UI style", () -> "dark_music".equals(c.themeStyle) ? "Dark Music" : "Liquid Glass Lite",
                        () -> c.themeStyle = "dark_music".equals(c.themeStyle) ? "glass" : "dark_music"));
                out.add(cycle("Font", () -> Fonts.NAMES[Fonts.indexOf(c.uiFont)], () -> c.uiFont = Fonts.next(c.uiFont)));
                out.add(new Row(Row.SAMPLE, "Sample"));
                out.add(slider("Panel opacity", () -> (c.customTransparency - 0.1) / 0.9,
                        v -> c.customTransparency = (float) (0.1 + v * 0.9),
                        () -> Math.round(c.customTransparency * 100) + "%"));
                out.add(toggle("Spring animations", () -> c.animations, () -> c.animations = !c.animations));
            }
            case SETTINGS -> {
                out.add(toggle("Enable XFiveLive", () -> c.enableMod, () -> c.enableMod = !c.enableMod));
                out.add(toggle("Auto resume music", () -> c.autoResume, () -> c.autoResume = !c.autoResume));
                out.add(toggle("Show album artwork", () -> c.showArtwork, () -> c.showArtwork = !c.showArtwork));
                out.add(toggle("Auto-download lyrics", () -> c.autoLyrics, () -> c.autoLyrics = !c.autoLyrics));
                out.add(action("Rescan music folder", XFiveLiveClient::refreshMusic));
                Row note = new Row(Row.NOTE, "Music folder: xfive-music   Lyrics: xfive-lirik");
                out.add(note);
            }
            default -> {
            }
        }
        rows = out;
    }

    private static String colorName() {
        return DynamicIslandHud.COLOR_NAMES[XFiveLiveClient.CONFIG.islandColor];
    }

    private static String repeatLabel() {
        return switch (XFiveLiveClient.CONFIG.repeatMode) {
            case 0 -> "Off";
            case 2 -> "One";
            default -> "All";
        };
    }

    // =====================================================================
    // Navigasi
    // =====================================================================

    private void goTo(Page target, boolean record) {
        if (target == page) {
            return;
        }
        if (record) {
            while (history.size() > histPos + 1) {
                history.remove(history.size() - 1);
            }
            history.add(target);
            histPos = history.size() - 1;
        }
        page = target;
        songScroll = 0;
        rowsScroll = 0;
        pageNanos = System.nanoTime();
        buildRows();
        rebuildWidgets();
    }

    private void goHistory(int delta) {
        int next = histPos + delta;
        if (next < 0 || next >= history.size()) {
            return;
        }
        histPos = next;
        goTo(history.get(histPos), false);
    }

    // =====================================================================
    // Input
    // =====================================================================

    private void playOrPause() {
        var m = XFiveLiveClient.MUSIC;
        if (m.currentTrack() == null && m.playlist().size() > 0) {
            m.playlist().select(0);
            m.playCurrent();
        } else {
            m.togglePause();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (closing || !settled()) {
            return true; // abaikan ketukan selama animasi pop-in/out
        }
        if (hudEditMode) {
            if (in(mx, my, doneRect())) {
                hudEditMode = false;
                XFiveLiveClient.CONFIG.save();
                return true;
            }
            int[][] boxes = {DynamicIslandHud.box(), com.xfive.live.hud.MusicHud.box(),
                    com.xfive.live.hud.LyricsHud.box()};
            String[] keys = {"dynamicIsland", "musicHud", "lyricsHud"};
            for (int i = 0; i < boxes.length; i++) {
                if (in(mx, my, boxes[i])) {
                    beginHudDrag(keys[i], boxes[i], mx, my);
                    return true;
                }
            }
            return true;
        }
        layout();

        if (in(mx, my, closeRect())) {
            onClose();
            return true;
        }
        if (in(mx, my, gearRect())) {
            goTo(Page.SETTINGS, true);
            return true;
        }
        if (in(mx, my, backRect())) {
            goHistory(-1);
            return true;
        }
        if (in(mx, my, fwdRect())) {
            goHistory(1);
            return true;
        }
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            if (in(mx, my, navRect(i))) {
                pressedKey = "nav" + i;
                goTo(pages[i], true);
                return true;
            }
        }

        // bilah pemutar
        int[][] pr = playerRects();
        if (in(mx, my, pr[6])) {
            seeking = true;
            seekAt(mx);
            return true;
        }
        String[] keys = {"repeat", "prev", "play", "next", "shuffle"};
        for (int i = 0; i < 5; i++) {
            if (in(mx, my, pr[i])) {
                pressedKey = keys[i];
                switch (i) {
                    case 0 -> XFiveLiveClient.cycleRepeat();
                    case 1 -> XFiveLiveClient.MUSIC.previous();
                    case 2 -> playOrPause();
                    case 3 -> XFiveLiveClient.MUSIC.next();
                    default -> XFiveLiveClient.toggleShuffle();
                }
                return true;
            }
        }
        if (showVolume() && in(mx, my, pr[5][0] - 4, pr[5][1] - 3, pr[5][2] + 8, pr[5][3] + 6)) {
            volumeDrag = true;
            setVolumeAt(mx);
            return true;
        }

        if (super.mouseClicked(event, doubleClick)) {
            return true; // EditBox
        }

        if (isMusicPage()) {
            return clickMusicPage(mx, my);
        }
        return clickRows(mx, my);
    }

    private boolean clickMusicPage(double mx, double my) {
        if (page == Page.LIBRARY) {
            if (in(mx, my, addRect())) {
                pressedKey = "add";
                String raw = sourceInput == null ? pendingSource : sourceInput.getValue();
                String msg = XFiveLiveClient.addSource(raw);
                if (msg.startsWith("Ditambahkan")) {
                    pendingSource = "";
                    if (sourceInput != null) {
                        sourceInput.setValue("");
                    }
                }
                XFiveLiveClient.MUSIC.setStatus(msg);
                return true;
            }
            if (in(mx, my, refreshRect())) {
                pressedKey = "refresh";
                XFiveLiveClient.refreshMusic();
                songScroll = 0;
                return true;
            }
        }
        if (wide()) {
            int[] n = nowRect();
            int cover = coverSize(n);
            if (in(mx, my, npPlayRect(n, cover - 4))) {
                pressedKey = "npplay";
                playOrPause();
                return true;
            }
            if (in(mx, my, npHeartRect(n, cover - 4))) {
                var t = XFiveLiveClient.MUSIC.currentTrack();
                if (t != null) {
                    toggleFavorite(t);
                }
                return true;
            }
        }
        if (in(mx, my, listX(), listTop(), listR() - listX(), listBot() - listTop())) {
            List<Integer> shown = shownIndexes();
            int row = (int) Math.floor((my - listTop() + songScroll) / STEP);
            int local = (int) Math.floor(my - listTop() + songScroll) - row * STEP;
            int idx = row >= 0 && row < shown.size() && local < CARD_H ? shown.get(row) : -1;
            if (idx >= 0 && mx >= listR() - 26) {
                toggleFavorite(XFiveLiveClient.MUSIC.playlist().tracks().get(idx));
                return true;
            }
            pressTarget = 1;
            pressedTrack = idx; // baris ditentukan saat jari menyentuh, bukan saat dilepas
            dragged = false;
            pressX = mx;
            pressY = my;
            lastY = my;
            return true;
        }
        return false;
    }

    private Row rowAt(double mx, double my) {
        if (my < rowsTop() || my >= rowsBot()) {
            return null;
        }
        for (Row r : rows) {
            int ax = rowsX() + r.x;
            int ay = rowsTop() + r.y - (int) Math.round(rowsScroll);
            if (in(mx, my, ax, ay, r.w, r.h)) {
                return r;
            }
        }
        return null;
    }

    private boolean clickRows(double mx, double my) {
        Row r = rowAt(mx, my);
        if (r != null && r.kind == Row.SLIDER) {
            int ax = rowsX() + r.x;
            Font font = GlassPanelRenderer.font();
            if (mx >= sliderX0(r, ax) - 6 && mx <= sliderX1(r, ax, font) + 6) {
                activeSlider = r;
                setSlider(r, mx);
                return true;
            }
        }
        pressTarget = 2;
        pressedRow = r;
        dragged = false;
        pressX = mx;
        pressY = my;
        lastY = my;
        return true;
    }

    private void setSlider(Row r, double mx) {
        int ax = rowsX() + r.x;
        int x0 = sliderX0(r, ax);
        int x1 = sliderX1(r, ax, GlassPanelRenderer.font());
        double f = Math.max(0, Math.min(1, (mx - x0) / Math.max(1, x1 - x0)));
        r.set.accept(f);
    }

    private void setVolumeAt(double mx) {
        int[] v = playerRects()[5];
        float f = (float) Math.max(0, Math.min(1, (mx - v[0]) / Math.max(1, v[2])));
        XFiveLiveClient.CONFIG.defaultVolume = f;
        XFiveLiveClient.MUSIC.setVolume(f);
    }

    private void seekAt(double mx) {
        long dur = XFiveLiveClient.MUSIC.durationMs();
        if (dur <= 0L) {
            return;
        }
        int[] s = playerRects()[6];
        double f = (mx - (s[0] + 1)) / Math.max(1, s[2] - 2);
        XFiveLiveClient.MUSIC.seekTo((float) Math.max(0, Math.min(1, f)));
    }

    private void beginHudDrag(String key, int[] box, double mx, double my) {
        draggedHudKey = key;
        dragOffsetX = (int) mx - box[0];
        dragOffsetY = (int) my - box[1];
        int[] pos = XFiveLiveClient.CONFIG.hudPositions.get(key);
        if (pos[0] < 0 || pos[1] < 0) {
            pos[0] = box[0];
            pos[1] = box[1];
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mx = event.x();
        double my = event.y();
        if (draggedHudKey != null && hudEditMode) {
            int[] pos = XFiveLiveClient.CONFIG.hudPositions.get(draggedHudKey);
            pos[0] = Math.max(0, Math.min(Math.max(0, width - 40), (int) mx - dragOffsetX));
            pos[1] = Math.max(0, Math.min(Math.max(0, height - 24), (int) my - dragOffsetY));
            return true;
        }
        if (seeking) {
            seekAt(mx);
            return true;
        }
        if (volumeDrag) {
            setVolumeAt(mx);
            return true;
        }
        if (activeSlider != null) {
            setSlider(activeSlider, mx);
            return true;
        }
        if (pressTarget != 0) {
            if (Math.abs(my - pressY) > 4 || Math.abs(mx - pressX) > 4) {
                dragged = true;
            }
            if (dragged) {
                if (pressTarget == 1) {
                    songScroll -= my - lastY;
                } else {
                    rowsScroll -= my - lastY;
                }
            }
            lastY = my;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        pressedKey = "";
        boolean handled = false;
        if (draggedHudKey != null) {
            draggedHudKey = null;
            XFiveLiveClient.CONFIG.save();
            return true;
        }
        if (seeking) {
            seeking = false;
            handled = true;
        }
        if (volumeDrag) {
            volumeDrag = false;
            XFiveLiveClient.CONFIG.save();
            handled = true;
        }
        if (activeSlider != null) {
            activeSlider = null;
            XFiveLiveClient.CONFIG.save();
            handled = true;
        }
        if (pressTarget == 1) {
            if (!dragged && pressedTrack >= 0) {
                XFiveLiveClient.MUSIC.playlist().select(pressedTrack);
                XFiveLiveClient.MUSIC.playCurrent();
            }
            handled = true;
        } else if (pressTarget == 2) {
            if (!dragged && pressedRow != null) {
                activateRow(pressedRow, event.x());
            }
            handled = true;
        }
        pressTarget = 0;
        pressedTrack = -1;
        pressedRow = null;
        dragged = false;
        if (handled) {
            return true;
        }
        return super.mouseReleased(event);
    }

    private void activateRow(Row r, double mx) {
        if (r.kind == Row.SWATCHES) {
            int size = swatchSize(r);
            int ax = rowsX() + r.x;
            int i = (int) Math.floor((mx - (ax + 8)) / (size + 4));
            if (i >= 0 && i < Themes.IDS.length) {
                XFiveLiveClient.CONFIG.theme = Themes.IDS[i];
                XFiveLiveClient.CONFIG.save();
            }
            return;
        }
        if (r.kind == Row.TOGGLE || r.kind == Row.CYCLE || r.kind == Row.ACTION) {
            r.action.run();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMusicPage() && in(mouseX, mouseY, listX(), listTop(), listR() - listX(), listBot() - listTop())) {
            songScroll -= scrollY * STEP;
            return true;
        }
        if (!isMusicPage() && in(mouseX, mouseY, rowsX(), rowsTop(), rowsR() - rowsX(), rowsBot() - rowsTop())) {
            rowsScroll -= scrollY * 26;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
