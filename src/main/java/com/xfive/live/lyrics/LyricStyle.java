package com.xfive.live.lyrics;

/**
 * Gaya animasi baris lirik dan hitungan waktunya. Murni Java supaya bisa diuji tanpa Minecraft.
 * Dibuat sendiri; hanya perhitungan sederhana (easing, porsi karaoke, teks ketikan).
 */
public final class LyricStyle {
    public static final String[] IDS = {"fade", "rise", "drop", "pop", "typewriter", "words", "wave", "none"};
    public static final String[] NAMES = {"Fade", "Rise", "Drop", "Pop", "Typewriter", "Words", "Wave", "Off"};
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

    /** Jumlah kata yang sudah muncul (satu kata demi satu), selesai di 60% durasi baris (maksimal 1,4 detik). */
    public static int revealedWords(int wordCount, long sinceStartMs, long durationMs) {
        long total = Math.max(250, Math.min(1400, (long) (durationMs * 0.6)));
        float p = Math.max(0f, Math.min(1f, sinceStartMs / (float) total));
        return Math.min(wordCount, (int) Math.ceil(wordCount * p));
    }

    /** Potongan teks yang berisi n kata pertama (spasi di antara kata dipertahankan). */
    public static String firstWords(String text, int n) {
        if (n <= 0) {
            return "";
        }
        int count = 0;
        int i = 0;
        int len = text.length();
        while (i < len) {
            while (i < len && text.charAt(i) == ' ') {
                i++;
            }
            if (i >= len) {
                break;
            }
            while (i < len && text.charAt(i) != ' ') {
                i++;
            }
            count++;
            if (count >= n) {
                return text.substring(0, i);
            }
        }
        return text;
    }

    /** Kemajuan huruf ke-i (0..1) pada animasi gelombang: tiap huruf mulai 25 ms setelah huruf sebelumnya. */
    public static float waveChar(int index, int count, long sinceStartMs) {
        long step = Math.min(25, count <= 1 ? 25 : 600 / Math.max(1, count - 1));
        long local = sinceStartMs - index * step;
        float t = Math.max(0f, Math.min(1f, local / 260f));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Selesai-nya animasi gelombang seluruh baris (ms). */
    public static long waveTotalMs(int count) {
        long step = Math.min(25, count <= 1 ? 25 : 600 / Math.max(1, count - 1));
        return Math.max(0, count - 1) * step + 260;
    }

    /** Jeda minimal (ms) antar baris supaya hitung mundur instrumental muncul. */
    public static final long GAP_MIN_MS = 8000;
    /** Baris tetap tampil 4 detik, sisanya jeda dipakai untuk hitung mundur. */
    public static final long GAP_HOLD_MS = 4000;

    /**
     * Kemajuan hitung mundur 0..1 saat ada jeda instrumental panjang, atau -1 jika tidak sedang jeda.
     * idx = baris aktif (-1 sebelum baris pertama). Intro juga dihitung jika baris pertama mulai >= 6 detik.
     */
    public static float countdown(java.util.List<LyricLine> lines, int idx, long songPos) {
        if (lines.isEmpty()) {
            return -1f;
        }
        long from;
        long to;
        if (idx < 0) {
            to = lines.get(0).timeMs();
            if (to < 6000) {
                return -1f;
            }
            from = 0;
        } else if (idx + 1 < lines.size()) {
            long start = lines.get(idx).timeMs();
            to = lines.get(idx + 1).timeMs();
            if (to - start < GAP_MIN_MS) {
                return -1f;
            }
            from = start + GAP_HOLD_MS;
        } else {
            return -1f;
        }
        if (songPos < from || songPos >= to) {
            return -1f;
        }
        return Math.max(0f, Math.min(1f, (songPos - from) / (float) (to - from)));
    }

    /** Menggeser posisi lagu dengan offset, tidak pernah di bawah 0. */
    public static long shifted(long positionMs, int offsetMs) {
        return Math.max(0L, positionMs + offsetMs);
    }
}
