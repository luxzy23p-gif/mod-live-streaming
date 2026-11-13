package com.xfive.live.lyrics;

/**
 * Gaya animasi baris lirik dan hitungan waktunya. Murni Java supaya bisa diuji tanpa Minecraft.
 * Dibuat sendiri; hanya perhitungan sederhana (easing, porsi karaoke, teks ketikan).
 */
public final class LyricStyle {
    public static final String[] IDS = {"fade", "rise", "drop", "pop", "typewriter", "none"};
    public static final String[] NAMES = {"Fade", "Rise", "Drop", "Pop", "Typewriter", "Off"};
    /** Lama animasi masuk (ms). */
    public static final int ENTER_MS = 320;
    public static final int OFFSET_MAX = 3000;

    private LyricStyle() {
    }

    public static int indexOf(String id) {
        if (id != null) {
            for (int i = 0; i < IDS.length; i++) {
                if (IDS[i].equals(id.strip())) {
                    return i;
                }
            }
        }
        return 0;
    }

    public static String normalize(String id) {
        return IDS[indexOf(id)];
    }

    public static String next(String id) {
        return IDS[(indexOf(id) + 1) % IDS.length];
    }

    /** Kemajuan animasi masuk 0..1 (easing keluar). Tanpa animasi selalu 1. */
    public static float enter(long sinceStartMs, boolean animated) {
        if (!animated) {
            return 1f;
        }
        float t = Math.max(0f, Math.min(1f, sinceStartMs / (float) ENTER_MS));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Skala "pop": mulai kecil, sedikit melewati 1, lalu menetap di 1. */
    public static float popScale(float t) {
        if (t >= 1f) {
            return 1f;
        }
        float s = 1f + 2.2f * (t - 1f) * (t - 1f) * (t - 1f) + 1.2f * (t - 1f) * (t - 1f);
        return 0.7f + 0.3f * Math.max(0f, Math.min(1.12f, s));
    }

    /** Lama baris dalam ms: sampai baris berikutnya, atau 4 detik jika ini baris terakhir. Maksimal 12 detik. */
    public static long lineDuration(java.util.List<LyricLine> lines, int idx) {
        if (idx < 0 || idx >= lines.size()) {
            return 4000;
        }
        long start = lines.get(idx).timeMs();
        long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
        return Math.max(300, Math.min(12_000, end - start));
    }

    /** Porsi baris yang sudah "dinyanyikan" 0..1 untuk sorot karaoke (selesai di 85% durasi baris). */
    public static float karaoke(long sinceStartMs, long durationMs) {
        if (durationMs <= 0) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, sinceStartMs / (durationMs * 0.85f)));
    }

    /** Jumlah huruf yang sudah diketik, selesai di 60% durasi baris (maksimal 1,4 detik). */
    public static int typedChars(int length, long sinceStartMs, long durationMs) {
        long total = Math.max(250, Math.min(1400, (long) (durationMs * 0.6)));
        float p = Math.max(0f, Math.min(1f, sinceStartMs / (float) total));
        return Math.min(length, Math.round(length * p));
    }

    /** Menggeser posisi lagu dengan offset, tidak pernah di bawah 0. */
    public static long shifted(long positionMs, int offsetMs) {
        return Math.max(0L, positionMs + offsetMs);
    }
}
