package com.xfive.live.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Properties;

/** Pengaturan X FIVE LIVE, disimpan di config/xfive-live.properties. Nilai di luar batas dikunci ke batas. */
public final class XFiveConfig {
    public static final String FILE_NAME = "xfive-live.properties";

    // General
    public boolean enableMod = true;
    public boolean showHud = true;
    public float hudScale = 1.0f;
    public float hudOpacity = 0.85f;
    public boolean dynamicIsland = true;
    public int islandW = 220;           // lebar kartu, 140..400
    public int islandH = 84;            // tinggi kartu, 60..160
    public int islandColor = 0;         // 0 ikut tema, 1..5 warna tetap
    public boolean islandShowPaused = false;
    public boolean islandAutoHide = false;  // kartu menutup sendiri setelah beberapa detik
    public int islandShowSec = 5;          // lama kartu terbuka, 2..30 detik
    public boolean islandCard = true;   // true = kartu lengkap, false = pil kecil

    // Music
    public boolean enableMusic = true;
    public float defaultVolume = 0.8f;
    public boolean autoResume = false;
    public boolean showArtwork = true;
    public String musicFolder = "";
    public int repeatMode = 1;          // 0 mati, 1 semua, 2 satu
    public boolean shuffle = false;
    public final Set<String> favoriteMusic = new LinkedHashSet<>();

    // Lyrics
    public boolean enableLyrics = true;
    public int lyricsFontSize = 10;
    public int highlightColor = 0x40DFFF;
    public int lyricsPosition = 2; // 0 = atas, 1 = tengah, 2 = bawah
    public boolean lyricsMinimal = false;

    // Appearance
    public String theme = "purple"; // lihat Themes.IDS
    /** UI skin: glass (Liquid Glass Lite) atau dark_music (matte music-player style). */
    public String themeStyle = "glass";
    public float customTransparency = 0.85f;
    public boolean animations = true;

    /**
     * Posisi HUD per komponen: musicHud, dynamicIsland, lyricsHud. Nilai = {x, y}.
     * Nilai -1 berarti posisi default (dynamicIsland di tengah atas, lyricsHud mengikuti preset).
     */
    public final Map<String, int[]> hudPositions = new HashMap<>();

    private final Path file;

    public XFiveConfig(Path configDir) {
        this.file = configDir.resolve(FILE_NAME);
        resetHudPositions();
    }

    public Path file() {
        return file;
    }

    public void resetHudPositions() {
        hudPositions.clear();
        hudPositions.put("musicHud", new int[] {8, 8});
        hudPositions.put("dynamicIsland", new int[] {-1, -1});
        hudPositions.put("lyricsHud", new int[] {-1, -1});
    }

