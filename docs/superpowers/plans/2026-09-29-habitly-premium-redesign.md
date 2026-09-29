# Habitly premium redesign ("Dark grid") — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild every Habitly screen on a new dark-first "grid" design system, add the flows the CA1 spec promised but never shipped, and move to four-tab navigation — without breaking the existing domain logic.

**Architecture:** Design tokens and a stateless component kit come first (`ui/theme`, `ui/components`), then each screen is rebuilt on the kit one at a time, each ending in a green build + tests + emulator check. Pure logic (tones, achievements, CSV, snapshots) lives in `domain/` or `data/` with JVM unit tests; composables are verified on the emulator.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.09.00) + Material 3, Navigation-Compose 2.8, Room 2.6.1, DataStore, WorkManager, Robolectric 4.13 + JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-29-habitly-premium-redesign-design.md` — read the section referenced by each task before starting it. Colour hex values, type sizes, radii and screen layouts are defined there and are not repeated here unless a task needs them verbatim.

## Global Constraints

- minSdk 26, compile/target SDK 35, JVM 17 — unchanged.
- Default theme is **Dark**; Light and System remain selectable.
- All colours come from `MaterialTheme.colorScheme`, `LocalHabitlyColors.current`, or `HabitTones.of(...)` — no raw hex literals in screen files.
- Typography only through `MaterialTheme.typography` roles mapped in Task 2 — no ad-hoc `fontSize` in screens.
- Touch targets ≥ 48dp; icon-only buttons have `contentDescription`; state never colour-only.
- Non-essential motion (confetti, count-up, bounce) is skipped when `LocalReduceMotion.current == true`.
- No new third-party libraries except `androidx.core:core-splashscreen:1.0.1`.
- Existing domain behaviour (streaks, schedules, skip/rest, goals, InsightEngine) must not change; its tests stay green.
- Commands run from the repo root `D:/04_College/Assignments/mobile application activity 1`:
  - Build: `./gradlew :app:assembleDebug -q`
  - All unit tests: `./gradlew :app:testDebugUnitTest -q`
  - One class: `./gradlew :app:testDebugUnitTest --tests "com.habitly.app.domain.HabitTonesTest" -q`
  - Install + launch: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk && $LOCALAPPDATA/Android/Sdk/platform-tools/adb shell am start -n com.habitly.app/.MainActivity`
- Baseline before Task 1: 177 unit tests, 0 failures.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## File map

```
app/src/main/res/font/                         NEW  plus_jakarta_sans_{regular,medium,semibold,bold,extrabold}.ttf
app/src/main/res/values/themes.xml             MOD  Theme.Habitly.Starting (splash)
app/src/main/res/drawable/ic_habitly_mark.xml  NEW  3x3 grid vector mark
app/src/main/res/xml/file_paths.xml            NEW  FileProvider cache path (CSV export)

ui/theme/Color.kt            REPLACE  palette constants (spec §3.1)
ui/theme/HabitlyColors.kt    NEW      extra roles + LocalHabitlyColors
ui/theme/HabitTones.kt       NEW      HabitPalette seeds + HabitTones.of()
ui/theme/Type.kt             REPLACE  Plus Jakarta Sans scale (spec §3.3)
ui/theme/Theme.kt            REPLACE  schemes, LocalReduceMotion
ui/theme/Contrast.kt         KEEP

ui/components/               NEW kit: IconTile, CheckButton, HabitGrid, HabitGridCard, HabitRow, StatTile(replaced),
                             HeroCard, SegmentedPill, SettingsGroup/SettingsRow, SectionLabel, Buttons,
                             HabitlyNavBar, HabitlySheet, EmptyState, ConfirmSheet, Celebration, ScreenHeader(replaced),
                             MonthGrid, WeekStrip(restyled), HabitIcon(expanded)
ui/navigation/               Destinations(4 tabs), HabitlyScaffold, HabitlyNavHost, ViewModelWiring.kt NEW

ui/today/        (renamed from ui/home)   TodayScreen, TodayViewModel, TodayUiState, TemplateSheet
ui/habits/       (renamed from ui/allhabits) HabitsScreen, HabitsViewModel, HabitsUiState
ui/detail/       HabitDetailScreen (rebuilt), ViewModel additions
ui/addedit/      AddEditHabitScreen (rebuilt), IconPickerSheet, GoalSheet, ViewModel additions
ui/insights/     NEW InsightsScreen (segment host), ui/stats + ui/calendar restyled, DaySheet
ui/you/          NEW YouScreen, YouViewModel
ui/achievements/ AchievementsScreen (rebuilt), BadgeSheet, BadgeCelebration
ui/settings/     SettingsScreen (rebuilt), ViewModel additions
ui/reminders/    NEW RemindersScreen, RemindersViewModel
ui/onboarding/   OnboardingScreen (rebuilt), OnboardingViewModel NEW
ui/splash/       DELETED (system splash)

domain/AchievementEvaluator.kt   REPLACE 16 badges + progress
domain/AchievementSnapshot.kt    NEW     snapshot builder (pure)
data/export/CsvExporter.kt       NEW
data/local/*                     MIGRATION_3_4, sortOrder, DAO queries
data/prefs/SettingsPrefs.kt      MOD     new prefs
data/repository/*                MOD     archive/restore/reorder/reminder/export/achievement progress
```

---

## Phase 1 — Foundation

### Task 1: Colour tokens, habit tones, theme defaults

**Files:**
- Replace: `app/src/main/java/com/habitly/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/habitly/app/ui/theme/HabitlyColors.kt`
- Create: `app/src/main/java/com/habitly/app/ui/theme/HabitTones.kt`
- Replace: `app/src/main/java/com/habitly/app/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/habitly/app/ui/theme/HabitTonesTest.kt`

**Interfaces:**
- Produces:
  - `object HabitPalette { val seeds: List<String>; fun nearestSeed(hex: String): String }`
  - `data class HabitTones(val full: Color, val mid: Color, val low: Color, val onFull: Color)`
  - `object HabitTonesFactory { fun of(hex: String, isDark: Boolean): HabitTones }` plus `@Composable fun habitTones(hex: String): HabitTones` (reads `LocalHabitlyColors.current.isDark`)
  - `data class HabitlyColors(isDark, surfaceRaised, gridEmpty, onSurfaceFaint, heroContainer, success, warning, primaryButton, onPrimaryButton)` and `val LocalHabitlyColors`
  - `val LocalReduceMotion: ProvidableCompositionLocal<Boolean>`
  - `fun hexToColor(hex: String): Color` (pure Kotlin, JVM-safe)
  - `HabitlyTheme(darkTheme: Boolean, content)` unchanged signature

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habitly.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTonesTest {

    private val darkCard = Color(0xFF17191E)
    private val lightCard = Color(0xFFFFFFFF)

    @Test
    fun `there are twelve palette seeds`() {
        assertEquals(12, HabitPalette.seeds.size)
    }

    @Test
    fun `every seed's full tone is a visible graphic on the dark card`() {
        HabitPalette.seeds.forEach { seed ->
            val tones = HabitTonesFactory.of(seed, isDark = true)
            assertTrue("$seed full on dark", contrastRatio(tones.full, darkCard) >= 3f)
        }
    }

    @Test
    fun `every seed's full tone is a visible graphic on the light card`() {
        HabitPalette.seeds.forEach { seed ->
            val tones = HabitTonesFactory.of(seed, isDark = false)
            assertTrue("$seed full on light", contrastRatio(tones.full, lightCard) >= 3f)
        }
    }

    @Test
    fun `the check glyph is legible on the full tone in both themes`() {
        HabitPalette.seeds.forEach { seed ->
            listOf(true, false).forEach { dark ->
                val t = HabitTonesFactory.of(seed, dark)
                assertTrue("$seed onFull dark=$dark", contrastRatio(t.onFull, t.full) >= 3f)
            }
        }
    }

    @Test
    fun `the three fill tones are distinct steps`() {
        HabitPalette.seeds.forEach { seed ->
            listOf(true, false).forEach { dark ->
                val t = HabitTonesFactory.of(seed, dark)
                assertNotEquals(t.full, t.mid)
                assertNotEquals(t.mid, t.low)
            }
        }
    }

    @Test
    fun `dark tones step down in luminance`() {
        HabitPalette.seeds.forEach { seed ->
            val t = HabitTonesFactory.of(seed, isDark = true)
            assertTrue(t.full.luminanceValue() > t.mid.luminanceValue())
            assertTrue(t.mid.luminanceValue() > t.low.luminanceValue())
        }
    }

    @Test
    fun `legacy colours map to the nearest seed by hue`() {
        assertEquals("#22B8CF", HabitPalette.nearestSeed("#1098AD")) // old teal -> cyan
        assertEquals("#FF6B6B", HabitPalette.nearestSeed("#F76C5E")) // old coral -> red
        assertEquals("#CED4DA", HabitPalette.nearestSeed("#495057")) // grey stays grey
        assertEquals("#FF922B", HabitPalette.nearestSeed("#FF922B")) // a seed maps to itself
    }

    @Test
    fun `hexToColor parses without android framework`() {
        assertEquals(Color(0xFF4DABF7), hexToColor("#4DABF7"))
    }
}

private fun Color.luminanceValue() = androidx.compose.ui.graphics.luminance(this)
```

Note: if `androidx.compose.ui.graphics.luminance` is an extension (`Color.luminance()`), write `private fun Color.luminanceValue() = this.luminance()` with `import androidx.compose.ui.graphics.luminance`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.habitly.app.ui.theme.HabitTonesTest" -q`
Expected: FAIL — unresolved reference `HabitPalette`.

- [ ] **Step 3: Replace `Color.kt`**

```kotlin
package com.habitly.app.ui.theme

import androidx.compose.ui.graphics.Color

// Spec §3.1 — neutral canvas; colour comes from the habits.
val DarkBackground = Color(0xFF0E0F12)
val DarkSurface = Color(0xFF17191E)
val DarkSurfaceRaised = Color(0xFF22252C)
val DarkOutline = Color(0xFF2A2E36)
val DarkGridEmpty = Color(0xFF262930)
val DarkOnSurface = Color(0xFFF2F3F5)
val DarkOnSurfaceMuted = Color(0xFF8B919C)
val DarkOnSurfaceFaint = Color(0xFF6B717C)
val DarkBrand = Color(0xFF9B8CFF)
val DarkBrandContainer = Color(0xFF2A2640)
val DarkHeroContainer = Color(0xFF1D1A33)
val DarkSuccess = Color(0xFF51CF66)
val DarkDanger = Color(0xFFFF6B6B)
val DarkWarning = Color(0xFFFCC419)

val LightBackground = Color(0xFFF4F5F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceRaised = Color(0xFFECEEF2)
val LightOutline = Color(0xFFDADDE3)
val LightGridEmpty = Color(0xFFE6E8EC)
val LightOnSurface = Color(0xFF111317)
val LightOnSurfaceMuted = Color(0xFF6B7280)
val LightOnSurfaceFaint = Color(0xFF8A909A)
val LightBrand = Color(0xFF6A55F0)
val LightBrandContainer = Color(0xFFECE8FF)
val LightHeroContainer = Color(0xFFECE8FF)
val LightSuccess = Color(0xFF2F9E44)
val LightDanger = Color(0xFFE03131)
val LightWarning = Color(0xFFE67700)
```

Then fix compile errors in files that referenced removed constants (`SageGreen`, `HeroDeep`, `HeroMid`, `StreakFlame`, `CoralAccent`, …): run `grep -rn "SageGreen\|HeroDeep\|HeroMid\|StreakFlame\|CoralAccent\|SageGreenLight\|SageGreenPale" app/src/main` and replace each use with the nearest role (`MaterialTheme.colorScheme.primary`, `LocalHabitlyColors.current.heroContainer`, `LocalHabitlyColors.current.warning`). These screens are rebuilt later; the goal now is only that they compile.

- [ ] **Step 4: Create `HabitlyColors.kt`**

```kotlin
package com.habitly.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Roles Material 3 has no slot for. Read with `LocalHabitlyColors.current`. */
@Immutable
data class HabitlyColors(
    val isDark: Boolean,
    val surfaceRaised: Color,
    val gridEmpty: Color,
    val onSurfaceFaint: Color,
    val heroContainer: Color,
    val success: Color,
    val warning: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
)

val DarkHabitlyColors = HabitlyColors(
    isDark = true,
    surfaceRaised = DarkSurfaceRaised,
    gridEmpty = DarkGridEmpty,
    onSurfaceFaint = DarkOnSurfaceFaint,
    heroContainer = DarkHeroContainer,
    success = DarkSuccess,
    warning = DarkWarning,
    primaryButton = DarkOnSurface,
    onPrimaryButton = DarkBackground,
)

val LightHabitlyColors = HabitlyColors(
    isDark = false,
    surfaceRaised = LightSurfaceRaised,
    gridEmpty = LightGridEmpty,
    onSurfaceFaint = LightOnSurfaceFaint,
    heroContainer = LightHeroContainer,
    success = LightSuccess,
    warning = LightWarning,
    primaryButton = LightOnSurface,
    onPrimaryButton = LightSurface,
)

val LocalHabitlyColors = staticCompositionLocalOf { DarkHabitlyColors }

/** True when the system animator scale is 0 — skip confetti, count-ups and bounces. */
val LocalReduceMotion = staticCompositionLocalOf { false }
```

- [ ] **Step 5: Create `HabitTones.kt`**

```kotlin
package com.habitly.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Parses "#RRGGBB" in pure Kotlin — android.graphics.Color returns 0 under JVM tests. */
fun hexToColor(hex: String): Color {
    val rgb = hex.removePrefix("#").toLong(16)
    return Color(0xFF000000L or rgb)
}

object HabitPalette {
    val seeds = listOf(
        "#FF922B", "#4DABF7", "#9775FA", "#51CF66", "#FF6B6B", "#FCC419",
        "#22B8CF", "#F06595", "#94D82D", "#748FFC", "#FF8787", "#CED4DA",
    )
    private const val GREY = "#CED4DA"

    /** Colours saved before the redesign are shown as the closest seed by hue; greys stay grey. */
    fun nearestSeed(hex: String): String {
        val upper = hex.uppercase()
        if (upper in seeds) return upper
        val (h, s, _) = hsl(hexToColor(hex))
        if (s < 0.18f) return GREY
        return seeds.filter { it != GREY && it != "#FF8787" }
            .minBy { seed -> hueDistance(h, hsl(hexToColor(seed)).first) }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}

@Immutable
data class HabitTones(val full: Color, val mid: Color, val low: Color, val onFull: Color)

object HabitTonesFactory {
    private val ink = Color(0xFF0E0F12)
    private val white = Color.White

    /** Spec §3.2 — lightness is varied, never alpha, so every step stays perceivable. */
    fun of(hex: String, isDark: Boolean): HabitTones {
        val seed = hexToColor(HabitPalette.nearestSeed(hex))
        return if (isDark) {
            val (h, s, l) = hsl(seed)
            HabitTones(
                full = seed,
                mid = hslToColor(h, s, l * 0.72f),
                low = hslToColor(h, s * 0.75f, l * 0.36f),
                onFull = ink,
            )
        } else {
            val full = seed.readableOn(white, target = 3f)
            HabitTones(
                full = full,
                mid = lerp(white, full, 0.6f),
                low = lerp(white, full, 0.25f),
                onFull = if (contrastRatio(white, full) >= 3f) white else LightOnSurface,
            )
        }
    }
}

@Composable
fun habitTones(hex: String): HabitTones = HabitTonesFactory.of(hex, LocalHabitlyColors.current.isDark)

internal fun hsl(c: Color): Triple<Float, Float, Float> {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    if (mx == mn) return Triple(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return Triple(h, s, l)
}

internal fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        (r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f),
    )
}
```

- [ ] **Step 6: Replace `Theme.kt`**

