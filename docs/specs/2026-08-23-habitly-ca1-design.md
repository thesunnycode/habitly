# Habitly — CA1 UI/UX-Focused Mobile Application Design

**Date:** 2026-08-23
**Course component:** CA1: UI/UX-Focused Mobile Application Development and Deployment (15 marks)
**Forward context:** CA3 will add an AI/ML feature on top of this same app (habit-risk prediction or
next-habit recommendation), so the data model is designed to already capture the signal that phase
will need, without requiring a schema migration.

## 1. Goal

Design and build a native Android habit-tracking app ("Habitly") that satisfies the CA1 rubric:
clean modern UI, intuitive UX with simple navigation, full responsiveness across screen sizes,
interactive components (buttons, forms, lists, dialogs, menus), consistency/readability/
accessibility/usability, meaningful functional features, and proper layout structure/visual
balance — plus a project report.

## 2. Tech Stack

- **Language:** Kotlin
- **UI toolkit:** Jetpack Compose + Material 3
- **Architecture:** MVVM (Repository → ViewModel → Compose UI), `StateFlow` for UI state
- **Persistence:** Room (local SQLite), fully offline — no backend
- **Navigation:** Navigation-Compose with a bottom-navigation shell
- **Background work:** WorkManager (periodic ~15 min worker) for reminder notifications
- **Charts:** custom Compose Canvas-drawn bar/line charts (avoids pulling in a third-party
  charting library's alpha-stage API before it's been verified against a real build)
- **Calendar heatmap:** custom Compose composable (GitHub-contribution-grid style), no external
  library
- **Min SDK:** 26 (Android 8.0) — covers effectively all real devices while keeping Compose/Room
  APIs simple; **Target/Compile SDK:** latest stable (35)

## 3. Information Architecture / Screens

Bottom navigation with 5 destinations: **Home, Stats, Calendar, Achievements, Settings**. Two
screens live outside the bottom nav (pushed on top): Add/Edit Habit and Habit Detail.

