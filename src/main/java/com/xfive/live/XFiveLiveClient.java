package com.xfive.live;

import com.mojang.blaze3d.platform.InputConstants;
import com.xfive.live.config.XFiveConfig;
import com.xfive.live.gui.ControlCenterScreen;
import com.xfive.live.hud.DynamicIslandHud;
import com.xfive.live.hud.LyricsHud;
import com.xfive.live.hud.MusicHud;
import com.xfive.live.lyrics.LyricsManager;
import com.xfive.live.music.MusicManager;
import com.xfive.live.music.PlaylistManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Entrypoint client X FIVE LIVE.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class XFiveLiveClient implements ClientModInitializer {
    public static final String MOD_ID = "xfive-live";
    private static final Identifier UI_SOUND_ID = Identifier.fromNamespaceAndPath(MOD_ID, "ui.notification");
    private static final SoundEvent UI_SOUND = SoundEvent.createVariableRangeEvent(UI_SOUND_ID);
    public static final XFiveConfig CONFIG = new XFiveConfig(FabricLoader.getInstance().getConfigDir());
    public static final MusicManager MUSIC = new MusicManager();
    public static final LyricsManager LYRICS = new LyricsManager();

    static final String SOURCE_FILE = "folder-musik.txt";
    private static final String SOURCE_TEMPLATE = String.join("\n",
            "# Tambahkan folder atau file lagu pribadi, satu per baris.",
            "# Contoh folder: /storage/emulated/0/Music",
            "# Contoh file:   /storage/emulated/0/Download/lagu.mp3",
            "# Baris yang diawali tanda # diabaikan.",
            "");

    /** Memutar efek UI untuk membuka/menutup Music Center. */
    public static void playUiSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(UI_SOUND, 1.0F));
        }
    }

    /** Folder musik yang sedang dipakai, ditampilkan di menu pengaturan. */
    public static Path musicFolder = null;

    private static KeyMapping hudKey;
    private static KeyMapping lyricsKey;
    private static KeyMapping settingsKey;
    private static KeyMapping musicCenterKey;
    private static PlaylistManager.Track lastTrack;
    private static boolean autoResumeDone = false;

    @Override
    public void onInitializeClient() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, UI_SOUND_ID, UI_SOUND);
        CONFIG.load();
        reloadMusic();
        ensureLyricsFolder();
        MUSIC.setVolume(CONFIG.defaultVolume);
        MUSIC.setRepeatMode(CONFIG.repeatMode);
        MUSIC.setShuffle(CONFIG.shuffle);

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        hudKey = register("key.xfive.hud", GLFW.GLFW_KEY_F8, category);
        lyricsKey = register("key.xfive.lyrics", GLFW.GLFW_KEY_F9, category);
        settingsKey = register("key.xfive.settings", GLFW.GLFW_KEY_RIGHT_SHIFT, category);
        // Satu pintasan terpadu: buka daftar lagu, pemutar, dan pengaturan audio sekaligus.
        musicCenterKey = register("key.xfive.music_center", GLFW.GLFW_KEY_F6, category);

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "overlay"),
                (graphics, delta) -> {
                    if (!CONFIG.enableMod || !CONFIG.showHud) {
                        return;
                    }
                    if (CONFIG.dynamicIsland) {
                        DynamicIslandHud.render(graphics);
                    } else {
                        MusicHud.render(graphics);
                    }
                    LyricsHud.render(graphics);
                });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> onTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> MUSIC.shutdown());
    }

    /**
     * Memuat daftar lagu dari folder default (xfive-music) dan folder-musik.txt saat startup.
     * Menu pengaturan memakai refreshMusic() agar daftar diperbarui tanpa menghentikan musik.
     */
    public static int reloadMusic() {
        // Folder default dibuat otomatis agar bisa langsung diisi lagu.
        Path folder = CONFIG.musicFolder.isBlank()
                ? FabricLoader.getInstance().getGameDir().resolve("xfive-music")
                : Path.of(CONFIG.musicFolder.strip());
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
            // folder tidak bisa dibuat, status akan menampilkan folder kosong
        }
        musicFolder = folder;
        return MUSIC.setSources(musicSources(folder));
    }

    /**
     * Sumber musik: folder default ditambah setiap baris di folder-musik.txt.
     * File daftar dibuat otomatis berisi petunjuk jika belum ada.
     */
    static List<Path> musicSources(Path defaultDir) {
        List<Path> out = new ArrayList<>();
        out.add(defaultDir);
        Path list = defaultDir.resolve(SOURCE_FILE);
        try {
            if (!Files.exists(list)) {
                Files.writeString(list, SOURCE_TEMPLATE, StandardCharsets.UTF_8);
                return out;
            }
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }
                try {
                    out.add(Path.of(s));
                } catch (RuntimeException badPath) {
                    // path tidak valid, lewati baris ini
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // daftar tidak bisa dibaca, pakai folder default saja
        }
        return out;
    }

    /**
     * Memperbarui daftar lagu tanpa perlu restart dan tanpa menghentikan musik.
     * Dipanggil otomatis saat menu dibuka.
     */
    public static int refreshMusic() {
        Path folder = CONFIG.musicFolder.isBlank()
                ? FabricLoader.getInstance().getGameDir().resolve("xfive-music")
                : Path.of(CONFIG.musicFolder.strip());
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
            // folder tidak bisa dibuat, daftar tetap dibaca dari folder lain
        }
        musicFolder = folder;
        return MUSIC.refresh(musicSources(folder));
    }

    /** Mode ulang berikutnya (mati, semua, satu), disimpan ke config. */
    public static void cycleRepeat() {
        CONFIG.repeatMode = MUSIC.cycleRepeat();
        CONFIG.save();
    }

    public static void toggleShuffle() {
        CONFIG.shuffle = MUSIC.toggleShuffle();
        CONFIG.save();
    }

    /** Mengembalikan ukuran Dynamic Island ke bawaan. */
    public static void resetIslandSize() {
        CONFIG.islandW = 220;
        CONFIG.islandH = 84;
        CONFIG.save();
    }

    public static void cycleIslandColor() {
        CONFIG.islandColor = (CONFIG.islandColor + 1) % DynamicIslandHud.COLOR_NAMES.length;
        CONFIG.save();
    }

    public static void toggleIslandShowPaused() {
        CONFIG.islandShowPaused = !CONFIG.islandShowPaused;
        CONFIG.save();
    }

    /** Beralih antara tampilan kartu lengkap dan pil kecil. */
    public static void toggleIslandCard() {
        CONFIG.islandCard = !CONFIG.islandCard;
        CONFIG.save();
    }

    /**
     * Menambah folder atau file lagu ke folder-musik.txt dari dalam game.
     * Mengembalikan pesan hasil untuk ditampilkan di status.
     */
    public static String addSource(String raw) {
        String path = raw == null ? "" : raw.replace("\"", "").strip();
        if (path.isEmpty()) {
            return "Isi path lagu dulu.";
        }
        Path p;
        try {
            p = Path.of(path);
        } catch (RuntimeException badPath) {
            return "Path tidak valid.";
        }
        if (!Files.exists(p)) {
            return "Path tidak ditemukan.";
        }
        if (Files.isRegularFile(p) && !PlaylistManager.isSupported(p)) {
            return "File harus MP3, OGG, atau WAV.";
        }
        Path folder = musicFolder == null
                ? FabricLoader.getInstance().getGameDir().resolve("xfive-music") : musicFolder;
        Path list = folder.resolve(SOURCE_FILE);
        try {
            Files.createDirectories(folder);
            if (!Files.exists(list)) {
                Files.writeString(list, SOURCE_TEMPLATE, StandardCharsets.UTF_8);
            }
            for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
                if (line.strip().equals(path)) {
                    return "Path ini sudah ada di daftar.";
                }
            }
            String existing = Files.readString(list, StandardCharsets.UTF_8);
            String lead = existing.isEmpty() || existing.endsWith("\n") ? "" : "\n";
            Files.writeString(list, lead + path + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            return "Gagal menyimpan daftar: " + e.getMessage();
        }
        int n = refreshMusic();
        return "Ditambahkan. Sekarang ada " + n + " lagu.";
    }

    private static KeyMapping register(String name, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(name, InputConstants.Type.KEYSYM, key, category));
    }

    private static void onTick() {
        while (settingsKey.consumeClick()) {
            Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen());
        }
        while (hudKey.consumeClick()) {
            CONFIG.showHud = !CONFIG.showHud;
            CONFIG.save();
        }
        while (lyricsKey.consumeClick()) {
            CONFIG.enableLyrics = !CONFIG.enableLyrics;
            CONFIG.save();
        }

        while (musicCenterKey.consumeClick()) {
            Minecraft.getInstance().setScreenAndShow(ControlCenterScreen.audio());
        }

        DynamicIslandHud.onTick();

        if (CONFIG.enableMusic) {
            if (CONFIG.autoResume && !autoResumeDone) {
                autoResumeDone = true;
                if (MUSIC.playlist().size() > 0) {
                    MUSIC.playCurrent();
                }
            }
            MUSIC.tick();
        }
        syncLyrics();
    }

    /** Membaca file .lrc hanya saat lagu berganti, bukan tiap tick. Jika tidak ada, coba unduh dari internet. */
    private static void syncLyrics() {
        PlaylistManager.Track track = MUSIC.currentTrack();
        if (track != lastTrack) {
            lastTrack = track;
            lyricsGeneration++;
            if (track == null) {
                LYRICS.clear();
            } else {
                Path lrc = findLrc(track);
                if (lrc != null) {
                    LYRICS.loadFile(lrc);
                } else {
                    LYRICS.clear();
                    if (CONFIG.autoLyrics) {
                        fetchLyricsAsync(track, lyricsGeneration);
                    }
                }
            }
        }
        // hasil unduhan dipasang di thread game, hanya jika lagunya masih sama
        PendingLyrics ready = pendingLyrics;
        if (ready != null) {
            pendingLyrics = null;
            if (ready.generation == lyricsGeneration) {
                LYRICS.load(ready.text);
                MUSIC.setStatus("Lirik ditemukan.");
            }
        }
    }

    private record PendingLyrics(int generation, String text) {
    }

    private static volatile PendingLyrics pendingLyrics;
    private static volatile int lyricsGeneration;
    private static final java.util.concurrent.ExecutorService LYRICS_POOL =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "xfive-lyrics");
                t.setDaemon(true);
                return t;
            });

    private static void fetchLyricsAsync(PlaylistManager.Track track, int generation) {
        LYRICS_POOL.execute(() -> {
            String text = com.xfive.live.lyrics.LrclibFetcher.fetch(track.artist(), track.title());
            if (text == null || generation != lyricsGeneration) {
                return;
            }
            try {
                Path dir = FabricLoader.getInstance().getGameDir().resolve(LYRICS_FOLDER);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(
                        com.xfive.live.lyrics.LrclibFetcher.fileStem(track.artist(), track.title()) + ".lrc"),
                        text, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException ignored) {
                // gagal menyimpan cache, lirik tetap ditampilkan untuk lagu ini
            }
            pendingLyrics = new PendingLyrics(generation, text);
        });
    }

    /** Folder lirik buatan sendiri: taruh file .lrc di sini dengan nama sama seperti lagu. */
    static final String LYRICS_FOLDER = "xfive-lirik";
    private static final String LYRICS_README = String.join("\n",
            "Taruh file lirik .lrc di folder ini.",
            "Nama file harus sama dengan nama lagu, contoh:",
            "  lagu  : Adele - Hello.mp3",
            "  lirik : Adele - Hello.lrc",
            "Lirik yang diletakkan di samping file lagu juga dibaca.",
            "");

    /** Membuat folder lirik dan petunjuknya jika belum ada. */
    static void ensureLyricsFolder() {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve(LYRICS_FOLDER);
            Files.createDirectories(dir);
            Path readme = dir.resolve("BACA-DULU.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, LYRICS_README, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException ignored) {
            // folder lirik tidak bisa dibuat, lirik dari samping lagu tetap bisa dipakai
        }
    }

    /** Mencari .lrc: di samping file lagu dulu, lalu di folder xfive-lirik. Null jika tidak ada. */
    static Path findLrc(PlaylistManager.Track track) {
        Path beside = lrcFor(track.path());
        if (Files.isRegularFile(beside)) {
            return beside;
        }
        Path dir = FabricLoader.getInstance().getGameDir().resolve(LYRICS_FOLDER);
        String stem = stemOf(track.path());
        String byArtist = track.artist() + " - " + track.title();
        for (String name : new String[] {stem, byArtist}) {
            try {
                Path candidate = dir.resolve(name + ".lrc");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException invalidName) {
                // nama tidak valid sebagai path, coba berikutnya
            }
        }
        return null;
    }

    private static String stemOf(Path audio) {
        String n = audio.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    static Path lrcFor(Path audio) {
        String s = audio.toString();
        int dot = s.lastIndexOf('.');
        return Path.of((dot > 0 ? s.substring(0, dot) : s) + ".lrc");
    }
}
