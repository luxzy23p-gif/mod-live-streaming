package com.xfive.live.config;

/** Daftar font UI. "default" memakai font bawaan Minecraft. Murni Java supaya bisa diuji. */
public final class Fonts {
    public static final String[] IDS = {"default", "outfit", "mono", "pixel", "tech", "serif", "jura"};
    public static final String[] NAMES = {"Default", "Outfit", "JetBrains Mono", "Pixelify", "Tektur", "Lora", "Jura"};

    private Fonts() {
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
}
