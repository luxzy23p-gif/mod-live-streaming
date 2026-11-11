package com.xfive.live.lyrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Menyimpan lirik aktif dan mencari baris yang sedang dinyanyikan. */
public final class LyricsManager {
    private List<LyricLine> lines = List.of();

    public void load(String lrcText) {
        lines = LrcParser.parse(lrcText);
    }

    /** Membaca file .lrc sekali. Mengembalikan false jika file tidak ada atau tidak terbaca. */
    public boolean loadFile(Path file) {
        if (file == null) {
            clear();
            return false;
        }
        try {
            load(Files.readString(file, StandardCharsets.UTF_8));
            return !lines.isEmpty();
        } catch (IOException | RuntimeException e) {
            clear();
            return false;
        }
    }

    public void clear() {
        lines = List.of();
    }

    public List<LyricLine> lines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Indeks baris aktif pada posisi tertentu (ms). -1 jika belum ada baris yang dimulai. */
    public int indexAt(long positionMs) {
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /** Teks baris pada indeks, atau string kosong jika di luar jangkauan. */
    public String textAt(int index) {
        return index >= 0 && index < lines.size() ? lines.get(index).text() : "";
    }
}
