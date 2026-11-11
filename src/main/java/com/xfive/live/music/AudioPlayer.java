package com.xfive.live.music;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Pemutar audio memakai OpenAL, sistem suara yang sama dengan yang dipakai Minecraft.
 * Ini yang membuat musik bisa berbunyi di Android (Zalith), tempat javax.sound tidak tersedia.
 *
 * Harus dipanggil dari thread game, karena konteks OpenAL Minecraft aktif di thread itu.
 * Seluruh lagu dimuat ke memori sebagai satu buffer. Lagu sangat panjang butuh memori besar.
 */
public final class AudioPlayer {
    private int source = 0;
    private int buffer = 0;
    private float volume = 0.8f;
    private long durationMs = 0L;
    private boolean loaded = false;
    private boolean wantPlaying = false;
    private String lastError = null;

    /** Lagu yang sudah didekode dan siap diserahkan ke OpenAL. */
    public record Prepared(ByteBuffer data, int format, int rate, long durationMs) {
    }

    /**
     * Mendekode file menjadi data siap putar. Bagian berat ini TIDAK menyentuh OpenAL,
     * jadi aman dipanggil dari thread latar agar game tidak tersendat.
     */
    public static Prepared prepare(Path file) throws IOException {
        if (file == null) {
            throw new IOException("Tidak ada file");
        }
        String name = file.getFileName().toString();
        if (!AudioDecoder.supports(name)) {
            throw new IOException("Format tidak didukung: " + name);
        }
        AudioDecoder.Pcm pcm = AudioDecoder.decode(file);
        if (pcm.samples().length == 0 || pcm.channels() < 1 || pcm.sampleRate() < 1) {
            throw new IOException("File kosong atau tidak bisa dibaca");
        }
        ByteBuffer data = ByteBuffer.allocateDirect(pcm.samples().length * 2).order(ByteOrder.nativeOrder());
        data.asShortBuffer().put(pcm.samples());
        int format = pcm.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        long duration = pcm.samples().length * 1000L / ((long) pcm.channels() * pcm.sampleRate());
        return new Prepared(data, format, pcm.sampleRate(), duration);
    }

    /** Menyerahkan lagu yang sudah disiapkan ke OpenAL. Hanya dari thread game, dan cepat. */
    public boolean attach(Prepared p) {
        close();
        lastError = null;
        if (ALC10.alcGetCurrentContext() == 0L) {
            lastError = "Sistem suara belum siap";
            return false;
        }
        try {
            ensureSource();
            buffer = AL10.alGenBuffers();
            checkError("alGenBuffers");
            AL10.alBufferData(buffer, p.format(), p.data(), p.rate());
            checkError("alBufferData");
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            checkError("alSourcei");
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
            durationMs = p.durationMs();
            loaded = true;
            return true;
        } catch (RuntimeException e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            close();
            return false;
        }
    }

    /** Memuat langsung di thread ini (lambat untuk lagu panjang). Pemutaran biasa memakai prepare + attach. */
    public boolean load(Path file) {
        try {
            return attach(prepare(file));
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void play() {
        if (!loaded) {
            return;
        }
        AL10.alSourcePlay(source);
        wantPlaying = true;
    }

    public void pause() {
        if (!loaded) {
            return;
        }
        AL10.alSourcePause(source);
        wantPlaying = false;
    }

    public void stop() {
        if (!loaded) {
            return;
        }
        AL10.alSourceStop(source);
        AL10.alSourcef(source, AL11.AL_SEC_OFFSET, 0f);
        wantPlaying = false;
    }

    public void seekMs(long ms) {
        if (!loaded) {
            return;
        }
        long target = Math.max(0L, Math.min(durationMs, ms));
        AL10.alSourcef(source, AL11.AL_SEC_OFFSET, target / 1000f);
    }

    public boolean isPlaying() {
        return loaded && state() == AL10.AL_PLAYING;
    }

    /** True ketika lagu seharusnya berputar tapi sudah berhenti sendiri (lagu habis). */
    public boolean isFinished() {
        return wantPlaying && loaded && state() == AL10.AL_STOPPED;
    }

    public long positionMs() {
        if (!loaded) {
            return 0L;
        }
        return (long) (AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET) * 1000f);
    }

    public long durationMs() {
        return durationMs;
    }

    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        if (source != 0) {
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
        }
    }

    public float volume() {
        return volume;
    }

    /** Keadaan internal OpenAL untuk diagnosis, misalnya "state=4116 (stopped)". */
    public String debugState() {
        if (!loaded) {
            return "belum dimuat";
        }
        int s = state();
        String name = s == AL10.AL_PLAYING ? "playing" : s == AL10.AL_PAUSED ? "paused"
                : s == AL10.AL_STOPPED ? "stopped" : s == AL10.AL_INITIAL ? "initial" : "?";
        return "state=" + name + ", durasi=" + (durationMs / 1000) + " detik";
    }

    public String lastError() {
        return lastError;
    }

    public void close() {
        if (source != 0) {
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
        }
        if (buffer != 0) {
            AL10.alDeleteBuffers(buffer);
            buffer = 0;
        }
        loaded = false;
        wantPlaying = false;
        durationMs = 0L;
    }

    private void ensureSource() {
        if (source == 0) {
            source = AL10.alGenSources();
            checkError("alGenSources");
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
        }
    }

    private int state() {
        return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
    }

    private static void checkError(String where) {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            throw new IllegalStateException("OpenAL error " + err + " di " + where);
        }
    }
}
