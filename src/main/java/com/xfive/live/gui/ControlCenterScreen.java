package com.xfive.live.gui;

import com.xfive.live.XFiveLiveClient;
import com.xfive.live.config.Themes;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unified XFiveLive Control Center.
 * Audio playlist is embedded in this screen; HUD sizing controls from the legacy
 * settings screen are restored here. All persistent controls save to XFiveConfig.
 */
public final class ControlCenterScreen extends Screen {
    private enum Page {
        HOME("Home"), AUDIO("Music Library"), FAVORITES("Favorites"), HUD("Custom HUD"),
        CONTROLS("Player Controls"), APPEARANCE("Appearance"), SETTINGS("Settings");

        final String title;
        Page(String title) { this.title = title; }
    }

    private static final int SIDEBAR_W = 176;
    private static final int ROW_H = 22;
    private final Page page;
    private EditBox songSearch;
    private EditBox sourceInput;
    private double songScroll;
    private boolean seeking;
    private boolean listPress = false;
    private boolean listDragged = false;
    private double listPressY;
    private double listLastY;
    private int listPressedTrackIndex = -1;
    private boolean hudEditMode = false;
    private String draggedHudKey = null;
    private int dragOffsetX;
    private int dragOffsetY;
    private Button playPauseButton;
    private Button repeatButton;
    private Button shuffleButton;
    private Button islandModeButton;
    private Button islandStyleButton;
    private Button islandColorButton;
    private Button pausedButton;
    private Button hudEditButton;

    public ControlCenterScreen() {
        this(Page.HOME);
    }

    /** Opens the unified music library/player directly from the single music-center keybind. */
    public static ControlCenterScreen audio() {
        return new ControlCenterScreen(Page.AUDIO);
    }

    private ControlCenterScreen(Page page) {
        super(Component.literal("XFiveLive Control Center"));
        this.page = page;
    }

    private int panelX() { return Math.max(8, (width - panelW()) / 2); }
    private int panelY() { return Math.max(8, (height - panelH()) / 2); }
    private int panelW() { return Math.min(1000, width - 16); }
    private int panelH() { return Math.min(620, height - 16); }
    private int contentX() { return panelX() + SIDEBAR_W + 18; }
    private int contentW() { return Math.max(140, panelW() - SIDEBAR_W - 34); }
    private int contentTop() { return panelY() + 68; }