```kotlin
package com.habitly.app.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Every role the app reads is set explicitly — unset roles fall back to Material's purple baseline.
private val DarkScheme = darkColorScheme(
    primary = DarkBrand, onPrimary = DarkBackground,
    primaryContainer = DarkBrandContainer, onPrimaryContainer = DarkBrand,
    secondary = DarkOnSurfaceMuted, onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceRaised, onSecondaryContainer = DarkOnSurface,
    tertiary = DarkWarning, onTertiary = DarkBackground,
    tertiaryContainer = DarkSurfaceRaised, onTertiaryContainer = DarkWarning,
    background = DarkBackground, onBackground = DarkOnSurface,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceRaised, onSurfaceVariant = DarkOnSurfaceMuted,
    surfaceContainer = DarkSurface, surfaceContainerHigh = DarkSurfaceRaised,
    surfaceContainerHighest = DarkSurfaceRaised, surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkBackground,
    inverseSurface = DarkOnSurface, inverseOnSurface = DarkBackground,
    outline = DarkOutline, outlineVariant = DarkOutline,
    error = DarkDanger, onError = DarkBackground,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = LightBrand, onPrimary = LightSurface,
    primaryContainer = LightBrandContainer, onPrimaryContainer = LightBrand,
    secondary = LightOnSurfaceMuted, onSecondary = LightSurface,
    secondaryContainer = LightSurfaceRaised, onSecondaryContainer = LightOnSurface,
    tertiary = LightWarning, onTertiary = LightSurface,
    tertiaryContainer = LightSurfaceRaised, onTertiaryContainer = LightWarning,
    background = LightBackground, onBackground = LightOnSurface,
    surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceRaised, onSurfaceVariant = LightOnSurfaceMuted,
    surfaceContainer = LightSurface, surfaceContainerHigh = LightSurfaceRaised,
    surfaceContainerHighest = LightSurfaceRaised, surfaceContainerLow = LightSurface,
    surfaceContainerLowest = LightBackground,
    inverseSurface = LightOnSurface, inverseOnSurface = LightSurface,
    outline = LightOutline, outlineVariant = LightOutline,
    error = LightDanger, onError = LightSurface,
    scrim = Color.Black,
)

@Composable
fun HabitlyTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalHabitlyColors provides if (darkTheme) DarkHabitlyColors else LightHabitlyColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = HabitlyTypography,
            shapes = HabitlyShapes,
            content = content,
        )
    }
}
```

Add to `Type.kt` for now (Task 2 replaces the typography, keep the shapes):

```kotlin
val HabitlyShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)
```

- [ ] **Step 7: Make Dark the default theme mode**

In `data/prefs/SettingsPrefs.kt` change the fallback `?: ThemeMode.SYSTEM` to `?: ThemeMode.DARK`. In `MainActivity.kt` change `collectAsState(initial = ThemeMode.SYSTEM)` to `initial = ThemeMode.DARK`. Update any assertion of the SYSTEM default in `app/src/test/java/com/habitly/app/data/prefs/SettingsPrefsTest.kt` and `ui/settings/SettingsViewModelTest.kt` to `ThemeMode.DARK`.

- [ ] **Step 8: Run tests**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: PASS — `HabitTonesTest` green, 177 existing tests green (ContrastTest may reference `HABIT_COLOR_SWATCHES`, which still exists).

- [ ] **Step 9: Commit**

```bash
git add app/src
git commit -m "feat(theme): dark-grid colour tokens, habit tones and dark default

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Typography with Plus Jakarta Sans

**Files:**
- Create: `app/src/main/res/font/plus_jakarta_sans_regular.ttf`, `..._medium.ttf`, `..._semibold.ttf`, `..._bold.ttf`, `..._extrabold.ttf`
- Replace: `app/src/main/java/com/habitly/app/ui/theme/Type.kt` (keep `HabitlyShapes` from Task 1)
- Create: `app/src/main/assets/licenses/OFL-PlusJakartaSans.txt`

**Interfaces:**
- Produces: `val PlusJakartaSans: FontFamily`; `HabitlyTypography` mapping —
  `displaySmall`=display, `headlineMedium`=headline, `titleLarge`=title, `titleMedium`=titleSmall,
  `bodyLarge`=body, `bodyMedium`=bodySmall, `labelMedium`=label, `headlineSmall`=stat,
  `labelLarge` = 14/20 600 (buttons), `bodySmall` = 12/16 500, `labelSmall` = 11/14 600.

- [ ] **Step 1: Download fonts (user approved 5 × ~129 KB files from github.com/tokotype/PlusJakartaSans, OFL 1.1)**

```bash
for w in Regular:regular Medium:medium SemiBold:semibold Bold:bold ExtraBold:extrabold; do
  src=${w%%:*}; dst=${w##*:}
  curl -sSL -o "app/src/main/res/font/plus_jakarta_sans_$dst.ttf" \
    "https://raw.githubusercontent.com/tokotype/PlusJakartaSans/master/fonts/ttf/PlusJakartaSans-$src.ttf"
done
curl -sSL -o app/src/main/assets/licenses/OFL-PlusJakartaSans.txt \
  "https://raw.githubusercontent.com/tokotype/PlusJakartaSans/master/OFL.txt"
ls -la app/src/main/res/font
```

Expected: five files of ~129 KB each. (Create `app/src/main/assets/licenses/` first with `mkdir -p`.)

- [ ] **Step 2: Replace `Type.kt`**

```kotlin
package com.habitly.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitly.app.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

// Spec §3.3. Screens use these roles only.
val HabitlyTypography = Typography(
    displayLarge = style(40, 46, FontWeight.ExtraBold),
    displayMedium = style(36, 42, FontWeight.ExtraBold),
    displaySmall = style(32, 38, FontWeight.ExtraBold),          // display
    headlineLarge = style(28, 34, FontWeight.ExtraBold),
    headlineMedium = style(26, 32, FontWeight.ExtraBold),        // headline — screen titles
    headlineSmall = style(22, 26, FontWeight.ExtraBold).copy(    // stat — tile numbers
        fontFeatureSettings = "tnum",
    ),
    titleLarge = style(20, 26, FontWeight.Bold),                 // title
    titleMedium = style(16, 22, FontWeight.Bold),                // titleSmall — card titles
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(15, 22, FontWeight.Medium),                // body
    bodyMedium = style(13, 18, FontWeight.Medium),               // bodySmall — metadata
    bodySmall = style(12, 16, FontWeight.Medium),
    labelLarge = style(14, 20, FontWeight.SemiBold),             // buttons
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.6f),      // label — section labels
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.4f),
)

val HabitlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Emulator check** — install, launch; confirm every screen now renders in the new font on the dark background (layouts are still old; that is expected).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/font app/src/main/assets app/src/main/java/com/habitly/app/ui/theme/Type.kt
git commit -m "feat(theme): bundle Plus Jakarta Sans and the new type scale

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Data layer — sortOrder migration, archive/reorder/reminder APIs, new prefs

**Files:**
- Modify: `data/local/HabitEntity.kt`, `data/local/HabitDao.kt`, `data/local/AppDatabase.kt`
- Modify: `data/repository/HabitDataSource.kt`, `data/repository/HabitRepository.kt`
- Modify: `data/prefs/SettingsPrefs.kt`
- Modify: every test fake implementing `HabitDataSource` (find with `grep -rln "HabitDataSource" app/src/test`)
- Test: `app/src/test/java/com/habitly/app/data/local/Migration3To4Test.kt`, additions to `HabitDaoTest.kt`, `HabitRepositoryTest.kt`, `SettingsPrefsTest.kt`

**Interfaces:**
- Produces:
  - `HabitEntity.sortOrder: Int = 0` (last constructor param, defaulted)
  - `HabitDao.observeActiveHabits()` ordered `sortOrder ASC, createdAt ASC`; `observeArchivedHabits(): Flow<List<HabitEntity>>`; `updateSortOrder(id: Long, sortOrder: Int)`; `maxSortOrder(): Int?`
  - `val MIGRATION_3_4`; DB version 4
  - `HabitDataSource` additions:
    `fun observeArchivedHabits(): Flow<List<HabitEntity>>`,
    `suspend fun setArchived(habitId: Long, archived: Boolean)`,
    `suspend fun reorder(orderedIds: List<Long>)`,
    `suspend fun setReminder(habitId: Long, time: String?)`,
    `suspend fun getHabit(habitId: Long): HabitEntity?`
  - `addHabit` assigns `sortOrder = (maxSortOrder() ?: 0) + 1` when the passed value is 0
  - `SettingsPrefs`: `userName: Flow<String?>`, `setUserName(String?)`; `weekStart: Flow<DayOfWeek>` (default MONDAY), `setWeekStart(DayOfWeek)`; `streakNudgeEnabled: Flow<Boolean>` (default true), `setStreakNudgeEnabled(Boolean)`; `lastCelebratedDate: Flow<String?>`, `setLastCelebratedDate(String)`; `seenBadgeKeys: Flow<Set<String>>`, `markBadgesSeen(Set<String>)`

- [ ] **Step 1: Write the failing migration test**

```kotlin
package com.habitly.app.data.local

import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {

    @Test
    fun migrationAddsSortOrderSeededFromId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        // Build a v3 schema by hand: the columns HabitEntity had before this change.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "name TEXT NOT NULL, icon TEXT NOT NULL, colorHex TEXT NOT NULL, " +
                                "category TEXT NOT NULL, frequencyType TEXT NOT NULL, frequencyDays TEXT, " +
                                "intervalDays INTEGER, reminderTime TEXT, createdAt INTEGER NOT NULL, " +
                                "isArchived INTEGER NOT NULL, targetCount INTEGER, unit TEXT)"
                        )
                        db.execSQL(
                            "CREATE TABLE habit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "habitId INTEGER NOT NULL, date TEXT NOT NULL, status TEXT NOT NULL, " +
                                "completedAt INTEGER, note TEXT, progress INTEGER NOT NULL DEFAULT 0, " +
                                "FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                        )
                        db.execSQL("CREATE INDEX index_habit_logs_habitId ON habit_logs (habitId)")
                        db.execSQL("CREATE UNIQUE INDEX index_habit_logs_habitId_date ON habit_logs (habitId, date)")
                        db.execSQL(
                            "CREATE TABLE achievements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "habitId INTEGER, type TEXT NOT NULL, unlockedAt INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO habits (id,name,icon,colorHex,category,frequencyType,createdAt,isArchived) " +
                                "VALUES (7,'Read','menu_book','#FF922B','Mind','DAILY',1000,0)"
                        )
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, o: Int, n: Int) = Unit
                }).build()
        )
        helper.writableDatabase.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val habit = db.habitDao().observeActiveHabits().first().single()
        assertEquals(7, habit.sortOrder)
        db.close()
        context.deleteDatabase(name)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.habitly.app.data.local.Migration3To4Test" -q`
Expected: FAIL — unresolved `MIGRATION_3_4` / `sortOrder`.

- [ ] **Step 3: Implement entity, DAO, migration**

`HabitEntity.kt` — append after `unit`:
```kotlin
    /** Position in the user's custom order; lower comes first. */
    val sortOrder: Int = 0,
```

`HabitDao.kt` — replace the active query and add:
```kotlin
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeArchivedHabits(): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("SELECT MAX(sortOrder) FROM habits")
    suspend fun maxSortOrder(): Int?
```

`AppDatabase.kt` — add, bump `version = 4`, add to `addMigrations(...)`:
```kotlin
/** Habits gain a user-controlled order; existing habits keep their creation order. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `habits` SET `sortOrder` = `id`")
    }
}
```

- [ ] **Step 4: Run migration test** — Expected: PASS.

- [ ] **Step 5: Write failing repository tests** (append to `HabitRepositoryTest.kt`, reusing its in-memory `db`/`repo` setup and its habit factory; if the factory is named differently, adapt the call):

```kotlin
    @Test
    fun archivedHabitsLeaveTheActiveListAndCanBeRestored() = runBlocking {
        val id = repo.addHabit(sampleHabit("Read"))
        repo.setArchived(id, true)
        assertTrue(repo.observeHabitsWithStats(LocalDate.of(2026, 9, 29)).first().none { it.habit.id == id })
        assertEquals(listOf(id), repo.observeArchivedHabits().first().map { it.id })
        repo.setArchived(id, false)
        assertTrue(repo.observeArchivedHabits().first().isEmpty())
    }

    @Test
    fun reorderPersistsTheGivenOrder() = runBlocking {
        val a = repo.addHabit(sampleHabit("A"))
        val b = repo.addHabit(sampleHabit("B"))
        val c = repo.addHabit(sampleHabit("C"))
        repo.reorder(listOf(c, a, b))
        val names = repo.observeHabitsWithStats(LocalDate.of(2026, 9, 29)).first().map { it.habit.name }
        assertEquals(listOf("C", "A", "B"), names)
    }

    @Test
    fun newHabitsAreAppendedToTheEnd() = runBlocking {
        repo.addHabit(sampleHabit("A"))
        repo.addHabit(sampleHabit("B"))
        val orders = repo.observeHabitsWithStats(LocalDate.of(2026, 9, 29)).first().map { it.habit.sortOrder }
        assertEquals(listOf(1, 2), orders)
    }

    @Test
    fun setReminderUpdatesOnlyTheTime() = runBlocking {
        val id = repo.addHabit(sampleHabit("Read"))
        repo.setReminder(id, "21:30")
        assertEquals("21:30", repo.getHabit(id)?.reminderTime)
        repo.setReminder(id, null)
        assertNull(repo.getHabit(id)?.reminderTime)
    }
```

- [ ] **Step 6: Implement repository additions**

`HabitDataSource.kt` — add the five signatures from Interfaces. `HabitRepository.kt`:
```kotlin
    override suspend fun addHabit(habit: HabitEntity): Long {
        val order = if (habit.sortOrder != 0) habit.sortOrder else (habitDao.maxSortOrder() ?: 0) + 1
        return habitDao.insert(habit.copy(sortOrder = order))
    }

    override fun observeArchivedHabits(): Flow<List<HabitEntity>> = habitDao.observeArchivedHabits()

    override suspend fun setArchived(habitId: Long, archived: Boolean) = habitDao.setArchived(habitId, archived)

    override suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> habitDao.updateSortOrder(id, index + 1) }
    }

    override suspend fun setReminder(habitId: Long, time: String?) {
        val habit = habitDao.getById(habitId) ?: return
        habitDao.update(habit.copy(reminderTime = time))
    }

    override suspend fun getHabit(habitId: Long): HabitEntity? = habitDao.getById(habitId)
```
Add no-op/in-memory implementations of the five methods to every test fake of `HabitDataSource`.

- [ ] **Step 7: Write failing prefs tests** (append to `SettingsPrefsTest.kt`, same context setup):

```kotlin
    @Test
    fun newPreferencesHaveSensibleDefaults() = runBlocking {
        assertNull(prefs.userName.first())
        assertEquals(java.time.DayOfWeek.MONDAY, prefs.weekStart.first())
        assertTrue(prefs.streakNudgeEnabled.first())
        assertNull(prefs.lastCelebratedDate.first())
        assertTrue(prefs.seenBadgeKeys.first().isEmpty())
    }

    @Test
    fun newPreferencesRoundTrip() = runBlocking {
        prefs.setUserName("Sunny")
        prefs.setWeekStart(java.time.DayOfWeek.SUNDAY)
        prefs.setStreakNudgeEnabled(false)
        prefs.setLastCelebratedDate("2026-09-29")
        prefs.markBadgesSeen(setOf("STREAK_7"))
        prefs.markBadgesSeen(setOf("FIRST_HABIT"))
        assertEquals("Sunny", prefs.userName.first())
        assertEquals(java.time.DayOfWeek.SUNDAY, prefs.weekStart.first())
        assertFalse(prefs.streakNudgeEnabled.first())
        assertEquals("2026-09-29", prefs.lastCelebratedDate.first())
        assertEquals(setOf("STREAK_7", "FIRST_HABIT"), prefs.seenBadgeKeys.first())
    }

    @Test
    fun blankNameIsStoredAsNoName() = runBlocking {
        prefs.setUserName("   ")
        assertNull(prefs.userName.first())
    }
```

- [ ] **Step 8: Implement prefs**

```kotlin
private val USER_NAME_KEY = stringPreferencesKey("user_name")
private val WEEK_START_KEY = stringPreferencesKey("week_start")
private val STREAK_NUDGE_KEY = booleanPreferencesKey("streak_nudge_enabled")
private val LAST_CELEBRATED_KEY = stringPreferencesKey("last_celebrated_date")
private val SEEN_BADGES_KEY = stringSetPreferencesKey("seen_badge_keys")

    val userName = context.habitlyDataStore.data.map { it[USER_NAME_KEY] }
    val weekStart = context.habitlyDataStore.data.map { prefs ->
        prefs[WEEK_START_KEY]?.let { runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull() }
            ?: java.time.DayOfWeek.MONDAY
    }
    val streakNudgeEnabled = context.habitlyDataStore.data.map { it[STREAK_NUDGE_KEY] ?: true }
    val lastCelebratedDate = context.habitlyDataStore.data.map { it[LAST_CELEBRATED_KEY] }
    val seenBadgeKeys = context.habitlyDataStore.data.map { it[SEEN_BADGES_KEY] ?: emptySet() }

    suspend fun setUserName(name: String?) {
        context.habitlyDataStore.edit { prefs ->
            val trimmed = name?.trim()
            if (trimmed.isNullOrEmpty()) prefs.remove(USER_NAME_KEY) else prefs[USER_NAME_KEY] = trimmed
        }
    }
    suspend fun setWeekStart(day: java.time.DayOfWeek) {
        context.habitlyDataStore.edit { it[WEEK_START_KEY] = day.name }
    }
    suspend fun setStreakNudgeEnabled(enabled: Boolean) {
        context.habitlyDataStore.edit { it[STREAK_NUDGE_KEY] = enabled }
    }
    suspend fun setLastCelebratedDate(date: String) {
        context.habitlyDataStore.edit { it[LAST_CELEBRATED_KEY] = date }
    }
    suspend fun markBadgesSeen(keys: Set<String>) {
        context.habitlyDataStore.edit { it[SEEN_BADGES_KEY] = (it[SEEN_BADGES_KEY] ?: emptySet()) + keys }
    }
```
(import `androidx.datastore.preferences.core.stringSetPreferencesKey`)

