# X FIVE LIVE 1.1.0 — Status Build

Mod client-side Fabric untuk Minecraft Java 26.2 (target Java 25). Dokumen ini memisahkan dengan jelas apa yang sudah diuji, apa yang baru ditulis, dan apa yang belum dikerjakan.

## Sudah diuji (48/48 tes lulus, JDK 21)

Tes ada di `tests/com/xfive/live/tests/XFiveTests.java` dan hanya memakai kode tanpa Minecraft.

- **Parser LRC**: timestamp `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, beberapa timestamp per baris, tag `[offset:ms]`, dan baris metadata yang dilewati.
- **Lirik aktif**: pencarian baris berdasarkan waktu (binary search), termasuk sebelum baris pertama dan di akhir lagu.
- **Playlist**: pindai folder sekali (bukan per frame), baca "Artis - Judul" dari nama file, next/previous dengan wrap-around.
- **Config**: simpan dan muat `config/xfive-live.properties`, nilai di luar batas dikunci, nilai rusak kembali ke default.

## UI Control Center 1.1.0

- Dua skin UI kini dapat dipilih pada Appearance: **Liquid Glass Lite** dan **Dark Music**. Pilihan disimpan sebagai `appearance.style` dan tidak menghapus pilihan warna aksen.
- Liquid Glass Lite memakai panel kaca ringan; Dark Music memakai panel charcoal matte dengan aksen tema.
- **Pilihan font khusus belum diimplementasikan.** UI masih memakai font Minecraft aktif.
- **Spring Pop-in belum diimplementasikan.** Tombol Animations yang sudah ada belum boleh dianggap sebagai bukti transisi buka/tutup.
- Animasi penutupan yang mengecil menuju HUD juga belum diimplementasikan.
- Build sukses hanya membuktikan kompilasi/paket; pengujian visual dan input tetap perlu dilakukan di Minecraft/Fabric.

## Ditulis, tapi belum dikompilasi atau diuji di game

- Entrypoint `XFiveLiveClient`, keybind F8/F9/F10, dan registrasi HUD.
- HUD musik dan lirik, serta `GlassPanelRenderer`.
- Pemutar WAV (`AudioPlayer`) memakai `javax.sound`. Kodenya terkompilasi di JDK 21, tapi belum pernah benar-benar memutar audio.
- Nama API Fabric dan Minecraft 26.2 (`KeyMapping.Category`, `KeyMappingHelper`, `GuiGraphicsExtractor.fill/text`, `HudElementRegistry`, dan lainnya) diambil dari dokumentasi dan hasil pencarian, belum dicek terhadap jar asli. Ini tempat paling mungkin error saat pertama kali build.

Pemeriksaan sintaks: seluruh file Minecraft tidak punya error parser. Error yang muncul hanya simbol dan paket Minecraft/Fabric yang tidak ada di lingkungan ini.

## Belum dikerjakan (tidak sesuai spesifikasi penuh)

- **MP3 dan OGG**: belum didukung. Perlu library decoder tambahan.
- **Android / Zalith**: `javax.sound` tidak tersedia di Android. Pemutaran audio di HP memerlukan backend audio lain. Build ini belum menyediakannya, jadi musik kemungkinan tidak berbunyi di Zalith.
- **Layar pengaturan** (`XFiveLiveScreen`, `SettingsScreen`): sudah ada (Control Center, F6 atau Right Shift).
- **Kontrol musik di HUD** (tombol play/pause/next): belum. HUD hanya menampilkan.
- **Drag and drop HUD** dan skala per komponen: belum. Posisi diatur lewat `hud.<nama>.x/y` di file config, dan skala masih global.
- **Animasi transisi Spring Pop-in dan animasi tutup ke HUD, font khusus, album artwork, avatar penonton**: belum.
- **SRT**: belum.
- **TikTok LIVE**: di luar cakupan versi ini, sesuai spesifikasi.
- **Ukuran font lirik**: saat ini hanya mengatur jarak baris, belum menskala teks.
- **Auto resume**: memutar lagu pertama saat game mulai, bila folder musik sudah diatur.
- **Right Shift membuka layar**: belum, karena layar belum dibuat.

## Cara build (di PC dengan internet dan JDK 25)

1. Pastikan `loom_version` di `gradle.properties` sesuai versi Fabric Loom terbaru untuk 26.x.
2. Jalankan `./gradlew build`.
3. Hasilnya di `build/libs/XFiveLive-1.1.0.jar`.

Jika build gagal, error pertama kemungkinan berasal dari nama API Minecraft yang belum diverifikasi di file HUD, `XFiveLiveClient`, atau `GlassPanelRenderer`.

## Pengaturan

File: `config/xfive-live.properties` (dibuat otomatis saat pertama kali disimpan).

- Folder musik default: `xfive-music` di dalam folder game. Taruh file `.mp3`, `.m4a`, `.ogg`, atau `.wav` di sana. Folder ini dibuat otomatis saat game dibuka.
- `music.folder`: bisa diganti ke folder lain lewat file config (belum ada menu di dalam game).
- `lyrics`: file `.lrc` dengan nama sama seperti lagu (`Artis - Judul.wav` dan `Artis - Judul.lrc`) dibaca otomatis saat lagu berganti.
- `appearance.*`, `general.*`, `hud.*`: lihat `XFiveConfig.java`.

## Tombol

| Tombol | Fungsi |
|---|---|
| Right Shift | Buka Control Center (Home) |
| F6 | Buka Control Center di halaman Audio (daftar lagu dan pemutar) |
| F8 | Tampilkan/sembunyikan HUD |
| F9 | Tampilkan/sembunyikan lirik |

Tombol ini terdaftar sebagai keybind Minecraft, sehingga bisa diubah di Controls.

## Versi Minecraft

- Cabang `main`: Minecraft **26.2** (Java 25).
- Cabang `mc-1.21.11`: Minecraft **1.21.11** (Java 21, Fabric Loader 0.20.1). Jar-nya bernama `XFiveLive-mc1.21.11-...jar`.
- Kedua cabang dibangun otomatis oleh GitHub Actions; ambil jar dari menu **Actions > Artifacts**.
