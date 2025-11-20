# GSW Game Tracker - UI Design

## Design Philosophy
Single-screen, zero-navigation app focused on one thing: tracking today's Warriors game.

## Screen Layout

### State 1: Game Today (Before Start)
```
┌─────────────────────────────────┐
│  🏀 Warriors vs Lakers          │
│  Today at 7:30 PM               │
│                                 │
│  GSW  --  vs  --  LAL           │
│  (15-8)        (12-11)          │
│                                 │
│  [Game hasn't started yet]      │
│                                 │
│                                 │
│                                 │
│                                 │
└─────────────────────────────────┘
```

### State 2: Game In Progress
```
┌─────────────────────────────────┐
│  🏀 Warriors vs Lakers          │
│  Q3  5:42 remaining             │
│                                 │
│  GSW  92  vs  88  LAL           │
│   LIVE                          │
│                                 │
│  Q1   Q2   Q3   Q4             │
│  28   30   34   --             │  (GSW)
│  25   27   36   --             │  (LAL)
│                                 │
│  ┌────── Worm Chart ──────┐    │
│  │         /─╲             │    │
│  │      /─/   ╲            │    │
│  │   /─/       ╲─╲         │    │
│  │ ─/             ╲        │    │
│  │ GSW +4                  │    │
│  └─────────────────────────┘    │
│  Q1    Q2    Q3    Q4           │
└─────────────────────────────────┘
```

### State 3: Game Final
```
┌─────────────────────────────────┐
│  🏀 Warriors vs Lakers          │
│  Final                          │
│                                 │
│  GSW  115  vs  108  LAL         │
│   FINAL - GSW Win               │
│                                 │
│  Q1   Q2   Q3   Q4             │
│  28   30   34   23             │  (GSW)
│  25   27   36   20             │  (LAL)
│                                 │
│  ┌────── Worm Chart ──────┐    │
│  │         /─╲    /──╲     │    │
│  │      /─/   ╲  /    ╲─╲  │    │
│  │   /─/       ╲/        ╲ │    │
│  │ ─/                     ╲│    │
│  │ GSW +7                  │    │
│  └─────────────────────────┘    │
│  Q1    Q2    Q3    Q4           │
└─────────────────────────────────┘
```

### State 4: No Game Today
```
┌─────────────────────────────────┐
│  🏀 No game today               │
│                                 │
│  Next game:                     │
│                                 │
│  Warriors vs Celtics            │
│  Tomorrow at 4:00 PM            │
│  @ TD Garden                    │
│                                 │
│                                 │
│                                 │
│                                 │
│                                 │
└─────────────────────────────────┘
```

## Component Breakdown

### 1. Header Section (Always Visible)
- **Game Title**: "Warriors vs [Opponent]" or "No game today"
- **Game Status**: "Today at 7:30 PM" / "Q3 5:42" / "Final"
- **Height**: ~80dp

### 2. Score Section (When game exists)
- **Large Score Display**:
  - Team tricodes: GSW / LAL
  - Scores: Large, bold numbers
  - Records: Small text below tricodes
  - Status badge: "LIVE" (pulsing), "FINAL", or hidden if not started
- **Height**: ~120dp

### 3. Quarter Breakdown (When game started)
- **Simple Grid**:
  ```
  Q1   Q2   Q3   Q4
  28   30   34   --    (GSW - home color)
  25   27   36   --    (LAL - away color)
  ```
- Left-aligned team colors/indicators
- "--" for quarters not yet played
- **Height**: ~80dp

### 4. Worm Chart (When quarters completed)
- **Chart Type**: Line graph showing score differential
- **X-axis**: Game time (labeled by quarters: Q1, Q2, Q3, Q4)
- **Y-axis**: Score differential (-20 to +20, adaptive)
- **Zero line**: Dashed horizontal line at y=0 (tied game)
- **Line color**:
  - Green/gold when GSW ahead
  - Red/gray when GSW behind
  - Or single color with fill above/below zero line
- **Current score differential**: Displayed on chart (e.g., "GSW +7")
- **Interactivity**: None for v1 (just view)
- **Height**: ~200-250dp (takes remaining space)

### 5. Next Game Section (When no game today)
- **Opponent**: Team name
- **Date/Time**: Relative (Tomorrow, Friday, etc.) + time
- **Venue**: Location (optional, if we want to show home/away)
- **Height**: ~150dp

## Visual Design

