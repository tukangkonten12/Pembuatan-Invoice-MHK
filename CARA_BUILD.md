# Invoice MHK (Android, Kotlin)

Aplikasi native Android, 100% offline. Minimal Android 10 (API 29).
PDF tersimpan di Download/Invoice.

## Cara 1 - GitHub Actions (tanpa install apa pun, paling mudah)
1. Buat akun & repository baru di github.com (boleh private).
2. Upload SELURUH isi folder proyek ini (termasuk folder .github, app, dan file *.kts)
   lewat "Add file > Upload files" di browser komputer (drag & drop folder hasil ekstrak zip).
3. Buka tab Actions > "Build APK" > Run workflow. Tunggu +- 5-8 menit sampai hijau.
4. Buka run tersebut > bagian Artifacts > unduh "Invoice-MHK-APK" (zip berisi app-debug.apk).
5. Kirim app-debug.apk ke HP, buka, izinkan "Install dari sumber ini", lalu pasang.

## Cara 2 - Android Studio
Buka folder proyek di Android Studio > tunggu sync > Build > Build APK(s).
File: app/build/outputs/apk/debug/app-debug.apk
