# Panduan Musik X FIVE LIVE (Android, Zalith Launcher)

Panduan ini untuk memasukkan lagu pribadi secara manual, tanpa Spotify atau YouTube.

## Langkah 1: Buka folder game di Zalith

1. Buka aplikasi **Zalith Launcher**.
2. Pilih instance Minecraft yang sudah ada mod X FIVE LIVE-nya.
3. Cari pengaturan folder game (sering disebut **Game directory** atau **Folder game**).
   Lokasinya bisa berbeda di tiap versi Zalith. Kalau tidak ketemu, cek pengaturan instance dan pengaturan umum Zalith.

## Langkah 2: Buka folder xfive-music

1. Buka game sekali dengan mod ini. Folder **xfive-music** akan dibuat otomatis di dalam folder game.
2. Keluar dari game, lalu buka aplikasi **File** (atau File Manager) di HP.
3. Masuk ke folder game dari Langkah 1, lalu buka folder **xfive-music**.

Kalau folder itu belum ada, buat sendiri dengan nama persis `xfive-music` (huruf kecil semua).

## Langkah 3: Salin lagu ke dalam folder

1. Salin file lagu ke dalam folder **xfive-music**.
2. Format yang didukung: **.mp3**, **.m4a**, **.ogg**, dan **.wav**.
3. Beri nama file dengan pola **Artis - Judul.mp3**, misalnya `Adele - Hello.mp3`.
   Dengan pola ini, nama artis dan judul tampil di HUD.
4. (Opsional) Lirik bisa ditaruh di file **Artis - Judul.lrc** di folder yang sama.

### Cara memasukkan lagu dari file ZIP

1. Unduh lagu dalam bentuk ZIP seperti biasa.
2. Buka aplikasi **File**, lalu ekstrak (unzip) file ZIP tersebut. Hasilnya berupa file .mp3, .m4a, .ogg, atau .wav.
3. Salin file-file lagu itu ke folder **xfive-music** (lihat Langkah 2).
4. Buka game, lalu tekan **Right Shift**. Daftar lagu diperbarui otomatis setiap menu dibuka, jadi **tidak perlu restart atau muat ulang Minecraft**.
5. Di menu, perhatikan baris **Folder lagu** dan **Jumlah lagu**. Baris itu menampilkan lokasi folder yang sedang dipakai dan berapa lagu yang terbaca.

Mod tidak bisa membuka pemilih file dari HP. Karena itu lagu harus disalin manual ke folder yang ditampilkan di menu.

## Langkah 4: Putar lagu di dalam game

1. Buka game, lalu tekan **F6** untuk membuka Control Center di halaman Audio.
2. Lagu baru otomatis terbaca. Tidak perlu restart atau muat ulang Minecraft.
3. Ketuk sebuah lagu untuk memutar, atau tekan tombol putar di bar bawah.
4. Atur volume dengan slider di bar bawah. Tombol panah pindah ke lagu sebelumnya atau berikutnya.
5. Tekan **Close** untuk keluar. Pengaturan tersimpan otomatis.

## Control Center (semua dalam satu jendela)

Tekan **F6** untuk langsung ke Audio (daftar lagu + pemutar), atau **Right Shift** untuk Home. Mouse otomatis aktif saat jendela terbuka. Menu di kiri:

- **Home**: pintasan ke semua halaman.
- **Audio**: daftar lagu. Geser dengan jari untuk menggulir, ketuk lagu untuk memutar, ketik di kotak cari. Isi path folder atau file di kotak kiri lalu tekan **+** untuk menambah lagu (contoh `/storage/emulated/0/Music`). Bar di bawah: prev, play/pause, next, repeat, shuffle, volume, dan progres lagu (ketuk atau geser).
- **HUD**: nyalakan atau matikan tiap HUD, atur HUD opacity, lebar dan tinggi Dynamic Island. **Drag HUD** membuka mode geser: tahan dan geser kotak HUD dengan jari.
- **Custom Controls**: bentuk Island (Card atau Pill), warna, Auto hide, lama pop-up, tampil saat jeda, repeat, shuffle.
- **Appearance**: 8 tema warna (Purple, Cyan, Rose, Emerald, Amber, Ocean, Crimson, Mono). Ketuk kotak warna untuk mengganti. Tema dipakai di Control Center dan semua HUD. Juga ada Glass opacity dan Animations.
- **Settings**: aktifkan mod, putar otomatis, tampilkan artwork.

Tekan **Close** atau Esc untuk keluar. Pengaturan tersimpan otomatis.

## Dynamic Island

Saat lagu diputar, kapsul kecil muncul di atas layar, membuka menjadi kartu, dan menutup kembali saat musik berhenti. Di **Custom Controls**, nyalakan **Auto hide pop-up** agar kartu hanya muncul beberapa detik setiap lagu mulai atau berganti. Atur lamanya dengan **Pop-up time**.

## Opsional: lagu dari folder lain

Kalau lagu ada di folder lain di HP (misalnya Music atau Download):

1. Buka **xfive-music**, lalu buat file teks bernama **folder-musik.txt**.
   File ini dibuat otomatis saat game dibuka, jadi cukup dibuka dan diedit.
2. Tulis satu path per baris. Contoh:
   ```
   /storage/emulated/0/Music
   /storage/emulated/0/Download/lagu.mp3
   ```
3. Baris yang diawali tanda **#** diabaikan.
4. Simpan, lalu buka game dan tekan **Right Shift**. Lagu dari folder tambahan akan terbaca.

Catatan: cara paling mudah dan paling pasti adalah menyalin lagu langsung ke folder **xfive-music**.

## Kalau musik tidak berbunyi

- Pastikan formatnya benar-benar MP3, M4A (AAC), OGG, atau WAV. Mengganti nama file .webm menjadi .mp3 tidak membuat file itu jadi MP3. M4A berisi Apple Lossless (ALAC) dan file AAC mentah (.aac) belum didukung; ubah dengan aplikasi konverter ke M4A (AAC) atau MP3.
- Kalau status menulis "tidak berisi audio", file-nya kemungkinan rusak atau tidak lengkap. Coba salin ulang dari sumbernya.
- Coba dulu dengan lagu pendek (1 sampai 3 menit). Lagu sangat panjang butuh memori besar.
- Lihat tulisan status di menu X FIVE LIVE. Di sana tertulis alasannya, misalnya "Format tidak didukung" atau "Belum ada lagu".
- Baris **Status** di menu (Right Shift) menampilkan kondisi terakhir. Jika tertulis "Gagal", pesan setelahnya menjelaskan penyebabnya.
- Cek volume di tiga tempat:
  1. Volume HP (tombol volume samping, dan pastikan tidak dalam mode senyap).
  2. Di Minecraft, buka **Options → Music & Sounds** dan pastikan **Master Volume** tidak nol.
  3. Slider **Volume** di menu X FIVE LIVE.

## Batasan saat ini

- Tidak ada koneksi ke Spotify atau YouTube. Musik hanya berasal dari file di HP.
- HUD di dalam game tidak bisa diklik (kursor terkunci). Tekan F6 untuk membuka Control Center dan mengendalikan musik.
- Pemutaran di Android belum diuji langsung di perangkat. Kalau tidak berbunyi, kirim status yang muncul di menu.
