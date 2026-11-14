package com.xfive.live.config;

import java.util.ArrayList;
import java.util.List;

/** Pengaturan dan perhitungan kartu spesifikasi. Murni Java supaya bisa diuji tanpa Minecraft. */
public final class SpecCard {
    public static final String[] MODE_NAMES = {"Off", "Auto", "Always"};
    public static final int MODE_OFF = 0;
    public static final int MODE_AUTO = 1;
    public static final int MODE_ALWAYS = 2;
    public static final int MAX_LINES = 4;
    public static final int MAX_TEXT = 60;

    private SpecCard() {
    }

    public static int mode(int raw) {
        return Math.max(0, Math.min(MODE_NAMES.length - 1, raw));
    }

    /** Membersihkan teks dari karakter kontrol dan membatasi panjangnya. */
    public static String clean(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.replaceAll("[\\p{Cntrl}]", "").strip();
        if (s.isEmpty()) {
            return fallback;
        }
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    /** Memecah baris spesifikasi yang dipisah tanda "|". Baris kosong dibuang, maksimal MAX_LINES. */
    public static List<String> lines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("\\|")) {
            String s = clean(part, "");
            if (!s.isEmpty()) {
                out.add(s);
                if (out.size() >= MAX_LINES) {
                    break;
                }
            }
        }
        return out;
    }

    /** Memisah judul: {awal, kata terakhir}. Kata terakhir diberi warna aksen. Satu kata: {"", kata}. */
    public static String[] splitTitle(String title) {
        String t = title == null ? "" : title.strip();
        int i = t.lastIndexOf(' ');
        if (i < 0) {
            return new String[] {"", t};
        }
        return new String[] {t.substring(0, i + 1), t.substring(i + 1)};
    }

    /** Jumlah huruf yang sudah diketik pada kecepatan cps huruf per detik. Sebelum mulai = 0. */
    public static int typedCount(int length, long sinceMs, int cps) {
        if (sinceMs <= 0 || length <= 0) {
            return 0;
        }
        return (int) Math.min(length, sinceMs * (long) Math.max(1, cps) / 1000L);
    }
}
