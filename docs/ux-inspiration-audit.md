# Habitly — inspiration audit and per-screen decisions

Source material: 18 reference screenshots across four products.

| Reference | What it is | What it's good at |
| --- | --- | --- |
| **TickTick** (blue/white) | All-in-one productivity suite | Week date strip, collapsible groups, goal units, calendar |
| **HabitKit** (dark) | Habit tracker, grid-first | Pixel-grid heatmaps, per-habit theming, share cards |
| **Loop-style** (black/yellow) | Habit tracker + focus timer | Habit templates, streak history tiles, deep customisation |
| **Daily Habit Planner** (colour cards) | Habit tracker | Solid colour cards, goal progress on the card |

## Patterns found in the references

| # | Pattern | Seen in | Verdict |
| --- | --- | --- | --- |
| A | Horizontal week date strip, today highlighted, tap a past day to log it | TickTick, Daily Habit Planner | **Adopt** |
| B | Measurable goals with units — "0/3 Cups", "200/1000 ml", "1x/30 min" | TickTick, Daily Habit Planner, widgets | **Adopt** |
| C | Day markers beyond done/not-done — ✓ done, ✗ missed, hatched skipped, partial ring | TickTick widgets | **Adopt (skip only)** |
| D | Lifetime "Total Days" counter per habit | TickTick, widgets | **Adopt** |
| E | Collapsible groups with a count — "Others 6 ⌄" | TickTick | **Adopt** |
| F | Time-of-day grouping — Anytime / Morning / Afternoon / Evening | Loop-style | **Reject** |
| G | Prebuilt habit templates by category — "Keep active & Get fit", "Ease stress" | Loop-style | **Adopt** |
| H | Pixel-grid heatmap per habit, many weeks at once | HabitKit | **Adopt** |
| I | Streak history tiles + celebration banner — "You're on a 9 DAYS STREAK!" | Loop-style | **Adopt** |
| J | Deep per-habit customisation — icon, colour, repeat, goal, period | Loop-style, HabitKit | **Partially have** |
| K | Pomodoro timer + white noise library | TickTick, Loop-style | **Reject** |
| L | Task inbox, Eisenhower priority matrix, Google/Outlook calendar sync | TickTick | **Reject** |
| M | Home-screen widgets, share-to-friends cards | HabitKit, TickTick | **Defer** |
| N | AI voice capture — speak a sentence, get structured entries | TickTick | **Defer to CA3** |

### Why the rejections

- **F (time-of-day grouping)** — the app already groups by category. Two competing groupings on one list is
  the kind of choice that makes a screen feel arbitrary; categories carry more information here because they
  are user-defined.
- **K (Pomodoro + white noise)** — a different product. It needs a foreground service, an audio pipeline, and
  asset licensing, and none of it makes the habit-tracking screens better.
- **L (tasks, matrix, calendar sync)** — TickTick is a task manager that also does habits. Habitly is not.
  Calendar sync additionally needs OAuth and network access, which contradicts the local-only design.
- **M (widgets, share)** — both are genuinely good, but a Glance widget is a separate surface that adds no
  in-app UI quality, and share cards need a render-to-bitmap path. Deferred, not dismissed.
- **N (AI voice)** — this is precisely the CA3 brief. Building it now would spend the next activity's scope.

## Per-screen decisions

### 1. Home / Today

| Add | Why |
| --- | --- |
| Week date strip (A) — last 7 days, today highlighted, tap to change the logged day | The single most-requested capability the app lacks: today it is impossible to record a habit you forgot yesterday. Every reference has this. |
| Goal progress on the card (B) — "2 / 3 cups" with a partial ring instead of an empty circle | Turns one binary tap into visible increments, which is what makes a water-type habit workable. |
| Streak-at-risk line when a habit with a live streak is still undone late in the day | Loss aversion is the strongest cheap retention lever. |

Keep: greeting, today's progress ring card, colour cards, 7-day strip, FAB.
Reject: task list, timer, matrix.

### 2. All Habits

| Add | Why |
| --- | --- |
| Lifetime total-days counter per habit (D) | Gives a long-lived number that only grows — the reason to keep a habit alive past a broken streak. |
| Collapsible category sections with counts (E) | Scales past ~10 habits, where a flat list stops working. |

Keep: search, "All" + category chips, TO DO / DONE sections, progress strip.
Reject: time-of-day grouping (F).

