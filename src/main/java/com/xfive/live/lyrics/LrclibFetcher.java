package com.xfive.live.lyrics;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Mengambil lirik sinkron (format LRC) dari lrclib.net, layanan lirik gratis tanpa akun.
 * Dipanggil dari thread latar, tidak pernah dari thread game. Hanya kode murni Java supaya bisa diuji.
 */
public final class LrclibFetcher {
    private static final String BASE = "https://lrclib.net/api/";
    private static final String AGENT = "XFiveLive/1.1 (https://github.com/luxzy23p-gif/mod-live-streaming)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LrclibFetcher() {
    }

    /** Teks LRC bersinkron untuk lagu ini, atau null jika tidak ketemu / tidak ada internet. */
    public static String fetch(String artist, String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        try {
            String body = get(BASE + "get?track_name=" + enc(title)
                    + (artist == null || artist.isBlank() ? "" : "&artist_name=" + enc(artist)));
            String synced = body == null ? null : firstSynced(body);
            if (synced != null) {
                return synced;
            }
            String q = (artist == null || artist.isBlank() ? "" : artist + " ") + title;
            body = get(BASE + "search?q=" + enc(q));
            return body == null ? null : firstSynced(body);
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", AGENT)
                .GET().build();
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return res.statusCode() == 200 ? res.body() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s.strip(), StandardCharsets.UTF_8);
    }

    /** Nilai "syncedLyrics" non-kosong pertama di JSON (objek tunggal atau array hasil pencarian). */
    public static String firstSynced(String json) {
        String key = "\"syncedLyrics\"";
        int from = 0;
        while (true) {
            int k = json.indexOf(key, from);
            if (k < 0) {
                return null;
            }
            int i = k + key.length();
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ':')) {
                i++;
            }
            if (i < json.length() && json.charAt(i) == '"') {
                String value = readString(json, i + 1);
                if (value != null && value.contains("[")) {
                    return value;
                }
            }
            from = k + key.length();
        }
    }

    /** Membaca string JSON mulai tepat setelah tanda kutip pembuka, termasuk escape. */
    private static String readString(String s, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (++i >= s.length()) {
                return null;
            }
            char e = s.charAt(i);
            switch (e) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 >= s.length()) {
                        return null;
                    }
                    try {
                        out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException bad) {
                        return null;
                    }
                    i += 4;
                }
                default -> out.append(e); // \" \\ \/
            }
        }
        return null;
    }

    /** Nama file aman untuk lirik hasil unduhan: "Artis - Judul". */
    public static String fileStem(String artist, String title) {
        String raw = (artist == null || artist.isBlank() ? "" : artist.strip() + " - ") + title.strip();
        String safe = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }
}