- [ ] **Step 9: Run all tests** — `./gradlew :app:testDebugUnitTest -q` → PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src
git commit -m "feat(data): habit sort order, archive/restore/reorder/reminder APIs and new prefs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: System splash screen

**Files:**
- Modify: `app/build.gradle.kts` (add `implementation("androidx.core:core-splashscreen:1.0.1")`)
- Create: `app/src/main/res/drawable/ic_habitly_mark.xml`
- Modify: `app/src/main/res/values/themes.xml`, `app/src/main/AndroidManifest.xml`
- Modify: `MainActivity.kt`, `ui/navigation/HabitlyNavHost.kt`, `ui/navigation/Destinations.kt`
- Delete: `ui/splash/SplashScreen.kt`

**Interfaces:**
- Produces: `MainActivity` decides the start destination itself (`Destination.Onboarding.route` or `Destination.Today.route` — Task 7 renames Home→Today; until then use `Destination.Home.route`). `Destination.Splash` is removed.

- [ ] **Step 1: Mark drawable** — a 108dp adaptive-safe vector: nine 14dp rounded squares (3×3, 6dp gaps) centred in a 108 viewport; cells alternate `#9B8CFF` (full) and `#3A3366` (dim) in the pattern full,dim,full / dim,full,full / full,full,dim.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#9B8CFF" android:pathData="M31,31h14v14h-14z"/>
    <path android:fillColor="#3A3366" android:pathData="M47,31h14v14h-14z"/>
    <path android:fillColor="#9B8CFF" android:pathData="M63,31h14v14h-14z"/>
    <path android:fillColor="#3A3366" android:pathData="M31,47h14v14h-14z"/>
    <path android:fillColor="#9B8CFF" android:pathData="M47,47h14v14h-14z"/>
    <path android:fillColor="#9B8CFF" android:pathData="M63,47h14v14h-14z"/>
    <path android:fillColor="#9B8CFF" android:pathData="M31,63h14v14h-14z"/>
    <path android:fillColor="#9B8CFF" android:pathData="M47,63h14v14h-14z"/>
    <path android:fillColor="#3A3366" android:pathData="M63,63h14v14h-14z"/>
</vector>
```

- [ ] **Step 2: Theme** — in `themes.xml` add (keep the existing app theme as `postSplashScreenTheme`; read its name from the manifest):

```xml
<style name="Theme.Habitly.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#0E0F12</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_habitly_mark</item>
    <item name="postSplashScreenTheme">@style/EXISTING_APP_THEME_NAME</item>
</style>
```
Set `android:theme="@style/Theme.Habitly.Starting"` on the `<activity>` in the manifest. Also set the existing app theme's `android:windowBackground` to `#0E0F12` so there is no white flash.

- [ ] **Step 3: MainActivity**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val splash = installSplashScreen()
    super.onCreate(savedInstanceState)
    var startRoute: String? = null
    splash.setKeepOnScreenCondition { startRoute == null }
    lifecycleScope.launch {
        val done = OnboardingPrefs(this@MainActivity).hasCompletedOnboarding.first()
        startRoute = if (done) Destination.Home.route else Destination.Onboarding.route
    }
    enableEdgeToEdge()
    setContent {
        val route = startRoute ?: return@setContent
        // …existing theme + scaffold + NavHost, with startDestination = route
    }
}
```
Because `setContent` must observe `startRoute`, hold it in `mutableStateOf<String?>(null)` rather than a plain `var`, and render nothing while it is null.

- [ ] **Step 4: Remove the Splash route** from `Destinations.kt` and `HabitlyNavHost.kt`; delete `ui/splash/SplashScreen.kt`.

- [ ] **Step 5: Build, test, emulator** — `./gradlew :app:assembleDebug -q && ./gradlew :app:testDebugUnitTest -q`; cold-launch on the emulator (`adb shell am force-stop com.habitly.app` first) and confirm the grid mark on near-black, then the app with no white flash.

- [ ] **Step 6: Commit** — `feat(splash): system splash with the Habitly grid mark`.

---

## Phase 2 — Component kit

All components live in `app/src/main/java/com/habitly/app/ui/components/`. Every file ends with two previews (`@Preview(name = "dark")` wrapped in `HabitlyTheme(darkTheme = true)` and `@Preview(name = "light")` with `darkTheme = false`) on a `Surface(color = MaterialTheme.colorScheme.background)`. Components are stateless: state in, lambdas out.

### Task 5: Primitives — IconTile, CheckButton, buttons, SectionLabel, SegmentedPill, ScreenHeader, icon set

**Files:**
- Create: `IconTile.kt`, `CheckButton.kt`, `Buttons.kt`, `SectionLabel.kt`, `SegmentedPill.kt`
- Replace: `ScreenHeader.kt`, `HabitIcon.kt`
- Test: `app/src/test/java/com/habitly/app/ui/components/HabitIconTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun IconTile(iconKey: String, colorHex: String, modifier: Modifier = Modifier, size: Dp = 36.dp)` — `habitTones(colorHex).low` background, `.full` glyph, radius `size/3`, glyph `size*0.5`.
  - `enum class CheckState { EMPTY, DONE, IN_PROGRESS, SKIPPED }`
  - `@Composable fun CheckButton(state: CheckState, colorHex: String, onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier, size: Dp = 40.dp)` — visual `size` square radius 12dp inside a 48dp min touch target (`Modifier.minimumInteractiveComponentSize()`); EMPTY = 1.5dp `outline` border; DONE = `full` fill + `Icons.Rounded.Check` in `onFull`; IN_PROGRESS = `low` fill + `Icons.Rounded.Add` in `full`; SKIPPED = `low` fill + `Icons.Rounded.Remove` in `full`. Spring press animation: `animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))` via `interactionSource.collectIsPressedAsState()`; when state becomes DONE fire `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`. Semantics: `role = Role.Checkbox; stateDescription = "Done" | "Not done" | "In progress" | "Rest day"`.
  - `@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, containerColor: Color = LocalHabitlyColors.current.primaryButton, contentColor: Color = LocalHabitlyColors.current.onPrimaryButton, leadingIcon: ImageVector? = null)` — pill, height 52dp, `labelLarge`.
  - `@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null)` — `surfaceRaised` pill.
  - `@Composable fun SectionLabel(text: String, modifier: Modifier = Modifier, count: Int? = null, trailing: (@Composable () -> Unit)? = null)` — renders `text.uppercase()` + `" · $count"` in `labelMedium`, `onSurfaceFaint`.
  - `@Composable fun <T> SegmentedPill(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier)` — `surfaceRaised` track, selected segment `outline`-coloured pill (dark) / `surface` with 1dp outline (light), animated with `animateDpAsState` offset; each segment `Role.Tab`, min height 40dp.
  - `@Composable fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, eyebrow: String? = null, onBack: (() -> Unit)? = null, onClose: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})` — optional nav icon row on top (back arrow or close ✕, 48dp), then `eyebrow` (bodyMedium muted), `title` (headlineMedium), `subtitle` (bodyMedium muted); top padding 8dp, bottom 12dp.
  - `fun habitIconFor(key: String): ImageVector` and `val HABIT_ICON_KEYS: List<String>` — 36 keys (below); unknown keys → `Icons.Rounded.Star`.
  - `val HABIT_COLOR_SWATCHES = HabitPalette.seeds` (keeps `ContrastTest` compiling).

Icon keys → `Icons.Rounded.*`: star→Star, water_drop→WaterDrop, menu_book→MenuBook, fitness_center→FitnessCenter, self_improvement→SelfImprovement, run→DirectionsRun, sleep→Bedtime, food→Restaurant, drink→LocalDrink, walk→DirectionsWalk, bike→DirectionsBike, pool→Pool, spa→Spa, favorite→Favorite, psychology→Psychology, school→School, code→Code, brush→Brush, music→MusicNote, language→Translate, edit→EditNote, savings→Savings, work→Work, cleaning→CleaningServices, eco→Eco, pets→Pets, phone_off→PhonelinkErase, sunny→WbSunny, alarm→Alarm, medication→Medication, smoke_free→SmokeFree, no_drinks→NoDrinks, coffee→Coffee, family→FamilyRestroom, call→Call, camera→PhotoCamera.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habitly.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HabitIconTest {
    @Test fun `there are 36 icon keys and no duplicates`() {
        assertEquals(36, HABIT_ICON_KEYS.size)
        assertEquals(36, HABIT_ICON_KEYS.toSet().size)
    }
    @Test fun `every key except star maps to something other than the fallback`() {
        HABIT_ICON_KEYS.filter { it != "star" }.forEach { assertNotEquals(it, Icons.Rounded.Star, habitIconFor(it)) }
    }
    @Test fun `unknown keys fall back to star`() {
        assertEquals(Icons.Rounded.Star, habitIconFor("nope"))
    }
    @Test fun `template icon keys are all known`() {
        com.habitly.app.domain.HabitTemplates.groups.flatMap { it.templates }.forEach {
            assert(it.icon in HABIT_ICON_KEYS) { "template icon ${it.icon} missing" }
        }
    }
}
```

- [ ] **Step 2: Run** — FAIL (icon count 9).
- [ ] **Step 3: Implement the six files** per Interfaces. Replace the old `HabitIcon.kt` entirely.
- [ ] **Step 4: Run all tests** — PASS. If `template icon keys are all known` fails, the template uses a key not in the list: add a mapping for it rather than editing the template.
- [ ] **Step 5: Build** — `./gradlew :app:assembleDebug -q`.
- [ ] **Step 6: Commit** — `feat(components): icon tiles, check button, buttons, segmented pill, header`.

### Task 6: Grid and layout components — HabitGrid, HabitGridCard, HabitRow, StatTile, HeroCard, MonthGrid, SettingsGroup, EmptyState, sheets, nav bar, celebration

**Files:**
- Create: `HabitGrid.kt`, `HabitGridCard.kt`, `HabitRow.kt`, `HeroCard.kt`, `MonthGrid.kt`, `SettingsGroup.kt`, `EmptyState.kt`, `HabitlySheet.kt`, `ConfirmSheet.kt`, `HabitlyNavBar.kt`, `Celebration.kt`
- Replace: `Surfaces.kt` → keep only `StatTile` (new style) and delete `SectionCard`/`OutlinedSectionCard` **after** Phase 4 screens stop using them (leave them for now, mark `@Deprecated("Use surface cards from the redesign")`).
- Restyle: `WeekStrip.kt`
- Test: `app/src/test/java/com/habitly/app/ui/components/GridMathTest.kt`