| # | Screen | Key elements | Rubric hooks |
|---|--------|--------------|---------------|
| 1 | Splash | Logo + brief animation, routes to Onboarding (first launch) or Home | Visual polish |
| 2 | Onboarding (3-page pager) | Illustration + copy per page, "Skip"/"Next", final page requests notification permission (Android 13+) | Smooth UX, permission handling |
| 3 | Home ("Today") | Top progress ring (today's completion %), scrollable list of habit cards (icon, name, streak flame, one-tap checkbox), FAB "+" to add habit, empty state | One-tap interaction, lists, empty state |
| 4 | Add/Edit Habit | Text field (name), icon picker grid, color swatch picker, category dropdown/chips, frequency selector (Daily / Specific days / Every N days), reminder time picker, optional note-template toggle | Forms, dialogs, menus |
| 5 | Habit Detail | Header (icon/name/category), calendar heatmap for that habit, stat tiles (current streak, best streak, completion %), Edit/Archive/Delete actions (confirmation dialog) | Data viz, dialogs |
| 6 | All Habits | Grouped-by-category list, filter chips, search bar | Lists, filtering |
| 7 | Stats/Analytics | Weekly & monthly bar charts (Vico), completion-rate trend line, best/worst habit callout cards, date-range selector | Charts, responsiveness |
| 8 | Calendar Heatmap (global) | Month grid, color intensity = habits completed that day, month navigation arrows | Custom visualization |
| 9 | Achievements | Grid of badge cards (locked/unlocked state) for streak milestones (7/30/100 days, first habit, etc.) | Gamification, visual feedback |
| 10 | Settings | Theme toggle (light/dark/system), notification master switch + per-habit reminder overview, reset-data (with confirmation dialog), export data (CSV via share sheet), About | Menus, dialogs, accessibility toggle |
| 11 | Permission/Reminder sub-screen | Explains why notification permission is needed, deep-links to system settings if denied | UX for permission edge cases |

**Navigation rule:** every screen reachable within 2 taps from Home; back-stack is shallow
(bottom-nav screens never stack on each other — switching tabs replaces, doesn't push).

## 4. Data Model (Room)

```
Habit
  id: Long (PK, autogen)
  name: String
  icon: String            // key into a curated icon set
  colorHex: String
  category: String
  frequencyType: enum { DAILY, SPECIFIC_DAYS, INTERVAL }
  frequencyDays: String?  // e.g. "MON,WED,FRI" when SPECIFIC_DAYS
  intervalDays: Int?      // when INTERVAL
  reminderTime: String?   // "HH:mm", null = no reminder
  createdAt: Long
  isArchived: Boolean

HabitLog
  id: Long (PK, autogen)
  habitId: Long (FK -> Habit.id)
  date: String            // "yyyy-MM-dd", one row per habit per day
  completed: Boolean
  completedAt: Long?      // epoch millis, null if not completed
  note: String?           // optional free-text logged at check-in (nullable, CA3 signal)

Achievement
  id: Long (PK, autogen)
  habitId: Long?          // null = global achievement
  type: String            // e.g. "STREAK_7", "STREAK_30", "FIRST_HABIT"
  unlockedAt: Long
```

`HabitLog.note` and `completedAt` are the only fields added purely for CA3's benefit — both are
optional, cost nothing in CA1's UI (a note field is simply an optional bonus at check-in), and
give a future prediction/recommendation model real per-completion signal (time-of-day, free-text
sentiment, day-of-week patterns) without an app-store-breaking schema migration later.

Repository layer (`HabitRepository`) wraps the DAOs and exposes `Flow`s; one `ViewModel` per
screen consumes the repository and exposes `StateFlow<UiState>`.

## 5. Reminders

A `PeriodicWorkRequest` (WorkManager, ~15 min interval — the minimum Android allows for periodic
work) queries habits with a `reminderTime` due in the last window and not yet completed today,
and posts a notification per habit via `NotificationCompat`. This avoids the exact-alarm
permission (`SCHEDULE_EXACT_ALARM`) restrictions introduced in Android 12+, at the cost of
reminder precision being within a ~15-minute window — an acceptable trade-off for a class project
demo. Android 13+ requires runtime `POST_NOTIFICATIONS` permission, requested at the end of
onboarding.

## 6. Visual Design

- **Style:** soft minimalist — rounded (16dp) cards, generous whitespace, subtle elevation, no
  harsh borders.
- **Palette:** neutral base (off-white / near-black surfaces for light/dark) with a single accent
  color. Two accent options to choose from at implementation time: sage green (`#6B9080`) or warm
  coral (`#F76C5E`). Both ship as a Material 3 color scheme (light + dark variants) generated from
  the seed color, so contrast ratios stay accessible (WCAG AA) automatically.
  Default: sage green, since it reads calmer and works well behind both the calendar heatmap
  (which needs its own green intensity scale) and streak-flame accents (orange) without clashing.
- **Typography:** Material 3 default type scale (Roboto), one weight step for headers vs. body.
- **Accessibility:** minimum 48dp touch targets, content-description on all icon-only buttons,
  color is never the sole signal (checkmarks/text accompany color state), dynamic text scaling
  respected (no fixed `sp` overrides that block system font-size).
- **Responsiveness:** Compose layouts use `WindowSizeClass` to switch the Home/Stats/Calendar
  screens between a single-column phone layout and a two-pane layout on tablet/large-screen width
  classes (list + detail side-by-side instead of full-screen push navigation).

## 7. Seed / Demo Data

On first launch (if the database is empty and it's a debug build), the app inserts 4 sample
habits (Drink Water, Read 20 Minutes, Workout, Meditate) each backfilled with ~14 days of
plausible check-in history (a mix of completed/missed days, weighted so at least one habit shows
a strong streak and one shows a broken one) — so Home, Stats, Calendar, and Achievements all look
populated immediately for grading, without requiring a live data-entry demo first.

## 8. Deployment & Testing Plan

- Build a debug APK from Android Studio; primary test target: Pixel 7 emulator (phone, API 34).
- Responsiveness check: run the same build on a second emulator profile — a small phone
  (e.g. Nexus S, API 34) and a 10" tablet profile — to demonstrate the `WindowSizeClass` layout
  adaptation and screenshot both.
- Manual test checklist per screen: navigation reachability, form validation (empty habit name
  blocked, at least one frequency day required for SPECIFIC_DAYS), check-in toggling updates
  streak/stats immediately, dark/light theme switch applies app-wide, notification fires within
  the expected window on a real completion-due habit.
- Rotate device orientation on Home and Stats to confirm no layout breakage (Compose state
  survives via `rememberSaveable`/ViewModel).

## 9. Report Deliverable

Standard Word project report (not IEEE two-column, to match a typical mobile-dev CA rather than
the earlier data-science paper unless you'd rather keep the IEEE format for consistency — default
here is standard single-column report):

Title Page → Abstract → Introduction → Objectives → Tools & Technologies Used → System
Architecture (component diagram) → Data Model (ER-style diagram of Habit/HabitLog/Achievement) →
Screen Flow / Navigation Diagram → UI/UX Design Principles Applied (with annotated screenshots per
principle: consistency, readability, accessibility, usability) → Feature Walkthrough (screenshots
of every screen with captions) → Testing (the checklist above + responsiveness screenshots) →
Conclusion → References.

## 10. Explicit Non-Goals for CA1

- No cloud sync, accounts, or multi-device support (local-only, per earlier decision).
- No AI/ML feature yet — that's CA3. This design deliberately keeps `HabitLog` shaped so CA3 can
  add an on-device TensorFlow Lite model later (e.g., "risk of missing today" classification or
  "recommend next habit") without changing the CA1 schema or UI contracts.
- No Play Store publishing — "deployment" here means installing and running a built APK on an
  emulator/device for demonstration.