### 3. Habit Detail

| Add | Why |
| --- | --- |
| Pixel grid of the last ~12 weeks (H), above the month view | HabitKit's signature: consistency is legible at a glance in a way a single month never is. |
| Fourth stat tile: total days completed (D) | Completes the tile row (current streak / best streak / completion / total). |
| Skip state rendered distinctly (C) | A skipped day should not read as a failure. |

Keep: habit-coloured tiles and heatmap, month navigation, archive/delete.
Defer: share-as-image card (M).

### 4. Add / Edit habit

| Add | Why |
| --- | --- |
| Template gallery for new habits (G), grouped by category | The highest-impact change in the whole audit: an empty first session is where habit apps lose users, and picking "Drink water" from a list beats typing it. |
| Optional goal: target number + unit (B) | Required for the goal progress in decisions 1 and 3. |
| Reminder time picker exposed in the form | The data model already carries `reminderTime`; the form never asks for it. |

Keep: live preview card, icon and colour pickers, frequency chips.
Reject: time-period selector (F).

### 5. Stats

| Add | Why |
| --- | --- |
| Average line across the weekly bar chart | Loop-style shows the average against the bars; a bar chart without a reference line is hard to read. |
| "Habits finished" count tile (I) | A cumulative achievement figure, not just a rate. |

Keep: stat tiles, trend line, bar chart, highlights.
Reject: time-spent statistics — nothing in the app tracks duration.

### 6. Calendar

| Add | Why |
| --- | --- |
| Tap a day to see which habits were completed that day | The grid currently shows that a day happened but not what happened in it — a dead end. |

Keep: month grid, day numbers, consistency tiles, legend.

### 7. Awards

| Add | Why |
| --- | --- |
| Celebration moment when a badge unlocks (I) | The badge already unlocks silently, so the reward never lands. |

Keep: badge tiles with requirements on locked ones.

### 8. Settings

Keep as-is: theme, reminder notifications, reset. Deliberately not adding HabitKit's display
toggles ("show completion indicator", "show description") — options that exist because the
author could not decide. Habitly should just look right.

## Revisions after researching the wider market

Research across Loop, Habitify, HabitKit, Streaks, Productive, TickTick, Habitica, Way of Life and
Everyday changed four of the decisions above.

### Skip is promoted from a nicety to a top-three feature

The documented churn pattern is "I broke my streak, there's no point now" — and habit apps *amplify*
it with streak-guilt notifications. Loop's rule is worth copying verbatim: a skipped day "leaves the
score unchanged and doesn't break the streak". Habitify goes further with **skip / fail / pause** as
three separate states, where an explicit *fail* and an *absence of data* both break the streak but
look different. Way of Life's whole premise is that "didn't do it" is an affirmative data point.

Two concrete rules follow:

- Model the log as an enum (`DONE, SKIPPED, FAILED, NO_DATA`), not the current boolean, with
  `SKIPPED` streak-neutral.
- **Never show a streak resetting to zero without a repair path on the same screen.** This is the
  single highest-value retention change available, and it is nearly free.

### Two defects this audit uncovered in code already shipped

| Defect | Detail |
| --- | --- |
| **Completion rate ignores the habit's own schedule** | `StreakCalculator.completionRate(records, today)` now divides by *elapsed calendar days*. For a `SPECIFIC_DAYS` or `INTERVAL` habit that under-reports badly — a 3×/week habit reads ~43%. TickTick deliberately normalises against the declared frequency so "you don't need to check off a habit everyday to get a 100% check-in rate"; a review screen reporting 43% for a habit being kept perfectly is a churn event. `FrequencyType` is currently only ever read by `FrequencyValidator` — the streak and rate maths ignore it entirely. `currentStreak` has the same flaw: any non-scheduled day breaks the streak. |
| **Notification permission is requested too early** | `OnboardingScreen` asks for `POST_NOTIFICATIONS` during onboarding, before a single habit exists. Wellness apps see disproportionate drop-off at permission prompts; the ask should come after the first habit is created and the first check-in logged, when its value is obvious. |

### Accessibility remediation for the colourful-card UI

The card style adopted from Daily Habit Planner concentrates most classic accessibility failures on
one screen. Specific to what is now built:

1. **State is encoded in colour/fill alone.** The day pills and check circle differ only by fill —
   WCAG 1.4.1. Every state needs a distinct glyph, following Habitify (arrow = skipped, cross =
   failed) and Loop (dash = skip).