**Interfaces:**
- Consumes: Task 5 primitives, `ContributionLevels` (levels 0..4), `habitTones`, `LocalHabitlyColors`.
- Produces:
  - `object GridMath { fun weekColumns(endDate: LocalDate, weeks: Int, weekStart: DayOfWeek): List<LocalDate>; fun monthCells(month: YearMonth, weekStart: DayOfWeek): List<LocalDate?> }` — `weekColumns` returns the first day of each column, oldest first, last column containing `endDate`; `monthCells` returns leading nulls then every day of the month, padded with trailing nulls to a multiple of 7.
  - `@Composable fun HabitGrid(levels: Map<LocalDate, Int>, colorHex: String?, modifier: Modifier = Modifier, weeks: Int = 16, endDate: LocalDate = LocalDate.now(), weekStart: DayOfWeek = DayOfWeek.MONDAY, cellGap: Dp = 3.dp, summary: String)` — fills width: cell = `(maxWidth - gap*(weeks-1)) / weeks` via `BoxWithConstraints`; level 0 → `gridEmpty`, 1 → `low`, 2 → `mid`, 3 → `lerp(mid, full, 0.5f)`, 4 → `full`; `colorHex == null` uses brand tones (`MaterialTheme.colorScheme.primary` as full, `primaryContainer` as low, `lerp` between for mid); future cells transparent; today's cell has a 1dp `onSurface` border; radius 3dp. Draw with a single `Canvas` (not 112 composables). Semantics: `clearAndSetSemantics { contentDescription = summary }`.
  - `@Composable fun HabitGridCard(name: String, iconKey: String, colorHex: String, meta: String, checkState: CheckState, levels: Map<LocalDate, Int>, onCheck: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, weeks: Int = 16, endDate: LocalDate = LocalDate.now(), weekStart: DayOfWeek = DayOfWeek.MONDAY, streakDays: Int = 0)` — `surface` card, radius 20dp (light theme: 1dp `outline` border), padding 14dp; row: `IconTile(36dp)`, column(name `titleMedium` max 2 lines, meta row: if `streakDays > 0` a `LocalFireDepartment` 14dp icon in `full` tone then `meta` in `bodyMedium` muted), `CheckButton`; then 10dp gap and `HabitGrid(weeks, 4 rows?)` — **note:** the card grid shows 7 rows (a real week) at the card's width; `summary = "$name: ${levels.count { it.value >= 4 }} days done in the last $weeks weeks"`. Uses `combinedClickable(onClick, onLongClick)`; semantics merge the card, with custom action "Mark done".
  - `@Composable fun HabitRow(name: String, iconKey: String, colorHex: String, supporting: String, last7: List<CheckState>, modifier: Modifier = Modifier, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null, muted: Boolean = false)` — 64dp min height, `IconTile(32dp)`, name `titleMedium` + supporting `bodyMedium` muted, 7 mini squares (6dp, radius 2dp: DONE full, SKIPPED low, IN_PROGRESS mid, EMPTY gridEmpty), then `trailing`; `muted` draws at 55% alpha.
  - `@Composable fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface, icon: ImageVector? = null, iconTint: Color = valueColor, animateFrom: Int? = null)` — `surface` card radius 16dp, padding 14dp; optional 18dp icon; value `headlineSmall`; label `labelMedium` muted. When `animateFrom != null` and value parses as Int, count up with `animateIntAsState` from `animateFrom` (skip if `LocalReduceMotion`).
  - `@Composable fun HeroCard(label: String, value: String, modifier: Modifier = Modifier, delta: String? = null, deltaPositive: Boolean? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {})` — `heroContainer`, radius 24dp, padding 20dp; label `labelMedium` in `primary`; value `displaySmall`; delta row with `ArrowOutward`/`SouthEast` icon in `success`/`error`; then `content`.
  - `@Composable fun MonthGrid(month: YearMonth, cellFill: (LocalDate) -> Color, cellText: (LocalDate) -> Color, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now(), selected: LocalDate? = null, weekStart: DayOfWeek = DayOfWeek.MONDAY, onDayClick: ((LocalDate) -> Unit)? = null, onDayLongClick: ((LocalDate) -> Unit)? = null, dayDescription: (LocalDate) -> String)` — weekday header letters (`labelSmall`, faint); 7-column rows of square cells (aspect 1, radius 10dp, gap 6dp) with the day number `bodyMedium`; today = 1.5dp `primary` border; selected = 2dp `onSurface` border; future days 40% alpha and not clickable; each cell `Role.Button` with `dayDescription(date)`; min touch target 48dp is met by the cell size on phones ≥ 360dp wide (verify at 200% font).
  - `@Composable fun SettingsGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit)` — optional `SectionLabel(title)` above a `surface` radius-20 card; rows separated by 0.5dp `outline` dividers inset 56dp.
  - `@Composable fun SettingsRow(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant, supporting: String? = null, value: String? = null, titleColor: Color = MaterialTheme.colorScheme.onSurface, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)` — 56dp min height; leading icon in a 32dp `surfaceRaised` rounded square; trailing: `trailing` if given, else `value` (bodyMedium muted) + chevron when `onClick != null`.
  - `@Composable fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null, extra: @Composable ColumnScope.() -> Unit = {})` — centred; 64dp `surfaceRaised` tile with 28dp `primary` icon; `titleLarge`; `bodyLarge` muted, centred, max width 280dp; `PrimaryButton` (wrap content) if action.
  - `@Composable fun HabitlySheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit)` — `ModalBottomSheet(containerColor = surfaceRaised, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { BottomSheetDefaults.DragHandle(color = outline) })`, content padding 20dp horizontal, 24dp bottom + navigation bar insets.
  - `@Composable fun ConfirmSheet(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, icon: ImageVector = Icons.Rounded.DeleteOutline, destructive: Boolean = true)` — `HabitlySheet`; 48dp icon tile tinted `error` (or `primary`); title `titleLarge`; message `bodyLarge` muted; stacked `PrimaryButton(confirmLabel, containerColor = if (destructive) error else primaryButton)` and `SecondaryButton("Cancel")`.
  - `data class NavItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)`
  - `@Composable fun HabitlyNavBar(items: List<NavItem>, selectedRoute: String?, onSelect: (NavItem) -> Unit, modifier: Modifier = Modifier)` — floating: `Modifier.padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()`, `surface` radius 24dp (light: 1dp outline), height 64dp; each item equal weight, `Role.Tab`, icon inside a pill (`primaryContainer` bg + `primary` tint when selected, 64×32dp, animated with `animateColorAsState`), label `labelSmall` below (onSurface selected / onSurfaceFaint otherwise).
  - `@Composable fun Celebration(visible: Boolean, title: String, message: String, onDismiss: () -> Unit)` — full-screen scrim (`scrim` 60%) + centred `surface` card radius 28dp with a 72dp `primaryContainer` circle holding `Icons.Rounded.Celebration`, `titleLarge` title, `bodyLarge` message, `PrimaryButton("Nice")`; confetti: a `Canvas` drawing 60 small rounded rects in the 12 palette seeds falling with `rememberInfiniteTransition` for 2.5s; skipped when `LocalReduceMotion`; auto-dismiss after 2500ms via `LaunchedEffect(visible)`.
  - `WeekStrip` new signature: `@Composable fun WeekStrip(days: List<LocalDate>, selected: LocalDate, completion: (LocalDate) -> Float, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now())` — equal-width chips radius 14dp: `surface` bg, weekday `labelSmall` faint, day `titleMedium`; selected = `primaryButton` bg with `onPrimaryButton` text; under the number a 5dp dot: `success` if completion ≥ 1, `mid` brand if > 0, `gridEmpty` otherwise; semantics "Tuesday 29 September, 3 of 4 done" (pass fraction; description built by caller is fine: add param `describe: (LocalDate) -> String`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habitly.app.ui.components

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridMathTest {
    @Test fun `week columns end with the week containing the end date`() {
        val cols = GridMath.weekColumns(LocalDate.of(2026, 9, 29), weeks = 3, weekStart = DayOfWeek.MONDAY)
        assertEquals(listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)), cols)
    }
    @Test fun `week columns respect a Sunday week start`() {
        val cols = GridMath.weekColumns(LocalDate.of(2026, 9, 29), weeks = 1, weekStart = DayOfWeek.SUNDAY)
        assertEquals(listOf(LocalDate.of(2026, 9, 27)), cols)
    }
    @Test fun `month cells pad to whole weeks`() {
        val cells = GridMath.monthCells(YearMonth.of(2026, 9), DayOfWeek.MONDAY) // 1 Sep 2026 is a Tuesday
        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        assertEquals(0, cells.size % 7)
        assertEquals(30, cells.count { it != null })
    }
    @Test fun `month cells with Sunday start shift by one`() {
        val cells = GridMath.monthCells(YearMonth.of(2026, 9), DayOfWeek.SUNDAY)
        assertNull(cells[1])
        assertEquals(LocalDate.of(2026, 9, 1), cells[2])
    }
}
```

- [ ] **Step 2: Run** — FAIL (no `GridMath`).
- [ ] **Step 3: Implement `GridMath`** (in `HabitGrid.kt`):

```kotlin
object GridMath {
    fun weekColumns(endDate: LocalDate, weeks: Int, weekStart: DayOfWeek): List<LocalDate> {
        val lastStart = endDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))
        return (weeks - 1 downTo 0).map { lastStart.minusWeeks(it.toLong()) }
    }
    fun monthCells(month: YearMonth, weekStart: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = List(lead) { null } + days
        val trail = (7 - cells.size % 7) % 7
        return cells + List(trail) { null }
    }
}
```

- [ ] **Step 4: Run test** — PASS.
- [ ] **Step 5: Implement the remaining components** per Interfaces, each with dark + light previews.
- [ ] **Step 6: Build + all tests** — PASS. (Old screens still compile: `WeekStrip`'s old call site in `HomeScreen` is rewritten in Task 8; if it breaks the build now, keep the old `WeekStrip` as `LegacyWeekStrip` until Task 8 deletes it.)
- [ ] **Step 7: Commit** — `feat(components): grid card, rows, tiles, hero, month grid, sheets, nav bar, celebration`.

---

## Phase 3 — Navigation shell

### Task 7: Four-tab navigation, route set, ViewModel wiring helper

**Files:**
- Replace: `ui/navigation/Destinations.kt`, `ui/navigation/HabitlyScaffold.kt`
- Create: `ui/navigation/ViewModelWiring.kt`
- Modify: `ui/navigation/HabitlyNavHost.kt`, `MainActivity.kt`
- Create placeholders: `ui/insights/InsightsScreen.kt`, `ui/you/YouScreen.kt`, `ui/reminders/RemindersScreen.kt` (temporary bodies noted below)
- Test: `app/src/test/java/com/habitly/app/ui/navigation/DestinationsTest.kt`

**Interfaces:**
- Produces:
```kotlin
sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Today : Destination("today")
    data object Habits : Destination("habits?segment={segment}") {
        const val BASE = "habits"
        fun createRoute(segment: String = "active") = "habits?segment=$segment"
    }
    data object Insights : Destination("insights?tab={tab}") {
        const val BASE = "insights"
        fun createRoute(tab: String = "stats") = "insights?tab=$tab"
    }
    data object You : Destination("you")
    data object Awards : Destination("awards")
    data object Settings : Destination("settings")
    data object Reminders : Destination("reminders")
    data object AddEditHabit : Destination("add_edit_habit?habitId={habitId}&template={template}") {
        fun createRoute(habitId: Long? = null, template: String? = null) =
            "add_edit_habit?habitId=${habitId ?: -1L}&template=${template.orEmpty()}"
    }
    data object HabitDetail : Destination("habit_detail/{habitId}") {
        fun createRoute(habitId: Long) = "habit_detail/$habitId"
    }
}
/** Tab routes as registered in the NavHost (patterns), in bar order. */
val TAB_DESTINATIONS = listOf(Destination.Today, Destination.Habits, Destination.Insights, Destination.You)
fun isTabRoute(route: String?): Boolean = TAB_DESTINATIONS.any { it.route == route }
```
  - `ViewModelWiring.kt`:
```kotlin
@Composable
fun rememberHabitRepository(): HabitRepository {
    val context = LocalContext.current.applicationContext
    return remember {
        val db = AppDatabase.getInstance(context)
        HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao())
    }
}

@Composable
inline fun <reified VM : ViewModel> habitlyViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    })
```
  - Template keys: `HabitTemplate.key` = name lower-cased with non-alphanumerics replaced by `_` (add `val key: String get() = …` to `HabitTemplate`) and `HabitTemplates.byKey(key: String): HabitTemplate?`.

- [ ] **Step 1: Failing test**

```kotlin
package com.habitly.app.ui.navigation

