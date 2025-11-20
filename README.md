# GSW Tracker

A super-lightweight Android app for tracking Golden State Warriors games with live scores and worm chart visualization.

## Features

- **Live Score Updates**: Real-time score updates every minute during games
- **Worm Chart**: Visual representation of game flow showing score differential
- **Quarter Breakdown**: See scores for each quarter at a glance
- **Minimal Data Usage**: ~100 KB per game with smart caching
- **No Background Service**: Uses WorkManager for efficient polling

## Requirements

- Android device with API 26+ (Android 8.0 Oreo or newer)
- Internet connection
- JDK 17+ for building

## Building from CLI

This project is designed to be built entirely from the command line without Android Studio.

### Build Debug APK

```bash
./gradlew assembleDebug
```

The APK will be created at: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK

```bash
./gradlew assembleRelease
```

The APK will be created at: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Install on Connected Device

```bash
./gradlew installDebug
```

### Run Tests

```bash
./gradlew test
```

### Clean Build

```bash
./gradlew clean
```

### View All Available Tasks

```bash
./gradlew tasks
```

## Project Structure

```
andnba/
├── API_RESEARCH.md          # API documentation and data usage analysis
├── UI_DESIGN.md             # UI/UX design specification
├── app/
│   ├── build.gradle.kts     # App-level Gradle configuration
│   ├── proguard-rules.pro   # ProGuard rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/in/anupcshan/gswtracker/
│       │   ├── GswTrackerApp.kt      # Application class
│       │   ├── MainActivity.kt        # Main activity
│       │   └── ui/
│       │       ├── GameScreen.kt      # Main game screen composable
│       │       └── theme/             # Theme and styling
│       └── res/                       # Resources (strings, colors, etc.)
├── build.gradle.kts         # Root-level Gradle configuration
├── settings.gradle.kts      # Gradle settings
├── gradle.properties        # Gradle properties
└── gradlew                  # Gradle wrapper script
```

## Architecture

- **UI**: Jetpack Compose with Material 3
- **State Management**: ViewModel + StateFlow
- **Networking**: OkHttp with client-side caching
- **Background Work**: WorkManager for periodic polling
- **JSON Parsing**: Kotlinx Serialization

## API Usage

The app uses NBA's official CDN endpoints:

- **Scoreboard**: `https://cdn.nba.com/static/json/liveData/scoreboard/todaysScoreboard_00.json`
- **Play-by-Play**: `https://cdn.nba.com/static/json/liveData/playbyplay/playbyplay_{gameId}.json`

See [API_RESEARCH.md](API_RESEARCH.md) for detailed API documentation.

## Data Usage

- **During Live Game**: ~100 KB (with caching)
- **Per Season (82 games)**: ~8 MB
- **Polling Frequency**: Every 60 seconds during games
- **Play-by-Play**: Downloaded once per quarter (~34 KB total)

## Design Decisions

- Single-screen app with no navigation
- Warriors brand colors (Gold #FFC72C, Blue #1D428A)
- Green line when GSW ahead, red when behind
- Minimal animations for battery efficiency
- CLI buildable (no Android Studio required)

See [UI_DESIGN.md](UI_DESIGN.md) for complete design specification.

## Development Status

- [x] Project setup and build configuration
- [x] Basic UI structure with Compose
- [x] Theme and color scheme
- [ ] API client implementation
- [ ] Scoreboard parser
- [ ] Play-by-Play parser
- [ ] Worm chart visualization
- [ ] WorkManager background polling
- [ ] State management with ViewModel
- [ ] Error handling and retry logic
- [ ] Loading states
- [ ] Pull-to-refresh

## Next Steps

1. Implement API client with OkHttp and caching
2. Create data models for scoreboard and play-by-play
3. Build worm chart with Canvas/Compose
4. Implement game state management
5. Add WorkManager for background updates
6. Polish UI and animations

## License

This is a personal project for tracking GSW games. Not affiliated with the NBA or Golden State Warriors.
