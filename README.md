# X Player

A modern, high-performance Android video player powered by **LibVLC** and **Jetpack Compose**.

## Features

- **LibVLC Core Engine**: Seamlessly play almost any video format (MP4, MKV, AVI, FLV, MOV, WebM, etc.) with hardware acceleration.
- **Jetpack Compose UI Controls**: Smooth, reactive, and modern touch overlay for player controls.
- **Gesture Controls**: Vertical swipe gestures for fast brightness and volume adjustment, horizontal swipe for seeking.
- **Picture-in-Picture (PiP)**: Continue watching videos while multitasking across apps.
- **Audio & Subtitle Track Selector**: Switch between multi-audio tracks and external/embedded subtitles.
- **Aspect Ratio Control**: Quick toggle between Fit Screen, Crop/Zoom, Stretch, 16:9, 4:3, and Original ratio.
- **Playback Speed**: Adjust speed from 0.25x up to 2.0x.
- **Background & Audio-only Playback**: Listen to video audio in the background or with the screen off.
- **Equalizer Integration**: Launch system audio equalizer directly from the player.
- **Sleep Timer**: Automatically pause playback after a set duration.
- **Screen Lock**: Prevent accidental touches while watching.
- **Folder & Video Browser**: Browse media files categorized by folders with fast thumbnail caching.

## Tech Stack

- **Language**: Kotlin
- **Video Engine**: LibVLC (`org.videolan.android:libvlc-all`)
- **UI**: Jetpack Compose + Material 3 + Android ViewBinding
- **Image & Thumbnail Loading**: Glide + FFmpegMediaMetadataRetriever
- **Min SDK**: 24 (Android 7.0+)
- **Target SDK**: 36

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/iyuji9041-ops/XPlayer.git
   ```
2. Open the project in Android Studio (Ladybug or newer recommended).
3. Let Gradle sync and download dependencies.
4. Build and run on an Android device or emulator running Android 7.0 (API 24) or higher.

## License

This project is licensed under the Apache License 2.0 / MIT License.