import com.habitly.app.domain.HabitTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {
    @Test fun `four tabs in bar order`() {
        assertEquals(listOf("today", "habits?segment={segment}", "insights?tab={tab}", "you"), TAB_DESTINATIONS.map { it.route })
    }
    @Test fun `pushed screens are not tabs`() {
        assertFalse(isTabRoute(Destination.Settings.route))
        assertFalse(isTabRoute(Destination.HabitDetail.route))
        assertTrue(isTabRoute(Destination.Today.route))
    }
    @Test fun `add edit route encodes id and template`() {
        assertEquals("add_edit_habit?habitId=-1&template=drink_water", Destination.AddEditHabit.createRoute(template = "drink_water"))
        assertEquals("add_edit_habit?habitId=5&template=", Destination.AddEditHabit.createRoute(habitId = 5))
    }
    @Test fun `every template has a unique key that resolves back`() {
        val all = HabitTemplates.groups.flatMap { it.templates }
        assertEquals(all.size, all.map { it.key }.toSet().size)
        all.forEach { assertEquals(it, HabitTemplates.byKey(it.key)) }
        assertEquals("drink_water", HabitTemplates.byKey("drink_water")?.key)
    }
}
```

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implement** `Destinations.kt`, template `key`/`byKey`, `ViewModelWiring.kt`.
- [ ] **Step 4: Scaffold** — `HabitlyScaffold(navController, content)`: `Scaffold(containerColor = background, bottomBar = { if (isTabRoute(currentRoute)) HabitlyNavBar(...) })`. Nav items: Today (`Icons.Outlined.CheckCircle`/`Icons.Rounded.CheckCircle`), Habits (`Icons.Outlined.ViewAgenda`/`Icons.Rounded.ViewAgenda`), Insights (`Icons.Outlined.Insights`/`Icons.Rounded.Insights`), You (`Icons.Outlined.AccountCircle`/`Icons.Rounded.AccountCircle`). `onSelect` navigates with `popUpTo(Destination.Today.route) { saveState = true }; launchSingleTop = true; restoreState = true`, using `Destination.Habits.createRoute()` / `Destination.Insights.createRoute()` for the parameterised tabs. Content gets the scaffold padding; screens handle status-bar insets themselves (`Modifier.statusBarsPadding()` on their list's first item / `contentPadding`), because `enableEdgeToEdge()` is on.
- [ ] **Step 5: NavHost** — rewrite `HabitlyNavHost` using `rememberHabitRepository()` + `habitlyViewModel { … }` for every screen. Map: `today` → existing `HomeScreen` (renamed in Task 8); `habits` (arg `segment` default "active") → existing `AllHabitsScreen` (rebuilt in Task 11); `insights` (arg `tab` default "stats") → `InsightsScreen(tab, onTabChange)` placeholder that shows `SegmentedPill(Stats/Calendar)` above the existing `StatsScreen` / `CalendarScreen`; `you` → `YouScreen` placeholder with three `SettingsRow`s navigating to Awards, Archived (`Destination.Habits.createRoute("archived")`), Settings; `awards`, `settings` → existing screens with `onBack`; `reminders` → placeholder `ScreenHeader("Reminders", onBack)`; `add_edit_habit` with args `habitId` (Long, default -1) and `template` (String, default ""); `habit_detail/{habitId}`. Start destination comes from `MainActivity` (`Destination.Today.route` or `Onboarding`). Existing screens that lack an `onBack` param get one added now (Awards, Settings) — render it via their existing header.
- [ ] **Step 6: Build + tests + emulator** — tap through all four tabs, open Awards/Settings from You and back; back from a tab returns to Today; Habits "Archived" link opens the Habits tab.
- [ ] **Step 7: Commit** — `feat(nav): four-tab shell (Today, Habits, Insights, You) with shared VM wiring`.

---

## Phase 4 — Screens

For every screen task: read the matching spec §6 section first, build only with Phase 2 components, respect insets (`statusBarsPadding` at the top, list bottom padding ≥ 96dp above the floating nav bar), and finish with an emulator check in **dark and light** (switch in Settings) before committing. Delete the replaced composables in the same task.

### Task 8: Today screen

**Files:**
- Move/rename: `ui/home/*` → `ui/today/` (`TodayScreen.kt`, `TodayViewModel.kt`, `TodayUiState.kt`); update package names, imports, tests (`HomeViewModelTest` → `TodayViewModelTest`)
- Create: `ui/today/TemplateSheet.kt`
- Modify: `ui/components/HabitStatusSheet.kt` (restyle onto `HabitlySheet`)
- Delete: `ui/components/HabitCard.kt`, `ui/components/ProgressRing.kt` if unused afterwards (keep `ProgressRing` if `TodayScreen` uses it — restyle to 6dp stroke, `gridEmpty` track, `primary` arc, rounded caps)
- Test: `app/src/test/java/com/habitly/app/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitDataSource.observeHabitsWithStats`, `observeRecordsForHabit` is **not** used per card (N flows); instead add to `HabitWithStats` a field `levels: Map<LocalDate, Int> = emptyMap()` computed in `HabitRepository.observeHabitsWithStats` via `ContributionLevels.forHabit(records)` restricted to the last 18 weeks (`records.filter { it.date >= today.minusWeeks(18) }`).
- Produces: `TodayUiState`:
```kotlin
data class TodayUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val weekDays: List<LocalDate> = emptyList(),
    val dayCompletion: Map<LocalDate, Float> = emptyMap(),
    val due: List<HabitWithStats> = emptyList(),      // due on selectedDate and not finished
    val done: List<HabitWithStats> = emptyList(),     // due and DONE or SKIPPED
    val notDue: List<HabitWithStats> = emptyList(),
    val doneCount: Int = 0,
    val dueCount: Int = 0,
    val userName: String? = null,
    val atRisk: HabitWithStats? = null,               // evening warning target
    val showCelebration: Boolean = false,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) { val isViewingToday: Boolean get() = weekDays.isEmpty() || selectedDate == weekDays.last()
    val isEmpty: Boolean get() = !isLoading && due.isEmpty() && done.isEmpty() && notDue.isEmpty() }
```
  - `TodayViewModel(repository: HabitDataSource, prefs: TodayPrefs, today: () -> LocalDate = { LocalDate.now() }, now: () -> LocalTime = { LocalTime.now() })` where `interface TodayPrefs { val userName: Flow<String?>; val weekStart: Flow<DayOfWeek>; val lastCelebratedDate: Flow<String?>; suspend fun setLastCelebratedDate(date: String) }` and `SettingsPrefs` implements it.
  - Methods kept: `onSelectDate`, `onToggleHabit(id)`, `onSetStatus(id, status)`; new `onIncrement(id)` (calls `incrementProgress` for goal habits), `onCelebrationShown()`.
  - Rules: `atRisk` = first due, unfinished habit with `currentStreak >= 2` when `now() >= 18:00` and viewing today. `showCelebration` = viewing today, `dueCount > 0`, `doneCount == dueCount`, and `lastCelebratedDate != today`. `onCelebrationShown` writes today's date.
  - For a past `selectedDate`, the lists are computed from each habit's `levels`/records for that date — reuse the existing HomeViewModel logic for selected dates (it already supports `onSelectDate`); keep its behaviour and tests.

- [ ] **Step 1: Failing tests** (in `TodayViewModelTest.kt`, using the existing fake data source from `HomeViewModelTest` plus a `FakeTodayPrefs`):

```kotlin
class FakeTodayPrefs(name: String? = null, celebrated: String? = null) : TodayPrefs {
    override val userName = MutableStateFlow(name)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val lastCelebratedDate = MutableStateFlow(celebrated)
    override suspend fun setLastCelebratedDate(date: String) { lastCelebratedDate.value = date }
}

@Test fun splitsHabitsIntoDueDoneAndNotDue() = runTest {
    val repo = FakeHabitDataSource(listOf(
        stats(id = 1, name = "Water", due = true, done = false),
        stats(id = 2, name = "Read", due = true, done = true),
        stats(id = 3, name = "Gym", due = false, done = false),
    ))
    val vm = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON })
    advanceUntilIdle()
    val s = vm.uiState.value
    assertEquals(listOf(1L), s.due.map { it.habit.id })
    assertEquals(listOf(2L), s.done.map { it.habit.id })
    assertEquals(listOf(3L), s.notDue.map { it.habit.id })
    assertEquals(1, s.doneCount); assertEquals(2, s.dueCount)
}

@Test fun flagsAStreakAtRiskOnlyInTheEvening() = runTest {
    val repo = FakeHabitDataSource(listOf(stats(id = 1, name = "Gym", due = true, done = false, streak = 4)))
    val noon = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.NOON })
    val evening = TodayViewModel(repo, FakeTodayPrefs(), today = { D }, now = { LocalTime.of(19, 0) })
    advanceUntilIdle()
    assertNull(noon.uiState.value.atRisk)
    assertEquals(1L, evening.uiState.value.atRisk?.habit?.id)
}

@Test fun celebratesOncePerDayWhenEverythingIsDone() = runTest {
    val repo = FakeHabitDataSource(listOf(stats(id = 1, name = "Read", due = true, done = true)))
    val prefs = FakeTodayPrefs()
    val vm = TodayViewModel(repo, prefs, today = { D }, now = { LocalTime.NOON })
    advanceUntilIdle()
    assertTrue(vm.uiState.value.showCelebration)
    vm.onCelebrationShown(); advanceUntilIdle()
    assertFalse(vm.uiState.value.showCelebration)
    assertEquals(D.toString(), prefs.lastCelebratedDate.value)
}

@Test fun exposesTheUserName() = runTest {
    val vm = TodayViewModel(FakeHabitDataSource(emptyList()), FakeTodayPrefs(name = "Sunny"), today = { D })
    advanceUntilIdle()
    assertEquals("Sunny", vm.uiState.value.userName)
    assertTrue(vm.uiState.value.isEmpty)
}
```
(`D = LocalDate.of(2026, 9, 29)`; `stats(...)` builds a `HabitWithStats` with `isDueToday = due`, `todayStatus = if (done) DONE else null`, `isCompletedToday = done`, `currentStreak = streak`. Put helpers at the top of the test file.)

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implement** the rename, `HabitWithStats.levels`, `TodayPrefs`, the ViewModel rules.
- [ ] **Step 4: Run tests** — PASS (old Home tests migrated and green).
- [ ] **Step 5: TodayScreen UI** (`TodayScreen(uiState, onCheck: (HabitWithStats) -> Unit, onSetStatus, onHabitClick, onSelectDate, onAddHabit: () -> Unit, onTemplatePicked: (HabitTemplate?) -> Unit, onCelebrationShown, windowWidthSizeClass)`), top to bottom in a `LazyColumn` (`contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp)`):
  1. Header row (`statusBarsPadding`): column [eyebrow date "Tue 29 Sep" or "Viewing Sat 26 Sep"; title "Today" / weekday name; greeting "Good evening, Sunny" `bodyLarge` muted only if name set] + `ProgressRing(56dp, done/due, label "3/4")`.
  2. `WeekStrip` (16dp top).
  3. At-risk banner if `atRisk != null`: `warning` at 14% alpha background, radius 16dp, `LocalFireDepartment` icon + "‘Workout’ streak (4) ends tonight" `bodyMedium`.
  4. `items(due)` → `HabitGridCard` (checkState: goal habit in progress → IN_PROGRESS, otherwise EMPTY; meta: `"${streak} days · ${schedule label}"` or `"$progress / $target $unit"`; onCheck: goal habits → `onIncrement`, else toggle).
  5. `SectionLabel("Done", count)` with a chevron toggling collapse (remember state), then `done` cards (DONE / SKIPPED states).
  6. `SectionLabel("Not today", count)` collapsed by default, then compact `HabitRow`s.
  7. Empty state (when `isEmpty`): `EmptyState(Icons.Rounded.GridView, "Start with one habit", "Pick something small you can do today.", "Create a habit", onAddHabit)` + a row of three `SecondaryButton` template chips (first three templates of the first group) calling `onTemplatePicked(template)`.
  - Extended FAB: `ExtendedFloatingActionButton(containerColor = primaryButton, contentColor = onPrimaryButton, shape = CircleShape, text = "New", icon = Add)`, bottom-end with 20dp padding, above the nav bar; tapping opens `TemplateSheet`.
  - `Celebration(visible = uiState.showCelebration, "All done for today", "Every habit ticked. See you tomorrow.", onCelebrationShown)`.
  - Long-press a card → restyled `HabitStatusSheet` (Done / Rest day / Clear, plus note) on `HabitlySheet`.
  - Expanded width: keep the existing two-pane behaviour (list left 0.45, detail right).
- [ ] **Step 6: TemplateSheet** — `TemplateSheet(onDismiss, onPick: (HabitTemplate?) -> Unit)`: `HabitlySheet(title = "New habit")`; search `TextField` (filled `surface`, radius 16, no indicator); first a full-width "Start from scratch" tile (`surface`, `Add` icon) → `onPick(null)`; then per group a `SectionLabel(group.title)` and a 2-column grid of template tiles: `habitTones(t.colorHex).low` background radius 16dp, `IconTile(28)`-style glyph in `full`, name `titleSmall`, sub-line (`"8 cups · daily"` / `"daily"`) `bodySmall` muted. Filter by search. NavHost wiring: `onPick(t)` → `navController.navigate(Destination.AddEditHabit.createRoute(template = t?.key))`.
- [ ] **Step 7: Build, tests, emulator (dark + light)** — tick a habit (haptic + spring), increment Water, finish all to see the celebration, long-press for rest day, select a past day, open the template sheet and pick one.
- [ ] **Step 8: Commit** — `feat(today): rebuild Today on grid cards with done/not-today sections, risk banner and celebration`.

### Task 9: Habit detail

**Files:**
- Replace: `ui/detail/HabitDetailScreen.kt`
- Modify: `ui/detail/HabitDetailViewModel.kt`, `ui/detail/HabitDetailUiState.kt`
- Delete: `ui/components/CalendarHeatmap.kt` (after this task and Task 12 no longer use it — delete in Task 12 if Calendar still references it)
- Test: `app/src/test/java/com/habitly/app/ui/detail/HabitDetailViewModelTest.kt` (additions)

**Interfaces:**
- Consumes: `HabitDataSource.setStatus`, `toggleCompletion`, `incrementProgress`, `setArchived`.
- Produces: ViewModel additions — `onToggleDay(date: LocalDate)` (ignores future dates; goal habits: marks DONE if not done, clears if done), `onSetDayStatus(date, status: CompletionStatus?)`, `onToggleToday()`, `onArchive()` now sets `uiState.isArchived = true` (NavHost pops and shows the Undo snackbar via `SavedStateHandle` key `"archived_habit"` = `habitId` on the previous back-stack entry), `onUndoArchive(habitId)` lives in the Today/Habits VMs as `repository.setArchived(id, false)`. UI state gains `totalDays: Int`, `todayStatus: CompletionStatus?`, `todayProgress: Int`, `levels: Map<LocalDate, Int>`, `isArchived: Boolean`.

- [ ] **Step 1: Failing tests** (extend the existing file; reuse its fake):

```kotlin
@Test fun togglingAPastDayMarksItDone() = runTest {
    val vm = detailVm(records = emptyList())
    vm.onToggleDay(D.minusDays(2)); advanceUntilIdle()
    assertEquals(CompletionStatus.DONE, fake.statusOn(HABIT_ID, D.minusDays(2)))
}
@Test fun futureDaysCannotBeToggled() = runTest {
    val vm = detailVm(records = emptyList())
    vm.onToggleDay(D.plusDays(1)); advanceUntilIdle()
    assertNull(fake.statusOn(HABIT_ID, D.plusDays(1)))
}
@Test fun totalDaysCountsDoneDays() = runTest {
    val vm = detailVm(records = listOf(done(D), done(D.minusDays(1)), skipped(D.minusDays(2))))
    advanceUntilIdle()
    assertEquals(2, vm.uiState.value.totalDays)
}
@Test fun archivingFlagsTheScreenToClose() = runTest {
    val vm = detailVm(records = emptyList())
    vm.onArchive(); advanceUntilIdle()
    assertTrue(vm.uiState.value.isArchived)
    assertTrue(fake.isArchived(HABIT_ID))
}
```
(Add `statusOn`, `isArchived` helpers to the fake if missing; `detailVm` constructs `HabitDetailViewModel(fake, HABIT_ID, today = { D })` — add a `today` constructor param defaulting to `LocalDate::now` if the VM lacks one.)

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Run** — PASS.
- [ ] **Step 5: UI** — `HabitDetailScreen(uiState, onBack, onEdit, onArchive, onDelete, onMonthChange, onToggleDay, onDayLongPress, onToggleToday, onRepairStreak)`:
  - Top bar: back (48dp) left; overflow `IconButton(MoreVert)` → `DropdownMenu` (container `surfaceRaised`, radius 16) with Edit (`Edit`), Archive (`Archive`), Delete (`DeleteOutline`, `error` text) → `ConfirmSheet("Delete habit?", "This removes ‘Read’ and all of its history. This can't be undone.", "Delete")`.
  - Hero row: `IconTile(52)`, name `titleLarge` (2 lines), meta `bodyMedium` muted: `"Mind · Daily · 21:00"`.
  - `surface` card with `HabitGrid(weeks = 18 compact / 26 expanded, summary = …)`, plus a Less→More legend row (`labelSmall`).
  - 2×2 `StatTile`s: Current (value in `full` tone, `LocalFireDepartment` icon), Best, Rate (`"86%"`), Total.
  - Repair card when applicable: `surface`, 4dp left accent bar in `full`, title/body as today, `SecondaryButton("Make it a rest day")`.
  - `surface` card: month switcher row (chevrons 48dp, `titleMedium` month) + `MonthGrid(cellFill = full for DONE, low for SKIPPED, mid for PARTIAL, gridEmpty otherwise; cellText = onFull on full, onSurface otherwise; onDayClick = onToggleDay; onDayLongClick = open HabitStatusSheet for that date; dayDescription = "24 September, done")`.
  - Sticky bottom bar (`Box` aligned bottom, `navigationBarsPadding`, 16dp padding, background gradient-free `background` colour): `PrimaryButton` with `containerColor = tones.full`, `contentColor = tones.onFull`: "Done today" (EMPTY) / "Undo today" (DONE) / "+1 ${unit} (5/8)" (goal in progress). List bottom padding 100dp.
  - NavHost: `onEdit` → `Destination.AddEditHabit.createRoute(habitId)`. When `uiState.isArchived`: `navController.previousBackStackEntry?.savedStateHandle?.set("archived_habit", habitId)` then `popBackStack()`. Today and Habits screens read `"archived_habit"` from their own `savedStateHandle`, show `SnackbarHost` "Archived ‘Read’" with action "Undo" → `repository.setArchived(id, false)`, then remove the key. Put the `SnackbarHostState` in `HabitlyScaffold` and pass it down via a `LocalSnackbarHost` CompositionLocal so any screen can show one above the nav bar.
- [ ] **Step 6: Build, tests, emulator (dark + light)** — Edit opens the form, archive → Undo works, delete asks for confirmation, tapping days toggles them, the sticky button updates the grid.
- [ ] **Step 7: Commit** — `feat(detail): rebuilt habit detail with edit entry, undoable archive, editable month`.

### Task 10: New / edit habit form

**Files:**
- Replace: `ui/addedit/AddEditHabitScreen.kt`
- Create: `ui/addedit/IconPickerSheet.kt`, `ui/addedit/GoalSheet.kt`
- Modify: `ui/addedit/AddEditHabitViewModel.kt`, `ui/addedit/AddEditUiState.kt`, NavHost wiring
- Test: `app/src/test/java/com/habitly/app/ui/addedit/AddEditHabitViewModelTest.kt` (additions)

**Interfaces:**
- Consumes: `HabitTemplates.byKey`, `HabitPalette`, `HABIT_ICON_KEYS`, repository `observeHabitsWithStats` (for existing categories).
- Produces: `AddEditHabitViewModel(repository, habitId: Long?, templateKey: String? = null)`. UI state adds `category: String` (default "General"), `existingCategories: List<String>`, `reminderTime: String?` (already exists — now editable), `isDirty: Boolean` (any field differs from the loaded/initial state). New handlers: `onCategoryChange(String)`, `onReminderTimeChange(String?)` (format "HH:mm"), existing `onTemplateSelected` reused by `templateKey` in `init`. Save writes category and reminder, and maps the colour through `HabitPalette.nearestSeed` so saved colours are always seeds.

- [ ] **Step 1: Failing tests** (extend existing file):

```kotlin
@Test fun templateKeyPrefillsTheForm() = runTest {
    val vm = AddEditHabitViewModel(fake, habitId = null, templateKey = "drink_water"); advanceUntilIdle()
    val s = vm.uiState.value
    assertEquals("Drink water", s.name)
    assertEquals("Health", s.category)
    assertEquals("09:00", s.reminderTime)
    assertEquals(8, s.targetCount)
}
@Test fun savingPersistsCategoryAndReminder() = runTest {
    val vm = AddEditHabitViewModel(fake, habitId = null); advanceUntilIdle()
    vm.onNameChange("Stretch"); vm.onCategoryChange("Fitness"); vm.onReminderTimeChange("07:15")
    vm.onSave(); advanceUntilIdle()
    val saved = fake.habits.single()
    assertEquals("Fitness", saved.category)
    assertEquals("07:15", saved.reminderTime)
}
@Test fun savedColoursAreAlwaysPaletteSeeds() = runTest {
    val vm = AddEditHabitViewModel(fake, habitId = null); advanceUntilIdle()
    vm.onNameChange("Stretch"); vm.onColorChange("#1098AD"); vm.onSave(); advanceUntilIdle()
    assertEquals("#22B8CF", fake.habits.single().colorHex)
}
@Test fun isDirtyTracksEdits() = runTest {
    val vm = AddEditHabitViewModel(fake, habitId = null); advanceUntilIdle()
    assertFalse(vm.uiState.value.isDirty)
    vm.onNameChange("x")
    assertTrue(vm.uiState.value.isDirty)
}
```
(Match the fake's actual property for stored habits; the existing test file shows it.)

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Run** — PASS.
- [ ] **Step 5: UI** — `AddEditHabitScreen(uiState, handlers…, onClose)` as a `Scaffold` with sticky bottom `PrimaryButton("Create habit" | "Save", enabled = uiState.canSave)`:
  1. `ScreenHeader(title = "New habit"/"Edit habit", onClose = …)`; close with `isDirty` → `ConfirmSheet("Discard changes?", "Your changes to this habit won't be saved.", "Discard", icon = Close)`; system back does the same via `BackHandler(enabled = isDirty)`.
  2. Live preview `HabitGridCard` using a deterministic sample `levels` map (last 16 weeks, level 4 on ~60% of days from a fixed seed `Random(42)`), non-interactive.
  3. Name: `TextField` (filled `surface`, radius 16, no indicator line) with leading `IconTile(32)` clickable → `IconPickerSheet` (6-column grid of 48dp tiles of all `HABIT_ICON_KEYS`, selected tile ringed with 2dp `onSurface`). Inline error text in `error` under the field when validation fails.
  4. Colour: `SectionLabel("Colour")` + `LazyRow`/`FlowRow` of 12 × 36dp circles (`habitTones(seed).full`), selected has a 2dp `onSurface` ring offset 3dp; each `Role.RadioButton` with a colour name description ("Orange", "Blue", …).
  5. Category: `SectionLabel("Category")` + `FlowRow` of `FilterChip`s from `existingCategories ∪ {Health, Fitness, Mind, Learning, Productivity, General}` + a "New…" chip that swaps to an inline `TextField` + check button.
  6. Repeat: `SectionLabel("Repeat")` + `SegmentedPill(Daily/Days/Interval)`; Days → 7 circle toggles (40dp, `full` fill when on, letters from week start); Interval → stepper row "Every [−] 2 [+] days" (48dp buttons).
  7. `SettingsGroup`: `SettingsRow(icon = Flag, title = "Daily goal", value = "8 cups" | "Off")` → `GoalSheet` (stepper 1..99 + unit `TextField` + suggestion chips: times, cups, glasses, pages, minutes, km, steps; "Remove goal" text button); `SettingsRow(icon = Alarm, "Reminder", value = "09:00" | "Off")` → M3 `TimePicker` inside `AlertDialog`-style `HabitlySheet` with "Set" / "Turn off"; `SettingsRow(icon = Category, "Category")` is not needed (chips above).
- [ ] **Step 6: Build, tests, emulator (dark + light)** — create from template, create from scratch, edit an existing habit (reached from detail), set/clear reminder, discard guard.
- [ ] **Step 7: Commit** — `feat(addedit): rebuilt habit form with icon/goal/reminder sheets, category and discard guard`.

### Task 11: Habits (manage) tab

**Files:**
- Move/rename: `ui/allhabits/*` → `ui/habits/` (`HabitsScreen.kt`, `HabitsViewModel.kt`, `HabitsUiState.kt`), tests likewise
- Test: `app/src/test/java/com/habitly/app/ui/habits/HabitsViewModelTest.kt`

**Interfaces:**
- Consumes: `observeHabitsWithStats`, `observeArchivedHabits`, `setArchived`, `reorder`, `deleteHabit`.
- Produces:
```kotlin
enum class HabitsSegment { ACTIVE, ARCHIVED }
enum class HabitSort { CUSTOM, NAME, STREAK, CREATED }
data class HabitGroup(val category: String, val habits: List<HabitWithStats>)
data class HabitsUiState(
    val segment: HabitsSegment = HabitsSegment.ACTIVE,
    val sort: HabitSort = HabitSort.CUSTOM,
    val query: String = "",
    val selectedCategory: String? = null,
    val categories: List<Pair<String, Int>> = emptyList(),   // name to count, active habits
    val groups: List<HabitGroup> = emptyList(),               // active, filtered, sorted
    val archived: List<HabitEntity> = emptyList(),
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
    val isLoading: Boolean = true,
) { val isFiltering get() = query.isNotBlank() || selectedCategory != null }
```
  `HabitsViewModel(repository, initialSegment: HabitsSegment)` with `onSegmentChange`, `onQueryChange`, `onCategorySelect(String?)`, `onSortChange`, `onArchive(id)`, `onRestore(id)`, `onDeleteForever(id)`, `onMove(fromId: Long, toId: Long)` (reorders within the full active list and calls `repository.reorder`), `onClearFilters()`.
  Sorting: CUSTOM keeps repository order (sortOrder); NAME case-insensitive; STREAK by `currentStreak` desc; CREATED by `createdAt` asc. Groups are ordered by category name; with sort ≠ CUSTOM, groups still apply. Search matches name or category, case-insensitive.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun groupsActiveHabitsByCategory() = runTest {
    val vm = vmWith(active = listOf(h(1, "Water", "Health"), h(2, "Read", "Mind"), h(3, "Sleep", "Health")))
    advanceUntilIdle()
    assertEquals(listOf("Health", "Mind"), vm.uiState.value.groups.map { it.category })
    assertEquals(listOf("Water", "Sleep"), vm.uiState.value.groups[0].habits.map { it.habit.name })
    assertEquals(listOf("Health" to 2, "Mind" to 1), vm.uiState.value.categories)
}
@Test fun searchMatchesNameOrCategory() = runTest {
    val vm = vmWith(active = listOf(h(1, "Water", "Health"), h(2, "Read", "Mind")))
    vm.onQueryChange("mind"); advanceUntilIdle()
    assertEquals(listOf("Read"), vm.uiState.value.groups.flatMap { it.habits }.map { it.habit.name })
}
@Test fun sortByStreakOrdersWithinGroups() = runTest {
    val vm = vmWith(active = listOf(h(1, "A", "X", streak = 1), h(2, "B", "X", streak = 9)))
    vm.onSortChange(HabitSort.STREAK); advanceUntilIdle()
    assertEquals(listOf("B", "A"), vm.uiState.value.groups.single().habits.map { it.habit.name })
}
@Test fun archiveAndRestoreMoveBetweenSegments() = runTest {
    val fake = FakeHabitsSource(active = listOf(h(1, "Read", "Mind")))
    val vm = HabitsViewModel(fake, HabitsSegment.ACTIVE)
    vm.onArchive(1); advanceUntilIdle()
    assertEquals(0, vm.uiState.value.activeCount); assertEquals(1, vm.uiState.value.archivedCount)
    vm.onRestore(1); advanceUntilIdle()
    assertEquals(1, vm.uiState.value.activeCount)
}
@Test fun movingAHabitReordersTheFullList() = runTest {
    val fake = FakeHabitsSource(active = listOf(h(1, "A", "X"), h(2, "B", "X"), h(3, "C", "X")))
    val vm = HabitsViewModel(fake, HabitsSegment.ACTIVE)
    vm.onMove(fromId = 3, toId = 1); advanceUntilIdle()
    assertEquals(listOf(3L, 1L, 2L), fake.lastReorder)
}
@Test fun startsOnTheRequestedSegment() = runTest {
    val vm = HabitsViewModel(FakeHabitsSource(), HabitsSegment.ARCHIVED); advanceUntilIdle()
    assertEquals(HabitsSegment.ARCHIVED, vm.uiState.value.segment)
}
```
(`FakeHabitsSource` implements `HabitDataSource` with in-memory active/archived lists, flipping habits between them in `setArchived`, recording `lastReorder`. `h(...)` builds `HabitWithStats`. Define both at the top of the test file.)

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Run** — PASS (migrate the old AllHabits tests that still apply; delete ones for removed behaviour such as TO DO/DONE sections).
- [ ] **Step 5: UI** — `HabitsScreen(uiState, handlers…, onHabitClick, onAddHabit)`:
  - `ScreenHeader("Habits", subtitle = "4 active")` with trailing sort `IconButton(Sort)` → `DropdownMenu` (Custom order, Name, Longest streak, Newest first; checkmark on the current one).
  - `SegmentedPill(ACTIVE/ARCHIVED, label = "Active ${n}" / "Archived ${n}")`.
  - ACTIVE: search field; category chips `LazyRow` ("All n" + categories); groups → `SectionLabel(category, count)` + `HabitRow`s inside one `surface` radius-20 card per group, 0.5dp dividers. Row `last7` from `last7DaysStatus` (map DONE→DONE, SKIPPED→SKIPPED, PARTIAL→IN_PROGRESS, null→EMPTY), supporting = schedule label + (" · 🔥" no — use text "· 12-day streak"). Swipe left: `SwipeToDismissBox` with `surfaceRaised` background and `Archive` icon → `onArchive` + snackbar "Archived ‘Read’ · Undo" (`onRestore`). CUSTOM sort: long-press drag handle (`DragIndicator` trailing icon) reorders — implement drag with `Modifier.pointerInput { detectDragGesturesAfterLongPress }` tracking the dragged item's offset and swapping with the neighbour when crossing half a row height, calling `onMove(draggedId, targetId)` on drop. If filtering, hide drag handles.
  - Filtering with no matches: `EmptyState(SearchOff, "No habits match", "Try a different word or clear the filters.", "Clear filters", onClearFilters)`.
  - ARCHIVED: `HabitRow(muted = true, trailing = TextButton("Restore"))` + overflow `MoreVert` → "Delete permanently" → `ConfirmSheet`. Empty: `EmptyState(Archive, "Nothing archived", "Habits you archive rest here, with their history kept.")`.
  - No FAB here; header gets a trailing `IconButton(Add)` → template sheet (reuse `TemplateSheet`).
  - NavHost: segment arg `"archived"` → `HabitsSegment.ARCHIVED`; use `habitlyViewModel(key = "habits-$segment")`.
- [ ] **Step 6: Build, tests, emulator** — segments, search, chips, sort, swipe-archive + undo, restore, delete forever, drag reorder (then confirm Today follows the new order).
- [ ] **Step 7: Commit** — `feat(habits): manage tab with active/archived, search, sort, swipe-archive and reorder`.

### Task 12: Insights — Stats restyle and Calendar with day sheet

**Files:**
- Replace: `ui/insights/InsightsScreen.kt` (from the Task 7 placeholder)
- Modify: `ui/stats/StatsScreen.kt` (restyle only; `StatsViewModel`/`StatsUiState`/`InsightEngine` logic unchanged except as noted), `ui/components/Charts.kt` (colours to tokens)
- Replace: `ui/calendar/CalendarScreen.kt`; modify `ui/calendar/CalendarViewModel.kt`, `CalendarUiState.kt`
- Create: `ui/calendar/DaySheet.kt`
- Delete: `ui/components/CalendarHeatmap.kt`, `ui/components/ContributionGrid.kt`, `ui/components/BarChart.kt` if no longer referenced (check with grep)
- Test: `app/src/test/java/com/habitly/app/ui/calendar/CalendarViewModelTest.kt` (additions)

**Interfaces:**
- Produces: `CalendarUiState` adds `selectedDate: LocalDate?`, `dayHabits: List<DayHabit>`, `dayFraction: Map<LocalDate, Float>` (done ÷ due for each date of the shown month; absent when nothing due), `weekStart: DayOfWeek`; `data class DayHabit(val habit: HabitEntity, val status: CompletionStatus?, val progress: Int)`. `CalendarViewModel` adds `onDaySelected(date)`, `onDayDismissed()`, `onToggleHabitOnDay(habitId, date)` (calls `toggleCompletion`, or `incrementProgress` for goal habits). Due-ness uses each habit's `HabitSchedule`.
  - `InsightsScreen(tab: String, onTabChange: (String) -> Unit, stats: @Composable () -> Unit, calendar: @Composable () -> Unit)` — header "Insights" + `SegmentedPill(Stats/Calendar)` + the chosen slot; on expanded width show both side-by-side (stats 0.55 / calendar 0.45) with no pill.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun selectingADayListsTheHabitsDueThatDay() = runTest {
    // Daily "Water" and Mon/Wed/Fri "Gym"; 2026-09-24 is a Thursday.
    val vm = calendarVm(habits = listOf(daily(1, "Water"), mwf(2, "Gym")), logs = listOf(done(1, "2026-09-24")))
    vm.onDaySelected(LocalDate.of(2026, 9, 24)); advanceUntilIdle()
    val s = vm.uiState.value
    assertEquals(listOf("Water"), s.dayHabits.map { it.habit.name })
    assertEquals(CompletionStatus.DONE, s.dayHabits.single().status)
}
@Test fun dayFractionIsDoneOverDue() = runTest {
    val vm = calendarVm(habits = listOf(daily(1, "A"), daily(2, "B")), logs = listOf(done(1, "2026-09-10")))
    advanceUntilIdle()
    assertEquals(0.5f, vm.uiState.value.dayFraction[LocalDate.of(2026, 9, 10)]!!, 0.001f)
}
@Test fun togglingFromTheDaySheetWritesThatDate() = runTest {
    val vm = calendarVm(habits = listOf(daily(1, "A")), logs = emptyList())
    vm.onToggleHabitOnDay(1, LocalDate.of(2026, 9, 20)); advanceUntilIdle()
    assertEquals(listOf(1L to LocalDate.of(2026, 9, 20)), fake.toggled)
}
@Test fun dismissingClearsTheSelection() = runTest {
    val vm = calendarVm(habits = listOf(daily(1, "A")), logs = emptyList())
    vm.onDaySelected(LocalDate.of(2026, 9, 20)); vm.onDayDismissed(); advanceUntilIdle()
    assertNull(vm.uiState.value.selectedDate)
}
```
(Construct with `month = YearMonth.of(2026, 9)`, `today = LocalDate.of(2026, 9, 29)`; fake records `toggled`. Habit `createdAt` must be before September so all days count. Match the fake/constructor already used in `CalendarViewModelTest`.)

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Run** — PASS.
- [ ] **Step 5: Calendar UI** — month switcher; `MonthGrid(cellFill = brand tones by fraction: 0 → gridEmpty, (0,0.34) → primaryContainer, [0.34,0.67) → lerp(primaryContainer, primary, 0.5), [0.67,1) → lerp(…,0.75), 1 → primary)`; `dayDescription = "24 September, 3 of 4 done"`; legend Less→More; two `StatTile`s (Days active, Consistency). Tap → `DaySheet(date, dayHabits, onToggle, onDismiss)`: `HabitlySheet(title = "Thursday 24 Sep")`, subtitle "3 of 4 done", each row `IconTile(32)` + name + `CheckButton(state, colorHex)`. Future dates are not selectable.
- [ ] **Step 6: Stats restyle** — keep structure and all insight logic; swap containers: period control → text tabs (Week/Month/Year, selected `onSurface` with a 2dp `primary` underline, others muted); completion hero → `HeroCard` (bar chart inside, weakest bar `error`, others `primary`, bar radius 6dp, avg line dashed `onSurfaceFaint`); in **Year** the hero slot shows `HabitGrid(levels = ContributionLevels.forAllHabits(...), colorHex = null, weeks = 52)` instead of bars (add `yearLevels: Map<LocalDate, Int>` to `StatsUiState`, built from data the VM already loads); "What we noticed" → `surface` card with `AutoAwesome` icon in `primary`; tile row → `StatTile`s with icons (`LocalFireDepartment` warning, `EmojiEvents`, `Star`, `CheckCircle`); weekly pattern + habit-by-habit on `surface` radius 20 cards; "GOING WELL"/"SLIPPING" chips → `SectionLabel` with `success`/`error` 8dp dots. Replace every hard-coded colour in `Charts.kt` with tokens.
- [ ] **Step 7: Build, tests, emulator (dark + light)** — Stats periods incl. Year grid; Calendar month nav, tap a past day, toggle a habit, verify Today reflects it.
- [ ] **Step 8: Commit** — `feat(insights): insights tab with restyled stats and calendar day sheet`.

### Task 13: Awards — 16 badges with progress and celebration

**Files:**
- Replace: `domain/AchievementEvaluator.kt`
- Create: `domain/AchievementSnapshot.kt`
- Modify: `data/repository/HabitRepository.kt`, `HabitDataSource.kt` (+ fakes)
- Replace: `ui/achievements/AchievementsScreen.kt`, `AchievementsViewModel.kt`, `AchievementsUiState.kt`
- Create: `ui/achievements/BadgeSheet.kt`, `ui/achievements/BadgeCelebrationHost.kt`
- Test: replace `app/src/test/java/com/habitly/app/domain/AchievementEvaluatorTest.kt`; create `AchievementSnapshotTest.kt`; update `AchievementsViewModelTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class AchievementGroup(val title: String) { STREAKS("Streaks"), CONSISTENCY("Consistency"), VOLUME("Volume"), SPECIAL("Special") }

enum class AchievementType(val group: AchievementGroup, val title: String, val criterion: String, val target: Int) {
    STREAK_3(AchievementGroup.STREAKS, "Warming up", "Keep any habit 3 days in a row", 3),
    STREAK_7(AchievementGroup.STREAKS, "One week", "Keep any habit 7 days in a row", 7),
    STREAK_30(AchievementGroup.STREAKS, "One month", "Keep any habit 30 days in a row", 30),
    STREAK_100(AchievementGroup.STREAKS, "Centurion", "Keep any habit 100 days in a row", 100),
    PERFECT_DAY(AchievementGroup.CONSISTENCY, "Perfect day", "Finish every habit due in one day", 1),
    PERFECT_WEEK(AchievementGroup.CONSISTENCY, "Perfect week", "Seven perfect days in a row", 7),
    MONTH_80(AchievementGroup.CONSISTENCY, "Strong month", "Keep 80% of a calendar month", 80),
    COMEBACK(AchievementGroup.CONSISTENCY, "Comeback", "Pick a habit back up after a broken streak", 1),
    CHECKINS_10(AchievementGroup.VOLUME, "Getting going", "10 check-ins in total", 10),
    CHECKINS_100(AchievementGroup.VOLUME, "Hundred club", "100 check-ins in total", 100),
    CHECKINS_500(AchievementGroup.VOLUME, "Habit machine", "500 check-ins in total", 500),
    GOAL_10(AchievementGroup.VOLUME, "Goal getter", "Meet a daily goal 10 times", 10),
    FIRST_HABIT(AchievementGroup.SPECIAL, "First step", "Create your first habit", 1),
    FIVE_HABITS(AchievementGroup.SPECIAL, "Full plate", "Track 5 habits at once", 5),
    EARLY_BIRD(AchievementGroup.SPECIAL, "Early bird", "10 check-ins before 8am", 10),
    SECOND_CHANCE(AchievementGroup.SPECIAL, "Second chance", "Mark a day as rest instead of breaking a streak", 1),
}

data class AchievementSnapshot(
    val bestStreak: Int = 0,
    val perfectDays: Int = 0,
    val longestPerfectRun: Int = 0,
    val bestMonthPercent: Int = 0,
    val comebacks: Int = 0,
    val totalCheckIns: Int = 0,
    val goalDaysMet: Int = 0,
    val habitsEver: Int = 0,
    val activeHabits: Int = 0,
    val earlyCheckIns: Int = 0,
    val restDays: Int = 0,
)

object AchievementEvaluator {
    fun progress(type: AchievementType, s: AchievementSnapshot): Int
    fun evaluate(s: AchievementSnapshot, alreadyUnlocked: Set<AchievementType>): Set<AchievementType>
}

object AchievementSnapshotBuilder {
    fun build(habits: List<HabitEntity>, logs: List<HabitLogEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): AchievementSnapshot
}
```
  Note the spec's `SECOND_CHANCE` ("use streak repair once") is implemented as "any rest (SKIPPED) day recorded", because a repaired day and a declared rest day are stored identically; the criterion text above reflects this.
  - Repository: `evaluateAchievements()` (no habitId) builds the snapshot from `habitDao.observeAllHabits().first()` + `logDao.observeAllLogs().first()` and inserts newly unlocked types (`habitId = null`); called after `setStatus`/`incrementProgress` reach DONE, after `setStatus(SKIPPED)`, and after `addHabit`. `HabitDataSource` adds `fun observeAchievementProgress(today: LocalDate): Flow<List<AchievementProgress>>` where `data class AchievementProgress(val type: AchievementType, val unlockedAt: Long?, val progress: Int)` — combines habits, logs and achievements.

- [ ] **Step 1: Failing evaluator tests** (replace file):

```kotlin
class AchievementEvaluatorTest {
    @Test fun `sixteen badges in four groups of four`() {
        assertEquals(16, AchievementType.entries.size)
        AchievementGroup.entries.forEach { g -> assertEquals(4, AchievementType.entries.count { it.group == g }) }
    }
    @Test fun `existing stored keys are kept`() {
        listOf("FIRST_HABIT", "STREAK_7", "STREAK_30", "STREAK_100").forEach { AchievementType.valueOf(it) }
    }
    @Test fun `streak badges unlock at their thresholds`() {
        val got = AchievementEvaluator.evaluate(AchievementSnapshot(bestStreak = 7), emptySet())
        assertTrue(AchievementType.STREAK_3 in got && AchievementType.STREAK_7 in got)
        assertFalse(AchievementType.STREAK_30 in got)
    }
    @Test fun `already unlocked badges are not returned again`() {
        val got = AchievementEvaluator.evaluate(AchievementSnapshot(bestStreak = 7), setOf(AchievementType.STREAK_3))
        assertFalse(AchievementType.STREAK_3 in got)
    }
    @Test fun `progress is capped at the target`() {
        assertEquals(30, AchievementEvaluator.progress(AchievementType.STREAK_30, AchievementSnapshot(bestStreak = 45)))
        assertEquals(12, AchievementEvaluator.progress(AchievementType.STREAK_30, AchievementSnapshot(bestStreak = 12)))
    }
    @Test fun `every badge maps to its snapshot field`() {
        val s = AchievementSnapshot(perfectDays = 1, longestPerfectRun = 7, bestMonthPercent = 85, comebacks = 1,
            totalCheckIns = 500, goalDaysMet = 10, habitsEver = 1, activeHabits = 5, earlyCheckIns = 10, restDays = 1)
        val got = AchievementEvaluator.evaluate(s, emptySet())
        (AchievementType.entries - setOf(AchievementType.STREAK_3, AchievementType.STREAK_7,
            AchievementType.STREAK_30, AchievementType.STREAK_100)).forEach { assertTrue("$it", it in got) }
    }
}
```

- [ ] **Step 2: Failing snapshot tests** (`AchievementSnapshotTest.kt`):

```kotlin
class AchievementSnapshotTest {
    private val zone = ZoneId.of("UTC")
    private val start = LocalDate.of(2026, 8, 1)
    private fun habit(id: Long, target: Int? = null, archived: Boolean = false) = HabitEntity(
        id = id, name = "H$id", icon = "star", colorHex = "#FF922B", category = "X",
        frequencyType = FrequencyType.DAILY, frequencyDays = null, intervalDays = null, reminderTime = null,
        createdAt = start.atStartOfDay(zone).toInstant().toEpochMilli(), isArchived = archived, targetCount = target,
    )
    private fun log(habitId: Long, date: LocalDate, status: CompletionStatus = CompletionStatus.DONE, hour: Int = 12) =
        HabitLogEntity(0, habitId, date.toString(), status,
            if (status == CompletionStatus.DONE) date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli() else null, null)

    @Test fun countsCheckInsEarlyBirdsAndRestDays() {
        val logs = listOf(log(1, start, hour = 7), log(1, start.plusDays(1), hour = 9), log(1, start.plusDays(2), CompletionStatus.SKIPPED))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, start.plusDays(3), zone)
        assertEquals(2, s.totalCheckIns); assertEquals(1, s.earlyCheckIns); assertEquals(1, s.restDays)
    }
    @Test fun perfectDaysNeedEveryDueHabitKept() {
        val d = start.plusDays(5)
        val logs = listOf(log(1, d), log(2, d), log(1, d.plusDays(1)))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1), habit(2)), logs, d.plusDays(2), zone)
        assertEquals(1, s.perfectDays); assertEquals(1, s.longestPerfectRun)
    }
    @Test fun perfectRunCountsConsecutiveDays() {
        val logs = (0L..6L).map { log(1, start.plusDays(it)) }
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, start.plusDays(7), zone)
        assertEquals(7, s.longestPerfectRun)
    }
    @Test fun goalDaysOnlyCountGoalHabits() {
        val logs = listOf(log(1, start), log(2, start))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1, target = 8), habit(2)), logs, start.plusDays(1), zone)
        assertEquals(1, s.goalDaysMet)
    }
    @Test fun bestMonthUsesOnlyFinishedMonths() {
        val logs = (0L until 31L).filter { it % 10 != 0L }.map { log(1, start.plusDays(it)) } // Aug: 27 of 31
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, LocalDate.of(2026, 9, 29), zone)
        assertEquals(87, s.bestMonthPercent)
    }
    @Test fun comebackAfterABrokenStreakOfThree() {
        val logs = listOf(log(1, start), log(1, start.plusDays(1)), log(1, start.plusDays(2)), log(1, start.plusDays(4)))
        val s = AchievementSnapshotBuilder.build(listOf(habit(1)), logs, start.plusDays(5), zone)
        assertEquals(1, s.comebacks)
    }
    @Test fun habitCountsSeparateActiveFromEver() {
        val s = AchievementSnapshotBuilder.build(listOf(habit(1), habit(2, archived = true)), emptyList(), start, zone)
        assertEquals(2, s.habitsEver); assertEquals(1, s.activeHabits)
    }
}
```

- [ ] **Step 3: Run** — FAIL.
- [ ] **Step 4: Implement evaluator**

```kotlin
object AchievementEvaluator {
    fun progress(type: AchievementType, s: AchievementSnapshot): Int {
        val raw = when (type) {
            AchievementType.STREAK_3, AchievementType.STREAK_7,
            AchievementType.STREAK_30, AchievementType.STREAK_100 -> s.bestStreak
            AchievementType.PERFECT_DAY -> s.perfectDays
            AchievementType.PERFECT_WEEK -> s.longestPerfectRun
            AchievementType.MONTH_80 -> s.bestMonthPercent
            AchievementType.COMEBACK -> s.comebacks
            AchievementType.CHECKINS_10, AchievementType.CHECKINS_100, AchievementType.CHECKINS_500 -> s.totalCheckIns
            AchievementType.GOAL_10 -> s.goalDaysMet
            AchievementType.FIRST_HABIT -> s.habitsEver
            AchievementType.FIVE_HABITS -> s.activeHabits
            AchievementType.EARLY_BIRD -> s.earlyCheckIns
            AchievementType.SECOND_CHANCE -> s.restDays
        }
        return raw.coerceIn(0, type.target)
    }

    fun evaluate(s: AchievementSnapshot, alreadyUnlocked: Set<AchievementType>): Set<AchievementType> =
        AchievementType.entries.filter { it !in alreadyUnlocked && progress(it, s) >= it.target }.toSet()
}
```

- [ ] **Step 5: Implement `AchievementSnapshotBuilder`**

```kotlin
object AchievementSnapshotBuilder {
    fun build(habits: List<HabitEntity>, logs: List<HabitLogEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): AchievementSnapshot {
        if (habits.isEmpty()) return AchievementSnapshot()
        val byHabit = logs.groupBy { it.habitId }
        val statusOf = logs.associate { (it.habitId to LocalDate.parse(it.date)) to it.status }
        val schedules = habits.associate { h ->
            h.id to HabitSchedule.parse(h.frequencyType, h.frequencyDays, h.intervalDays,
                Instant.ofEpochMilli(h.createdAt).atZone(zone).toLocalDate())
        }
        val startOf = habits.associate { it.id to Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        val kept = setOf(CompletionStatus.DONE, CompletionStatus.SKIPPED)

        val bestStreak = habits.maxOf { h ->
            StreakCalculator.bestStreak((byHabit[h.id] ?: emptyList()).map { CompletionRecord(LocalDate.parse(it.date), it.status) })
        }

        // Perfect days: every habit due that day kept, at least one actually done.
        val first = startOf.values.min()
        var perfectDays = 0; var run = 0; var longestRun = 0
        var d = first
        while (!d.isAfter(today)) {
            val due = habits.filter { !startOf.getValue(it.id).isAfter(d) && schedules.getValue(it.id).isDue(d) }
            val perfect = due.isNotEmpty() && due.all { statusOf[it.id to d] in kept } &&
                due.any { statusOf[it.id to d] == CompletionStatus.DONE }
            if (perfect) { perfectDays++; run++; longestRun = maxOf(longestRun, run) } else if (d != today) run = 0
            d = d.plusDays(1)
        }

        // Best finished calendar month, over days each habit was due.
        var bestMonth = 0
        var month = YearMonth.from(first)
        while (month.isBefore(YearMonth.from(today))) {
            var due = 0; var done = 0
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                habits.filter { !startOf.getValue(it.id).isAfter(date) && schedules.getValue(it.id).isDue(date) }.forEach {
                    due++; if (statusOf[it.id to date] in kept) done++
                }
            }
            if (due >= 10) bestMonth = maxOf(bestMonth, done * 100 / due)
            month = month.plusMonths(1)
        }

        // Comebacks: a done day after a miss that ended a run of at least 3.
        var comebacks = 0
        habits.forEach { h ->
            var streak = 0; var armed = false
            var day = startOf.getValue(h.id)
            while (day.isBefore(today)) {
                if (schedules.getValue(h.id).isDue(day)) {
                    when (statusOf[h.id to day]) {
                        CompletionStatus.DONE -> { if (armed) { comebacks++; armed = false }; streak++ }
                        CompletionStatus.SKIPPED -> Unit
                        else -> { if (streak >= 3) armed = true; streak = 0 }
                    }
                }
                day = day.plusDays(1)
            }
            if (armed && statusOf[h.id to today] == CompletionStatus.DONE) comebacks++
        }

        val done = logs.filter { it.status == CompletionStatus.DONE }
        val goalHabits = habits.filter { it.targetCount != null }.map { it.id }.toSet()
        return AchievementSnapshot(
            bestStreak = bestStreak,
            perfectDays = perfectDays,
            longestPerfectRun = longestRun,
            bestMonthPercent = bestMonth,
            comebacks = comebacks,
            totalCheckIns = done.size,
            goalDaysMet = done.count { it.habitId in goalHabits },
            habitsEver = habits.size,
            activeHabits = habits.count { !it.isArchived },
            earlyCheckIns = done.count { log -> log.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).hour < 8 } == true },
            restDays = logs.count { it.status == CompletionStatus.SKIPPED },
        )
    }
}
```
Check `HabitSchedule.parse`'s real parameter names (`type`, `frequencyDays`, `intervalDays`, `startDate`) and `StreakCalculator.bestStreak`'s signature before compiling; adapt the calls, not the behaviour. Check `CompletionStatus` for `PARTIAL`/`MISSED` names.

- [ ] **Step 6: Run domain tests** — PASS. If `comebackAfterABrokenStreakOfThree` fails because `today` handling differs, fix the builder, not the test.
- [ ] **Step 7: Repository + ViewModel** — implement `evaluateAchievements()` and `observeAchievementProgress`; `AchievementsUiState(items: List<AchievementProgress>, unlockedCount: Int, total: Int = 16, groups: Map<AchievementGroup, List<AchievementProgress>>)`; `AchievementsViewModel(repository, today)`. Update `AchievementsViewModelTest` to assert grouping and counts from a fake emitting three progresses (one unlocked).
- [ ] **Step 8: UI** — `AchievementsScreen(uiState, onBack)`: `ScreenHeader("Awards", onBack)`, progress bar (`LinearProgressIndicator`, 6dp, rounded, `primary` on `gridEmpty`) + "5 of 16 unlocked"; per group `SectionLabel(group.title)` + 3-column grid of badge tiles (`surface` radius 16, padding 12): 52dp circle — unlocked: group colour tones (`STREAKS` orange seed, `CONSISTENCY` yellow, `VOLUME` violet, `SPECIAL` cyan: `low` bg + `full` glyph); locked: `gridEmpty` bg + `Lock` glyph in `onSurfaceFaint`. Glyphs: streaks `LocalFireDepartment`, perfect `Star`/`AutoAwesome`, month `CalendarMonth`, comeback `Replay`, check-ins `TaskAlt`, goal `Flag`, first `Spa`, five `GridView`, early `WbTwilight`, second chance `Bedtime`. Title `titleSmall` 2 lines centred; locked shows a 4dp progress bar + "12/30" `labelSmall`; unlocked shows the date "12 Sep" `labelSmall` muted. Tap → `BadgeSheet` (96dp badge, title `titleLarge`, criterion `bodyLarge`, "Unlocked on 12 September 2026" or progress bar + "12 of 30").
- [ ] **Step 9: Celebration host** — `BadgeCelebrationHost(repository, prefs)` placed once in `HabitlyScaffold`: collects `observeAchievementProgress`, finds unlocked types not in `prefs.seenBadgeKeys`; on the **first** collection after install, marks all currently unlocked as seen without celebrating (so existing users are not spammed); afterwards shows a full-screen dialog (`Dialog(usePlatformDefaultWidth = false)`, scrim, badge scales 0.4→1 with `spring(dampingRatio = 0.45f)` unless reduce motion, confetti from `Celebration`, "New badge", title, criterion, `PrimaryButton("Continue")`) one at a time, then `markBadgesSeen`.
- [ ] **Step 10: Build, all tests, emulator** — check a 3rd consecutive day on a habit to unlock "Warming up"; confirm the celebration appears once.
- [ ] **Step 11: Commit** — `feat(awards): sixteen badges with progress, badge sheet and unlock celebration`.

### Task 14: You tab, Settings, Reminders, CSV export

**Files:**
- Replace: `ui/you/YouScreen.kt`; create `ui/you/YouViewModel.kt`
- Replace: `ui/settings/SettingsScreen.kt`; modify `SettingsViewModel.kt`, `SettingsUiState.kt`
- Replace: `ui/reminders/RemindersScreen.kt`; create `ui/reminders/RemindersViewModel.kt`
- Create: `data/export/CsvExporter.kt`, `app/src/main/res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider), `HabitlyApp.kt` (stop auto-seeding), `reminder/ReminderWorker.kt` (streak nudge), `ui/components/NotificationPermissionPrompt.kt` (becomes a sheet linking to Reminders)
- Test: `app/src/test/java/com/habitly/app/data/export/CsvExporterTest.kt`, `ui/you/YouViewModelTest.kt`, additions to `SettingsViewModelTest.kt`, `reminder/ReminderWorkerTest.kt`

**Interfaces:**
- Produces:
  - `object CsvExporter { fun export(habits: List<HabitEntity>, logs: List<HabitLogEntity>): String; fun fileName(today: LocalDate): String }` — header `habit,category,date,status,progress,unit,note`; rows sorted by habit name then date; RFC 4180 quoting (wrap in quotes when the value contains `,`, `"` or newline; double inner quotes); `fileName` → `habitly-export-2026-09-29.csv`.
  - `HabitDataSource.observeAllHabits(): Flow<List<HabitEntity>>` and `observeAllLogs(): Flow<List<HabitLogEntity>>` (thin pass-throughs to the DAOs; add to fakes).
  - `YouUiState(name: String?, memberSince: YearMonth?, activeHabits: Int, bestStreak: Int, checkIns: Int, perfectDays: Int, recentBadges: List<AchievementProgress>, nextBadge: AchievementProgress?, unlockedCount: Int)`; `YouViewModel(repository, prefs: YouPrefs, today)` with `onNameChange(String)`; `interface YouPrefs { val userName: Flow<String?>; suspend fun setUserName(name: String?) }` implemented by `SettingsPrefs`. Numbers come from `AchievementSnapshotBuilder` over all habits/logs; `recentBadges` = last 3 unlocked by `unlockedAt`; `nextBadge` = locked badge with the highest progress/target ratio.
  - `SettingsUiState` adds `weekStart`, `streakNudgeEnabled`, `versionName: String`, `isDebug: Boolean`; handlers `onWeekStartChange`, `onStreakNudgeToggle`, `onExport(): String` (returns CSV text; the screen writes the file and shares), `onLoadDemoData()` (calls `SeedDataProvider.seedIfEmpty`).
  - `RemindersUiState(habits: List<HabitEntity>)`, `RemindersViewModel(repository)` with `onSetReminder(id, time: String?)`.
  - `ReminderWorker` streak nudge: for each habit with a reminder time, if `now` is between reminder+30min and reminder+45min (the worker runs ~every 15 min), the habit is due today and not done, `currentStreak >= 2`, and `streakNudgeEnabled`, post "Keep your ${n}-day ${name} streak going" — extract the rule into `ReminderDueChecker.isStreakNudgeDue(reminder: LocalTime, now: LocalTime, streak: Int, doneToday: Boolean, dueToday: Boolean): Boolean` and unit-test it.

- [ ] **Step 1: Failing tests**

```kotlin
class CsvExporterTest {
    private fun habit(id: Long, name: String, unit: String? = null) = HabitEntity(id = id, name = name, icon = "star",
        colorHex = "#FF922B", category = "Mind", frequencyType = FrequencyType.DAILY, frequencyDays = null,
        intervalDays = null, reminderTime = null, createdAt = 0, isArchived = false, unit = unit)
    @Test fun writesAHeaderAndOneRowPerLog() {
        val csv = CsvExporter.export(listOf(habit(1, "Read")),
            listOf(HabitLogEntity(0, 1, "2026-09-28", CompletionStatus.DONE, 1L, null, 0)))
        assertEquals("habit,category,date,status,progress,unit,note\nRead,Mind,2026-09-28,DONE,0,,\n", csv)
    }
    @Test fun quotesValuesWithCommasQuotesAndNewlines() {
        val csv = CsvExporter.export(listOf(habit(1, "Read, slowly")),
            listOf(HabitLogEntity(0, 1, "2026-09-28", CompletionStatus.DONE, 1L, "said \"hi\"\nthen", 0)))
        assertTrue(csv.contains("\"Read, slowly\""))
        assertTrue(csv.contains("\"said \"\"hi\"\"\nthen\""))
    }
    @Test fun sortsByHabitThenDate() {
        val csv = CsvExporter.export(listOf(habit(1, "B"), habit(2, "A")), listOf(
            HabitLogEntity(0, 1, "2026-09-02", CompletionStatus.DONE, 1L, null, 0),
            HabitLogEntity(0, 2, "2026-09-03", CompletionStatus.DONE, 1L, null, 0),
            HabitLogEntity(0, 2, "2026-09-01", CompletionStatus.DONE, 1L, null, 0)))
        val dates = csv.lines().drop(1).filter { it.isNotBlank() }.map { it.split(",")[0] + it.split(",")[2] }
        assertEquals(listOf("A2026-09-01", "A2026-09-03", "B2026-09-02"), dates)
    }
    @Test fun fileNameIncludesTheDate() {
        assertEquals("habitly-export-2026-09-29.csv", CsvExporter.fileName(LocalDate.of(2026, 9, 29)))
    }
}
```

```kotlin
// ReminderDueCheckerTest additions
@Test fun streakNudgeFiresHalfAnHourAfterTheReminder() {
    assertTrue(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), streak = 3, doneToday = false, dueToday = true))
    assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 10), 3, false, true))
    assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), 1, false, true))
    assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), 3, true, true))
    assertFalse(ReminderDueChecker.isStreakNudgeDue(LocalTime.of(18, 0), LocalTime.of(18, 35), 3, false, false))
}
```

```kotlin
// YouViewModelTest
@Test fun summarisesLifetimeNumbers() = runTest {
    val vm = YouViewModel(fakeWithTwoDoneDays(), FakeYouPrefs("Sunny"), today = { LocalDate.of(2026, 9, 29) })
    advanceUntilIdle()
    val s = vm.uiState.value
    assertEquals("Sunny", s.name); assertEquals(2, s.checkIns); assertEquals(1, s.activeHabits)
}
@Test fun renamingSavesThroughPrefs() = runTest {
    val prefs = FakeYouPrefs(null)
    val vm = YouViewModel(fakeWithTwoDoneDays(), prefs, today = { LocalDate.of(2026, 9, 29) })
    vm.onNameChange("Ada"); advanceUntilIdle()
    assertEquals("Ada", prefs.userName.value)
}
```
(Build the fake from the one used in earlier tasks; it must implement `observeAllHabits`, `observeAllLogs`, `observeAchievementProgress`.)

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** `CsvExporter`, `isStreakNudgeDue` (`dueToday && !doneToday && streak >= 2 && !now.isBefore(reminder.plusMinutes(30)) && now.isBefore(reminder.plusMinutes(45))`), worker wiring, `YouViewModel`, settings VM additions, `RemindersViewModel`. **Step 4: Run** — PASS.
- [ ] **Step 5: Stop auto-seeding** — remove the `SeedWorker` enqueue from `HabitlyApp.onCreate`; keep `SeedWorker`/`SeedDataProvider` for the debug "Load demo data" action (call `SeedDataProvider.seedIfEmpty` directly from `SettingsViewModel.onLoadDemoData` on `viewModelScope`).
- [ ] **Step 6: FileProvider** — manifest:
```xml
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
</provider>
```
`file_paths.xml`: `<paths><cache-path name="exports" path="exports/" /></paths>`. Share: write to `cacheDir/exports/<fileName>`, `FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)`, `Intent(ACTION_SEND).setType("text/csv").putExtra(EXTRA_STREAM, uri).addFlags(FLAG_GRANT_READ_URI_PERMISSION)`, `startActivity(Intent.createChooser(intent, "Export habits"))`.
- [ ] **Step 7: You UI** — per spec §6.9: header row (64dp `primaryContainer` circle with initial in `titleLarge` `primary`; name `titleLarge` or "Add your name" in `primary`; "Since Aug 2026 · 4 habits" muted; tap → `HabitlySheet` with name `TextField` + Save); three `StatTile`s (Best streak with fire icon, Check-ins, Perfect days); awards preview `surface` card: row "Awards" + "5 of 16" + "See all" chevron → Awards; row of up to 4 badge circles (3 recent + next locked with a thin progress ring); `SettingsGroup` rows: Reminders (`NotificationsNone`), Archived habits (`Inventory2`), Settings (`Tune`).
- [ ] **Step 8: Settings UI** — per spec §6.11: `ScreenHeader("Settings", onBack)`; groups Appearance (Theme `SegmentedPill` Light/Dark/Auto as the row's full-width content; Week starts row → `DropdownMenu` Monday/Sunday); Reminders (Notifications `Switch`, Streak-at-risk nudge `Switch` disabled when notifications off, Habit reminders → Reminders); Data (Export CSV `Share` icon; Load demo data only when `isDebug`; Reset all data in `error` → `ConfirmSheet("Reset everything?", "This deletes every habit, all history and your awards. This can't be undone.", "Reset")`); About (Version value, "All data stays on this device" supporting text, Licences → `HabitlySheet` showing the OFL text read from `assets/licenses/OFL-PlusJakartaSans.txt`). `Switch` colours: checked track `primary`, thumb `onPrimary`.
- [ ] **Step 9: Reminders UI** — permission card on top: if `POST_NOTIFICATIONS` not granted (API 33+), `surface` card with `NotificationsActive` 48dp tile, "Get a nudge at the time you chose — only if the habit isn't done yet.", `PrimaryButton("Allow notifications")` (launch permission request); if denied twice (`shouldShowRequestPermissionRationale` false after a request), button reads "Open system settings" (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`). Below, `SettingsGroup("Habit reminders")` rows: `IconTile` + name + value time/"Off" → `TimePicker` sheet with "Set"/"Turn off". `NotificationPermissionPrompt` becomes a `HabitlySheet` shown once after the first check-in with "Turn on reminders" → navigates to Reminders, and "Not now".
- [ ] **Step 10: Build, all tests, emulator (dark + light)** — rename in You, export CSV (share sheet opens with the file), toggle prefs, week start Sunday changes Today's week strip and Calendar, reset with confirmation, Reminders permission flow.
- [ ] **Step 11: Commit** — `feat(you): You tab, rebuilt settings, reminders screen, CSV export and streak nudge`.

### Task 15: Onboarding

**Files:**
- Replace: `ui/onboarding/OnboardingScreen.kt`; create `ui/onboarding/OnboardingViewModel.kt`
- Test: `app/src/test/java/com/habitly/app/ui/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Produces: `OnboardingViewModel(repository: HabitDataSource, prefs: OnboardingStore, now: () -> Long = System::currentTimeMillis)`; `interface OnboardingStore { suspend fun setUserName(name: String?); suspend fun setCompleted() }` (adapter over `SettingsPrefs` + `OnboardingPrefs` in the NavHost). State: `OnboardingUiState(step: Int = 0, name: String = "", selected: Set<String> = emptySet(), isFinished: Boolean = false)`; handlers `onNext()`, `onBack()`, `onNameChange`, `onToggleTemplate(key)`, `onSkip()` (completes with no habits), `onFinish()` (creates one habit per selected template key in `ONBOARDING_TEMPLATE_KEYS` order via `HabitTemplate.toEntity(now)`, saves name, sets completed, `isFinished = true`). `val ONBOARDING_TEMPLATE_KEYS` = the 8 keys: drink_water, read_20_minutes, workout, meditate, sleep_by_11pm, morning_run, journal, stretch — verify against `HabitTemplates` and replace any key that does not exist with another existing template key. `fun HabitTemplate.toEntity(createdAt: Long): HabitEntity` lives in `domain/HabitTemplate.kt` and is reused by the Add/Edit prefill where practical.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun finishingCreatesTheSelectedHabitsAndSavesTheName() = runTest {
    val repo = RecordingDataSource(); val store = FakeOnboardingStore()
    val vm = OnboardingViewModel(repo, store, now = { 1000L })
    vm.onNameChange("Sunny"); vm.onToggleTemplate("drink_water"); vm.onToggleTemplate("meditate")
    vm.onFinish(); advanceUntilIdle()
    assertEquals(listOf("Drink water", "Meditate"), repo.added.map { it.name })
    assertEquals("Sunny", store.name); assertTrue(store.completed); assertTrue(vm.uiState.value.isFinished)
}
@Test fun skipCompletesWithoutHabits() = runTest {
    val repo = RecordingDataSource(); val store = FakeOnboardingStore()
    val vm = OnboardingViewModel(repo, store)
    vm.onSkip(); advanceUntilIdle()
    assertTrue(repo.added.isEmpty()); assertTrue(store.completed)
}
@Test fun togglingTwiceDeselects() = runTest {
    val vm = OnboardingViewModel(RecordingDataSource(), FakeOnboardingStore())
    vm.onToggleTemplate("drink_water"); vm.onToggleTemplate("drink_water")
    assertTrue(vm.uiState.value.selected.isEmpty())
}
@Test fun onboardingKeysAllExist() {
    ONBOARDING_TEMPLATE_KEYS.forEach { assertNotNull(it, HabitTemplates.byKey(it)) }
    assertEquals(8, ONBOARDING_TEMPLATE_KEYS.size)
}
@Test fun stepsAdvanceAndGoBack() = runTest {
    val vm = OnboardingViewModel(RecordingDataSource(), FakeOnboardingStore())
    vm.onNext(); vm.onNext(); assertEquals(2, vm.uiState.value.step)
    vm.onBack(); assertEquals(1, vm.uiState.value.step)
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Run** — PASS.
- [ ] **Step 5: UI** — `OnboardingScreen(uiState, handlers…)`, full-bleed `background`, `statusBarsPadding` + `navigationBarsPadding`:
  - Top row: 4 progress dashes (20×4dp, radius 2, `onSurface` for ≤ step, `outline` otherwise, animated width 28dp for current), "Skip" `TextButton` right (hidden on step 3).
  - Step 0 "Small days add up": a 12-week × 7 `HabitGrid`-style canvas (brand tones) whose cells fill in left-to-right over 1.6s (`Animatable` progress; static when reduce motion); headline `displaySmall` "Small days\nadd up"; body `bodyLarge` muted "Tick off a habit, watch your grid fill in. That's the whole idea.".
  - Step 1 "Streaks that forgive": illustration row of 7 large cells (done, done, rest (low + Remove glyph), done, done, done, today outline) + "Missed a day for a good reason? Mark it as rest and your streak carries on."
  - Step 2 "What should we call you?": filled `TextField` (radius 16), "Optional — used for your greeting."; button "Continue".
  - Step 3 "Pick a few habits to start": 2-column grid of the 8 templates (tile = `habitTones(color).low` fill + 2dp `full` border when selected, `IconTile` glyph, name `titleSmall`, check badge top-right when selected); button "Add ${n} habits" (disabled at 0) + `TextButton("Start with no habits")` → `onSkip`.
  - Bottom `PrimaryButton` "Next" (steps 0–1). System back → `onBack` (step > 0).
  - Step transitions: `AnimatedContent` slide + fade (reduce motion: fade only).
  - NavHost: when `isFinished`, navigate to `Destination.Today.route` popping `onboarding` inclusive.
- [ ] **Step 6: Build, tests, emulator** — `adb shell pm clear com.habitly.app`, launch: splash → onboarding; complete all steps selecting 3 habits → Today shows them and "Good …, Sunny"; clear again and Skip → Today empty state.
- [ ] **Step 7: Commit** — `feat(onboarding): four-step onboarding with name and starter-habit picker`.

---

## Phase 5 — Polish

### Task 16: Cleanup, light theme, large screens, font scale, accessibility pass

**Files:**
- Delete any now-unused files (`grep` each candidate for references first): `ui/components/Surfaces.kt` (`SectionCard`, `OutlinedSectionCard`), `ConfirmDialog.kt`, `HabitCard.kt`, `CalendarHeatmap.kt`, `ContributionGrid.kt`, `BarChart.kt`, `ProgressRing.kt` (if unused), `ui/home`, `ui/allhabits`, `ui/splash` leftovers; old colour constants.
- Modify: whichever screens fail the checks below.
- Test: `app/src/test/java/com/habitly/app/ui/theme/ThemeContrastTest.kt`

- [ ] **Step 1: Failing contrast test for the §3.1 roles**

```kotlin
class ThemeContrastTest {
    @Test fun `body text meets 4_5 on its surfaces in both themes`() {
        listOf(DarkOnSurface to DarkSurface, DarkOnSurface to DarkBackground, DarkOnSurfaceMuted to DarkSurface,
            LightOnSurface to LightSurface, LightOnSurface to LightBackground, LightOnSurfaceMuted to LightSurface,
            DarkBackground to DarkOnSurface /* primary button */, LightSurface to LightOnSurface)
            .forEach { (fg, bg) -> assertTrue("$fg on $bg", contrastRatio(fg, bg) >= 4.5f) }
    }
    @Test fun `brand and state colours are visible graphics`() {
        listOf(DarkBrand to DarkSurface, DarkSuccess to DarkSurface, DarkDanger to DarkSurface, DarkWarning to DarkSurface,
            LightBrand to LightSurface, LightSuccess to LightSurface, LightDanger to LightSurface, LightWarning to LightSurface,
            DarkOutline to DarkBackground)
            .forEach { (fg, bg) -> assertTrue("$fg on $bg", contrastRatio(fg, bg) >= if (fg == DarkOutline) 1.3f else 3f) }
    }
}
```
If a pair fails, adjust the **token value** (smallest change that passes), update the spec table, and re-run.

- [ ] **Step 2: Run** — fix tokens until PASS.
- [ ] **Step 3: Delete unused files**; build; all tests PASS.
- [ ] **Step 4: Manual matrix on the emulator** — for each of Today, Habits, Detail, Form, Insights (Stats, Calendar), You, Awards, Settings, Reminders, Onboarding:
  - dark and light theme;
  - `adb shell settings put system font_scale 2.0` (restore with `1.0`): nothing clipped, names wrap, buttons grow;
  - `adb shell settings put global animator_duration_scale 0` (restore `1`): no confetti/count-up;
  - tablet width: `adb shell wm size 1600x2560` (restore with `adb shell wm size reset`): Today two-pane, Insights side-by-side;
  - TalkBack spot-check of Today (cards announce name, state, streak; check button is a checkbox) and Calendar (day cells announce date + fraction).
  Fix every defect found, in the component where possible.
- [ ] **Step 5: Report screenshots** — capture every screen (dark + light) into `docs/screenshots/2026-09-29/` with `adb exec-out screencap -p > file.png`, named `NN-screen-theme.png`.
- [ ] **Step 6: Commit** — `chore(ui): remove legacy components, fix contrast, large-screen and font-scale issues`, then `docs: add redesign screenshots` for the images.

---

## Self-review notes

- Spec coverage: §3 → Tasks 1–2, 4; §4 → Tasks 5–6; §5 → Task 7; §6.1 → 4; §6.2 → 15; §6.3 → 8; §6.4 → 11; §6.5 → 9; §6.6 → 10; §6.7–6.8 → 12; §6.9, 6.11, 6.12 → 14; §6.10 → 13; §7 → 3, 13, 14; §8 → 5, 6, 16; §9 → tests in each task + 16; §10 → phase order.
- Deviation recorded: `SECOND_CHANCE` = any rest day (repairs and rest days are indistinguishable in storage).
- Type names used across tasks: `HabitTonesFactory.of`, `habitTones`, `LocalHabitlyColors`, `LocalReduceMotion`, `CheckState`, `GridMath`, `HabitGridCard`, `HabitRow`, `NavItem`, `TAB_DESTINATIONS`, `isTabRoute`, `rememberHabitRepository`, `habitlyViewModel`, `HabitTemplate.key`, `HabitTemplates.byKey`, `TodayPrefs`, `YouPrefs`, `OnboardingStore`, `AchievementProgress`, `AchievementSnapshot(Builder)`, `CsvExporter`.
