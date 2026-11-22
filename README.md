# Face Swap Studio (Android)

A personal Android photo-editing starter app. Photo selection, face detection, basic face-region compositing, and saving are performed on-device. No hosted server or subscription is required.

## Build an APK

1. Open the **Actions** tab in this repository.
2. Run **Android APK** using **Run workflow**, or push a commit to `main`.
3. Open the completed workflow run and download the `Face-Swap-Studio-debug` artifact.
4. Extract the ZIP and install `app-debug.apk` on your Android phone. You may need to allow installs from your file manager.

## Current capabilities

- Select a source face photo and a destination photo from the gallery.
- Detect the largest face in each photo using Google ML Kit.
- Create a simple oval face-region composite and preview it.
- Save the result under Pictures/FaceSwapStudio.
- Image processing is local; the build workflow uses GitHub-hosted runners.

## Important limitation

This first build is a **basic face-region composite**, not a photorealistic deep-learning face-swap model. It does not guarantee accurate alignment, skin-tone matching, expression transfer, or seamless blending. A dedicated, appropriately licensed on-device swap model and device-specific performance testing are still required for high-quality results. Use only images you own or have permission to edit, and disclose edits when they could mislead others.

## Requirements

- Android 6.0 (API 23) or newer.
- JDK 17 and Gradle 8.9 for local builds.
