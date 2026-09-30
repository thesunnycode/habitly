# Habitly — premium redesign ("Dark grid") design spec

**Date:** 2026-09-29
**Supersedes (visual/IA sections only):** `docs/specs/2026-08-23-habitly-ca1-design.md` §3, §6
**Keeps:** the existing domain layer (streaks, schedules, skip/rest days, goals, InsightEngine,
contribution levels), Room + MVVM architecture, WorkManager reminders.

## 1. Why

A per-screen audit on the Pixel emulator (2026-09-29) found the app functional but not premium:

| Screen | Finding |
| --- | --- |
| Splash, Onboarding | Placeholder — plain text on white, no pagination, no visuals, no setup step |
| All Habits | Duplicates Home's cards; no management purpose; "0 of 4 done" shown twice |
| Habit Detail | **No Edit entry point** (Edit screen unreachable); archive is silent and irreversible |
| Add/Edit | **No reminder time picker**, no category picker, one long flat form |
| Calendar | Tapping a day does nothing; six-month grid is mostly empty |
| Awards | 4 badges, half-empty screen, no progress, no celebration |
| Settings | 3 cards; no export, about, week start, per-habit reminders; reset styled like a normal button |
| Global | No empty states, no micro-interactions, archived habits unrecoverable |

The user's direction: every screen redone to feel like a premium app, in the **HabitKit-style
"bold dark grid"** direction, dark-first with a matching light theme, four-tab navigation.

## 2. Goals and non-goals

**Goals**
1. One coherent design system (tokens, type, components, motion) used by every screen.
2. Every screen in §5 rebuilt on that system, including all flows the CA1 spec promised but never
   shipped (reminder picker, category picker, CSV export, About, permission explainer).
3. Fix the broken flows: Edit reachable, archive undoable + archived list, calendar day tap.
4. Keep every existing domain behaviour and its unit tests green.

**Non-goals** (unchanged from the inspiration audit): home-screen widgets, share cards, Pomodoro,
cloud sync, accounts, AI capture (CA3 scope).

## 3. Design system

### 3.1 Colour — neutral canvas, colour comes from habits

