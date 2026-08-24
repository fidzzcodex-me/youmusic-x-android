# YouMusic — Android APK (native wrapper)

## Notifikasi "sedang memutar" ala Spotify

App ini punya foreground service + notifikasi media control persis
seperti Spotify/YouTube Music:

- Muncul notifikasi dengan cover, judul, artis, dan tombol
  **Previous / Play-Pause / Next** begitu lagu mulai diputar.
- Lagu **tetap jalan** walau app di-minimize atau layar dikunci (sama
  seperti VPN app yang punya notifikasi "terhubung" terus-menerus) —
  ini karena app-nya jalan sebagai *foreground service*, jenis khusus
  yang memang diizinkan Android untuk terus aktif di background.
- Tombol di notifikasi (dan tombol media di headset/earphone Bluetooth)
  langsung mengontrol audio di dalam WebView, bukan cuma tampilan doang.
- Waktu lagu di-pause, notifikasi tetap kelihatan (bisa di-swipe hilang)
  — tepat seperti perilaku Spotify. Waktu diputar lagi, notifikasi jadi
  "ongoing" (gak bisa di-swipe) sampai di-pause lagi.

Butuh izin notifikasi (`POST_NOTIFICATIONS`) yang akan diminta otomatis
sekali ke user saat pertama buka app di Android 13 ke atas.

**Penting**: fitur ini butuh `app.js` versi terbaru di web app kamu
(yang sudah punya `notifyNative()` / `window.__nativeMediaCommand`) —
kalau belum kamu deploy, minta saya kirimkan ulang zip web app-nya biar
sinkron.

Ini project Android (Kotlin) yang membungkus web app YouMusic kamu
(`https://youmusic-x.vercel.app`) jadi APK asli, pakai `WebView` full-screen.
Tema, fitur, dan sistemnya **sama persis** dengan versi web — karena memang
yang ditampilkan adalah website kamu itu sendiri, cuma dibungkus jadi app
native. Setiap kamu deploy ulang websitenya, APK ini otomatis ikut update
tanpa perlu build ulang (karena kontennya di-load langsung dari internet,
bukan disalin ke dalam APK).

## Cara pasang ke repo GitHub kamu

1. Extract zip ini.
2. Salin folder `android/` ke root repo GitHub kamu (sejajar dengan folder
   web app `youmusic/` yang sudah ada).
3. Salin folder `.github/workflows/android-build.yml` ke
   `.github/workflows/` di repo kamu (kalau folder `.github/workflows/`
   sudah ada, cukup taruh file `android-build.yml`-nya saja di situ).
4. Commit & push ke branch `main`.

Struktur akhir repo kamu kira-kira begini:
```
repo-kamu/
├── youmusic/              (web app, sudah ada)
├── android/                (project Android, baru)
│   ├── app/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle.properties
└── .github/workflows/
    ├── android-build.yml   (baru)
    └── ...(workflow lain yang mungkin sudah ada)
```

## Cara build APK-nya

Begitu di-push, workflow **"Build Android APK"** otomatis jalan (tab
**Actions** di GitHub repo kamu). Bisa juga dipicu manual:
tab **Actions** → pilih **Build Android APK** → **Run workflow**.

Setelah selesai (~2-4 menit), buka run tersebut → bagian **Artifacts** di
bawah → download **youmusic-debug-apk**. Itu file `.apk` siap install,
tinggal ditransfer ke HP Android dan buka (mungkin perlu izinkan
"install dari sumber tidak dikenal" sekali).

## Kalau domain deploy-nya beda

Kalau URL live kamu bukan `youmusic-x.vercel.app`, buka
`android/app/src/main/java/com/youmusic/app/MainActivity.kt`, ganti nilai
`APP_URL` di bagian atas file, commit & push lagi.

## Catatan soal APK debug vs release

APK yang dihasilkan sekarang adalah **debug build** — sudah bisa langsung
diinstall & dipakai normal, ditandatangani otomatis pakai debug key yang
di-generate Android sendiri saat build (tidak perlu setup apa-apa). Ini
cukup untuk pemakaian pribadi / testing.

Kalau nanti mau upload ke Google Play, App-mu perlu di-build sebagai
**release** dan ditandatangani pakai keystore asli (bukan debug key) —
itu langkah terpisah yang melibatkan bikin keystore + simpan sebagai
GitHub Secret. Bilang aja kalau sudah butuh itu, saya bantu siapkan.

## Fitur yang ikut jalan otomatis di dalam WebView

Karena WebView Android modern itu Chromium asli, semua fitur PWA kamu
tetap jalan tanpa kode tambahan:
- Service Worker + offline caching (termasuk fitur download lagu offline)
- `localStorage` (liked songs, resume playback setelah reload)
- Autoplay audio tanpa perlu tap dua kali
- Tombol Back Android otomatis navigasi mundur di dalam app (bukan keluar
  app), dan keluar app kalau memang sudah di halaman paling awal.
