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

    private static final int SIDEBAR_W = 142;
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
    private boolean hudEditMode = false;
    private String draggedHudKey = null;
    private int dragOffsetX;
    private int dragOffsetY;
    private Button playPauseButton;
    private Button repeatButton;
    private Button shuffleButton;
    private Button islandModeButton;
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

    private int panelX() { return Math.max(8, (width - Math.min(720, width - 16)) / 2); }
    private int panelY() { return Math.max(8, (height - Math.min(430, height - 16)) / 2); }
    private int panelW() { return Math.min(720, width - 16); }
    private int panelH() { return Math.min(430, height - 16); }
    private int contentX() { return panelX() + SIDEBAR_W + 18; }
    private int contentW() { return Math.max(140, panelW() - SIDEBAR_W - 34); }
    private int contentTop() { return panelY() + 46; }

    @Override
    protected void init() {
        int sx = panelX();
        int sy = panelY();
        int pw = panelW();
        int ph = panelH();
        int cx = contentX();
        int cw = contentW();
        int y = contentTop();

        int step = Math.max(20, Math.min(25, (ph - 126) / Page.values().length));
        int navY = sy + 56;
        for (Page target : Page.values()) {
            if (target == Page.HUD || target == Page.CONTROLS || target == Page.APPEARANCE || target == Page.SETTINGS) {
                if (target == Page.HUD) navY += 10;
            }
            addRenderableWidget(button(sx + 10, navY, SIDEBAR_W - 20, step - 4,
                    target.title, () -> open(target)));
            navY += step;
        }

        switch (page) {
            case HOME -> {
                int half = Math.max(62, (cw - 10) / 2);
                addRenderableWidget(button(cx, y, half, 27, "Music Library", () -> open(Page.AUDIO)));
                addRenderableWidget(button(cx + half + 10, y, half, 27, "Favorites", () -> open(Page.FAVORITES)));
                y += 37;
                addRenderableWidget(button(cx, y, half, 27, "Custom HUD", () -> open(Page.HUD)));
                addRenderableWidget(button(cx + half + 10, y, half, 27, "Settings", () -> open(Page.SETTINGS)));
                y += 38;
                addRenderableWidget(button(cx, y, cw, 24, "Appearance & Theme", () -> open(Page.APPEARANCE)));
            }
            case AUDIO, FAVORITES -> initAudio(cx, cw, y);
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
                y += 52; // ruang untuk deretan warna tema
                addRenderableWidget(new OpacitySlider(cx, y, cw, 20)); y += 26;
                addRenderableWidget(button(cx, y, cw, 22,
                        "UI style: " + ("dark_music".equals(c.themeStyle) ? "Dark Music" : "Liquid Glass Lite"), () -> {
                            c.themeStyle = "dark_music".equals(c.themeStyle) ? "glass" : "dark_music";
                            c.save();
                            open(Page.APPEARANCE);
                        }));
                y += 27;
                addToggle(cx, y, cw, "Animations", () -> c.animations = !c.animations);
            }
            case SETTINGS -> {
                XFiveConfig c = XFiveLiveClient.CONFIG;
                addToggle(cx, y, cw, "Enable XFiveLive", () -> c.enableMod = !c.enableMod); y += 29;
                addToggle(cx, y, cw, "Auto resume music", () -> c.autoResume = !c.autoResume); y += 29;
                addToggle(cx, y, cw, "Show album artwork", () -> c.showArtwork = !c.showArtwork); y += 29;
                addRenderableWidget(button(cx, y, cw, 24, "Save settings", c::save));
            }
        }

        addRenderableWidget(button(sx + 10, sy + ph - 34, SIDEBAR_W - 20, 24, "Close", this::onClose));
    }

    private int audioNowWidth() { return Math.min(166, Math.max(72, contentW() / 3)); }
    private int audioListX() { return contentX() + audioNowWidth() + 12; }
    private int audioListW() { return Math.max(70, contentW() - audioNowWidth() - 12); }
    private int audioPlayerY() { return panelY() + panelH() - 76; }
    private int audioListTop() { return songSearch == null ? contentTop() + 36 : songSearch.getY() + 33; }
    private int audioListBottom() { return audioPlayerY() - 10; }

    private void initAudio(int x, int w, int y) {
        int nowW = audioNowWidth();
        int listX = x + nowW + 12;
        int listW = Math.max(120, w - nowW - 12);
        int playerY = audioPlayerY();

        int refreshW = Math.min(62, Math.max(38, listW / 4));
        int searchW = Math.max(40, listW - refreshW - 4);
        songSearch = new EditBox(GlassPanelRenderer.font(), listX, y + 20, searchW, 19,
                Component.literal("Search songs"));
        songSearch.setMaxLength(80);
        songSearch.setHint(Component.literal("Search title or artist"));
        songSearch.setResponder(s -> songScroll = 0);
        songSearch.setFocused(true);
        addRenderableWidget(songSearch);
        addRenderableWidget(button(listX + listW - refreshW, y + 20, refreshW, 19, "Scan", () -> {
            XFiveLiveClient.refreshMusic();
            songScroll = 0;
        }));

        int pathY = playerY - 42;
        int addW = Math.min(29, Math.max(20, nowW / 4));
        int pathW = Math.max(40, nowW - addW - 16);
        sourceInput = new EditBox(GlassPanelRenderer.font(), x + 7, pathY, pathW, 18,
                Component.literal("Music folder/file"));
        sourceInput.setMaxLength(300);
        sourceInput.setHint(Component.literal("Folder or file path"));
        addRenderableWidget(sourceInput);
        addRenderableWidget(button(x + 7 + pathW + 3, pathY, addW, 18, "+", () -> {
            XFiveLiveClient.addSource(sourceInput.getValue());
            sourceInput.setValue("");
            XFiveLiveClient.refreshMusic();
        }));

        int controlsX = x + nowW + 10;
        int controlsW = Math.max(120, w - nowW - 20);
        int gap = 4;
        int prevW = 38, playW = 48, nextW = 38, repeatW = 54, shuffleW = 58;
        int used = prevW + playW + nextW + repeatW + shuffleW + gap * 5;
        int volumeW = Math.max(58, controlsW - used);
        int cy = playerY + 25;

        addRenderableWidget(button(controlsX, cy, prevW, 21, "Prev", XFiveLiveClient.MUSIC::previous));
        playPauseButton = Button.builder(Component.literal(XFiveLiveClient.MUSIC.isPlaying() ? "Pause" : "Play"),
                b -> {
                    XFiveLiveClient.MUSIC.togglePause();
                    b.setMessage(Component.literal(XFiveLiveClient.MUSIC.isPlaying() ? "Pause" : "Play"));
                }).bounds(controlsX + prevW + gap, cy, playW, 21).build();
        addRenderableWidget(playPauseButton);
        addRenderableWidget(button(controlsX + prevW + playW + gap * 2, cy, nextW, 21, "Next",
                XFiveLiveClient.MUSIC::next));
        int rx = controlsX + prevW + playW + nextW + gap * 3;
        repeatButton = Button.builder(Component.literal("Repeat " + repeatLabel()), b -> {
            XFiveLiveClient.cycleRepeat();
            b.setMessage(Component.literal("Repeat " + repeatLabel()));
        }).bounds(rx, cy, repeatW, 21).build();
        addRenderableWidget(repeatButton);
        int sx = rx + repeatW + gap;
        shuffleButton = Button.builder(Component.literal("Shuffle " + onOff(XFiveLiveClient.CONFIG.shuffle)), b -> {
            XFiveLiveClient.toggleShuffle();
            b.setMessage(Component.literal("Shuffle " + onOff(XFiveLiveClient.CONFIG.shuffle)));
        }).bounds(sx, cy, shuffleW, 21).build();
        addRenderableWidget(shuffleButton);
        int vx = sx + shuffleW + gap;
        if (vx + volumeW > x + w) volumeW = Math.max(48, x + w - vx);
        addRenderableWidget(new VolumeSlider(vx, cy, volumeW, 21));
    }

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
        islandModeButton = Button.builder(Component.literal("Dynamic Island: " + (c.islandCard ? "Card" : "Pill")),
                b -> { XFiveLiveClient.toggleIslandCard(); b.setMessage(Component.literal("Dynamic Island: " + (c.islandCard ? "Card" : "Pill"))); })
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
        if ((page != Page.AUDIO && page != Page.FAVORITES) || songSearch == null) return;
        int x = audioListX();
        int w = audioListW();
        int top = audioListTop();
        int bottom = audioListBottom();
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        int nowX = contentX();
        int nowW = audioNowWidth();
        int nowY = contentTop() - 2;
        int playerY = audioPlayerY();

        // Left "Now Playing" card beside the live playlist.
        GlassPanelRenderer.panel(g, nowX, nowY, nowW, playerY - nowY - 8,
                Math.min(0.72f, XFiveLiveClient.CONFIG.customTransparency), accent);
        GlassPanelRenderer.text(g, font, "NOW PLAYING", nowX + 9, nowY + 10, accent);
        g.fill(nowX + 10, nowY + 34, nowX + nowW - 10, nowY + 34 + Math.min(104, nowW - 20),
                GlassPanelRenderer.argb(accent, 0.16f));
        PlaylistManager.Track currentTrack = XFiveLiveClient.MUSIC.currentTrack();
        String currentTitle = currentTrack == null ? "No song selected" : currentTrack.title();
        String currentArtist = currentTrack == null ? XFiveLiveClient.MUSIC.status() : currentTrack.artist();
        int infoY = nowY + 34 + Math.min(104, nowW - 20) + 13;
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(currentTitle, 22), nowX + 9, infoY, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(currentArtist, 24), nowX + 9, infoY + 13, GlassPanelRenderer.MUTED);
        String state = XFiveLiveClient.MUSIC.isPlaying() ? "PLAYING" :
                (XFiveLiveClient.MUSIC.isLoading() ? "LOADING" : XFiveLiveClient.MUSIC.status().toUpperCase(Locale.ROOT));
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(state, 20), nowX + 9, infoY + 31, accent);
        int total = XFiveLiveClient.MUSIC.playlist().size();
        int active = XFiveLiveClient.MUSIC.playlist().currentIndex();
        GlassPanelRenderer.text(g, font, (active >= 0 ? (active + 1) : 0) + " / " + total + " tracks",
                nowX + 9, infoY + 45, GlassPanelRenderer.MUTED);

        // Right-side song list. Kolom: nomor, judul, artis. Header dan baris memakai posisi kolom yang sama.
        g.fill(x, top - 2, x + w, bottom, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.035f));
        int numX = x + 7;
        int titleX = x + 30;
        int artistW = Math.max(56, Math.min(110, w / 4));
        int artistX = x + w - artistW - 8;
        int titleMax = Math.max(30, artistX - titleX - 8);
        int headerY = songSearch.getY() + 24;
        GlassPanelRenderer.text(g, font, "#", numX, headerY, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "TITLE", titleX, headerY, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "ARTIST", artistX, headerY, GlassPanelRenderer.MUTED);
        g.fill(x, top - 4, x + w, top - 3, GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.10f));
        List<PlaylistManager.Track> tracks = XFiveLiveClient.MUSIC.playlist().tracks();
        List<Integer> shown = filteredSongIndexes();
        int visibleRows = Math.max(1, (bottom - top) / ROW_H);
        songScroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows), songScroll));
        int start = (int) songScroll;
        int textDy = (ROW_H - 8) / 2; // teks setinggi 8 px, dipusatkan di baris
        for (int r = 0; r < visibleRows && start + r < shown.size(); r++) {
            int idx = shown.get(start + r);
            PlaylistManager.Track t = tracks.get(idx);
            int ry = top + r * ROW_H;
            boolean current = idx == XFiveLiveClient.MUSIC.playlist().currentIndex();
            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + ROW_H;
            if (current || hover) {
                g.fill(x + 2, ry, x + w - 2, ry + ROW_H - 1,
                        GlassPanelRenderer.argb(accent, current ? 0.24f : 0.10f));
                if (current) {
                    g.fill(x + 2, ry, x + 4, ry + ROW_H - 1, GlassPanelRenderer.argb(accent, 1f));
                }
            }
            int color = current ? GlassPanelRenderer.WHITE : GlassPanelRenderer.MUTED;
            GlassPanelRenderer.text(g, font, current ? ">" : String.valueOf(idx + 1), numX, ry + textDy,
                    current ? accent : GlassPanelRenderer.MUTED);
            GlassPanelRenderer.text(g, font, fit(font, t.title(), titleMax), titleX, ry + textDy,
                    current ? GlassPanelRenderer.WHITE : 0xE6E9F2);
            GlassPanelRenderer.text(g, font, fit(font, t.artist(), artistW), artistX, ry + textDy, color);
            String fav = XFiveLiveClient.CONFIG.favoriteMusic.contains(t.path().toAbsolutePath().toString()) ? "♥" : "♡";
            GlassPanelRenderer.text(g, font, fav, x + w - 12, ry + textDy, accent);
        }
        String count = shown.size() + " / " + tracks.size() + " songs";
        GlassPanelRenderer.text(g, font, count, x + w - font.width(count) - 5, bottom + 2, GlassPanelRenderer.MUTED);

        // Persistent mini-player bar spanning both columns.
        GlassPanelRenderer.panel(g, contentX(), playerY, contentW(), 64,
                Math.min(0.74f, XFiveLiveClient.CONFIG.customTransparency), accent);
        String miniTitle = currentTrack == null ? "XFiveLive Player" : currentTrack.title();
        String miniArtist = currentTrack == null ? "Choose a song from the list" : currentTrack.artist();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(miniTitle, 23), contentX() + 9, playerY + 10, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(miniArtist, 25), contentX() + 9, playerY + 25, GlassPanelRenderer.MUTED);

        // Interactive seek bar: click or drag to move forward/backward in the current track.
        long duration = XFiveLiveClient.MUSIC.durationMs();
        long position = XFiveLiveClient.MUSIC.positionMs();
        float progress = duration > 0L ? Math.max(0f, Math.min(1f, (float) position / duration)) : 0f;
        int seekX = contentX() + 10;
        int seekW = Math.max(1, contentW() - 20);
        int seekY = playerY + 48;
        int trackColor = GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.24f);
        int fillColor = GlassPanelRenderer.argb(accent, 0.96f);
        g.fill(seekX, seekY, seekX + seekW, seekY + 3, trackColor);
        int filled = Math.round(seekW * progress);
        if (filled > 0) g.fill(seekX, seekY, seekX + filled, seekY + 3, fillColor);
        int knobX = seekX + filled;
        g.fill(knobX - 2, seekY - 2, knobX + 3, seekY + 5, GlassPanelRenderer.WHITE);
        String elapsed = formatTime(position);
        String totalTime = formatTime(duration);
        GlassPanelRenderer.text(g, font, elapsed, seekX, playerY + 54, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, totalTime, seekX + seekW - font.width(totalTime), playerY + 54, GlassPanelRenderer.MUTED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        int accent = GlassPanelRenderer.accent(XFiveLiveClient.CONFIG.theme);
        GlassPanelRenderer.panel(g, x, y, w, h, XFiveLiveClient.CONFIG.customTransparency, accent);
        // sidebar lebih gelap, dengan penanda aksen di halaman aktif
        g.fill(x + 3, y + 8, x + SIDEBAR_W - 2, y + h - 8, GlassPanelRenderer.argb(0x000000, 0.30f));
        int step = Math.max(20, Math.min(25, (h - 126) / Page.values().length));
        int activeY = y + 56 + page.ordinal() * step;
        if (page.ordinal() >= Page.HUD.ordinal()) activeY += 10;
        g.fill(x + 6, activeY + 3, x + 9, activeY + step - 8,
                GlassPanelRenderer.argb(accent, 1f));
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.fill(x + SIDEBAR_W, y + 12, x + SIDEBAR_W + 1, y + h - 12,
                GlassPanelRenderer.argb(GlassPanelRenderer.WHITE, 0.10f));
        Font font = GlassPanelRenderer.font();
        GlassPanelRenderer.text(g, font, "XFiveLive", x + 12, y + 14, GlassPanelRenderer.WHITE);
        GlassPanelRenderer.text(g, font, "MUSIC PLAYER", x + 12, y + 27, GlassPanelRenderer.MUTED);
        GlassPanelRenderer.text(g, font, "LIBRARY", x + 12, y + 45, GlassPanelRenderer.MUTED);
        if (page.ordinal() >= Page.HUD.ordinal()) {
            GlassPanelRenderer.text(g, font, "SETTINGS", x + 12, y + 45 + step * 2 + 4, GlassPanelRenderer.MUTED);
        }
        GlassPanelRenderer.text(g, font, page.title, x + SIDEBAR_W + 18, y + 20, accent);
        if (page == Page.HOME) drawHomeDashboard(g, font, mouseX, mouseY);
        drawAudioList(g, font, mouseX, mouseY);
        drawSwatches(g, font);
        if (page == Page.HUD && hudEditMode) {
            int[][] boxes = {
                    com.xfive.live.hud.DynamicIslandHud.box(),
                    com.xfive.live.hud.MusicHud.box(),
                    com.xfive.live.hud.LyricsHud.box()
            };
            GlassPanelRenderer.text(g, font, "DRAG HUD: click a highlighted HUD and move it", 12, height - 18, accent);
            for (int i = 0; i < boxes.length; i++) {
                int[] b = boxes[i];
                int tint = i % 2 == 0 ? accent : GlassPanelRenderer.CYAN;
                g.fill(b[0] - 2, b[1] - 2, b[0] + b[2] + 2, b[1] + b[3] + 2,
                        GlassPanelRenderer.argb(tint, 0.18f));
            }
        }
        PlaylistManager.Track track = XFiveLiveClient.MUSIC.currentTrack();
        String now = track == null ? XFiveLiveClient.MUSIC.status() : track.title() + " - " + track.artist();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(now, 46),
                x + SIDEBAR_W + 18, y + h - 18, GlassPanelRenderer.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
        if ((page == Page.AUDIO || page == Page.FAVORITES) && songSearch != null && inSeekBar(event.x(), event.y())) {
            seekAt(event.x());
            seeking = true;
            return true;
        }
        if ((page == Page.AUDIO || page == Page.FAVORITES) && songSearch != null && inList(event.x(), event.y())) {
            int row = (int) ((event.y() - audioListTop()) / ROW_H);
            List<Integer> shown = filteredSongIndexes();
            int shownIndex = (int) songScroll + row;
            if (event.x() >= audioListX() + audioListW() - 22 && shownIndex >= 0 && shownIndex < shown.size()) {
                PlaylistManager.Track track = XFiveLiveClient.MUSIC.playlist().tracks().get(shown.get(shownIndex));
                String key = track.path().toAbsolutePath().toString();
                if (!XFiveLiveClient.CONFIG.favoriteMusic.add(key)) XFiveLiveClient.CONFIG.favoriteMusic.remove(key);
                XFiveLiveClient.CONFIG.save();
                return true;
            }
            // ketuk memutar saat jari dilepas; geser menggulir daftar (layar sentuh tidak punya roda mouse)
            listPress = true;
            listDragged = false;
            listPressY = event.y();
            listLastY = event.y();
            return true;
        }
        return false;
    }

    private boolean inList(double mx, double my) {
        int x = audioListX();
        return mx >= x && mx <= x + audioListW() && my >= audioListTop() && my < audioListBottom();
    }

    private void playRowAt(double my) {
        int top = audioListTop();
        int visibleRows = Math.max(1, (audioListBottom() - top) / ROW_H);
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
        if (seeking && (page == Page.AUDIO || page == Page.FAVORITES)) {
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
            if (!listDragged) {
                playRowAt(event.y());
            }
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
        int top = audioPlayerY() + 48;
        return mx >= left && mx <= right && my >= top - 5 && my <= top + 8;
    }

    private void seekAt(double mouseX) {
        long duration = XFiveLiveClient.MUSIC.durationMs();
        if (duration <= 0L) return;
        int left = contentX() + 10;
        int trackWidth = Math.max(1, contentW() - 20);
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
        if ((page == Page.AUDIO || page == Page.FAVORITES) && mouseX >= audioListX() && mouseX <= audioListX() + audioListW()
                && mouseY >= audioListTop() && mouseY <= audioListBottom()) {
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
        IslandWidthSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.islandW - 140) / 260.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Bar width: " + XFiveLiveClient.CONFIG.islandW + " px")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandW = 140 + (int) Math.round(value * 260); }
    }

    private static final class IslandHeightSlider extends AbstractSliderButton {
        IslandHeightSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.islandH - 60) / 100.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Bar height: " + XFiveLiveClient.CONFIG.islandH + " px")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandH = 60 + (int) Math.round(value * 100); }
    }

    private static final class IslandTimeSlider extends AbstractSliderButton {
        IslandTimeSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), (XFiveLiveClient.CONFIG.islandShowSec - 2) / 28.0); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal("Pop-up time: " + (2 + (int) Math.round(value * 28)) + " s")); }
        @Override protected void applyValue() { XFiveLiveClient.CONFIG.islandShowSec = 2 + (int) Math.round(value * 28); }
    }
}