| Role | Dark (default) | Light |
| --- | --- | --- |
| `background` | `#0E0F12` | `#F4F5F7` |
| `surface` (card) | `#17191E` | `#FFFFFF` |
| `surfaceRaised` (inputs, segments, sheets) | `#22252C` | `#ECEEF2` |
| `outline` (hairlines, empty-state borders) | `#2A2E36` | `#DADDE3` |
| `gridEmpty` (empty contribution cell) | `#262930` | `#E6E8EC` |
| `onBackground` / `onSurface` | `#F2F3F5` | `#111317` |
| `onSurfaceMuted` | `#8B919C` | `#6B7280` |
| `onSurfaceFaint` (labels, inactive nav) | `#6B717C` | `#8A909A` |
| `brand` (active tab, hero, focus) | `#9B8CFF` | `#6A55F0` |
| `brandContainer` | `#2A2640` | `#ECE8FF` |
| `heroContainer` (Insights hero) | `#1D1A33` | `#ECE8FF` |
| `success` / `danger` / `warning` | `#51CF66` / `#FF6B6B` / `#FCC419` | `#2F9E44` / `#E03131` / `#E67600` (nudged from #E67700 to clear 3:1 on white) |
| Primary button | `#F2F3F5` on-text `#0E0F12` | `#111317` on-text `#FFFFFF` |

Implemented as a Material 3 `ColorScheme` plus a `HabitlyColors` `CompositionLocal` for the roles
Material has no slot for (`surfaceRaised`, `gridEmpty`, `onSurfaceFaint`, `heroContainer`,
`success`, `warning`). Theme mode: System / Light / Dark from Settings; **default is Dark**.

### 3.2 Habit palette — 12 seeds, four tones each per theme

Seeds: orange `#FF922B`, blue `#4DABF7`, violet `#9775FA`, green `#51CF66`, red `#FF6B6B`,
yellow `#FCC419`, cyan `#22B8CF`, pink `#F06595`, lime `#94D82D`, indigo `#748FFC`,
salmon `#FF8787`, grey `#CED4DA`.

A new `HabitTones.of(seedHex, isDark)` returns four tones:

| Tone | Dark | Light | Used for |
| --- | --- | --- | --- |
| `full` | seed | seed darkened to ≥ 3:1 on white | check fill, full grid cell, icon glyph, CTA |
| `mid` | seed × 0.72 lightness | seed at 60% over white | grid level 2 |
| `low` | seed × 0.36 lightness | seed at 25% over white | icon tile bg, grid level 1, + button bg |
| `onFull` | `#0E0F12` | `#FFFFFF` | glyph on the check fill |

Lightness is varied in HSL, never alpha, so every cell stays perceivable (the audit's WCAG 1.4.11
finding). Existing habits whose `colorHex` is not a palette seed are mapped to the nearest seed by
hue at read time — no data migration.

### 3.3 Typography — Plus Jakarta Sans (OFL), bundled in `res/font`

| Token | Size / line | Weight | Use |
| --- | --- | --- | --- |
| `display` | 32 / 38 | 800 | hero numbers (78%, streak count) |
| `headline` | 26 / 32 | 800 | screen titles ("Today", "Habits") |
| `title` | 20 / 26 | 700 | sheet titles, habit name on detail |
| `titleSmall` | 16 / 22 | 700 | card titles, habit names on cards |
| `body` | 15 / 22 | 500 | body copy |
| `bodySmall` | 13 / 18 | 500 | metadata ("12 days · Daily") |
| `label` | 12 / 16 | 600, +0.6 letter-spacing | section labels ("HEALTH · 2"), tile captions |
| `stat` | 22 / 26 | 800, tabular figures | tile numbers |

All sizes in `sp`; nothing blocks system font scaling. Habit names wrap to two lines, never
ellipsise at one.

Font files: Regular, Medium, SemiBold, Bold, ExtraBold `.ttf` from the Google Fonts repository
(OFL 1.1). Downloading them requires the user's go-ahead at implementation time.

### 3.4 Shape, spacing, elevation

- Radii: card 20dp · tile / input / icon tile 12dp · grid cell 3dp · chips, buttons, segments pill.
- Screen margin 20dp; card padding 16dp; gap between cards 10dp; section gap 24dp.
- No shadows in dark mode; separation is tonal (background → surface → raised). In light mode, cards
  get a 1dp `outline` border instead of elevation.
- Minimum touch target 48dp everywhere; visual can be smaller if the target is expanded.

### 3.5 Motion and feedback

| Moment | Behaviour |
| --- | --- |
| Check-off | Check button spring scale 1 → 0.88 → 1 (stiffness medium, ~250ms) + `HapticFeedbackType.LongPress`; newest grid cell fades in |
| Goal increment (+) | Same spring, progress text animates, button becomes ✓ when target reached |
| Numbers | Stat/hero numbers count up on first composition (`animateIntAsState`, 600ms) |
| All done today | One-shot celebration overlay: confetti burst + "All done for today" card, auto-dismiss 2.5s |
| Badge unlocked | Full-screen dialog: badge scales in with bounce, title, "Continue" |
| Screen transitions | Tabs crossfade (150ms); pushed screens slide + fade (250ms) |

All non-essential motion (confetti, count-up, bounce) is skipped when
`Settings.Global.ANIMATOR_DURATION_SCALE == 0` (system reduce-motion), exposed as
`LocalReduceMotion`.

## 4. Component kit (`ui/components/`)

New or replaced components. Each is a stateless composable with a `@Preview` for dark and light.

| Component | Purpose |
| --- | --- |
| `HabitGridCard` | Today card: icon tile, name, meta line (flame + streak · schedule or "5 / 8 glasses"), `CheckButton`, and a `HabitGrid` of the last 16 weeks × 7 days. Tap → detail, long-press → `HabitStatusSheet` (skip / rest / note) |
| `CheckButton` | 48dp square (12dp radius). States: empty (outline), done (full + ✓), goal-in-progress (low + "+"), skipped (low + dash). `Role.Checkbox` with `stateDescription` |
| `HabitGrid` | Contribution grid, configurable weeks and cell size; 4 tone levels + `gridEmpty`; one semantics node summarising the range; cells `clearAndSetSemantics` |
| `HabitRow` | Dense 64dp manage row: icon tile, name + schedule, 7-day mini strip, optional drag handle |
| `IconTile` | Rounded square with `low` bg and `full` glyph, sizes 28/36/48dp |
| `StatTile` (replaced) | Number in `stat` + caption in `label`, on `surface`; optional accent colour for the number |
| ~~`HeroCard`~~ | Removed — superseded by the Stats hero in §11 |
| `SegmentedPill` | 2–3 option pill switch (Active/Archived, Stats/Calendar, Light/Dark/Auto, Week/Month/Year) |
| `SettingsGroup` / `SettingsRow` | Grouped rounded list; row = leading icon, label, optional supporting text, trailing value / chevron / switch |
| `SectionLabel` | Uppercase tracked label with a count ("HEALTH · 2") |
| `PrimaryButton` / `SecondaryButton` | Full-width pill buttons per §3.1 |
| `HabitlyNavBar` | 4-tab floating bar: `surface`, 20dp radius, 12dp inset from edges; selected item gets a `brandContainer` pill + `brand` icon; labels always shown |
| `HabitlySheet` | `ModalBottomSheet` wrapper with drag handle, 28dp top radius, `surfaceRaised` |
| `EmptyState` | Icon in a 64dp tile, title, one sentence, one primary action |
| `ConfirmSheet` | Destructive confirm as a bottom sheet: icon, title, message, danger button + cancel (replaces `ConfirmDialog`) |
| `Celebration` | Confetti + message overlay, reduce-motion aware |
| `ScreenHeader` (replaced) | Optional back/close, `headline` title, optional subtitle, trailing actions |

Components removed once unused: `SectionCard`, `OutlinedSectionCard`, `HabitCard`,
`CalendarHeatmap` (month grid is re-implemented as `MonthGrid`), `ConfirmDialog`.

## 5. Information architecture

```
Bottom tabs:  Today · Habits · Insights · You

Today ──► Habit detail ──► Edit habit
  └─(+)─► Template sheet ──► New habit
Habits [Active | Archived] ──► Habit detail
Insights [Stats | Calendar] ── Calendar ──► Day sheet
You ──► Awards ──► Badge sheet
    ──► Reminders (permission explainer + per-habit list)
    ──► Archived habits  (= Habits tab, Archived segment)
    ──► Settings
Onboarding (first launch only) ──► Reminders explainer (after first check-in)
```

Routes: `today`, `habits?segment={active|archived}`, `insights?tab={stats|calendar}`, `you`,
`awards`, `reminders`, `settings`, `habit_detail/{habitId}`,
`add_edit_habit?habitId={id}&template={key}`, `onboarding`. Every screen is within two taps of
Today. Tabs replace, pushed screens stack; the nav bar is hidden on pushed screens.

Repeated repository/ViewModel-factory boilerplate in `HabitlyNavHost` (eight copies) is replaced by
one `rememberHabitRepository()` and a `viewModelFactory { }` helper, since every screen's wiring is
being touched anyway.

## 6. Screens

### 6.1 Splash
`androidx.core:core-splashscreen` system splash: Habitly mark (a 3×3 grid glyph, brand-coloured
cells) on `background`. `setKeepOnScreenCondition` holds only until the onboarding flag is read.
The Compose `SplashScreen` route is deleted.

### 6.2 Onboarding (4 steps)
Top bar: progress dashes (4) left, "Skip" right. Bottom: primary pill button.
1. **Welcome** — animated grid filling in (reduce-motion: static), "Small days add up".
2. **Forgiving streaks** — illustration of a rest-day cell, "Miss a day? Mark it rest and keep going".
3. **Your name** — single optional text field; saved to `userName` pref; "Continue" / "Skip".
4. **Pick habits** — 2-column tile grid of 8 templates from `HabitTemplate`; multi-select; button
   reads "Add N habits" (disabled at 0, with "Or start empty" text button).

Finishing creates the chosen habits, sets the onboarding flag, and lands on Today. Skip from any
step lands on Today with no habits (empty state). The debug seed no longer runs automatically; it
moves to Settings → "Load demo data" (debug builds only) so report screenshots stay easy.

### 6.3 Today
- Header: small date (`bodySmall`, muted), "Today" (`headline`) or the selected date's name when
  viewing a past day, progress ring (done/due) on the right. Greeting line "Good evening, {name}"
  under the title only when a name is set.
- Week strip: last 7 days as rounded date chips; selected = primary-button colours; a dot under each
  day shows done (all), partial, none. Tapping a past day switches the list to that day.
- Streak-at-risk banner (warning tint) after 18:00 when a habit with streak ≥ 2 is still due.
- List: `HabitGridCard` for each due habit, sorted by `sortOrder`. Completed habits move into a
  collapsible "DONE · n" section below.
- Not-due-today habits appear in a collapsed "NOT TODAY · n" section.
- FAB: extended pill "+ New" (primary colours), bottom-end above the nav bar; list gets bottom
  padding so it never covers a card.
- Empty state: `EmptyState` ("Start with one habit") + three one-tap template chips.
- All-done: `Celebration` once per day (tracked in prefs by date).
- Expanded width: list and `HabitDetail` side by side (existing two-pane behaviour kept).

### 6.4 Habits (manage)
- Header "Habits" with a sort menu (Custom, Name, Streak, Created).
- `SegmentedPill`: Active n · Archived n.
- Search field (`surfaceRaised`, no outline).
- Category chips row: "All n", then each category with count.
- Grouped by category with `SectionLabel`s; `HabitRow`s.
- Custom sort enables long-press-drag reorder, persisted to `sortOrder`.
- Swipe row left → archive (Active) / restore (Archived), with a snackbar "Archived Read · Undo".
- Archived rows: muted, trailing "Restore" text button; overflow has "Delete permanently" →
  `ConfirmSheet`.
- Empty search: inline "No habits match 'x'" + "Clear filters".
- Empty archived: "Nothing archived".

### 6.5 Habit detail
- Top bar: back, overflow menu (Edit, Archive, Delete). Archive pops back and shows an Undo
  snackbar on the previous screen; Delete opens `ConfirmSheet`.
- Hero: `IconTile` 48dp, name (`title`), meta "Category · schedule · reminder time".
- `HabitGrid`, full width, last 18 weeks (26 on expanded width).
- Stat tiles 2 × 2: Current streak (habit colour), Best, Rate (schedule-aware), Total days.
- Streak-repair card (existing logic), restyled on `surface` with habit-colour accent.
- `MonthGrid` with month switcher. Tap a day → toggle done; long-press → status sheet (skip/rest/
  note). Future days disabled. Goal habits show progress fraction in the sheet.
- Sticky bottom: primary pill in habit colour — "Done today" / "Undo today" / "+1 glass (5/8)".

### 6.6 New / edit habit
- **Template sheet** (from any "+"): title "New habit", search field, "Start from scratch" tile
  first, then 2-column tinted template tiles grouped by `HabitTemplate` category. Picking one opens
  the form pre-filled (name, icon, colour, category, frequency, goal, reminder).
- **Form** (full screen, close ✕ top-left, title "New habit"/"Edit habit"):
  1. Live preview `HabitGridCard` (sample grid in the chosen colour).
  2. Name field with leading `IconTile` → icon picker sheet (6-column grid, ~36 icons).
  3. Colour row: 12 palette swatches, selected has a ring.
  4. Category: chips of existing categories + "New…" (inline text field).
  5. Repeat: `SegmentedPill` Daily / Days / Interval; Days shows 7 circles; Interval shows a stepper
     "Every [2] days".
  6. Daily goal row → sheet with number stepper + unit field (suggested units chips).
  7. Reminder row → Material 3 `TimePicker` dialog; "Off" when unset; clear action.
  - Sticky "Create habit" / "Save" pill; disabled until the name is valid; validation errors inline.
  - Back/close with unsaved changes → `ConfirmSheet` "Discard changes?".

### 6.7 Insights — Stats
`SegmentedPill` Stats/Calendar under the "Insights" header, then the existing Stats content
restyled:
- Week / Month / Year text tabs.
- `HeroCard`: "Completion", display %, delta vs previous period (arrow, success/danger colour),
  bar chart of the period (weakest bar in `danger`).
- "Noticed" insight card (InsightEngine output), brand sparkle icon.
- Tile row: Current streak, Best ever, Perfect days, Total done.
- Weekly pattern chart; Habit-by-habit list (Going well / Slipping groups) with sparkline + rate.
- Low-data states as currently implemented.

### 6.8 Insights — Calendar
- Month switcher; `MonthGrid` 7 columns, 40dp+ cells, fill = share of due habits done that day
  (4 brand tones + empty); today ringed; selected day outlined.
- Legend (Less → More).
- Tapping a day opens the **Day sheet**: "Thursday 24 Sep · 3 of 4", each due habit with its
  `CheckButton` (editable for past days), plus any note.
- Below the month: "Days active" and "Consistency" tiles for the month.
- The six-month all-habits grid is removed from this tab. In Stats, when the period is **Year**, the
  hero's chart slot shows a 52-week all-habits `HabitGrid` (brand tones) instead of bars.

### 6.9 You
- Profile header: initial avatar (brandContainer), name or "Add your name" (tap → edit sheet),
  "Since {month} · n habits".
- Lifetime tiles: Best streak, Check-ins, Perfect days.
- Awards preview card: "Awards · x of 16", last 3 unlocked + next locked with progress, "See all".
- `SettingsGroup`: Reminders, Archived habits, Settings (chevrons).

### 6.10 Awards
- Back, "Awards" header, overall progress bar "x of 16 unlocked".
- Sections Streaks / Consistency / Volume / Special, 3-column grid.
- Unlocked: coloured circle + glyph, title, unlock date. Locked: grey lock, title, mini progress bar
  + "12/30".
- Tap → badge sheet: large badge, criterion, date earned or progress.
- New unlocks (detected on any screen via repository flow) show the badge `Celebration` dialog once.

Badge set (existing four keys keep their stored names):

| Group | Key | Criterion |
| --- | --- | --- |
| Streaks | `STREAK_3`, `STREAK_7`, `STREAK_30`, `STREAK_100` | best streak on any habit ≥ n |
| Consistency | `PERFECT_DAY` | all due habits done on one day |
| | `PERFECT_WEEK` | 7 consecutive perfect days |
| | `MONTH_80` | ≥ 80% completion over a full calendar month |
| | `COMEBACK` | complete a habit on the due day after a broken streak of ≥ 3 |
| Volume | `CHECKINS_10`, `CHECKINS_100`, `CHECKINS_500` | total completed logs ≥ n |
| | `GOAL_10` | numeric goal fully met on 10 days |
| Special | `FIRST_HABIT` | create a habit |
| | `FIVE_HABITS` | 5 active habits at once |
| | `EARLY_BIRD` | 10 completions with `completedAt` before 08:00 |
| | `SECOND_CHANCE` | use streak repair once |

`AchievementEvaluator` takes an `AchievementSnapshot` (best streak, perfect-day series, monthly
rates, total check-ins, goal days, early completions, active habit count, repair used, comeback
flag) and returns newly unlocked keys plus `progress(key): Pair<Int, Int>` for locked ones.

### 6.11 Settings
Back, "Settings" header, grouped rows:
- **Appearance:** Theme (`SegmentedPill` Light/Dark/Auto), Week starts (Monday/Sunday → week strip,
  calendars, weekly stats).
- **Reminders:** Notifications master switch, Streak-at-risk nudge switch, "Habit reminders" →
  Reminders screen.
- **Data:** Export CSV (writes `habitly-export-YYYY-MM-DD.csv` to cache and opens the share sheet via
  `FileProvider`), Load demo data (debug only), Reset all data (danger text → `ConfirmSheet`).
- **About:** version, "All data stays on this device", open-source licences (font OFL).

### 6.12 Reminders
- Permission state card: if not granted, explainer ("Get a nudge at the time you chose, only if the
  habit isn't done yet") + "Allow notifications"; if permanently denied, "Open system settings".
- Per-habit list: `HabitRow` with trailing reminder time; tap → `TimePicker`; switch to turn off.
- Also shown once as a sheet after the first ever check-in (replaces `NotificationPermissionPrompt`).

## 7. Data changes

- **Room v3 → v4** (`MIGRATION_3_4`): `ALTER TABLE habits ADD COLUMN sortOrder INTEGER NOT NULL
  DEFAULT 0`, then `UPDATE habits SET sortOrder = id`. `HabitDao.getActiveHabits` orders by
  `sortOrder, createdAt`; new `getArchivedHabits()`; `updateSortOrders(list)`.
- Repository: `archive/restore/delete(id)`, `reorder(ids)`, `setReminder(id, time?)`,
  `exportCsv(): String`, `achievementSnapshot(): Flow<AchievementSnapshot>`.
- **Prefs** (`SettingsPrefs`): `userName: String?`, `weekStart: DayOfWeek` (default Monday),
  `streakNudgeEnabled: Boolean` (default true), `lastCelebratedDate: String?`,
  `seenBadgeKeys: Set<String>`.
- `ReminderWorker`: adds the streak-at-risk notification (≈30 min after reminder time, habit still
  due, streak ≥ 2, nudge enabled).

## 8. Accessibility

Carries forward every item in the inspiration audit's remediation list, plus:
- Contrast: text ≥ 4.5:1 and graphical objects ≥ 3:1 against their surface in both themes, checked
  by a unit test over `HabitTones` for all 12 seeds and against the §3.1 roles.
- State is never colour-only: check states have glyphs; calendar days carry the day number and a
  content description ("24 September, 3 of 4 done").
- Grids expose one summary semantics node per grid (or per month).
- TalkBack order follows visual order; sheets trap focus; every icon button has a label.
- Layouts verified at 200% font scale on Today, Detail, Form and Settings.

## 9. Testing

- Existing unit tests stay green (domain untouched except `AchievementEvaluator`).
- New unit tests: `HabitTones` (tones + contrast), `AchievementEvaluator` (every key, progress),
  `MIGRATION_3_4` (Room migration test), CSV export format, reorder/archive/restore repository ops,
  onboarding creating selected templates.
- ViewModel tests for new state: Habits segments/sort/search, Calendar day selection, You
  aggregates, Settings new prefs.
- Manual emulator pass per screen in dark and light, compact and expanded widths, 200% font scale;
  screenshots saved for the CA1 report.

## 10. Delivery phases

Each phase ends with a build, tests, an emulator check of the touched screens, and a commit.

1. **Foundation** — fonts, colour/type/shape tokens, `HabitTones`, theme default Dark,
   core-splashscreen, Room `MIGRATION_3_4` + new prefs (§7), since Today already sorts by `sortOrder`.
2. **Component kit** — §4 components with previews.
3. **Navigation** — 4-tab shell, routes, NavHost wiring helper, You/Insights containers.
4. **Today** — incl. template sheet, celebration, empty state.
5. **Habit detail + New/Edit form** — incl. Edit entry, reminder/category pickers, discard guard.
6. **Habits manage** — segments, reorder, archive/restore with undo.
7. **Insights** — Stats restyle, Calendar + Day sheet.
8. **Awards** — 16 badges, evaluator, progress, celebration.
9. **You, Settings, Reminders** — prefs, CSV export, permission flow.
10. **Onboarding** — 4 steps, template picker, seed moved to Settings.
11. **Polish pass** — light theme, expanded width, 200% font, TalkBack, reduce motion, report screenshots.

## 11. Addendum (2026-09-30) — Stats redesign: "heatmap hero" with habit colours

User feedback after delivery: the Stats view "looks bad". Researched TickTick, Checker, Kann, Apple
Fitness Trends, Duolingo, Todoist, Gentler Streak; the user chose direction A ("heatmap hero") with
habit colours via **filter chips + colour stacks**. This section replaces §6.7.

Top to bottom (Stats segment of Insights; the Calendar segment is unchanged):
1. Header: "Insights" title with a compact `SegmentedPill` **W · M · Y** at the right; the
   Stats/Calendar pill below as today. The old text period tabs are removed.
2. **Habit chips** (horizontal scroll): `All` + one chip per active habit (colour dot + name).
   Selected chip: habit `low` tone background, `full` text/outline; `All` selected uses brand tones.
3. **Hero card** (`surface`, radius 24):
   - Label (`labelMedium`): "All habits · Last 30 days" / "{habit} · Last 7 days" …
   - Big % (`displaySmall`) + caption "of due days kept"; change chip (success/danger tint, arrow;
     neutral "Same as before" at 0; hidden when there is no previous period).
   - Grid of the window's days, laid out in weeks aligned to the week-start pref (weekday header
     row). Window = the same rolling window the % uses: Week 7 days, Month 30 days, Year 52 weeks.
     The Week view is a single rolling 7-day row ending today; its header shows each day's own
     weekday rather than the week-start order (Month rows are aligned to the week-start pref).
     - **All + Week/Month:** each day cell is a **colour stack** — each active habit owns 1/N of the
       cell (N = active habits); a habit DONE that day fills its slice in its `full` tone, a declared
       rest day in its `low` tone (rest slices on top), stacked bottom-up; the unfilled remainder
       stays `gridEmpty`, so fill height shows how much of the day was kept.
     - **Habit selected:** cells use that habit's tones (DONE full, PARTIAL mid, SKIPPED low).
     - **Year:** a 52-week `HabitGrid` (8dp cells, horizontal scroll opened at the latest week);
       All uses brand intensity (stacks are unreadable at that size), a habit uses its colour.
     - Today outlined; days outside the habit's life / future days faint.
     - Cell semantics: "24 September: Water, Read done" (grid exposes one summary node + per-cell
       descriptions only in the Week view).
   - One insight line under the grid (`bodyMedium` muted), from the first InsightEngine insight for
     the current filter, e.g. "Best on Wednesdays · Sundays slip most".
4. **2×2 tiles**: Current streak, Best ever, Perfect days, Total done — for the selected habit
   (Perfect days → "Rest days" when a single habit is selected) or all habits.
5. **Habits list** (`surface` card): per habit — `IconTile(32)`, name, a 4dp progress bar in the
   habit's `full` tone showing its rate, the %, and a trend arrow (up success / down danger / none
   at 0). Tapping a row opens that habit's Detail. Sorted by rate descending.

Removed from Stats: the "What we noticed" paragraph card, the lavender hero with bar chart and red
weakest bar, the weekly-pattern chart, the Going well / Slipping group labels, red "failure"
colouring. Low-data states stay (locked copy when there is not enough history).