2. **The check circle is 40dp**, below the 48dp Android minimum. Expand the touch target beyond the
   visual bounds rather than growing the circle.
3. **Heatmap empty cells sit at alpha 0.07**, far below the 3:1 required for graphical objects
   (WCAG 1.4.11). Floor the alpha and give every cell a border so the cell is perceivable even when
   the fill is not.
4. **Toggle semantics are wrong.** `HabitCard` puts the state into `contentDescription`; it should be
   `Role.Checkbox` with the state in `stateDescription`, so TalkBack announces state changes.
5. **Habit colours are one hex reused in both themes.** Store a seed and derive light/dark tonal
   values, or pastel labels will fail 4.5:1 on dark cards.
6. **Rows will break at 200% font scale** — let the habit name wrap to two lines instead of
   ellipsising.
7. Gate any celebration animation behind the system reduce-motion setting (WCAG 2.3.3).

### Widgets: the deferral is weaker than it looked

Both HabitKit and Loop treat widgets as a primary feature — HabitKit ships "habit check-offs without
opening the app", Loop ships five widgets — and the reason is that a widget removes the app-launch
step from the logging loop entirely, which is the most leveraged retention change in the category.
On Android this is Glance plus a Room query. It stays deferred only because CA1 is marked on in-app
UI/UX and a widget renders outside the app; it is the first thing to build once that constraint is
gone, not a nice-to-have.

### One thing the research confirms is already right

`ReminderWorker` only notifies when the habit is still incomplete — the same suppression Loop
documents, and a small detail with outsized goodwill.

## Suggested build order

1. **Schedule-aware streak and completion maths** — fixes a live defect and unblocks honest stats.
2. **Skip state + streak repair path** — highest-value retention change, now that the log becomes an enum.
3. **Week date strip + retroactive logging** — biggest functional gap; every reference has it.
4. **Habit templates in the Add flow** — biggest first-session win.
5. **Accessibility remediation pass** (items 1–7 above) — directly relevant to a UI/UX-marked activity.
6. **Goals with units + partial progress.**
7. **Pixel grid on Habit Detail** + total-days tiles.
8. **Day-of-week histogram on Stats** — per the research the most actionable chart in Loop and the
   least copied; answers "which days do I actually fail?".
9. Move the notification permission ask after first check-in.
10. Calendar day detail, award celebration, collapsible groups, chart average line.

## Detailed research findings worth implementing verbatim

### Templates: a preset must fill three fields, not one

This is the finding that changes how the Add flow should be built. TickTick ships a **Habit Gallery**
of 60+ habits in four categories (life, health, exercise, mentality) with search and one-tap add.
Habitify shows its gallery *first* when you tap `+`, and each template carries its own pre-set unit
list (minutes, litres, pages, km). Streaks pairs every preset with a correct icon *and* a sensible
default frequency, and auto-suggests an icon from the typed name ("10 push ups" → an exercise glyph).

The common mechanism: a template collapses the create form from six decisions to one tap because it
fills **name + icon + frequency/unit simultaneously**. A gallery that only fills the name is barely
worth building. Habitica takes the same idea further up the funnel — onboarding asks which life
areas you want to improve, then seeds tasks into each.

Counterexamples worth noting: HabitKit and Loop ship **no templates at all** and survive on being
fast, which is a reminder that this is an accelerant, not a requirement.

### Streak-at-risk: copy Habitify's exact rule

Habitify fires a push **roughly 30 minutes after the habit's own reminder time, only if the habit is
still incomplete**, with the streak count in the copy: *"You're on a 7-day streak! Keep it going
today!"* The timing rule is the whole feature — `ReminderWorker` already knows how to suppress
notifications for completed habits, so this is a second scheduled check rather than new machinery.

### Grace days should be silent

Streaks ships a **"2-Day Rule"** so a single miss doesn't zero the streak. Duolingo pre-distributes
streak freezes and **consumes them automatically** — the user never sees a modal asking permission —
and reports users with freezes averaging 17.19 days versus 11.62 without. The lesson for the repair
path decided above: repair should be quiet and generous, not a guilt prompt.

### Stats screen ordering is a solved problem

Synthesised from Habitify, Loop, HabitKit and Streaks, the order they converge on is:

