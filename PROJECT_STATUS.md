# GSW Tracker - Project Status

## ✅ Completed

### Research & Design
- [x] NBA API research and endpoint analysis
- [x] Data usage calculation and optimization strategy
- [x] UI/UX design specification
- [x] Technology stack selection

### Project Setup
- [x] Android project structure created
- [x] Gradle build configuration (CLI buildable)
- [x] Jetpack Compose with Material 3 setup
- [x] Warriors brand theme and colors
- [x] Basic app structure (Application, MainActivity, GameScreen)
- [x] Dependencies configured (OkHttp, Kotlinx Serialization, WorkManager)
- [x] ProGuard rules for release builds
- [x] Documentation (API_RESEARCH.md, UI_DESIGN.md, README.md)

### Build System
- [x] Gradle wrapper configured
- [x] **Successfully builds from CLI**: `./gradlew assembleDebug`
- [x] Debug APK size: 8.5 MB (will be smaller in release)

## 📋 Next Steps (Implementation Phase)

### Phase 1: API Client (Priority: High)
- [ ] Create data models for NBA API responses
  - [ ] Scoreboard model
  - [ ] Play-by-Play model
  - [ ] Team and Game models
- [ ] Implement OkHttp client with caching
  - [ ] Add network interceptor for 60s cache TTL
  - [ ] Add logging interceptor for debugging
  - [ ] Configure gzip compression
- [ ] Create repository layer
  - [ ] ScoreboardRepository
  - [ ] PlayByPlayRepository
- [ ] Add error handling and retry logic

### Phase 2: UI Implementation (Priority: High)
- [ ] Create GameViewModel with StateFlow
- [ ] Implement game state sealed class
- [ ] Build UI components
  - [ ] Header section (game title, status)
  - [ ] Score display section
  - [ ] Quarter breakdown grid
  - [ ] Worm chart composable
  - [ ] Next game display
  - [ ] Error states
  - [ ] Loading states
- [ ] Add pull-to-refresh functionality

### Phase 3: Worm Chart (Priority: Medium)
- [ ] Research/choose charting library (Vico vs MPAndroidChart vs Custom)
- [ ] Parse play-by-play for scoring events
- [ ] Build worm data model (WormPoint with time, score diff)
- [ ] Implement chart rendering
  - [ ] Green line when GSW ahead
  - [ ] Red line when GSW behind
  - [ ] Zero line (tied game)
  - [ ] Quarter markers on X-axis
- [ ] Add chart animations

### Phase 4: Background Work (Priority: Medium)
- [ ] Implement WorkManager scheduler
- [ ] Create ScoreboardWorker
- [ ] Add game time detection logic
- [ ] Implement adaptive polling
  - [ ] Check once/hour when no game
  - [ ] Poll every 60s during game
  - [ ] Stop polling when game ends
- [ ] Add quarter transition detection for play-by-play fetching

### Phase 5: Polish (Priority: Low)
- [ ] Add proper loading skeletons
- [ ] Improve error messages
- [ ] Add animations for score changes
- [ ] Implement live badge pulse animation
- [ ] Add app icon (currently using default)
- [ ] Test on different screen sizes
- [ ] Test with real API data during live game
- [ ] Performance optimization

### Phase 6: Future Enhancements (Optional)
- [ ] Settings screen (WiFi-only mode, team selection)
- [ ] Notification for game start/end
- [ ] Home screen widget
- [ ] Tap chart to see score at specific time
- [ ] Swipe to see past games
- [ ] Share game result as image
- [ ] Manual dark/light theme toggle

## 📊 Key Metrics

### Data Usage (Optimized)
- **Per game**: ~100 KB
- **Per season**: ~8 MB
- **Polling**: 60-second intervals during games
- **Compression**: gzip (~90% reduction)
- **Caching**: Client-side 60s TTL

### Performance Targets
- **App size**: < 10 MB (release build)
- **Initial load**: < 2 seconds
- **API response time**: < 500ms (depends on NBA CDN)
- **Background work**: < 5 seconds per poll
- **Battery impact**: Minimal (WorkManager optimization)

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────┐
│            MainActivity                  │
│  (Compose UI + Material 3 Theme)        │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│           GameViewModel                  │
│  (StateFlow<GameState>)                 │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│           Repository Layer               │
│  - ScoreboardRepository                  │
│  - PlayByPlayRepository                  │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│          OkHttp Client                   │
│  - Gzip compression                      │
│  - 60s cache TTL                         │
│  - Logging interceptor                   │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│           NBA CDN API                    │
│  - todaysScoreboard_00.json             │
│  - playbyplay_{gameId}.json             │
└─────────────────────────────────────────┘

            Background Work
┌─────────────────────────────────────────┐
│          WorkManager                     │
│  - PeriodicWorkRequest (60s)            │
│  - Game time detection                   │
│  - Quarter transition logic              │
└─────────────────────────────────────────┘
```

## 📱 Current App State

The app currently shows a placeholder screen with:
- Warriors gold and blue theme
- Basic Compose layout
- "Ready to track Warriors games" message

**Next**: Implement API client to fetch real game data.

## 🚀 How to Continue Development

1. **Start the app on emulator/device**:
   ```bash
   ./gradlew installDebug
   ```

2. **Begin Phase 1**: Create data models in `app/src/main/java/com/gswtracker/data/model/`

3. **Test API responses**: Use `curl` or Postman to understand response structure

4. **Iterate**: Build feature by feature, test frequently

5. **Reference docs**: Check API_RESEARCH.md and UI_DESIGN.md as needed

## 📝 Notes

- All API endpoints tested and working as of Nov 20, 2025
- ETags don't work for 304 responses, but client-side caching does
- GSW game ID today: Check scoreboard for `"teamTricode": "GSW"`
- Remember to filter play-by-play for scoring events only (saves processing)

## 🎯 Success Criteria

The app will be considered MVP-complete when:
- [x] Builds from CLI
- [ ] Shows today's GSW game with live score
- [ ] Updates score every 60 seconds
- [ ] Displays quarter-by-quarter breakdown
- [ ] Shows worm chart after each quarter
- [ ] Handles "no game today" state
- [ ] Works on mobile data (data efficient)
- [ ] Runs without crashes for full game duration

---

**Current Phase**: Setup Complete ✅
**Next Phase**: API Client Implementation 🚧
**Estimated Completion**: 3-5 days of focused development
