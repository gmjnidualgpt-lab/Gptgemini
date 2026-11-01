# Most Nowy Photos Cleaner

Android module for finding byte-identical local photos and moving selected extra copies to Android Trash after the system confirmation dialog.

## Build
`gradle :photoscleaner:assembleDebug`

APK: `photoscleaner/build/outputs/apk/debug/photoscleaner-debug.apk`

## Scope and privacy
- Scans images exposed to the app by Android MediaStore.
- Computes SHA-256 locally. Image bytes are not uploaded.
- Keeps the first item in every duplicate group by default.
- Selected extra copies are sent to Android's trash through MediaStore.createTrashRequest. Android asks the user to confirm.
- Does not scan items that exist only in the Google Photos cloud. Google's public Photos API does not grant general-purpose full-library access or arbitrary deletion.
