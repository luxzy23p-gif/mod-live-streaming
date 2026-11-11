package com.xfive.live.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mengubah file audio menjadi PCM 16-bit. Format dikenali dari ISI file (bukan dari nama),
 * jadi file yang namanya .mp3 tapi isinya OGG tetap bisa dibaca.
 * WAV dibaca manual, OGG lewat stb_vorbis (bawaan LWJGL Minecraft), MP3 lewat JLayer,
 * M4A (AAC) lewat JAAD. Semuanya Java murni atau bawaan Minecraft, jadi jalan di Android.
 * BELUM DIVERIFIKASI terhadap jar Minecraft 26.2 (lihat README).
 */
public final class AudioDecoder {

    public record Pcm(short[] samples, int channels, int sampleRate) {
    }

    private AudioDecoder() {
    }

    public static boolean supports(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".m4a");
    }

    public static Pcm decode(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        if (data.length == 0) {
            throw new IOException("File kosong (0 byte)");
        }
        String format = detect(data);
        if (format.equals("mp3")) {
            return decodeMp3(data);
        }
        if (format.equals("ogg")) {
            return decodeOgg(data);
        }
        if (format.equals("wav")) {
            return decodeWav(data);
        }
        if (format.equals("m4a")) {
            if (data.length >= 8 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') {
                return decodeM4a(file);
            }
            throw new IOException("File AAC mentah (.aac) belum didukung. Pakai M4A, MP3, OGG, atau WAV.");
        }
        if (format.equals("webm")) {
            throw new IOException("File ini format WebM/Opus, bukan MP3. Ubah ke MP3, OGG, atau WAV.");
        }
        throw new IOException("Format tidak dikenali (" + data.length + " byte). Gunakan MP3, M4A, OGG, atau WAV.");
    }

    /** Mendeteksi format dari beberapa byte pertama file. */
    static String detect(byte[] b) {
        if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
            return "mp3"; // MP3 dengan tag ID3 di depan
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF) {
            // MPEG audio: sync 11 bit, lalu layer 2 bit. Layer III (MP3) = 01.
            if ((b[1] & 0xE0) == 0xE0 && ((b[1] >> 1) & 0x03) == 0x01) {
                return "mp3";
            }
            // ADTS (AAC): sync 0xFFF dengan layer 00
            if ((b[1] & 0xF6) == 0xF0) {
                return "m4a";
            }
        }
        if (b.length >= 12 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') {
            return "ogg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') {
            return "wav";
        }
        if (b.length >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            return "m4a"; // kontainer MP4/M4A
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
            return "webm"; // kontainer Matroska/WebM
        }
        return "unknown";
    }

    private static Pcm decodeWav(byte[] b) throws IOException {
        int pos = 12;
        int channels = 0;
        int sampleRate = 0;
        int bits = 0;
        int format = 0;
        while (pos + 8 <= b.length) {
            String id = ascii(b, pos);
            int size = le32(b, pos + 4);
            int body = pos + 8;
            if ("fmt ".equals(id)) {
                format = le16(b, body);
                channels = le16(b, body + 2);
                sampleRate = le32(b, body + 4);
                bits = le16(b, body + 14);
            } else if ("data".equals(id)) {
                if (channels == 0) {
                    throw new IOException("WAV tidak punya header fmt");
                }
                if (format != 1 || bits != 16) {
                    throw new IOException("WAV harus PCM 16-bit (sekarang " + bits + "-bit)");
                }
                int len = Math.min(size < 0 ? b.length - body : size, b.length - body);
                short[] s = new short[len / 2];
                for (int i = 0; i < s.length; i++) {
                    s[i] = (short) le16(b, body + i * 2);
                }
                return new Pcm(s, channels, sampleRate);
            }
            pos = body + size + (size & 1);
        }
        throw new IOException("WAV tidak punya chunk data");
    }

    private static Pcm decodeOgg(byte[] bytes) throws IOException {
        ByteBuffer in = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        in.put(bytes).flip();
        IntBuffer channels = BufferUtils.createIntBuffer(1);
        IntBuffer rate = BufferUtils.createIntBuffer(1);
        ShortBuffer out = STBVorbis.stb_vorbis_decode_memory(in, channels, rate);
        if (out == null) {
            throw new IOException("OGG tidak bisa didekode (mungkin bukan Vorbis)");
        }
        try {
            short[] s = new short[out.remaining()];
            out.get(s);
            return new Pcm(s, channels.get(0), rate.get(0));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    private static Pcm decodeMp3(byte[] data) throws IOException {
        List<short[]> chunks = new ArrayList<>();
        int channels = 0;
        int rate = 0;
        int total = 0;
        try (InputStream in = new ByteArrayInputStream(data)) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();
            while (true) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                SampleBuffer frame = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (channels == 0) {
                    channels = frame.getChannelCount();
                    rate = frame.getSampleFrequency();
                }
                short[] buf = frame.getBuffer();
                int len = frame.getBufferLength();
                short[] copy = new short[len];
                System.arraycopy(buf, 0, copy, 0, len);
                chunks.add(copy);
                total += len;
                bitstream.closeFrame();
            }
        } catch (Exception e) {
            throw new IOException("MP3 rusak atau tidak didukung: " + e.getMessage(), e);
        }
        if (total == 0) {
            throw new IOException("MP3 tidak berisi audio yang bisa dibaca");
        }
        short[] all = new short[total];
        int off = 0;
        for (short[] c : chunks) {
            System.arraycopy(c, 0, all, off, c.length);
            off += c.length;
        }
        return new Pcm(all, channels, rate);
    }

    /** M4A (AAC-LC / HE-AAC di kontainer MP4). ALAC (Apple Lossless) tidak didukung. */
    private static Pcm decodeM4a(Path file) throws IOException {
        short[] out = new short[1 << 20];
        int count = 0;
        int channels = 0;
        int rate = 0;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
            net.sourceforge.jaad.mp4.MP4Container container = new net.sourceforge.jaad.mp4.MP4Container(raf);
            net.sourceforge.jaad.mp4.api.Movie movie = container.getMovie();
            if (movie == null) {
                throw new IOException("M4A tidak berisi data audio");
            }
            net.sourceforge.jaad.mp4.api.Track audio = null;
            for (net.sourceforge.jaad.mp4.api.Track t : movie.getTracks()) {
                if (t instanceof net.sourceforge.jaad.mp4.api.AudioTrack) {
                    audio = t;
                    break;
                }
            }
            if (audio == null) {
                throw new IOException("M4A tidak punya jalur audio");
            }
            net.sourceforge.jaad.aac.Decoder decoder;
            try {
                decoder = new net.sourceforge.jaad.aac.Decoder(audio.getDecoderSpecificInfo());
            } catch (RuntimeException e) {
                throw new IOException("M4A ini bukan AAC (mungkin Apple Lossless). Ubah ke AAC, MP3, OGG, atau WAV.", e);
            }
            net.sourceforge.jaad.aac.SampleBuffer buf = new net.sourceforge.jaad.aac.SampleBuffer();
            net.sourceforge.jaad.mp4.api.Frame frame;
            while ((frame = audio.readNextFrame()) != null) {
                decoder.decodeFrame(frame.getData(), buf);
                byte[] d = buf.getData();
                if (d == null || d.length < 2) {
                    continue;
                }
                if (channels == 0) {
                    channels = Math.max(1, buf.getChannels());
                    rate = buf.getSampleRate();
                }
                int n = d.length / 2;
                if (count + n > out.length) {
                    out = java.util.Arrays.copyOf(out, Math.max(out.length * 2, count + n));
                }
                boolean big = buf.isBigEndian();
                for (int i = 0; i < n; i++) {
                    int hi = d[i * 2];
                    int lo = d[i * 2 + 1] & 0xFF;
                    int a = d[i * 2] & 0xFF;
                    int b = d[i * 2 + 1];
                    out[count++] = big ? (short) ((hi << 8) | lo) : (short) ((b << 8) | a);
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException | OutOfMemoryError e) {
            throw new IOException("M4A rusak atau tidak didukung: " + e.getMessage(), e);
        }
        if (count == 0 || channels == 0 || rate == 0) {
            throw new IOException("M4A tidak berisi audio yang bisa dibaca");
        }
        return new Pcm(java.util.Arrays.copyOf(out, count), channels, rate);
    }

    private static String ascii(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.US_ASCII);
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int off) {
        return le16(b, off) | (le16(b, off + 2) << 16);
    }
}
