package com.xfive.live.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Daftar lagu dari satu folder. Folder dipindai hanya saat scan() dipanggil
 * (bukan per frame). Nama file "Artis - Judul.mp3" dibaca sebagai artis dan judul.
 */
public final class PlaylistManager {
    public static final List<String> SUPPORTED_EXTENSIONS = List.of(".mp3", ".m4a", ".wav", ".ogg");

    public record Track(Path path, String title, String artist) {
    }

    private final List<Track> tracks = new ArrayList<>();
    private int index = -1;

    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int scan(Path dir) {
        tracks.clear();
        index = -1;
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(PlaylistManager::isSupported)
                    .sorted()
                    .forEach(p -> tracks.add(parse(p)));
        } catch (IOException e) {
            tracks.clear();
        }
        if (!tracks.isEmpty()) {
            index = 0;
        }
        return tracks.size();
    }

    /** Membaca artis dan judul dari nama file, misalnya "Adele - Hello.mp3". */
    public static Track parse(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String clean = cleanName(base);
        int sep = clean.indexOf(" - ");
        if (sep > 0) {
            return new Track(file, clean.substring(sep + 3).strip(), clean.substring(0, sep).strip());
        }
        return new Track(file, clean, "Unknown Artist");
    }

    /**
     * Nama hasil unduhan sering memakai "_" sebagai pengganti spasi, dan "–" sebagai pengganti "-".
     * Keduanya dirapikan, lalu tag di ujung nama seperti "(1280)" atau "(Official Video)" dibuang.
     */
    static String cleanName(String base) {
        String s = base.replace('_', ' ').replace('–', '-');
        s = s.replaceAll("\\s+", " ").strip();
        s = stripTrailingTags(s);
        return s.isEmpty() ? base.strip() : s;
    }

    private static String stripTrailingTags(String text) {
        String s = text;
        while (s.endsWith(")") || s.endsWith("]")) {
            int open = Math.max(s.lastIndexOf('('), s.lastIndexOf('['));
            if (open <= 0) {
                break;
            }
            String inside = s.substring(open + 1, s.length() - 1).strip().toLowerCase(Locale.ROOT);
            if (!isTagWord(inside)) {
                break;
            }
            s = s.substring(0, open).strip();
        }
        return s;
    }

    private static boolean isTagWord(String inside) {
        for (String word : TAG_WORDS) {
            if (inside.contains(word)) {
                return true;
            }
        }
        return inside.matches("\\d{3,4}p?");
    }

    /** Kata yang menandakan tag rilis di ujung nama file, bukan bagian dari judul. */
    private static final List<String> TAG_WORDS = List.of(
            "official", "lyric", "video", "audio", "visual", "remaster", "4k", "hq", "hd");

    /**
     * Memindai beberapa sumber sekaligus. Setiap sumber bisa berupa folder (semua lagu di dalamnya)
     * atau satu file lagu. Sumber yang tidak ada atau tidak bisa dibaca dilewati tanpa error.
     */
    public synchronized int scanSources(List<Path> sources) {
        tracks.clear();
        index = -1;
        java.util.Set<Path> seen = new java.util.LinkedHashSet<>();
        for (Path src : sources) {
            if (src == null) {
                continue;
            }
            try {
                if (Files.isDirectory(src)) {
                    try (Stream<Path> stream = Files.list(src)) {
                        stream.filter(Files::isRegularFile)
                                .filter(PlaylistManager::isSupported)
                                .sorted()
                                .forEach(seen::add);
                    }
                } else if (Files.isRegularFile(src) && isSupported(src)) {
                    seen.add(src.toAbsolutePath());
                }
            } catch (IOException | RuntimeException e) {
                // sumber tidak bisa dibaca, lewati
            }
        }
        for (Path p : seen) {
            tracks.add(parse(p));
        }
        if (!tracks.isEmpty()) {
            index = 0;
        }
        return tracks.size();
    }

    public synchronized int size() {
        return tracks.size();
    }

    public synchronized List<Track> tracks() {
        return List.copyOf(tracks);
    }

    public synchronized int currentIndex() {
        return index;
    }

    public synchronized Track current() {
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    public synchronized Track next() {
        if (tracks.isEmpty()) {
            return null;
        }
        index = (index + 1) % tracks.size();
        return tracks.get(index);
    }

    public synchronized Track previous() {
        if (tracks.isEmpty()) {
            return null;
        }
        index = (index - 1 + tracks.size()) % tracks.size();
        return tracks.get(index);
    }

    /** Adds a temporary or external online track to the current queue. */
    public synchronized int addExternal(Path file, String title, String artist) {
        if (file == null || !Files.isRegularFile(file) || !isSupported(file)) return -1;
        tracks.add(new Track(file, title == null ? file.getFileName().toString() : title,
                artist == null ? "Unknown Artist" : artist));
        return tracks.size() - 1;
    }

    public synchronized boolean select(int newIndex) {
        if (newIndex < 0 || newIndex >= tracks.size()) {
            return false;
        }
        index = newIndex;
        return true;
    }
}