### Colors
- **Primary**: Warriors gold (#FFC72C)
- **Secondary**: Warriors blue (#1D428A)
- **Background**: Dark (#121212) or Light (#FFFFFF) - follow system theme
- **Text**: High contrast (white on dark, black on light)
- **Accent**:
  - Green for leading (#4CAF50)
  - Red for trailing (#F44336)

### Typography
- **Team names**: 18sp, semi-bold
- **Scores**: 48sp, bold
- **Quarter scores**: 16sp, medium
- **Status text**: 14sp, regular
- **Small text (records, time)**: 12sp, regular

### Spacing
- **Screen padding**: 16dp horizontal, 12dp vertical
- **Section spacing**: 16dp between major sections
- **Component spacing**: 8dp between related elements

### Worm Chart Details

#### Chart Style Option A: Score Differential Line
```
  +20 ┐
      │     ╱─╲
  +10 │  ╱─╯   ╲
      │ ╱        ╲
    0 ├─────────────  (dashed line)
      │          ╲─╮
  -10 │            ╲
  -20 ┘
      Q1  Q2  Q3  Q4
```
- Single line showing GSW score - Opponent score
- Positive = GSW winning
- Negative = GSW losing
- Zero line shows when game is tied

#### Chart Style Option B: Dual Line (Alternative)
```
  120 ┐ ─── GSW
      │    /
  100 │   /
      │  /  --- LAL
   80 │ /
      │/
   60 ┘
      Q1  Q2  Q3  Q4
```
- Two lines showing actual scores
- Visually shows which team is ahead
- More data but potentially cluttered

**Recommendation**: Start with Option A (differential) - cleaner and more focused.

#### Data Points
- One point per scoring event (~200-250 per game)
- Smooth line interpolation between points
- Update chart when new quarter data fetched

## Refresh Behavior

### Pull-to-Refresh
- Swipe down from top to manually refresh
- Shows loading indicator
- Updates score, quarters, and refetches if needed

### Auto-Refresh
- Silent background updates every 60 seconds (when game live)
- No loading indicators (seamless update)
- Update only changed data (score, quarter, chart if new quarter)

### Loading States
- **Initial load**: Centered spinner
- **Refresh**: Small spinner in header
- **Error**: Toast message, retry button

## Animations

### Minimal Animations (Keep it lightweight)
1. **Live badge pulse**: Subtle opacity animation (1.0 → 0.7 → 1.0, 2s cycle)
2. **Score change**: Brief highlight/flash when score updates
3. **Chart draw**: Animate line drawing when first showing chart (300ms)
4. **Quarter transition**: Fade in new quarter score (200ms)

## Error States

### No Network
```
┌─────────────────────────────────┐
│  🏀 Warriors                    │
│                                 │
│  Unable to load game data       │
│                                 │
│  ⚠ No network connection        │
│                                 │
│  [ Retry ]                      │
│                                 │
└─────────────────────────────────┘
```

### API Error
```
┌─────────────────────────────────┐
│  🏀 Warriors                    │
│                                 │
│  Unable to load game data       │
│                                 │
│  ⚠ Something went wrong         │
│                                 │
│  [ Retry ]                      │
│                                 │
└─────────────────────────────────┘
```

### Stale Data
- Show last updated time in small text
- "Updated 5 minutes ago"
- Allow manual refresh

## Implementation Notes

### Worm Chart Library Options
1. **MPAndroidChart**: Popular, feature-rich, but heavy
2. **Vico**: Modern, Compose-friendly, lightweight
3. **Custom Canvas drawing**: Maximum control, minimal dependencies
4. **Jetpack Compose Canvas**: If using Compose

**Recommendation**: Use Vico if Compose, MPAndroidChart if Views (or custom for learning)

### Layout System
- **Single Activity** with Fragment or Composable
- **RecyclerView**: Not needed (single screen, no scrolling list)
- **ConstraintLayout**: Good for Views approach
- **Column + LazyColumn**: Good for Compose approach

### State Management
```kotlin
sealed class GameState {
    object Loading : GameState()
    object NoGameToday : GameState()
    data class GameScheduled(val game: Game) : GameState()
    data class GameLive(val game: Game, val quarters: List<QuarterScore>) : GameState()
    data class GameFinal(
        val game: Game,
        val quarters: List<QuarterScore>,
        val wormData: List<WormPoint>
    ) : GameState()
    data class Error(val message: String) : GameState()
}
```

### Responsive Design
- Support portrait mode primarily
- Landscape: Optional (same layout, wider chart)
- Tablet: Same layout, larger text sizes
- Different screen sizes: Use dp properly, no hardcoded sizes

## Future Enhancements (Not for v1)
- [ ] Tap chart to see score at specific time
- [ ] Swipe to see past games
- [ ] Settings screen (WiFi-only, notifications)
- [ ] Widget support
- [ ] Game highlights/key plays below chart
- [ ] Share game result as image
- [ ] Dark/light theme toggle

## Final Design Decisions

1. **Worm chart color**: ✅ Green line when GSW ahead, red when behind
2. **Quarter scores alignment**: ✅ Horizontal grid
3. **Technology**: ✅ Jetpack Compose with Material 3
4. **Build system**: ✅ Gradle with CLI buildable (no Android Studio required)
5. **Target SDK**: API 34, Minimum SDK: API 26