1. Period selector (month / year)
2. Hero number — current streak
3. Scalar tile row — total completions, best streak, completion rate, skipped/failed
4. **Calendar or tile-grid heatmap** — the recognition anchor, and it sits *above* the abstract charts
5. Best-streaks ranked bar list
6. Trend chart with a Day/Week/Month/Year toggle, plus average and goal reference lines
7. Smoothed/derived metrics last (Loop's Score, Habitify's Consistency Rate)
8. Behavioural breakdowns at the bottom — day-of-week histogram, time-of-day distribution

Loop's habit detail is literally nine stacked cards in that spirit, and its Score uses exponential
smoothing with published calibration: a perfectly kept daily habit reads **80% after one month, 96%
after two, 99% after three**. Both Loop's Score and Habitify's Consistency Rate needed dedicated
help-centre articles to explain, which is the signal that a smoothed composite metric is the one
thing users cannot read unaided — so if Habitly ever adds one, it needs an inline explanation.

### The pixel grid has a published accessibility spec

Adrian Roselli's audit of GitHub's contribution graph is effectively the spec for what a HabitKit
style grid gets wrong, and it found **three of GitHub's five grid colours measured below 3:1**.
GitHub's remediation is the pattern to copy: announce the grid as a table with its dimensions, read
per-day content, and **under forced-colors mode let the square's size carry intensity instead of its
colour**.

Concrete rules for our grid:

- Do not use an alpha ramp for intensity — vary lightness, and give every cell a border so the cell
  stays perceivable when the fill does not.
- One semantics node per week or month carrying a summary ("June: 22 of 30 days completed"), with
  individual cells `clearAndSetSemantics {}`. A 365-cell grid with 365 focus stops is a TalkBack
  disaster.
- Keep the year grid **read-only** and route editing to the month calendar — 48dp per cell is
  impossible at year scale, which is exactly how HabitKit is architected.

### These accessibility failures are documented, not theoretical

Loop's own issue tracker contains each defect this audit flagged, found with Google's Accessibility
Scanner: [#666](https://github.com/iSoron/uhabits/issues/666) contrast below 4.5:1,
[#665](https://github.com/iSoron/uhabits/issues/665) touch targets under 48dp,
[#2170](https://github.com/iSoron/uhabits/issues/2170) confetti ignoring system reduce-motion, and
most pointedly [#719](https://github.com/iSoron/uhabits/issues/719) — *"dark mode unchecked items
have low contrast"*, because the incomplete-day marker uses the same colour in both themes. That is
precisely the alpha-0.07 empty cell in our own `CalendarHeatmap`, reported by a real user and closed
without a fix.

Habitica's tracker adds the other half: [#494](https://github.com/HabitRPG/habitica-android/issues/494)
+/- buttons that are not colourblind-distinguishable, and
[#1467](https://github.com/HabitRPG/habitica-android/issues/1467) user-chosen theme colours bleeding
into the app bar and breaking text contrast — the exact hazard of letting users pick arbitrary habit
colours, which Habitly does.

Also worth noting on the platform side: Material's dark theme specifies **#121212, not pure black**,
because bright content on true black causes eye strain and kills the tonal separation that makes
elevated cards distinguishable. Pure black belongs behind an opt-in OLED setting, which is how
HabitKit shipped it. And a user-picked habit colour should be resolved to a **tone step** (roughly
tone 40 on light, tone 80 on dark) rather than used raw in both themes — the same conclusion the
audit reached from the contrast angle.

Apple's new accessibility nutrition labels make the industry split visible: Streaks and Habitify both
declare "Differentiate Without Color Alone" and "Sufficient Contrast"; HabitKit and Productive
declare nothing — notable for HabitKit, whose entire UI *is* a colour-encoded grid.

### Claims deliberately not used

Two figures circulate widely and could not be traced to a primary source: a "2020 CHI study" naming
streak anxiety as the top abandonment cause, and "users on rigid streaks were 63% more likely to quit
after one missed day". Both appear only on content-marketing sites. The qualitative churn pattern is
well evidenced; those specific numbers should not be cited in the CA report.

Coverage gap: reddit.com was unreachable, so the user-voice evidence above comes from GitHub issue
trackers, App Store review text and design teardowns rather than subreddit discussion. Two of the four
research passes also terminated early on a session limit; their topics — today-screen logging and
completion states — were substantially covered by the passes that completed.
