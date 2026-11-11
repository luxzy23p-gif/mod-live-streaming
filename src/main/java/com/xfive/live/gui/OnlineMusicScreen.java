package com.xfive.live.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xfive.live.XFiveLiveClient;
import com.xfive.live.music.PlaylistManager;
import com.xfive.live.render.GlassPanelRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Online catalog search, temporary online playback, and creator-authorized downloads.
 * Audius current API uses is_streamable / is_downloadable fields.
 */
public final class OnlineMusicScreen extends Screen {
    private static final String API = "https://api.audius.co/v1";
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "xfive-online-music");
        t.setDaemon(true);
        return t;
    });
    private static final long MAX_BYTES = 100L * 1024L * 1024L;

    private record Track(String id, String title, String artist, long duration,
                         boolean streamable, boolean downloadable) {}
    private EditBox queryBox;
    private volatile List<Track> results = List.of();
    private volatile String status = "Search Audius tracks by title or artist.";
    private volatile boolean searching;
    private volatile boolean busy;

    public OnlineMusicScreen() { super(Component.literal("XFiveLive Online Music")); }

    private int px() { return Math.max(8, (width - Math.min(900, width - 16)) / 2); }
    private int py() { return Math.max(8, (height - Math.min(560, height - 16)) / 2); }
    private int pw() { return Math.min(900, width - 16); }
    private int ph() { return Math.min(560, height - 16); }
    private int sidebarW() { return Math.max(112, Math.min(166, pw() / 5)); }
    private int contentX() { return px() + sidebarW() + 22; }
    private int contentRight() { return px() + pw() - 22; }
    private int contentW() { return contentRight() - contentX(); }
    private int searchY() { return py() + 70; }
    private int searchButtonX() { return contentRight() - 82; }
    private int listTop() { return py() + 142; }
    private int rowHeight() { return 43; }
    private int visibleRows() { return Math.max(1, Math.min(8, (py() + ph() - 86 - listTop()) / rowHeight())); }

    @Override protected void init() {
        int inputX = contentX() + 12;
        int inputW = Math.max(50, searchButtonX() - inputX - 10);
        queryBox = new EditBox(GlassPanelRenderer.font(), inputX + 25, searchY() + 4,
                inputW - 30, 20, Component.literal("Search music"));
        queryBox.setMaxLength(100);
        queryBox.setHint(Component.literal("Search song title or artist"));
        queryBox.setBordered(false);
        queryBox.setTextColor(0xF2F3F7);
        queryBox.setTextColorUneditable(0x8D929E);
        addRenderableWidget(queryBox);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        int sx = px(), sy = py();
        int navX = sx + 12, navW = sidebarW() - 24;
        if (inside(mx, my, navX, sy + 112, navW, 30)) {
            Minecraft.getInstance().setScreenAndShow(new ControlCenterScreen());
            return true;
        }
        if (inside(mx, my, navX, sy + 148, navW, 30)) return true;
        if (inside(mx, my, navX, sy + 184, navW, 30)) {
            Minecraft.getInstance().setScreenAndShow(ControlCenterScreen.audio());
            return true;
        }
        if (inside(mx, my, searchButtonX(), searchY(), 82, 30)) {
            search();
            return true;
        }
        for (int i = 0; i < visibleRows(); i++) {
            int ry = listTop() + i * rowHeight();
            if (inside(mx, my, contentX(), ry, contentW(), rowHeight() - 3)) {
                Track track = getTrack(i);
                if (track == null) return true;
                if (mx >= contentRight() - 148 && mx < contentRight() - 78) playOnline(i);
                else if (mx >= contentRight() - 76) download(i);
                return true;
            }
        }
        return false;
    }

    private void search() {
        String query = queryBox == null ? "" : queryBox.getValue().strip();
        if (query.isEmpty()) { status = "Enter a song title or artist first."; return; }
        if (searching) return;
        searching = true;
        results = List.of();
        status = "Searching online catalog...";
        NETWORK.submit(() -> {
            try {
                String url = API + "/tracks/search?query="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&limit=20&app_name=xfive_live";
                String body = requestText(url);
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                List<Track> found = new ArrayList<>();
                JsonElement data = root.get("data");
                if (data != null && data.isJsonArray()) {
                    for (JsonElement element : data.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject item = element.getAsJsonObject();
                        String id = value(item, "id");
                        String title = value(item, "title");
                        JsonObject user = item.has("user") && item.get("user").isJsonObject()
                                ? item.getAsJsonObject("user") : new JsonObject();
                        String artist = value(user, "name");
                        long duration = item.has("duration") && !item.get("duration").isJsonNull()
                                ? item.get("duration").getAsLong() : 0L;
                        boolean streamable = bool(item, "is_streamable");
                        boolean downloadable = bool(item, "is_downloadable");
                        if (!id.isBlank() && !title.isBlank() && (streamable || downloadable)) {
                            found.add(new Track(id, title,
                                    artist.isBlank() ? "Unknown artist" : artist,
                                    duration, streamable, downloadable));
                        }
                    }
                }
                results = List.copyOf(found);
                status = found.isEmpty()
                        ? "No playable tracks found. Try another title or artist."
                        : found.size() + " results. Play streams online or save allowed tracks.";
            } catch (Exception e) {
                status = "Search failed: " + message(e);
            } finally {
                searching = false;
            }
        });
    }

    private void playOnline(int index) {
        Track track = getTrack(index);
        if (track == null || !track.streamable() || busy) return;
        busy = true;
        status = "Preparing online playback: " + track.title();
        NETWORK.submit(() -> {
            Path temp = null;
            try {
                Path cache = FabricLoader.getInstance().getGameDir().resolve("xfive-online-cache");
                Files.createDirectories(cache);
                temp = Files.createTempFile(cache, "online-", ".mp3");
                transfer(API + "/tracks/" + track.id() + "/stream?app_name=xfive_live", temp);
                Path playable = temp;
                Minecraft.getInstance().execute(() -> {
                    boolean started = XFiveLiveClient.MUSIC.playOnlineFile(
                            playable, track.title(), track.artist());
                    status = started
                            ? "Online track prepared: " + track.title()
                            : "Could not start playback: " + XFiveLiveClient.MUSIC.status();
                    busy = false;
                });
                temp = null; // Keep cache file while the decoder/player may still need it.
            } catch (Exception e) {
                status = "Online playback failed: " + message(e);
                busy = false;
            } finally {
                if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        });
    }

    private void download(int index) {
        Track track = getTrack(index);
        if (track == null || !track.downloadable() || busy) return;
        busy = true;
        status = "Saving: " + track.title();
        NETWORK.submit(() -> {
            Path temp = null;
            try {
                Path folder = FabricLoader.getInstance().getGameDir().resolve("xfive-music");
                Files.createDirectories(folder);
                String safe = (track.artist() + " - " + track.title())
                        .replaceAll("[\\\\/:*?\\\"<>|]", "_")
                        .replaceAll("\\s+", " ").strip();
                if (safe.length() > 120) safe = safe.substring(0, 120).strip();
                Path target = folder.resolve(safe + ".mp3");
                int suffix = 2;
                while (Files.exists(target)) target = folder.resolve(safe + " (" + suffix++ + ").mp3");
                temp = Files.createTempFile(folder, "xfive-download-", ".part");
                transfer(API + "/tracks/" + track.id() + "/download?app_name=xfive_live", temp);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                temp = null;
                Minecraft.getInstance().execute(() -> {
                    XFiveLiveClient.refreshMusic();
                    status = "Saved to xfive-music: " + track.title();
                    busy = false;
                });
            } catch (Exception e) {
                status = "Download failed: " + message(e);
                busy = false;
            } finally {
                if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        });
    }

    private Track getTrack(int index) {
        List<Track> snapshot = results;
        return index >= 0 && index < snapshot.size() ? snapshot.get(index) : null;
    }

    private static String requestText(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void transfer(String url, Path destination) throws IOException {
        HttpURLConnection connection = open(url);
        try {
            String type = connection.getContentType();
            if (type != null && (type.contains("application/json") || type.contains("text/html"))) {
                throw new IOException("The music server returned a web response, not audio.");
            }
            long total = 0;
            try (InputStream in = connection.getInputStream();
                 var out = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("Audio exceeds 100 MB.");
                    out.write(buffer, 0, n);
                }
            }
            if (total < 128) throw new IOException("Audio response was empty or incomplete.");
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(90000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "XFiveLive/1.2");
        connection.setRequestProperty("Accept", "audio/mpeg,application/json;q=0.8,*/*;q=0.5");
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("Music service HTTP " + code);
        }
        return connection;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static String message(Exception e) {
        String s = e.getMessage();
        if (s == null || s.isBlank()) return e.getClass().getSimpleName();
        return s.length() > 110 ? s.substring(0, 110) : s;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py(), w = pw(), h = ph();
        int accent = 0xA86BFF;
        int muted = 0xA2A5B0;
        Font font = GlassPanelRenderer.font();
        List<Track> snapshot = results;

        // Premium charcoal shell and permanently dark left navigation rail.
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x55000000);
        g.fill(x, y, x + w, y + h, 0xF018191E);
        g.fill(x, y, x + sidebarW(), y + h, 0xF0101116);
        g.fill(x + sidebarW(), y + 14, x + sidebarW() + 1, y + h - 14, 0x22FFFFFF);
        g.fill(x + 12, y + 12, x + 42, y + 42, 0xFF292B34);
        GlassPanelRenderer.text(g, font, "X", x + 22, y + 21, accent);
        GlassPanelRenderer.text(g, font, "XFIVE", x + 50, y + 15, 0xF4F4F7);
        GlassPanelRenderer.text(g, font, "MUSIC", x + 50, y + 29, muted);

        GlassPanelRenderer.text(g, font, "MENU", x + 15, y + 78, 0x777D8A);
        String[] nav = {"Home", "Online Music", "Local Library"};
        int[] navY = {y + 112, y + 148, y + 184};
        for (int i = 0; i < nav.length; i++) {
            boolean selected = i == 1;
            if (selected) {
                g.fill(x + 7, navY[i] + 2, x + sidebarW() - 8, navY[i] + 29, 0x332B1640);
                g.fill(x + 7, navY[i] + 3, x + 10, navY[i] + 27, accent);
            }
            String symbol = i == 0 ? "⌂" : i == 1 ? "♫" : "▤";
            GlassPanelRenderer.text(g, font, symbol, x + 19, navY[i] + 10, selected ? accent : muted);
            GlassPanelRenderer.text(g, font, nav[i], x + 39, navY[i] + 10,
                    selected ? 0xF4F4F7 : muted);
        }

        g.fill(x + 14, y + h - 74, x + sidebarW() - 14, y + h - 73, 0x22FFFFFF);
        GlassPanelRenderer.text(g, font, "NOW PLAYING", x + 15, y + h - 60, 0x777D8A);
        PlaylistManager.Track current = XFiveLiveClient.MUSIC.currentTrack();
        String now = current == null ? "Nothing playing" : current.title();
        GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(now, 18), x + 15, y + h - 43, 0xDDE0E8);
        GlassPanelRenderer.text(g, font, "Saved in xfive-music", x + 15, y + h - 24, muted);

        // Main content header.
        GlassPanelRenderer.text(g, font, "ONLINE MUSIC", contentX(), y + 20, 0xF4F4F7);
        GlassPanelRenderer.text(g, font, "Discover and play music from independent artists",
                contentX(), y + 36, muted);

        // Recessed rounded search field, built from layered rectangles for vanilla GUI compatibility.
        int searchX = contentX() + 7, searchY = searchY(), searchW = searchButtonX() - searchX - 10;
        g.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + 31, 0xFF08090D);
        g.fill(searchX + 1, searchY + 1, searchX + searchW - 1, searchY + 29, 0xFF25262D);
        GlassPanelRenderer.text(g, font, "⌕", searchX + 10, searchY + 9, 0xAEB3C0);
        g.fill(searchButtonX(), searchY, searchButtonX() + 82, searchY + 30, 0xFF343640);
        g.fill(searchButtonX(), searchY, searchButtonX() + 82, searchY + 1, 0x557E8290);
        GlassPanelRenderer.text(g, font, "Search", searchButtonX() + 17, searchY + 10, 0xF0F1F5);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        GlassPanelRenderer.text(g, font, status, contentX() + 7, y + 112,
                busy || searching ? accent : muted);

        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int ry = listTop() + i * rowHeight();
            Track t = i < snapshot.size() ? snapshot.get(i) : null;
            boolean hover = mouseX >= contentX() && mouseX <= contentRight()
                    && mouseY >= ry && mouseY <= ry + rowHeight() - 3;
            g.fill(contentX() + 2, ry, contentRight() - 2, ry + rowHeight() - 3,
                    hover ? 0xFF292B34 : 0xFF202127);
            if (t == null) continue;
            String title = GlassPanelRenderer.trim(t.title(), 38);
            String artist = GlassPanelRenderer.trim(t.artist(), 34);
            GlassPanelRenderer.text(g, font, title, contentX() + 14, ry + 7, 0xF0F1F5);
            String detail = artist + (t.duration() > 0 ? "  •  " + t.duration() / 60 + ":"
                    + String.format(Locale.ROOT, "%02d", t.duration() % 60) : "");
            GlassPanelRenderer.text(g, font, GlassPanelRenderer.trim(detail, 42),
                    contentX() + 14, ry + 22, muted);

            int playX = contentRight() - 148;
            int saveX = contentRight() - 76;
            g.fill(playX, ry + 9, playX + 64, ry + 31, t.streamable() && !busy ? 0xFF48404F : 0xFF303139);
            g.fill(saveX, ry + 9, saveX + 66, ry + 31, t.downloadable() && !busy ? 0xFF48404F : 0xFF303139);
            GlassPanelRenderer.text(g, font, "Play", playX + 17, ry + 16,
                    t.streamable() && !busy ? 0xF4F4F7 : 0x777D8A);
            GlassPanelRenderer.text(g, font, "Save", saveX + 17, ry + 16,
                    t.downloadable() && !busy ? 0xF4F4F7 : 0x777D8A);
        }
        GlassPanelRenderer.text(g, font, "Play streams online • Save keeps creator-enabled downloads offline",
                contentX() + 7, y + h - 30, muted);
    }

    @Override public boolean isPauseScreen() { return false; }
}