    public void load() {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                return; // pakai default jika file rusak
            }
        }
        enableMod = bool(p, "general.enableMod", enableMod);
        showHud = bool(p, "general.showHud", showHud);
        hudScale = flt(p, "general.hudScale", hudScale, 0.5f, 2.0f);
        hudOpacity = flt(p, "general.hudOpacity", hudOpacity, 0.1f, 1.0f);
        dynamicIsland = bool(p, "general.dynamicIsland", dynamicIsland);
        islandW = integer(p, "island.width", islandW, 140, 400);
        islandH = integer(p, "island.height", islandH, 60, 160);
        islandColor = integer(p, "island.color", islandColor, 0, 5);
        islandShowPaused = bool(p, "island.showPaused", islandShowPaused);
        islandAutoHide = bool(p, "island.autoHide", islandAutoHide);
        islandShowSec = integer(p, "island.showSec", islandShowSec, 2, 30);
        islandCard = bool(p, "island.card", islandCard);

        enableMusic = bool(p, "music.enable", enableMusic);
        defaultVolume = flt(p, "music.defaultVolume", defaultVolume, 0f, 1f);
        autoResume = bool(p, "music.autoResume", autoResume);
        showArtwork = bool(p, "music.showArtwork", showArtwork);
        musicFolder = p.getProperty("music.folder", musicFolder);
        repeatMode = integer(p, "music.repeat", repeatMode, 0, 2);
        shuffle = bool(p, "music.shuffle", shuffle);
        favoriteMusic.clear();
        String savedFavorites = p.getProperty("music.favorites", "");
        for (String item : savedFavorites.split("\\n")) if (!item.isBlank()) favoriteMusic.add(item.strip());

        enableLyrics = bool(p, "lyrics.enable", enableLyrics);
        lyricsFontSize = integer(p, "lyrics.fontSize", lyricsFontSize, 6, 20);
        highlightColor = integer(p, "lyrics.highlightColor", highlightColor, 0, 0xFFFFFF);
        lyricsPosition = integer(p, "lyrics.position", lyricsPosition, 0, 2);
        lyricsMinimal = bool(p, "lyrics.minimal", lyricsMinimal);

        theme = Themes.normalize(p.getProperty("appearance.theme", theme));
        themeStyle = "dark_music".equals(p.getProperty("appearance.style", themeStyle).strip()) ? "dark_music" : "glass";
        customTransparency = flt(p, "appearance.transparency", customTransparency, 0.1f, 1f);
        animations = bool(p, "appearance.animations", animations);

        for (String key : hudPositions.keySet()) {
            int[] pos = hudPositions.get(key);
            pos[0] = integer(p, "hud." + key + ".x", pos[0], -10_000, 10_000);
            pos[1] = integer(p, "hud." + key + ".y", pos[1], -10_000, 10_000);
        }
    }

    public void save() {
        Properties p = new Properties();
        put(p, "general.enableMod", enableMod);
        put(p, "general.showHud", showHud);
        put(p, "general.hudScale", hudScale);
        put(p, "general.hudOpacity", hudOpacity);
        put(p, "general.dynamicIsland", dynamicIsland);
        put(p, "island.width", islandW);
        put(p, "island.height", islandH);
        put(p, "island.color", islandColor);
        put(p, "island.showPaused", islandShowPaused);
        put(p, "island.autoHide", islandAutoHide);
        put(p, "island.showSec", islandShowSec);
        put(p, "island.card", islandCard);

        put(p, "music.enable", enableMusic);
        put(p, "music.defaultVolume", defaultVolume);
        put(p, "music.autoResume", autoResume);
        put(p, "music.showArtwork", showArtwork);
        put(p, "music.folder", musicFolder);
        put(p, "music.repeat", repeatMode);
        put(p, "music.shuffle", shuffle);
        put(p, "music.favorites", String.join("\\n", favoriteMusic));

        put(p, "lyrics.enable", enableLyrics);
        put(p, "lyrics.fontSize", lyricsFontSize);
        put(p, "lyrics.highlightColor", highlightColor);
        put(p, "lyrics.position", lyricsPosition);
        put(p, "lyrics.minimal", lyricsMinimal);

        put(p, "appearance.theme", theme);
        put(p, "appearance.style", themeStyle);
        put(p, "appearance.transparency", customTransparency);
        put(p, "appearance.animations", animations);

        for (Map.Entry<String, int[]> e : hudPositions.entrySet()) {
            put(p, "hud." + e.getKey() + ".x", e.getValue()[0]);
            put(p, "hud." + e.getKey() + ".y", e.getValue()[1]);
        }

        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "X FIVE LIVE configuration");
            }
        } catch (IOException ignored) {
            // gagal menyimpan tidak boleh membuat game crash
        }
    }

    private static void put(Properties p, String key, Object value) {
        p.setProperty(key, String.valueOf(value));
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.strip());
    }

    private static float flt(Properties p, String key, float def, float min, float max) {
        try {
            return clamp(Float.parseFloat(p.getProperty(key, Float.toString(def)).strip()), min, max);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int integer(Properties p, String key, int def, int min, int max) {
        try {
            return (int) clamp(Integer.parseInt(p.getProperty(key, Integer.toString(def)).strip()), min, max);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static long clamp(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }
}