    @Override
    protected void init() {
        int cx = contentX();
        int cw = contentW();
        int y = contentTop();

        switch (page) {
            case HOME, AUDIO, FAVORITES -> initAudio(cx, cw, y);
            case HUD -> initHud(cx, cw, y);
            case CONTROLS -> initControls(cx, cw, y);
            case APPEARANCE -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                Button themeBtn = Button.builder(Component.literal("Theme: " + themeName()), b -> {
                    c.theme = Themes.next(c.theme);
                    b.setMessage(Component.literal("Theme: " + themeName()));
                    c.save();
                }).bounds(cx, y, cw, 22).build();
                addRenderableWidget(themeBtn);
                y += 52;
                addRenderableWidget(new OpacitySlider(cx, y, cw, 20)); y += 30;
                addRenderableWidget(button(cx, y, cw, 22,
                        "UI style: " + ("dark_music".equals(c.themeStyle) ? "Dark Music" : "Liquid Glass Lite"), () -> {
                            c.themeStyle = "dark_music".equals(c.themeStyle) ? "glass" : "dark_music";
                            c.save();
                            open(Page.APPEARANCE);
                        }));
                y += 30;
                addToggle(cx, y, cw, "Animations", () -> c.animations = !c.animations);
            }
            case SETTINGS -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                addToggle(cx, y, cw, "Enable XFiveLive", () -> c.enableMod = !c.enableMod); y += 30;
                addToggle(cx, y, cw, "Auto resume music", () -> c.autoResume = !c.autoResume); y += 30;
                addToggle(cx, y, cw, "Show album artwork", () -> c.showArtwork = !c.showArtwork); y += 30;
                addRenderableWidget(button(cx, y, cw, 24, "Save settings", c::save));
            }
        }

        // Persistent mini-player controls stay visible on every page.
        initMiniPlayerControls();
    }

    private int audioNowWidth() { return 0; }
    private int audioListX() { return contentX(); }
    private int audioListW() { return contentW(); }
    private int audioPlayerY() { return panelY() + panelH() - 88; }
    private int audioListTop() { return songSearch == null ? contentTop() + 52 : songSearch.getY() + 34; }
    private int audioListBottom() { return audioPlayerY() - 16; }

    private void initAudio(int x, int w, int y) {
        int searchY = y + 26;
        int scanW = Math.min(72, Math.max(52, w / 8));
        int searchW = Math.max(80, w - scanW - 10);
        songSearch = new EditBox(GlassPanelRenderer.font(), x, searchY, searchW, 22,
                Component.literal("Search songs"));
        songSearch.setMaxLength(100);
        songSearch.setHint(Component.literal("Search title or artist"));
        songSearch.setResponder(q -> songScroll = 0);
        songSearch.setFocused(true);
        addRenderableWidget(songSearch);
        addRenderableWidget(button(x + searchW + 8, searchY, scanW, 22, "Refresh", () -> {
            XFiveLiveClient.refreshMusic();
            songScroll = 0;
        }));
    }

    private void initMiniPlayerControls() {
        int x = contentX();
        int y = audioPlayerY() + 27;
        int w = contentW();
        int gap = 5;
        int prevW = 34, playW = 40, nextW = 34, repeatW = 58, shuffleW = 64;
        int fixed = prevW + playW + nextW + repeatW + shuffleW + gap * 5;
        int volumeW = Math.max(70, w - fixed - 10);
        int bx = x + 10;
        addRenderableWidget(button(bx, y, prevW, 22, "◀◀", XFiveLiveClient.MUSIC::previous));
        bx += prevW + gap;
        playPauseButton = Button.builder(Component.literal(XFiveLiveClient.MUSIC.isPlaying() ? "Ⅱ" : "▶"),
                b -> {
                    XFiveLiveClient.MUSIC.togglePause();
                    b.setMessage(Component.literal(XFiveLiveClient.MUSIC.isPlaying() ? "Ⅱ" : "▶"));
                }).bounds(bx, y, playW, 22).build();
        addRenderableWidget(playPauseButton);
        bx += playW + gap;
        addRenderableWidget(button(bx, y, nextW, 22, "▶▶", XFiveLiveClient.MUSIC::next));
        bx += nextW + gap;
        repeatButton = Button.builder(Component.literal("Repeat " + repeatLabel()), b -> {
            XFiveLiveClient.cycleRepeat();
            b.setMessage(Component.literal("Repeat " + repeatLabel()));
        }).bounds(bx, y, repeatW, 22).build();
        addRenderableWidget(repeatButton);
        bx += repeatW + gap;
        shuffleButton = Button.builder(Component.literal("Shuffle " + onOff(XFiveLiveClient.CONFIG.shuffle)), b -> {
            XFiveLiveClient.toggleShuffle();
            b.setMessage(Component.literal("Shuffle " + onOff(XFiveLiveClient.CONFIG.shuffle)));
        }).bounds(bx, y, shuffleW, 22).build();
        addRenderableWidget(shuffleButton);
        bx += shuffleW + gap;
        volumeW = Math.max(48, Math.min(volumeW, x + w - bx - 8));
        addRenderableWidget(new VolumeSlider(bx, y, volumeW, 22));
    }

    // Susunan dua kolom

    // Susunan dua kolom supaya semua pengaturan muat di layar HP.
    private int gx;
    private int gy;
    private int gcw;
    private int gcol;
    private boolean gtwo;

    private void gridBegin(int x, int y, int w) {
        gx = x;
        gy = y;
        gtwo = w >= 280;
        gcw = gtwo ? (w - 6) / 2 : w;
        gcol = 0;
    }

    private int cellX() {
        return gx + (gcol == 1 ? gcw + 6 : 0);
    }

    private void nextCell() {
        if (gtwo && gcol == 0) {
            gcol = 1;
        } else {
            gcol = 0;
            gy += 25;
        }
    }

    private void initHud(int x, int w, int y) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        gridBegin(x, y, w);
        addToggle(cellX(), gy, gcw, "Master HUD", () -> c.showHud = !c.showHud); nextCell();
        addToggle(cellX(), gy, gcw, "Music HUD", () -> c.enableMusic = !c.enableMusic); nextCell();
        addToggle(cellX(), gy, gcw, "Dynamic Island", () -> c.dynamicIsland = !c.dynamicIsland); nextCell();
        islandStyleButton = Button.builder(Component.literal("Dynamic Bar: " + (c.islandStyle == 1 ? "Boring Notch" : "Classic")),
                b -> {
                    c.islandStyle = c.islandStyle == 0 ? 1 : 0;
                    c.islandW = Math.max(0, Math.min(520, c.islandW));
                    c.islandH = Math.max(0, Math.min(160, c.islandH));
                    c.save();
                    b.setMessage(Component.literal("Dynamic Bar: " + (c.islandStyle == 1 ? "Boring Notch" : "Classic")));
                }).bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(islandStyleButton); nextCell();
        addToggle(cellX(), gy, gcw, "Lyrics HUD", () -> c.enableLyrics = !c.enableLyrics); nextCell();
        addRenderableWidget(new HudOpacitySlider(cellX(), gy, gcw, 20)); nextCell();
        addRenderableWidget(new IslandWidthSlider(cellX(), gy, gcw, 20)); nextCell();
        addRenderableWidget(new IslandHeightSlider(cellX(), gy, gcw, 20)); nextCell();
        hudEditButton = Button.builder(Component.literal("Drag HUD: " + onOff(hudEditMode)), b -> {
            hudEditMode = !hudEditMode;
            b.setMessage(Component.literal("Drag HUD: " + onOff(hudEditMode)));
        }).bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(hudEditButton); nextCell();
        addRenderableWidget(button(cellX(), gy, gcw, 20, "Reset HUD positions", () -> {
            c.resetHudPositions();
            c.save();
            draggedHudKey = null;
        }));
    }

    private void initControls(int x, int w, int y) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        gridBegin(x, y, w);
        islandStyleButton = Button.builder(Component.literal("Dynamic Bar style: " + (c.islandStyle == 1 ? "Boring Notch" : "Classic")),
                b -> {
                    c.islandStyle = c.islandStyle == 0 ? 1 : 0;
                    if (c.islandStyle == 1) {
                        c.islandW = Math.max(c.islandW, 480);
                        c.islandH = Math.max(c.islandH, 112);
                    } else {
                        c.islandW = Math.min(c.islandW, 400);
                        c.islandH = Math.min(c.islandH, 160);
                    }
                    c.save();
                    b.setMessage(Component.literal("Dynamic Bar style: " + (c.islandStyle == 1 ? "Boring Notch" : "Classic")));
                }).bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(islandStyleButton); nextCell();
        islandModeButton = Button.builder(Component.literal("Classic shape: " + (c.islandCard ? "Card" : "Pill")),
                b -> { XFiveLiveClient.toggleIslandCard(); b.setMessage(Component.literal("Classic shape: " + (c.islandCard ? "Card" : "Pill"))); })
                .bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(islandModeButton); nextCell();
        islandColorButton = Button.builder(Component.literal("Island color: " + colorName()),
                b -> { XFiveLiveClient.cycleIslandColor(); b.setMessage(Component.literal("Island color: " + colorName())); })
                .bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(islandColorButton); nextCell();
        addRenderableWidget(new IslandWidthSlider(cellX(), gy, gcw, 20)); nextCell();
        addRenderableWidget(new IslandHeightSlider(cellX(), gy, gcw, 20)); nextCell();
        addToggle(cellX(), gy, gcw, "Auto hide pop-up", () -> c.islandAutoHide = !c.islandAutoHide); nextCell();
        addRenderableWidget(new IslandTimeSlider(cellX(), gy, gcw, 20)); nextCell();
        pausedButton = Button.builder(Component.literal("Show while paused: " + onOff(c.islandShowPaused)),
                b -> { XFiveLiveClient.toggleIslandShowPaused(); b.setMessage(Component.literal("Show while paused: " + onOff(c.islandShowPaused))); })
                .bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(pausedButton); nextCell();
        repeatButton = Button.builder(Component.literal("Repeat: " + repeatLabel()),
                b -> { XFiveLiveClient.cycleRepeat(); b.setMessage(Component.literal("Repeat: " + repeatLabel())); })
                .bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(repeatButton); nextCell();
        shuffleButton = Button.builder(Component.literal("Shuffle: " + onOff(c.shuffle)),
                b -> { XFiveLiveClient.toggleShuffle(); b.setMessage(Component.literal("Shuffle: " + onOff(c.shuffle))); })
                .bounds(cellX(), gy, gcw, 20).build();
        addRenderableWidget(shuffleButton); nextCell();
        addRenderableWidget(button(cellX(), gy, gcw, 20, "Reset Island size", () -> {
            XFiveLiveClient.resetIslandSize(); refreshPage();
        }));
    }

    private static String colorName() {
        return com.xfive.live.hud.DynamicIslandHud.COLOR_NAMES[XFiveLiveClient.CONFIG.islandColor];
    }

    private void addToggle(int x, int y, int w, String label, Runnable action) {
        Button b = Button.builder(Component.literal(label + ": " + toggleState(label)), button -> {
            action.run();
            XFiveLiveClient.CONFIG.save();
            button.setMessage(Component.literal(label + ": " + toggleState(label)));
        }).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
    }

    private String toggleState(String label) {
        XFiveConfig c = XFiveLiveClient.CONFIG;
        boolean value = switch (label) {
            case "Master HUD" -> c.showHud;
            case "Music HUD" -> c.enableMusic;
            case "Dynamic Island" -> c.dynamicIsland;
            case "Lyrics HUD" -> c.enableLyrics;
            case "Animations" -> c.animations;
            case "Enable XFiveLive" -> c.enableMod;
            case "Auto resume music" -> c.autoResume;
            case "Show album artwork" -> c.showArtwork;
            default -> false;
        };
        return onOff(value);
    }

    /** Memotong teks dengan "…" agar muat dalam maxPx piksel. */
    private static String fit(Font font, String text, int maxPx) {
        if (font.width(text) <= maxPx) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && font.width(t + "…") > maxPx) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static String themeName() {
        return Themes.NAMES[Themes.indexOf(XFiveLiveClient.CONFIG.theme)];
    }

    /** Kotak warna tema ke-i: {x, y, lebar, tinggi}. */
    private int[] swatch(int i) {
        int gap = 4;
        int size = Math.max(14, Math.min(24, (contentW() - gap * (Themes.IDS.length - 1)) / Themes.IDS.length));
        return new int[] {contentX() + i * (size + gap), contentTop() + 28, size, 18};
    }

    private void drawSwatches(GuiGraphicsExtractor g, Font font) {
        if (page != Page.APPEARANCE) return;
        int selected = Themes.indexOf(XFiveLiveClient.CONFIG.theme);
        for (int i = 0; i < Themes.IDS.length; i++) {
            int[] b = swatch(i);
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], GlassPanelRenderer.argb(Themes.COLORS[i], 1f));
            if (i == selected) {
                g.fill(b[0] - 2, b[1] - 2, b[0] + b[2] + 2, b[1] - 1, GlassPanelRenderer.argb(0xFFFFFF, 1f));
                g.fill(b[0] - 2, b[1] + b[3] + 1, b[0] + b[2] + 2, b[1] + b[3] + 2, GlassPanelRenderer.argb(0xFFFFFF, 1f));
                g.fill(b[0] - 2, b[1] - 1, b[0] - 1, b[1] + b[3] + 1, GlassPanelRenderer.argb(0xFFFFFF, 1f));
                g.fill(b[0] + b[2] + 1, b[1] - 1, b[0] + b[2] + 2, b[1] + b[3] + 1, GlassPanelRenderer.argb(0xFFFFFF, 1f));
            }
        }
    }

    private void drawHomeDashboard(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        int x = contentX();
        int w = contentW();
        int y = contentTop() + 4;
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        PlaylistManager.Track current = XFiveLiveClient.MUSIC.currentTrack();

        GlassPanelRenderer.text(g, font, "Welcome to XFiveLive", x + 2, y, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, "Your music, library and personal controls in one place",
                x + 2, y + 15, GlassPanelRenderer.MUTED);

        int heroY = y + 38;
        int heroH = Math.max(82, Math.min(124, panelH() / 3));
        GlassPanelRenderer.panel(g, x, heroY, w, heroH,
                Math.min(0.92f, XFiveLiveClient.CONFIG.customTransparency), accent);
        g.fill(x + 2, heroY + 2, x + w - 2, heroY + heroH - 2, 0x55202130);
        GlassPanelRenderer.text(g, font, "NOW PLAYING", x + 14, heroY + 13, accent);
        GlassPanelRenderer.text(g, font,
                GlassPanelRenderer.trim(current == null ? "Choose a song to get started" : current.title(), 38),
                x + 14, heroY + 35, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font,
                GlassPanelRenderer.trim(current == null ? "Your music library is ready" : current.artist(), 42),
                x + 14, heroY + 51, GlassPanelRenderer.MUTED);
        String playback = XFiveLiveClient.MUSIC.isPlaying() ? "PLAYING" :
                (XFiveLiveClient.MUSIC.isLoading() ? "LOADING" : "PAUSED");
        GlassPanelRenderer.text(g, font, playback, x + w - font.width(playback) - 16,
                heroY + heroH - 18, accent);

        int cardY = heroY + heroH + 14;
        int gap = 8;
        int cardW = (w - gap * 2) / 3;
        String[] labels = {"MUSIC LIBRARY", "ONLINE SEARCH", "SETTINGS"};
        String[] details = {
                XFiveLiveClient.MUSIC.playlist().size() + " local tracks",
                "Find and play online",
                "HUD, colors and themes"
        };
        for (int i = 0; i < 3; i++) {
            int cx = x + i * (cardW + gap);
            GlassPanelRenderer.panel(g, cx, cardY, cardW, 66, 0.82f, accent);
            GlassPanelRenderer.text(g, font, labels[i], cx + 8, cardY + 14, GlassPanelRenderer.WHITE);
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(details[i], 22),
                    cx + 8, cardY + 34, GlassPanelRenderer.MUTED);
        }

        int quickY = cardY + 80;
        GlassPanelRenderer.text(g, font, "QUICK ACCESS", x + 2, quickY, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "Library  •  Online Music  •  Custom HUD  •  Appearance",
                x + 2, quickY + 17, 0xDDE0E8);
        GlassPanelRenderer.text(g, font, "Use the left navigation to open each section.",
                x + 2, quickY + 34, GlassPanelRenderer.MUTED);
    }

    private void drawAudioList(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if ((page != Page.HOME && page != Page.AUDIO && page != Page.FAVORITES) || songSearch == null) return;
        int x = audioListX();
        int w = audioListW();
        int top = audioListTop();
        int bottom = audioListBottom();
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        PlaylistManager.Track current = XFiveLiveClient.MUSIC.currentTrack();

        GlassPanelRenderer.text(g, font, page == Page.FAVORITES ? "YOUR FAVORITES" : "MUSIC LIBRARY",
                x + 2, contentTop() - 12, GlassPanelRenderer.WHITE);
        String subtitle = page == Page.FAVORITES
                ? "Your saved tracks, ready to play"
                : XFiveLiveClient.MUSIC.playlist().size() + " tracks in your local library";
        GlassPanelRenderer.text(g, font, fit(font, subtitle, w - 4), x + 2, contentTop() + 4, GlassPanelRenderer.MUTED);

        // Soft hero strip matching the supplied music-player reference.
        int heroY = contentTop() + 34;
        int heroH = 56;
        g.fill(x, heroY, x + w, heroY + heroH, 0x66363A40);
        g.fill(x, heroY, x + 3, heroY + heroH, GlassPanelRenderer.argb(accent, 0.95f));
        GlassPanelRenderer.text(g, font, "NOW PLAYING", x + 14, heroY + 10, accent);
        String title = current == null ? "Choose a song from your library" : current.title();
        String artist = current == null ? "XFiveLive Music Player" : current.artist();
        GlassPanelRenderer.text(g, font, fit(font, title, w - 30), x + 14, heroY + 27, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, fit(font, artist, w - 30), x + 14, heroY + 41, GlassPanelRenderer.MUTED);

        int listTop = Math.max(top, heroY + heroH + 12);
        g.fill(x, listTop, x + w, bottom, 0x33202024);
        int numX = x + 12;
        int titleX = x + 42;
        int artistW = Math.max(64, Math.min(150, w / 4));
        int artistX = x + w - artistW - 34;
        int titleMax = Math.max(45, artistX - titleX - 10);
        int headerY = listTop + 10;
        GlassPanelRenderer.text(g, font, "#", numX, headerY, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "TITLE", titleX, headerY, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "ARTIST", artistX, headerY, GlassPanelRenderer.MUTED);
        g.fill(x, listTop + 25, x + w, listTop + 26, 0x33FFFFFF);

        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        List<Integer> shown = filteredSongIndexes();
        int rowsTop = listTop + 29;
        int visibleRows = Math.max(1, (bottom - rowsTop) / ROW_H);
        songScroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows), songScroll));
        int start = (int) songScroll;
        for (int r = 0; r < visibleRows && start + r < shown.size(); r++) {
            int idx = shown.get(start + r);
            PlaylistManager.Track track = tracks.get(idx);
            int ry = rowsTop + r * ROW_H;
            boolean selected = idx == XFiveLiveClient.MUSIC.playlist().currentIndex();
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + ROW_H;
            if (selected || hovered) {
                g.fill(x + 2, ry, x + w - 2, ry + ROW_H - 1,
                        GlassPanelRenderer.argb(accent, selected ? 0.22f : 0.09f));
                if (selected) g.fill(x + 2, ry, x + 4, ry + ROW_H - 1, accent);
            }
            GlassPanelRenderer.text(g, font, String.valueOf(idx + 1), numX, ry + 7, GlassPanelRenderer.MUTED);
            GlassPanelRenderer.text(g, font, fit(font, track.title(), titleMax), titleX, ry + 7,
                    selected ? GlassPanelRenderer.WHITE : 0xE6E9F2);
            GlassPanelRenderer.text(g, font, fit(font, track.artist(), artistW), artistX, ry + 7,
                    GlassPanelRenderer.MUTED);
            String favorite = XFiveLiveClient.CONFIG.favoriteMusic.contains(track.path().toAbsolutePath().toString()) ? "♥" : "♡";
            GlassPanelRenderer.text(g, font, favorite, x + w - 18, ry + 7, accent);
        }
        String count = shown.size() + " songs";
        GlassPanelRenderer.text(g, font, count, x + w - font.width(count) - 4, bottom + 2, GlassPanelRenderer.MUTED);
    }

    private void drawMiniPlayer(GuiGraphicsExtractor g, Font font) {
        int x = contentX();
        int y = audioPlayerY();
        int w = contentW();
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        GlassPanelRenderer.panel(g, x, y, w, 72,
                Math.min(0.90f, XFiveLiveClient.CONFIG.customTransparency), accent);
        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        String title = track == null ? "XFiveLive Music Player" : track.title();
        String artist = track == null ? "Choose a track from Music Library" : track.artist();
        GlassPanelRenderer.text(g, font, fit(font, title, Math.max(90, w / 3)), x + 12, y + 10, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, fit(font, artist, Math.max(90, w / 3)), x + 12, y + 27, GlassPanelRenderer.MUTED);
        long duration = XFiveLiveClient.MUSIC.durationMs();
        long position = XFiveLiveClient.MUSIC.positionMs();
        float progress = duration > 0 ? Math.max(0f, Math.min(1f, (float) position / duration)) : 0f;
        int seekX = x + 12;
        int seekW = Math.max(1, w - 24);
        int seekY = y + 57;
        g.fill(seekX, seekY, seekX + seekW, seekY + 3, 0x55FFFFFF);
        int filled = Math.round(seekW * progress);
        if (filled > 0) g.fill(seekX, seekY, seekX + filled, seekY + 3, GlassPanelRenderer.argb(accent, 1f));
        g.fill(seekX + filled - 2, seekY - 2, seekX + filled + 3, seekY + 5, GlassPanelRenderer.WHITE);
        String elapsed = formatTime(position), total = formatTime(duration);
        GlassPanelRenderer.text(g, font, elapsed, seekX, y + 65, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, total, seekX + seekW - font.width(total), y + 65, GlassPanelRenderer.MUTED);
    }

    private int navY(Page target) {
        return panelY() + switch (target) {
            case HOME -> 86;
            case AUDIO -> 116;
            case FAVORITES -> 146;
            case HUD -> 206;
            case CONTROLS -> 236;
            case APPEARANCE -> 296;
            case SETTINGS -> 326;
        };
    }

    private void drawSidebar(GuiGraphicsExtractor g, Font font, int x, int y, int h, int accent,
                             int mouseX, int mouseY) {
        g.fill(x + 2, y + 2, x + SIDEBAR_W, y + h - 2, 0xEE17191D);
        GlassPanelRenderer.text(g, font, "XFiveLive", x + 18, y + 20, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, "MUSIC PLAYER", x + 18, y + 37, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "LIBRARY", x + 18, y + 70, 0x858B96);
        GlassPanelRenderer.text(g, font, "PERSONALIZE", x + 18, y + 190, 0x858B96);
        GlassPanelRenderer.text(g, font, "PREFERENCES", x + 18, y + 280, 0x858B96);
        for (Page target : Page.values()) {
            int by = navY(target);
            int bx = x + 10, bw = SIDEBAR_W - 20;
            boolean selected = page == target;
            boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 24;
            if (selected || hovered) {
                g.fill(bx, by, bx + bw, by + 24, selected ? 0xFF35383E : 0xFF282B30);
                if (selected) g.fill(bx, by + 3, bx + 3, by + 21, accent);
            }
            String label = switch (target) {
                case HOME -> "⌂   Home";
                case AUDIO -> "♫   Music Library";
                case FAVORITES -> "♥   Favorites";
                case HUD -> "▣   Custom HUD";
                case CONTROLS -> "☷   Player Controls";
                case APPEARANCE -> "✧   Appearance";
                case SETTINGS -> "⚙   Settings";
            };
            GlassPanelRenderer.text(g, font, fit(font, label, bw - 18), bx + 9, by + 8,
                    selected ? GlassPanelRenderer.WHITE : 0xC6CAD2);
        }
        int closeY = y + h - 42;
        GlassPanelRenderer.text(g, font, "×   Close", x + 20, closeY + 8, 0xC6CAD2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        GlassPanelRenderer.panel(g, x, y, w, h, XFiveLiveClient.CONFIG.customTransparency, accent);
        Font font = GlassPanelRenderer.font();

        // Draw the mini-player surface and seek bar BEFORE widgets. The previous
        // order painted this translucent layer over the transport buttons, making
        // Play / Repeat / Shuffle look faded and difficult to tap.
        drawMiniPlayer(g, font);
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        drawSidebar(g, font, x, y, h, accent, mouseX, mouseY);
        g.fill(x + SIDEBAR_W, y + 12, x + SIDEBAR_W + 1, y + h - 12, 0x33FFFFFF);
        GlassPanelRenderer.text(g, font, page.title.toUpperCase(Locale.ROOT),
                contentX(), y + 25, GlassPanelRenderer.WHITE);
        if (page == Page.HOME || page == Page.AUDIO || page == Page.FAVORITES) {
            drawAudioList(g, font, mouseX, mouseY);
        }
        drawSwatches(g, font);
        if (page == Page.HUD && hudEditMode) {
            drawHudPreviews(g, font, accent);
            GlassPanelRenderer.text(g, font, "HUD PREVIEW: drag each visible preview to reposition it",
                    12, height - 18, accent);
        }
    }

    /**
     * Draws live, labelled previews at the exact coordinates used by the in-game HUD.
     * These previews remain visible even when music/lyrics are idle, so users can see
     * and drag every HUD element while configuring it.
     */
    private void drawHudPreviews(GuiGraphicsExtractor g, Font font, int accent) {
        int[] island = com.xfive.live.hud.DynamicIslandHud.box();
        int[] music = com.xfive.live.hud.MusicHud.box();
        int[] lyrics = com.xfive.live.hud.LyricsHud.box();
        XFiveConfig c = XFiveLiveClient.CONFIG;

        // Dynamic Bar / Island preview
        int ix = island[0], iy = island[1], iw = island[2], ih = island[3];
        int islandBg = c.islandStyle == 1 ? 0xF2050609 : 0xE51B2029;
        g.fill(ix, iy, ix + iw, iy + ih, islandBg);
        g.fill(ix, iy, ix + iw, iy + 2, GlassPanelRenderer.argb(accent, 0.95f));
        if (c.islandStyle == 1) {
            GlassPanelRenderer.text(g, font, "BORING NOTCH PREVIEW", ix + 16, iy + 9, 0xFFB8C0CE);
            GlassPanelRenderer.text(g, font, "Now Playing", ix + 18, iy + 31, 0xFFFFFFFF);
            GlassPanelRenderer.text(g, font, "Song title  •  Artist", ix + 18, iy + 48, 0xFFB8C0CE);
            GlassPanelRenderer.text(g, font, "◀◀    ▶    ▶▶", ix + 18, iy + Math.max(68, ih - 23), 0xFFFFFFFF);
            GlassPanelRenderer.text(g, font, "▮▮▮", ix + iw - 42, iy + Math.max(68, ih - 23), accent);
        } else {
            GlassPanelRenderer.text(g, font, "DYNAMIC ISLAND PREVIEW", ix + 10, iy + 8, 0xFFFFFFFF);
            GlassPanelRenderer.text(g, font, "Song title  •  Artist", ix + 10, iy + 24, 0xFFB8C0CE);
            g.fill(ix + 10, iy + ih - 8, ix + iw - 10, iy + ih - 6, 0x66FFFFFF);
            g.fill(ix + 10, iy + ih - 8, ix + 10 + Math.max(1, (iw - 20) / 3), iy + ih - 6, accent);
        }
        g.fill(ix - 2, iy - 2, ix + iw + 2, iy, GlassPanelRenderer.argb(accent, 0.95f));
        g.fill(ix - 2, iy + ih, ix + iw + 2, iy + ih + 2, GlassPanelRenderer.argb(accent, 0.95f));
        g.fill(ix - 2, iy, ix, iy + ih, GlassPanelRenderer.argb(accent, 0.95f));
        g.fill(ix + iw, iy, ix + iw + 2, iy + ih, GlassPanelRenderer.argb(accent, 0.95f));

        // Music HUD preview
        int mx = music[0], my = music[1], mw = music[2], mh = music[3];
        g.fill(mx, my, mx + mw, my + mh, 0xE51B2029);
        g.fill(mx, my, mx + mw, my + 2, GlassPanelRenderer.argb(GlassPanelRenderer.CYAN, 0.95f));
        GlassPanelRenderer.text(g, font, "MUSIC HUD PREVIEW", mx + 6, my + 5, 0xFFFFFFFF);
        GlassPanelRenderer.text(g, font, "Song title  •  Artist", mx + 6, my + 17, 0xFFB8C0CE);
        g.fill(mx + 6, my + mh - 6, mx + mw - 6, my + mh - 5, 0x66FFFFFF);
        g.fill(mx + 6, my + mh - 6, mx + 6 + (mw - 12) / 3, my + mh - 5, GlassPanelRenderer.CYAN);
        g.fill(mx - 2, my - 2, mx + mw + 2, my, GlassPanelRenderer.argb(GlassPanelRenderer.CYAN, 0.95f));
        g.fill(mx - 2, my + mh, mx + mw + 2, my + mh + 2, GlassPanelRenderer.argb(GlassPanelRenderer.CYAN, 0.95f));
        g.fill(mx - 2, my, mx, my + mh, GlassPanelRenderer.argb(GlassPanelRenderer.CYAN, 0.95f));
        g.fill(mx + mw, my, mx + mw + 2, my + mh, GlassPanelRenderer.argb(GlassPanelRenderer.CYAN, 0.95f));

        // Lyrics preview (three lines at the configured preset/free position)
        int lx = lyrics[0], ly = lyrics[1], lw = lyrics[2], lh = lyrics[3];
        g.fill(lx, ly, lx + lw, ly + lh, 0x55303038);
        String[] lyricLines = {"Previous lyric line", "CURRENT LYRIC PREVIEW", "Next lyric line"};
        int lineH = Math.max(9, lh / 3);
        for (int i = 0; i < lyricLines.length; i++) {
            int color = i == 1 ? c.highlightColor : 0xFFB8C0CE;
            String line = fit(font, lyricLines[i], Math.max(40, lw - 12));
            GlassPanelRenderer.text(g, font, line, lx + (lw - font.width(line)) / 2,
                    ly + 3 + i * lineH, color);
        }
        g.fill(lx - 2, ly - 2, lx + lw + 2, ly, GlassPanelRenderer.argb(c.highlightColor, 0.9f));
        g.fill(lx - 2, ly + lh, lx + lw + 2, ly + lh + 2, GlassPanelRenderer.argb(c.highlightColor, 0.9f));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Page target : Page.values()) {
            int by = navY(target), bx = panelX() + 10, bw = SIDEBAR_W - 20;
            if (event.x() >= bx && event.x() <= bx + bw && event.y() >= by && event.y() <= by + 24) {
                open(target);
                return true;
            }
        }
        if (event.x() >= panelX() + 10 && event.x() <= panelX() + SIDEBAR_W - 10
                && event.y() >= panelY() + panelH() - 46) {
            onClose();
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (page == Page.APPEARANCE) {
            for (int i = 0; i < Themes.IDS.length; i++) {
                if (inside(event.x(), event.y(), swatch(i))) {
                    XFiveLiveClient.CONFIG.theme = Themes.IDS[i];
                    XFiveLiveClient.CONFIG.save();
                    rebuildWidgets(); // label tombol tema ikut berubah
                    return true;
                }
            }
        }
        if (page == Page.HUD && hudEditMode) {
            int[] island = com.xfive.live.hud.DynamicIslandHud.box();
            int[] music = com.xfive.live.hud.MusicHud.box();
            int[] lyrics = com.xfive.live.hud.LyricsHud.box();
            if (inside(event.x(), event.y(), island)) {
                beginHudDrag("dynamicIsland", island, event.x(), event.y());
                return true;
            }
            if (inside(event.x(), event.y(), music)) {
                beginHudDrag("musicHud", music, event.x(), event.y());
                return true;
            }
            if (inside(event.x(), event.y(), lyrics)) {
                beginHudDrag("lyricsHud", lyrics, event.x(), event.y());
                return true;
            }
        }
        if (songSearch != null && inSeekBar(event.x(), event.y())) {
            seekAt(event.x());
            seeking = true;
            return true;
        }
        if ((page == Page.HOME || page == Page.AUDIO || page == Page.FAVORITES) && songSearch != null && inList(event.x(), event.y())) {
            int row = (int) ((event.y() - audioRowsTop()) / ROW_H);
            List<Integer> shown = filteredSongIndexes();
            int shownIndex = (int) songScroll + row;
            if (event.x() >= audioListX() + audioListW() - 22 && shownIndex >= 0 && shownIndex < shown.size()) {
                PlaylistManager.Track track = XFiveLiveClient.MUSIC.playlist().tracks().get(shown.get(shownIndex));
                String key = track.path().toAbsolutePath().toString();
                if (!XFiveLiveClient.CONFIG.favoriteMusic.add(key)) XFiveLiveClient.CONFIG.favoriteMusic.remove(key);
                XFiveLiveClient.CONFIG.save();
                return true;
            }
            // Capture the exact row at pointer-down. Some touchscreens report a
            // slightly different release coordinate; never recalculate the song from it.
            listPressedTrackIndex = shownIndex >= 0 && shownIndex < shown.size()
                    ? shown.get(shownIndex) : -1;
            listPress = true;
            listDragged = false;
            listPressY = event.y();
            listLastY = event.y();
            return true;
        }
        return false;
    }

    /**
     * Y-coordinate of the first actual song row. The list has a now-playing banner
     * and a column header above it; clicks and drawing must use the same row origin.
     */
    private int audioRowsTop() {
        int heroY = contentTop() + 34;
        int heroH = 56;
        int listTop = Math.max(audioListTop(), heroY + heroH + 12);
        return listTop + 29;
    }

    private boolean inList(double mx, double my) {
        int x = audioListX();
        return mx >= x && mx <= x + audioListW()
                && my >= audioRowsTop() && my < audioListBottom();
    }

    private void playRowAt(double my) {
        int top = audioRowsTop();
        int visibleRows = Math.max(0, (audioListBottom() - top) / ROW_H);
        int row = (int) ((my - top) / ROW_H);
        List<Integer> shown = filteredSongIndexes();
        int index = (int) songScroll + row;
        if (row >= 0 && row < visibleRows && index >= 0 && index < shown.size()) {
            XFiveLiveClient.MUSIC.playlist().select(shown.get(index));
            XFiveLiveClient.MUSIC.playCurrent();
        }
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

    private static boolean inside(double mx, double my, int[] box) {
        return mx >= box[0] && mx <= box[0] + box[2] && my >= box[1] && my <= box[1] + box[3];
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (listPress) {
            if (Math.abs(event.y() - listPressY) > 4) {
                listDragged = true;
            }
            if (listDragged) {
                songScroll += (listLastY - event.y()) / ROW_H;
                listLastY = event.y();
            }
            return true;
        }
        if (seeking) {
            seekAt(event.x());
            return true;
        }
        if (draggedHudKey != null && hudEditMode) {
            int maxX = Math.max(0, width - 40);
            int maxY = Math.max(0, height - 24);
            int[] pos = XFiveLiveClient.CONFIG.hudPositions.get(draggedHudKey);
            pos[0] = Math.max(0, Math.min(maxX, (int) event.x() - dragOffsetX));
            pos[1] = Math.max(0, Math.min(maxY, (int) event.y() - dragOffsetY));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (listPress) {
            listPress = false;
            if (!listDragged && listPressedTrackIndex >= 0) {
                XFiveLiveClient.MUSIC.playlist().select(listPressedTrackIndex);
                XFiveLiveClient.MUSIC.playCurrent();
            }
            listPressedTrackIndex = -1;
            listDragged = false;
            return true;
        }
        XFiveLiveClient.CONFIG.save(); // simpan sekali saat jari dilepas, bukan tiap geseran slider
        if (seeking) {
            seeking = false;
            return true;
        }
        if (draggedHudKey != null) {
            draggedHudKey = null;
            XFiveLiveClient.CONFIG.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean inSeekBar(double mx, double my) {
        int left = contentX() + 7;
        int right = contentX() + contentW() - 7;
        int top = audioPlayerY() + 57;
        return mx >= left && mx <= right && my >= top - 5 && my <= top + 8;
    }

    private void seekAt(double mouseX) {
        long duration = XFiveLiveClient.MUSIC.durationMs();
        if (duration <= 0L) return;
        int left = contentX() + 12;
        int trackWidth = Math.max(1, contentW() - 24);
        float fraction = (float) ((mouseX - left) / trackWidth);
        XFiveLiveClient.MUSIC.seekTo(Math.max(0f, Math.min(1f, fraction)));
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private List<Integer> filteredSongIndexes() {
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        String query = songSearch == null ? "" : songSearch.getValue().strip().toLowerCase(Locale.ROOT);
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistManager.Track t = tracks.get(i);
            boolean favoriteOnly = page == Page.FAVORITES;
            boolean favorite = XFiveLiveClient.CONFIG.favoriteMusic.contains(t.path().toAbsolutePath().toString());
            if ((!favoriteOnly || favorite) && (query.isEmpty() || t.title().toLowerCase(Locale.ROOT).contains(query)
                    || t.artist().toLowerCase(Locale.ROOT).contains(query))) shown.add(i);
        }
        return shown;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if ((page == Page.HOME || page == Page.AUDIO || page == Page.FAVORITES) && mouseX >= audioListX() && mouseX <= audioListX() + audioListW()
                && mouseY >= audioRowsTop() && mouseY <= audioListBottom()) {
            songScroll -= scrollY;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String repeatLabel() {
        return switch (XFiveLiveClient.CONFIG.repeatMode) {
            case 0 -> "Off";
            case 2 -> "One";
            default -> "All";
        };
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private void open(Page target) { Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen(target)); }
    private void refreshPage() { XFiveLiveClient.CONFIG.save(); }
    private void saveAndReopen() { XFiveLiveClient.CONFIG.save(); refreshPage(); }

    private static Button button(int x, int y, int w, int h, String label, Runnable action) {
        return Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, h).build();
    }

    @Override public void onClose() { XFiveLiveClient.CONFIG.save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }

    private static final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), XFiveLiveClient.CONFIG.defaultVolume); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Volume: " + Math.round(value * 100) + "%")); }
        @Override protected void applyValue() {
            XFiveLiveClient.CONFIG.defaultVolume = (float) value;
            XFiveLiveClient.MUSIC.setVolume((float) value);
           
        }
    }

    private static final class OpacitySlider extends AbstractSliderButton {
        OpacitySlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.customTransparency - 0.1) / 0.9); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Glass opacity: " + Math.round((0.1 + value * 0.9) * 100) + "%")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.customTransparency = (float) (0.1 + value * 0.9); }
    }

    private static final class HudOpacitySlider extends AbstractSliderButton {
        HudOpacitySlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.hudOpacity - 0.1) / 0.9); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("HUD opacity: " + Math.round((0.1 + value * 0.9) * 100) + "%")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.hudOpacity = (float) (0.1 + value * 0.9); }
    }

    private static final class IslandWidthSlider extends AbstractSliderButton {
        IslandWidthSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), XFiveLiveClient.CONFIG.islandW / 520.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Bar width: " + XFiveLiveClient.CONFIG.islandW + " px")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandW = (int) Math.round(value * 520); }
    }

    private static final class IslandHeightSlider extends AbstractSliderButton {
        IslandHeightSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), XFiveLiveClient.CONFIG.islandH / 160.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Bar height: " + XFiveLiveClient.CONFIG.islandH + " px")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandH = (int) Math.round(value * 160); }
    }

    private static final class IslandTimeSlider extends AbstractSliderButton {
        IslandTimeSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.islandShowSec - 2) / 28.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Pop-up time: " + (2 + (int) Math.round(value * 28)) + " s")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandShowSec = 2 + (int) Math.round(value * 28); }
    }
}
