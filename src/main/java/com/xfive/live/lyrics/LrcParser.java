package com.xfive.live.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser LRC. Mendukung [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx],
 * beberapa timestamp dalam satu baris, dan tag [offset:ms].
 */
public final class LrcParser {
    private static final Pattern TIME =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET =
            Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]$", Pattern.CASE_INSENSITIVE);

    private LrcParser() {
    }

    public static List<LyricLine> parse(String text) {
        List<LyricLine> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        long offsetMs = 0L;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher off = OFFSET.matcher(line);
            if (off.matches()) {
                try {
                    offsetMs = Long.parseLong(off.group(1));
                } catch (NumberFormatException ignored) {
                    // offset tidak valid, abaikan
                }
                continue;
            }
            Matcher m = TIME.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                times.add(toMillis(m));
                end = m.end();
            }
            if (times.isEmpty()) {
                continue; // tag metadata seperti [ar:...] atau baris kosong
            }
            String lyric = line.substring(end).strip();
            for (long t : times) {
                out.add(new LyricLine(Math.max(0L, t + offsetMs), lyric));
            }
        }
        out.sort(Comparator.comparingLong(LyricLine::timeMs));
        return out;
    }

    private static long toMillis(Matcher m) {
        long minutes = Long.parseLong(m.group(1));
        long seconds = Long.parseLong(m.group(2));
        long millis = 0L;
        String frac = m.group(3);
        if (frac != null) {
            switch (frac.length()) {
                case 1 -> millis = Long.parseLong(frac) * 100L;
                case 2 -> millis = Long.parseLong(frac) * 10L;
                default -> millis = Long.parseLong(frac);
            }
        }
        return minutes * 60_000L + seconds * 1_000L + millis;
    }
}
