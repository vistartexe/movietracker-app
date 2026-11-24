# MovieTracker — pengembangan F1–F9

Buka folder MovieTracker di Android Studio, lakukan Gradle Sync, jalankan app. Struktur Java/XML/SQLite asli dipertahankan. Tidak perlu menghapus database lama.

- F1/F2: login, daftar, genre, detail, favorit, rating asli.
- F3: filter mood berbasis genre dan durasi 15 film katalog lokal.
- F4: room dan voting suka/lewati bergantian di satu HP; dilengkapi Layar Pemutar Film (Cinema Screen) interaktif setelah voting selesai untuk menampilkan pemutaran film Nobar Match.
- F5: Groq API terintegrasi langsung di kode (`Constants.GROQ_API_KEY`), pengguna tidak perlu mengisi API key manual di aplikasi. Percakapan lanjutan selama halaman aktif.
- F6: tautan platform resmi; tidak memverifikasi ketersediaan judul/wilayah.
- F7: tautan situs penjual tiket resmi; tidak menjual tiket atau memverifikasi jadwal.
- F8: simulasi Gratis 5 dan Plus 50 respons sukses per hari, tersimpan lokal per akun; belum ada pembayaran, iklan, atau validasi server.
- F9: riwayat film yang benar-benar dipilih dari hasil voting lokal, tersimpan per akun.

## Uji manual
1. Login atau daftar, buka halaman utama, pastikan fitur lama berjalan.
2. Filter komedi dan 101–120 menit; buka detail hasil.
3. Buat room dua peserta, pilih Suka pada film yang sama untuk keduanya, pilih film hasil, cek riwayat.
4. Masukkan API key Groq, kirim pertanyaan dan pertanyaan lanjutan. Periksa kuota dan pesan kesalahan bila key salah.
5. Buka detail film, coba tautan platform dan tiket.
6. Ubah paket demo dan cek batas AI.

Validasi XML dilakukan. Build APK dan pengujian perangkat/API belum dilakukan karena Android SDK dan kredensial tidak tersedia di lingkungan pengerjaan.

## Patch login/home
Pemulihan sesi pada MainActivity, pembersihan task saat login, pencegahan pengalihan berulang pada sesi tidak valid, dan pemuatan ikon home dengan AppCompat serta ripple eksplisit. XML dan ID diperiksa statis. Build dan pengujian perangkat belum dilakukan; jika crash berlanjut, periksa FATAL EXCEPTION pada Logcat.
