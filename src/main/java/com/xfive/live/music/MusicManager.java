package com.xfive.live.music;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Menggabungkan playlist dan pemutar: putar, jeda, berhenti, berikutnya, sebelumnya,
 * mode ulang (mati, semua, satu), acak, dan volume.
 */
public final class MusicManager {
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;
    private static final long RESTART_THRESHOLD_MS = 3_000L;

    private final PlaylistManager playlist = new PlaylistManager();
    private final AudioPlayer player = new AudioPlayer();
    private final Random random = new Random();
    private Path folder;
    private String status = "Belum ada musik";
    private PlaylistManager.Track loadedTrack;
    private boolean error = false;
    private int repeatMode = REPEAT_ALL;
    private boolean shuffle = false;

    /** Dekode lagu berjalan di thread ini supaya game tidak tersendat saat pindah lagu. */
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xfive-audio-loader");
        t.setDaemon(true);
        return t;
    });
    private CompletableFuture<AudioPlayer.Prepared> loading;
    private PlaylistManager.Track loadingTrack;

    public int setFolder(Path dir) {
        player.stop();
        folder = dir;
        loadedTrack = null;
        int n = playlist.scan(dir);
        status = n == 0 ? "Folder kosong atau tidak ditemukan" : n + " lagu";
        return n;
    }

    public Path folder() {
        return folder;
    }

    /** Memakai daftar sumber campuran (folder dan file). Dipanggil saat startup. */
    public int setSources(java.util.List<Path> sources) {
        player.stop();
        loadedTrack = null;
        int n = playlist.scanSources(sources);
        status = n == 0 ? "Belum ada lagu. Isi folder xfive-music." : n + " lagu";
        return n;
    }

    /** Memuat dan memutar lagu aktif. Mengembalikan false jika gagal (lihat status()). */
    public boolean playCurrent() {
        PlaylistManager.Track track = playlist.current();
        if (track == null) {
            status = "Playlist kosong";
            return false;
        }
        if (loadedTrack == null || !loadedTrack.path().equals(track.path())) {
            // Lagu baru didekode di thread latar. Pemutaran dimulai otomatis oleh tick() saat siap.
            requestLoad(track);
            return true;
        }
        player.setVolume(player.volume());
        player.play();
        if (!player.isPlaying()) {
            status = "Gagal mulai: " + player.debugState();
            error = true;
            return false;
        }
        error = false;
        status = "Memutar";
        return true;
    }

    /** Memperbarui daftar lagu TANPA menghentikan lagu yang sedang diputar. */
    public int refresh(java.util.List<Path> sources) {
        PlaylistManager.Track before = playlist.current();
        int n = playlist.scanSources(sources);
        if (before != null) {
            java.util.List<PlaylistManager.Track> list = playlist.tracks();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).path().equals(before.path())) {
                    playlist.select(i);
                    break;
                }
            }
        }
        // Status dari pemutaran (Memutar, Dijeda, Berhenti) tidak ditimpa saat menu dibuka.
        if (loadedTrack == null && loading == null && !error) {
            status = n == 0 ? "Belum ada lagu. Isi folder xfive-music." : n + " lagu";
        }
        return n;
    }

    public void togglePause() {
        if (loading != null) {
            return; // lagu sedang dimuat
        }
        if (player.isPlaying()) {
            player.pause();
            status = "Dijeda";
        } else if (player.isLoaded()) {
            player.play();
            status = "Memutar";
        } else {
            playCurrent();
        }
    }

    /** Menghentikan lagu dan mengembalikan posisi ke awal. Putar lagi akan mulai dari awal. */
    public void stop() {
        player.stop();
        if (playlist.current() != null) {
            status = "Berhenti";
        }
    }

    public void next() {
        int size = playlist.size();
        if (shuffle && size > 1) {
            int current = playlist.currentIndex();
            int pick;
            do {
                pick = random.nextInt(size);
            } while (pick == current);
            playlist.select(pick);
        } else {
            playlist.next();
        }
        playCurrent();
    }

    public void previous() {
        if (player.positionMs() > RESTART_THRESHOLD_MS) {
            player.seekMs(0L);
        } else {
            playlist.previous();
            playCurrent();
        }
    }

    /** Pindah ke posisi tertentu di lagu, dengan fraction 0..1. */
    public void seekTo(float fraction) {
        long dur = player.durationMs();
        if (dur > 0) {
            player.seekMs((long) (Math.max(0f, Math.min(1f, fraction)) * dur));
        }
    }

    public void setVolume(float volume) {
        player.setVolume(volume);
    }

    public float volume() {
        return player.volume();
    }

    /** Dipanggil dari client tick. Mengurus lagu yang sudah habis sesuai mode ulang. */
    public void tick() {
        if (loading != null) {
            finishLoad();
            return;
        }
        if (!player.isFinished()) {
            return;
        }
        if (repeatMode == REPEAT_ONE && loadedTrack != null) {
            player.stop();
            playCurrent();
            return;
        }
        boolean lastTrack = playlist.currentIndex() >= playlist.size() - 1;
        if (repeatMode == REPEAT_OFF && !shuffle && lastTrack) {
            player.stop();
            status = "Selesai";
            return;
        }
        next();
    }

    /** Menghentikan lagu lama lalu mulai mendekode lagu baru di thread latar. */
    private void requestLoad(PlaylistManager.Track track) {
        player.stop();
        loadedTrack = null;
        loadingTrack = track;
        status = "Memuat...";
        error = false;
        Path file = track.path();
        loading = CompletableFuture.supplyAsync(() -> {
            try {
                return AudioPlayer.prepare(file);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                throw new CompletionException(e);
            }
        }, loader);
    }

    /** Dipanggil dari tick. Jika dekode selesai, serahkan ke OpenAL (cepat) lalu putar. */
    private void finishLoad() {
        CompletableFuture<AudioPlayer.Prepared> f = loading;
        if (f == null || !f.isDone()) {
            return;
        }
        loading = null;
        PlaylistManager.Track track = loadingTrack;
        try {
            AudioPlayer.Prepared prepared = f.join();
            if (!player.attach(prepared)) {
                status = "Gagal: " + player.lastError();
                error = true;
                loadedTrack = null;
                return;
            }
            loadedTrack = track;
            player.setVolume(player.volume());
            player.play();
            if (player.isPlaying()) {
                error = false;
                status = "Memutar";
            } else {
                status = "Gagal mulai: " + player.debugState();
                error = true;
            }
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String msg = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            status = "Gagal: " + msg;
            error = true;
            loadedTrack = null;
        }
    }

    /** True selama lagu baru masih didekode di thread latar. */
    public boolean isLoading() {
        return loading != null;
    }

    public int repeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int mode) {
        repeatMode = Math.max(REPEAT_OFF, Math.min(REPEAT_ONE, mode));
    }

    /** Pindah ke mode ulang berikutnya: mati, semua, satu, lalu mati lagi. */
    public int cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        return repeatMode;
    }

    public boolean shuffle() {
        return shuffle;
    }

    public void setShuffle(boolean on) {
        shuffle = on;
    }

    public boolean toggleShuffle() {
        shuffle = !shuffle;
        return shuffle;
    }

    public void setStatus(String text) {
        status = text;
    }

    public PlaylistManager.Track currentTrack() {
        return playlist.current();
    }

    public long positionMs() {
        return player.positionMs();
    }

    public long durationMs() {
        return player.durationMs();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    /** True jika pemutaran terakhir gagal. Dipakai pop-up untuk menampilkan pesan error. */
    public boolean hasError() {
        return error;
    }

    public String status() {
        return status;
    }

    public PlaylistManager playlist() {
        return playlist;
    }

    public void shutdown() {
        loader.shutdownNow();
        player.close();
    }
}
