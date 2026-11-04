# Offline OCR models

These public trained models are bundled with the application; the app never downloads them.

Source: https://github.com/tesseract-ocr/tessdata_fast/tree/4.1.0
License: Apache-2.0; see ../licenses/tessdata-fast.txt.

| File | SHA-256 |
|---|---|
| eng.traineddata | 7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2 |
| jpn.traineddata | 1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d |

Engine: cz.adaptech.tesseract4android:tesseract4android:4.9.0 (Apache-2.0).
Native library notices are taken from the matching 4.9.0 source tree:
https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0/tesseract4android/src/main/cpp
See ../licenses/ for Tesseract4Android, Tesseract, Leptonica, libjpeg and libpng.

Japanese horizontal text and Latin text use jpn+eng with the LSTM engine.
Recognition confidence is an engine estimate, not a guarantee. A user must review
the raw text and proposed fields before applying them, then save the editor explicitly.
Missing years and ambiguous dates are left for manual input. Artist/tour identifiers,
payment confirmation and linked transactions are never inferred by OCR.
