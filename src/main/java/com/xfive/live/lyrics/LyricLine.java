package com.xfive.live.lyrics;

/** Satu baris lirik dengan waktu tampil dalam milidetik. */
public record LyricLine(long timeMs, String text) {
}
