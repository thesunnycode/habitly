# Stats screen — audit and rebuild plan

## Audit of the current screen

Captured live on the emulator. Twelve findings, of which the first three are structural rather than
cosmetic — restyling would not fix them.

| # | Finding |
| --- | --- |
| 1 | Four identical grey cards stacked. Same fill, radius and weight, so nothing leads. |
| 2 | Two bar charts look interchangeable but mean different things — one is the last 7 days, the other is all-time by weekday. Same colour, shape and size, adjacent. |
| 3 | The line chart shows the same data as the bar chart directly below it. |
| 4 | The line chart reads as broken: descends, flatlines at zero, no axis, labels, fill or points. |
| 5 | Two time windows on one screen with no period control — the subtitle says "last 7 days" while the weekday chart is all-time. |
| 6 | "Best day: F" — a single letter as a headline stat. |
| 7 | Low-data state unhandled: 3 days of history is drawn as 7 days and 7 bars, 4 of them empty, which reads as failure rather than "not yet". |
| 8 | No comparison anywhere. 33% of what? |
| 9 | Tile colours (green, cream, green) are arbitrary — colour carries no data. |
| 10 | Square-cornered `drawRect` bars; the line is unsmoothed `drawLine` segments. |
| 11 | `strokeWidth = 6f` is raw pixels, not dp, so line weight changes with screen density. |
| 12 | Charts hard-coded to 160dp regardless of content. |

## What the research says

Market research across Apple Fitness Trends, Oura, Strava, WHOOP, Habitify, Loop, HabitKit,
Gentler Streak, RISE and Duolingo. The findings that decide this rebuild:

- **The top of a good stats screen is a computed insight, not a chart.** Oura's Trends tab opens
  with three curated trends. Gentler Streak deliberately refuses to reduce wellbeing to a score and
  "translates stats into words". This is the largest perceived-intelligence gain available per line
  of code, and it needs no new chart.
- **The sort order is the insight.** Apple Fitness Trends has no hero chart at all — it has three
  groups in fixed priority order: *Keep it Going*, *Worth a Look*, *Needs More Data*, each row
  carrying an up/down arrow against a longer baseline. A flat list becomes a verdict.
- **A 7-day line chart of a completion rate should not ship.** With seven binary-derived points the
  series can only take eight values, so the line is a staircase and day-to-day movement is noise. A
  line earns its place at weekly or monthly granularity over eight or more periods, which is exactly
  where Habitify and Strava use one.
- **Low data is a first-class state, not an edge case.** Apple gates trends behind 180 days and
  ships a named *Needs More Data* bucket; WHOOP's weekly assessment only appears after five logged
  days in that week. The screen is never broken — it is partly not-yet-earned.
- **Never show a bare delta.** Always state the basis: "+12% vs last month". Suppress it entirely
  when the sample is too small rather than printing a misleading figure.

## Decisions

### Adopt

| Change | Replaces | Why |
| --- | --- | --- |
| Insight block at the top, at most three sentences, rotating | nothing | Oura's and Gentler Streak's pattern; the highest-value change and it needs no chart |
| Hero completion number with an explicit "vs last period" chip | the "Completion" tile | Answers "is this good?", which the screen currently cannot |
| Segmented Week / Month / Year above the primary chart | nothing | Fixes the two-unlabelled-time-windows problem (finding 5). Three options, not Strava's six |
| Seven day pills for the selected week | the line chart | Kills findings 3, 4 and 10 in one move |
| Records strip: current streak, best streak, total completions, with a "new best" callout | nothing | Loop ships exactly this; it is also the data source for the insights |
| Weekday distribution with the weakest day tinted differently | the second bar chart | Keeps the one genuinely actionable chart, and makes colour carry data (finding 9) |
| Per-habit list sorted into Going well / Slipping / Not enough data, each with a sparkline | the "Highlights" rows | Apple Fitness Trends' structure verbatim; turns a flat list into a verdict |
| Per-card gating with a ghost preview and "unlocks on day N" | nothing | Fixes finding 7 the way Apple does, per card rather than per screen |
| Rounded caps, gradient fills, dp-aware strokes, staggered spring entry | flat rects and hairlines | Fixes findings 10-12 and is what separates "alive" from "grey stack" |

### Reject

- **Community comparison.** WHOOP can compare you to a global cohort; a habit tracker should not.
  Rank invites the wrong motivation, and the app is local-only with nothing to compare against.
- **Pie or donut of habits by category.** Habit sets are small, so the slices carry no information.
- **Radar / spider charts of "life areas".** Impressive-looking and unreadable.
- **A heatmap on this screen.** The Calendar screen already has the contribution graph. Duplicating
  it would repeat the mistake finding 3 identifies.
- **Loop's smoothed habit-strength score.** Genuinely good, but both Loop's Score and Habitify's
  Consistency Rate needed dedicated help articles to explain. A number nobody can read unaided does
  not belong on a screen we are trying to simplify. Deferred, not dismissed.
- **A charting library (Vico).** Canvas covers everything above and stays distinctive.

## Insight templates

Each has a trigger and a minimum sample, so a template can never fire on data too thin to support
it. Copy style follows the researched house rules: one fact plus one implication, specific, never
scolding, and biased toward kindness on a bad result.

| Insight | Fires when | Copy |
| --- | --- | --- |
| Weak weekday | one weekday is ≥20pp below the mean, ≥4 occurrences | "You usually skip Thursdays — 2 of the last 6." |
| Strong weekday | mirror of the above | "Mondays are your best day: 6 for 6 this month." |
| Personal best | current streak beats the previous maximum | "Longest streak yet — 14 days. Previous best was 11." |
| Best period | this period beats every previous one, ≥3 periods of history | "Your best month yet: 82%, up from 74%." |
| Ahead of pace | completions to date beat the same point last period | "Ahead of where you were this time last month — 9 done against 6." |
| Recovery | a miss followed by ≥3 consecutive completions | "You missed Tuesday and got straight back on it. That is the part that matters." |
| Habit slipping | a habit's rate fell ≥25pp against the previous period | "Reading has slipped: 40% this month, down from 78%." |
| Rest-positive | everything completed for ≥14 days | "Twenty-one days without a miss. A planned rest day will not break this." |

At most three are shown, highest-priority first, and the set rotates so the screen changes between
visits.

## Build order

1. Insight engine in the domain layer, with tests per template and per trigger threshold.
2. Hero number and comparison chip, including the suppression rule.
3. Replace the line chart with the seven day pills; delete `LineChart`.
4. Period selector and the plumbing to make every card honour it.
5. Records strip.
6. Rewrite `BarChart`: rounded caps, dp-aware strokes, gradient, staggered entry, weakest-day tint.
7. Per-habit verdict list with sparklines.
8. Per-card low-data gating with ghost previews.
