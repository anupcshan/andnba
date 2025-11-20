# NBA Game Tracker - API Research & Design

## Summary
Lightweight Android app for tracking Golden State Warriors games with live scores and "worm" visualization showing game flow.

## API Endpoints

### 1. Today's Scoreboard
**URL**: `https://cdn.nba.com/static/json/liveData/scoreboard/todaysScoreboard_00.json`

**Purpose**: Get all games for today, find GSW games, monitor live status

**Key Fields**:
```json
{
  "scoreboard": {
    "gameDate": "2025-11-19",
    "games": [{
      "gameId": "0022500255",           // Use this for play-by-play
      "gameCode": "20251119/HOUCLE",
      "gameStatus": 1|2|3,               // 1=scheduled, 2=live, 3=final
      "gameStatusText": "Final",
      "period": 4,                       // Current quarter
      "gameClock": "PT02M15.00S",        // Time remaining in period
      "homeTeam": {
        "teamTricode": "GSW",
        "score": 115,
        "periods": [                     // Quarter-by-quarter
          {"period": 1, "score": 28},
          {"period": 2, "score": 30},
          ...
        ]
      },
      "awayTeam": { /* same structure */ }
    }]
  }
}
```

**Size**:
- Uncompressed: ~28 KB
- **Compressed (gzip): ~2.5 KB** ✅

### 2. Play-by-Play
**URL**: `https://cdn.nba.com/static/json/liveData/playbyplay/playbyplay_{gameId}.json`

**Purpose**: Get detailed scoring events for worm chart

**Key Fields**:
```json
{
  "game": {
    "gameId": "0022400264",
    "actions": [
      {
        "actionNumber": 1,
        "period": 1,
        "clock": "PT11M58.00S",        // Time remaining in period
        "timeActual": "2024-11-23T...", // Actual timestamp
        "scoreHome": "5",
        "scoreAway": "3",
        "actionType": "2pt",
        "shotResult": "Made",
        "teamTricode": "GSW",
        "personId": 123456,
        "playerNameI": "S. Curry"
      },
      // ... 400-600 events per game
    ]
  }
}
```

**Size**:
- Uncompressed: ~390 KB
- **Compressed (gzip): ~34 KB** ✅

**Event Distribution**:
- After Q1: ~145 events (~9 KB compressed)
- After Q2: ~270 events (~17 KB compressed)
- After Q3: ~413 events (~27 KB compressed)
- After Q4: ~528 events (~34 KB compressed)

## Data Usage Estimates

### Without Client-Side Caching
**Per game with 1-minute polling:**
- Scoreboard polling (150 requests): 150 × 2.5 KB = 375 KB
- Play-by-play (4 fetches): 87 KB
- **Total: ~462 KB per game**
- **Per season (82 games): ~38 MB**

### With OkHttp Client-Side Caching (RECOMMENDED)
**Per game with 60-second cache TTL:**
- Scoreboard polling: ~3-5 actual network requests = 10-15 KB
- Play-by-play (4 fetches): 87 KB
- **Total: ~100 KB per game** ✅
- **Per season (82 games): ~8 MB** ✅

## Implementation Strategy

### Polling Schedule
```
Before game start: Check scoreboard once per hour
Game time (±30 min): Poll scoreboard every 60 seconds
Game ended: Stop polling
No GSW game today: Single morning check, then idle
```

### Progressive Worm Chart Updates
Fetch play-by-play at quarter transitions:
1. **After Q1**: Fetch play-by-play (~9 KB) → Show Q1 worm
2. **After Q2**: Fetch play-by-play (~17 KB) → Show Q1+Q2 worm (halftime!)
3. **After Q3**: Fetch play-by-play (~27 KB) → Show Q1+Q2+Q3 worm
4. **Game Final**: Fetch play-by-play (~34 KB) → Show complete worm

**Detection Logic**:
```kotlin
if (currentPeriod > lastSeenPeriod) {
    // Quarter just changed - fetch updated play-by-play
    fetchPlayByPlay(gameId)
    updateWormChart()
}
```

### OkHttp Configuration

```kotlin
val cache = Cache(
    directory = File(cacheDir, "http_cache"),
    maxSize = 10L * 1024 * 1024 // 10 MB
)

val client = OkHttpClient.Builder()
    .cache(cache)
    .addNetworkInterceptor { chain ->
        val response = chain.proceed(chain.request())
        // Force cache with 60s TTL (NBA CDN doesn't send Cache-Control)
        response.newBuilder()
            .header("Cache-Control", "public, max-age=60")
            .removeHeader("Pragma")
            .build()
    }
    .build()
```

**Why forced caching needed:**
- NBA CDN does **not** support If-None-Match (ETag) conditional requests
- No Cache-Control headers sent by server
- Client-side cache prevents redundant downloads
- 60-second TTL balances freshness vs efficiency

### Background Work with WorkManager

```kotlin
// Schedule only during game windows
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

val workRequest = PeriodicWorkRequestBuilder<ScoreboardWorker>(
    repeatInterval = 1,
    repeatIntervalTimeUnit = TimeUnit.MINUTES
)
    .setConstraints(constraints)
    .build()

// Cancel when game ends
```

## API Characteristics

### Reliability
- ✅ Official NBA CDN (cdn.nba.com)
- ✅ No authentication required
- ✅ No documented rate limits
- ✅ Same infrastructure for all live data
- ⚠️ Undocumented API - could change without notice

### Caching Behavior
- ❌ No ETag/If-None-Match support (always returns 200)
- ❌ No Cache-Control headers
- ✅ Provides ETag and Last-Modified (but doesn't honor conditional requests)
- ✅ Consistent data format across endpoints

### Compression
- ✅ Supports gzip (Accept-Encoding: gzip)
- ✅ 90%+ compression ratio on JSON data
- ✅ Automatically handled by OkHttp

## Data Efficiency Features

1. **Team Filtering**: Only track GSW games (ignore other teams)
2. **Smart Scheduling**: Only poll during game windows
3. **Progressive Loading**: Fetch play-by-play per quarter, not continuously
4. **Client Caching**: 60s cache prevents redundant requests
5. **Compression**: gzip reduces transfer by 90%
6. **WiFi Preference**: Optional setting to fetch play-by-play only on WiFi

## Worm Chart Data Requirements

To build a worm chart, we need:
- Time series of score differentials
- One data point per scoring event (~200-250 scoring plays per game)
- Can be built from play-by-play `actions` array

**Filter for scoring events**:
```kotlin
val scoringEvents = playByPlay.game.actions.filter {
    it.scoreHome != null && it.scoreAway != null &&
    (it.actionType == "2pt" || it.actionType == "3pt" ||
     it.actionType == "freethrow") &&
    it.shotResult == "Made"
}
```

**Data points needed**:
```kotlin
data class WormPoint(
    val gameTimeSeconds: Int,  // Convert from period + clock
    val homeScore: Int,
    val awayScore: Int,
    val scoreDiff: Int = homeScore - awayScore
)
```

## Next Steps

1. Design UI wireframes
2. Implement basic networking layer with OkHttp
3. Build scoreboard parser and GSW game detector
4. Implement play-by-play parser and worm chart generator
5. Create WorkManager background scheduler
6. Design and implement worm chart visualization
7. Add settings (WiFi-only mode, notifications, etc.)
