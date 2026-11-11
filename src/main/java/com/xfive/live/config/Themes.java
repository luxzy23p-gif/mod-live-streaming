package com.xfive.live.config;

/** Daftar tema warna UI dan HUD. Murni Java supaya bisa diuji tanpa Minecraft. */
public final class Themes {
    public static final String[] IDS = {"purple", "cyan", "rose", "emerald", "amber", "ocean", "crimson", "mono"};
    public static final String[] NAMES = {"Purple", "Cyan", "Rose", "Emerald", "Amber", "Ocean", "Crimson", "Mono"};
    public static final int[] COLORS = {0x965CFF, 0x40DFFF, 0xFF5C8A, 0x3DDC97, 0xFFB347, 0x4C8DFF, 0xFF4D4D, 0xE6E6E6};

    private Themes() {
    }

    /** Indeks tema, atau 0 (purple) jika id tidak dikenal. */
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

    public static int accent(String id) {
        return COLORS[indexOf(id)];
    }

    public static String next(String id) {
        return IDS[(indexOf(id) + 1) % IDS.length];
    }
}
