# Third-party components

This preview uses AndroidX (including Compose, Material 3, Room, DataStore,
Biometric, Activity, Fragment, Lifecycle, SQLite), Kotlin and kotlinx.coroutines.
These components are distributed under the Apache License 2.0 and their
respective copyright notices. SQLCipher and SQLCipher for Android are maintained
by Zetetic LLC and include their own license terms and notices.

License texts are bundled in the APK under `assets/licenses/` and in this source
project at `app/src/main/assets/licenses/`. The exact resolved Maven components
and versions are listed in `reports/runtime-dependencies.txt`. Test-only JUnit
and JSON dependencies are specified in `app/build.gradle.kts` and are not shipped
as application runtime dependencies.

- AndroidX: https://android.googlesource.com/platform/frameworks/support/
- Kotlin: https://github.com/JetBrains/kotlin
- Coroutines: https://github.com/Kotlin/kotlinx.coroutines
- SQLCipher for Android: https://github.com/sqlcipher/sqlcipher-android
- SQLCipher: https://github.com/sqlcipher/sqlcipher

The preview's own application icon is an original vector drawing. It uses the
Android system font and Material icons supplied by the Compose dependency.

Version 0.3 adds Tesseract4Android 4.9.0 and its bundled native Tesseract,
Leptonica, libjpeg and libpng libraries. Matching upstream license/copyright
texts are included under `assets/licenses/`. Japanese and English trained
models come from `tesseract-ocr/tessdata_fast` tag 4.1.0 (Apache-2.0).
Model hashes and source URLs are in `assets/ocr/PROVENANCE.md`.
This is a local JNI OCR engine, without a network or analytics SDK.
