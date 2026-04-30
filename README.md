# VideoVault - Multi-Platform Video Downloader

A powerful, multi-language Android video downloader powered by the [Cobalt API](https://github.com/imputnet/cobalt).

## Features

### 🌐 Multi-Platform Support
Download videos from **20+ platforms**:
- Twitter/X
- YouTube
- TikTok
- Instagram
- Reddit
- Facebook
- Vimeo
- Twitch
- Pinterest
- Tumblr
- SoundCloud
- And many more!

### 🎬 Video Player
- **Online playback** — stream videos directly from URLs
- **Lock screen viewing** — lock controls to prevent accidental touches
- **Speed control** — 0.5x to 3x playback speed
- **Gesture controls** — swipe for volume, brightness, and seek
- **Double-tap** to skip forward/backward 10 seconds
- **Fullscreen** mode with orientation lock

### 📁 Local Video Management
- Browse all downloaded videos
- Browse device video library
- Sort by date, size, or name
- Playback history tracking

### 🌍 10 Languages Supported
- English (English)
- 中文简体 (Chinese Simplified)
- 한국어 (Korean)
- 日本語 (Japanese)
- Español (Spanish)
- Français (French)
- Deutsch (German)
- Русский (Russian)
- العربية (Arabic)
- हिन्दी (Hindi)

### ⚙️ Customizable Settings
- Theme: System / Light / Dark
- Language selection
- Default video quality (Best/4K/2K/HD/SD)
- Auto-play toggle
- Cache management

## Architecture

- **Language**: Kotlin
- **UI**: Material Design 3 + ViewBinding
- **Video Player**: ExoPlayer (Media3)
- **Database**: Room
- **Networking**: OkHttp
- **Image Loading**: Coil
- **Architecture**: MVVM with StateFlow/Coroutines

## Project Structure

```
videovault/
├── app/src/main/
│   ├── java/com/videovault/
│   │   ├── App.kt                          # Application class
│   │   ├── ui/
│   │   │   ├── MainActivity.kt             # Main activity with bottom nav
│   │   │   ├── home/                       # URL parsing & download
│   │   │   ├── online/                     # Online video player
│   │   │   ├── downloads/                  # Download management
│   │   │   ├── local/                      # Local video browser
│   │   │   ├── player/                     # Video player with gestures
│   │   │   └── settings/                   # App settings
│   │   ├── data/
│   │   │   ├── remote/api/CobaltApiService.kt  # Cobalt API integration
│   │   │   ├── remote/repository/          # Data repository
│   │   │   ├── local/                      # Room database & download manager
│   │   │   └── model/                      # Data models
│   │   ├── service/                        # Download foreground service
│   │   └── util/                           # Utilities
│   └── res/
│       ├── layout/                         # XML layouts
│       ├── drawable/                       # Vector icons & shapes
│       ├── menu/                           # Bottom navigation
│       ├── values/                         # Default (English) strings
│       ├── values-zh/                      # Chinese
│       ├── values-ko/                      # Korean
│       ├── values-ja/                      # Japanese
│       ├── values-es/                      # Spanish
│       ├── values-fr/                      # French
│       ├── values-de/                      # German
│       ├── values-ru/                      # Russian
│       ├── values-ar/                      # Arabic
│       └── values-hi/                      # Hindi
├── build.gradle
├── settings.gradle
└── gradle/
```

## How to Build

1. Open in Android Studio
2. Sync Gradle
3. Run on device or emulator (min SDK 24 / Android 7.0)

## API Configuration

The app uses the [Cobalt API](https://github.com/imputnet/cobalt/blob/main/docs/api.md) for video parsing. By default it connects to `https://api.cobalt.tools`.

You can configure a custom Cobalt instance in the app settings.

## License

MIT License
