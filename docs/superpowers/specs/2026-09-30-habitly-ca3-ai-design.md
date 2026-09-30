# Habitly CA3 — AI features design

Date: 2026-09-30 · Branch: `feature/ca3-ai` (off `master` after the CA1 redesign merge)

## 1. Goal and scope

CA3 asks for a deployed mobile app that integrates an AI/ML model, with at least one AI-driven feature
(prediction, classification, recommendation or assistance), a clean responsive UI that presents AI output
clearly, and a project report. Habitly adds four features that together cover all four AI types:

| # | Feature | AI type | Where it runs | Layout (chosen from mockups) |
|---|---|---|---|---|
| 1 | Create with AI | Classification + extraction | LLM via proxy | 1B — dedicated full screen, prompt chips, voice |
| 2 | Week in review | Recommendation | LLM via proxy | 2B — swipeable story pages with Apply |
| 3 | Quick coach | Assistance / decision support | LLM via proxy | 3B — bottom sheet opened from Today |
| 4 | Today's forecast | Prediction | On-device ML (logistic regression) | 4B — forecast strip at the top of Today |

Deliverables in scope: the four features, a Cloudflare Worker proxy, deployment guide + signed release
build instructions, and a Word report draft (`docs/report/`). Out of scope: accounts, cloud sync,
streaming responses, persisted coach chat history, TFLite models, Play Integrity.

All new UI uses the existing dark-grid design system (HabitlyColors, HabitTones, HabitPalette, IconTile,
SegmentedPill, Plus Jakarta Sans scale, LocalReduceMotion) in dark and light themes, and must work at
compact and expanded widths and at 200% font scale.

## 2. Architecture

```
Habitly app ──HTTPS + app token──► Cloudflare Worker (proxy/) ──► OpenAI API
  │                                  • one endpoint per feature     (OPENAI_API_KEY is a
  │                                  • owns prompts + JSON schemas    Worker secret only)
  │                                  • validates input and output
  │                                  • per-device + global daily caps (KV)
  └─ on-device: RiskModel (logistic regression over Room history; no network)
```

The OpenAI key never ships in the APK and is never committed; the user sets it with
`wrangler secret put`. The proxy only performs Habitly tasks, so a leaked app token cannot be used as a
general GPT endpoint.

### 2.1 App-side units (package `com.habitly.app`)

| Unit | Purpose |
|---|---|
| `ai/AiApi` (interface) | `suggestHabits`, `weeklyReview`, `coach`; each returns `AiResult<T>` |
| `ai/AiResult` | `Success(value, remaining)`, `Offline`, `RateLimited(resetAt)`, `Failed(reason)` |
| `ai/HttpAiApi` | OkHttp + kotlinx.serialization; 20 s call timeout; maps HTTP codes to `AiResult` |
| `ai/DemoAiApi` | Canned but realistic responses built from the user's real habit names/ids |
| `ai/AiGateway` | Picks Demo vs Http, enforces consent + feature switch, exposes `remaining` |
| `ai/AiContextBuilder` | Builds the anonymised summaries sent to the proxy (§2.3) |
| `ai/AiDtos` | `@Serializable` request/response types shared by all features |
| `ai/AiActionValidator` + `ai/AiActionRunner` | Validates and executes the whitelisted actions (§2.4) with Undo |
| `ml/FeatureExtractor` | Builds feature vectors from Room history |
| `ml/RiskModel` | Logistic regression: train, predict, explain, evaluate |
| `ml/ForecastRepository` | Loads/stores weights (DataStore), retrains when stale, exposes today's forecast |
| `ml/ForecastTrainWorker` | Daily WorkManager retrain |

Config: `AI_BASE_URL` and `AI_APP_TOKEN` are read from `local.properties` into `BuildConfig`
(empty by default → the app behaves as "AI not configured" and offers Demo AI). A random install id
(UUID, DataStore) is sent as `X-Device-Id`. The app gains the `INTERNET` permission.

New dependencies: `com.squareup.okhttp3:okhttp`, `org.jetbrains.kotlinx:kotlinx-serialization-json`
(+ serialization plugin), test: `com.squareup.okhttp3:mockwebserver`.

### 2.2 Consent, settings and errors

- First use of any LLM feature shows a consent sheet: what is sent (habit names, schedules, reminder
  times, daily done/rest/missed counts), what is never sent (your name, notes), where it goes (Habitly
  proxy → OpenAI). **Allow** stores `aiConsent = true`; **Not now** closes without calling.
- Settings gains an **AI features** section: `AI features` switch (default on, still gated by consent),
  `Show forecast` switch (default on), `What's shared` (opens the consent text read-only), and a
  `Demo AI` switch revealed by tapping the section header 5 times (for offline presentations).
- The forecast needs no consent (nothing leaves the device).
- Shared states for all LLM features: skeleton loader while waiting; error card with Retry and friendly
  copy — Offline: "AI needs an internet connection"; RateLimited: "Daily AI limit reached — back
  tomorrow"; not configured: "AI isn't set up in this build — turn on Demo AI in Settings";
  Failed: "Something went wrong. Try again." No raw error text, no crashes.
- When `remaining ≤ 3`, LLM surfaces show "N AI requests left today".

### 2.3 Context sent to the proxy (`AiContextBuilder`)

Per active habit: `id`, `name`, `category`, `frequency` (type/days/interval), `targetCount`/`unit`,
`reminderTime`, `createdDaysAgo`, `currentStreak`, `bestStreak`, last 7 days and previous 7 days
(`done`, `rested`, `missed`, `due`), per-weekday done/due over the last 28 days, `avgCheckInHour`
(when available), today's status and forecast risk. No user name, no notes, no free text other than
what the user types into Create with AI / Coach. Payload is capped at 20 habits.

### 2.4 Whitelisted actions

LLM output may only propose these; the app validates ids/values against current data before offering
them and runs them through the repository with a Snackbar Undo:

| Action | Validation |
|---|---|
| `set_reminder(habitId, "HH:mm")` | habit exists and active; valid 24 h time |
| `set_days(habitId, [1..7])` | non-empty, distinct ISO weekdays; habit becomes SPECIFIC_DAYS |
| `set_rest_day(habitId, date)` | today or within the next 7 days; habit due that day |
| `set_goal(habitId, target)` | 1..999; habit already has a numeric goal or unit supplied |
| `log_done(habitId)` | habit due today and not already DONE |
| `rest_today(habitId)` | habit due today and not DONE |
| `open_habit(habitId)` | habit exists |

Invalid actions are dropped silently (logged in debug).

## 3. Feature 1 — Create with AI (1B)

**Entry points:** a "✨ Create with AI" row at the top of `TemplateSheet`; a sparkle icon button in the
Habits header; a line in Today's empty state. New destination `Destination.CreateWithAi`.

**Screen:** title "Create with AI", prompt "What do you want to change?", helper "Say it in your own
words". Multiline field (max 300 chars, counter near the limit) with a mic button that launches
`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (no RECORD_AUDIO permission; button hidden if no
recogniser is available). Prompt chips fill the field: Sleep better · Get fit · Less screen time ·
Read more · Drink more water · Feel calmer. **Suggest** is enabled at ≥ 3 non-blank characters.

**Results:** 1–4 suggestion cards (IconTile in the habit colour, name, schedule line via existing
`ScheduleLabel` e.g. "Daily · 8 cups · 09:00", one-line why). Card actions: **Add** (creates the
habit immediately → card shows "Added ✓ · Undo"), **Edit** (opens AddEdit prefilled; nothing saved
until the user saves), **✕** dismiss. Footer: **Add all** (remaining cards) and **Try again**
(re-request with the same text). A detected-intent chip ("Sleep") sits above the cards.

**Proxy contract** `POST /v1/habits/suggest`
- Request: `{ text, existingHabitNames: [..], iconKeys: [..], categories: [..] }`
- Response: `{ intent: "sleep"|"fitness"|"focus"|"health"|"mind"|"other", habits: [{ name, iconKey,
  colorSeed (0..11), category, frequency: { type: "DAILY"|"SPECIFIC_DAYS"|"INTERVAL", days?, intervalDays? },
  targetCount?, unit?, reminderTime?, why }] }`

**App-side sanitising (`SuggestionSanitizer`):** trim name to 40 chars and drop blanks; unknown
`iconKey` → default icon for the intent; `colorSeed` clamped to palette; frequency validated with the
existing `FrequencyValidator` (fallback DAILY); `targetCount` 1..999 else null; invalid time dropped;
names case-insensitively equal to existing habits dropped; max 4.

## 4. Feature 2 — Week in review (2B)

**Availability:** a review covers the most recent completed 7-day week as defined by the user's
`weekStart` preference (the 7 days ending the day before the current week starts). Needs ≥ 3 days of
history, otherwise the entry shows "Available after 3 days of tracking".

**Entry points:** a slim dismissible banner on Today "Your week in review is ready ✨" from the last
day of the week at 18:00 until two days into the new week (dismissal stored per week); a
"Week in review" link under the Stats hero on Insights (always available). New destination
`Destination.WeekReview`.

**Screen:** full-screen pager (HorizontalPager), segmented progress bars at the top, close (✕) and ⋯
menu (Regenerate). No auto-advance. Pages (3–5):
1. Summary — headline + 1–2 lines + the 7-day colour-stack row (reuse the Stats window grid).
2. Win — best habit/streak with its IconTile.
3. Pattern (1–2 pages) — headline + mini weekday bar in the habit's colour.
4. Tip pages (1–3) — headline, reason, **Apply** (runs a §2.4 action → "Applied ✓" + Undo) and **Skip**.
5. Close — "See you next week" + Done.

Visuals are computed on-device from real data; the LLM supplies only text, the page kind, the
referenced habit id and actions. TalkBack reads each page as one node; Previous/Next buttons are
provided; reduce motion snaps page changes.

**Proxy contract** `POST /v1/review/weekly`
- Request: `{ weekStart: "YYYY-MM-DD", habits: [HabitSummary] }` (§2.3)
- Response: `{ pages: [{ kind: "summary"|"win"|"pattern"|"tip", headline (≤ 60), body (≤ 160),
  habitId?, action? }] }` — 3–5 pages, exactly one summary first, at most 3 tips.

**Caching:** the validated review is stored in DataStore as JSON keyed by the week-start date (keep the
last 4). Reopening costs no call; Regenerate replaces it.

## 5. Feature 3 — Quick coach (3B)

**Entry:** a sparkle **Coach** icon button in the Today header opens a `ModalBottomSheet`
(partially expanded ~60 %, draggable to full).

**Contents:**
- Opening message generated on open from today's context + forecast (e.g. "You have 2 habits left.
  Workout is at risk — a 15-min version still keeps your streak.").
- Up to 2 action buttons under any coach message (§2.4 actions).
- Quick chips: Plan my day · Why do I slip? · Motivate me · Which habit first?
- "Ask anything…" field (max 300 chars) → user bubble, typing indicator (three dots; static under
  reduce motion), then the reply fades in.
- The conversation lives only while the sheet is open (ViewModel state); nothing is persisted.

**Proxy contract** `POST /v1/coach`
- Request: `{ today: "YYYY-MM-DD", habits: [HabitSummary], messages: [{ role: "user"|"coach", text }] }`
  (last 6 messages; empty list = opening message)
- Response: `{ reply (≤ 80 words), actions: [Action] (≤ 2) }`

**Safety:** the system prompt restricts the coach to habits, routines and general wellbeing; medical,
crisis or self-harm topics get a short supportive reply pointing to a professional/helpline with no
actions. User text is checked with OpenAI's moderation endpoint first; flagged input returns a fixed
safe reply.

## 6. Feature 4 — Today's forecast (4B, on-device ML)

**Model:** binary logistic regression predicting P(miss) for a habit that is due on a day. One pooled
model across the user's habits.

**Training rows:** every past day (up to 180 days back) on which a habit was due, excluding days
before creation and SKIPPED (rest) days. Label 1 = MISSED, PARTIAL or unlogged; 0 = DONE.

**Features (all computed only from data before that day; 20, indices in `ml/RiskFeatures.kt`):**
0–6 weekday one-hot; 7 done rate over the habit's last 7 judged due days; 8 done rate over the last 28 (the
baseline's input); 9 same-weekday rate over the last 20 same weekdays, smoothed towards the habit's own long-run
rate, (kept + 4·rate90)/(n + 4); 10 `ln(1 + streak going in)`; 11 missed the previous judged due day (0/1);
12 `ln(1 + days since start)`; 13 has reminder (0/1); 14 weekend (0/1); 15 share of the user's *other* habits
kept over the 3 days before (0.5 if none was judged); 16 `ln(1 + scheduled days since last kept)` — due,
non-skipped days after the most recent DONE, capped at 30 (never kept: scheduled days since start); 17 trend,
rate7 − rate28; 18 smoothed rate over the last 90 judged due days, (kept + 1)/(n + 2); 19 rested (SKIPPED) the
previous due day. "Judged" = due, on/after the habit's start, not SKIPPED. Unsmoothed rates with no history
default to 0.5. Training rows: every judged due day of the last 180 days before today.

**Training:** z-score standardisation (means/stds stored with the weights), L2-regularised batch gradient
descent on primitive arrays, learning rate 0.2, until the largest gradient component is < 1e-5 or 3000 epochs,
deterministic; λ chosen by cross-validation (below). A full fit on ~585 rows takes ~0.6 s on the JVM. Retrain
when the stored model is > 24 h old (on app start) and daily via `ForecastTrainWorker`. Weights, scaler,
training timestamp, sample count and metrics are stored in DataStore.

**Cold start:** until there are ≥ 40 rows containing both classes, a hand-set **starter model** is
used (weights favouring the recent-rate and missed-yesterday features); the strip labels itself
"Learning your patterns…".

**Evaluation:** rolling-origin cross-validation over the last 4 weeks: fold k tests the rows dated in
[today − 7k, today − 7(k − 1)) with a model trained only on rows dated before that week (folds with < 40 training
rows, one class, or no test rows are skipped). For each λ in {0.001, 0.003, 0.01, 0.03, 0.1, 0.3} the out-of-fold
predictions are pooled; the λ with the lowest pooled Brier score wins (ties → larger λ). `ModelMetrics` reports
that λ's pooled accuracy (threshold 0.5), ROC AUC, Brier score, test rows, folds used and λ, next to a baseline —
"your usual keep rate", miss probability = 1 − rate28 clamped to [0.02, 0.98] — scored on the same rows
(`baselineAuc`, `baselineBrier`). Because λ is chosen on the same folds, the reported Brier is slightly
optimistic (≈ 0.0025 vs a fixed λ = 0.01 on the benchmark). The model is then retrained on all rows for use.
No usable fold → λ = 0.01 and metrics = null.

**Explanation:** for each prediction, the feature with the largest positive contribution (weight ×
standardised value, > 0.05) that has a true phrase gives the reason. Risk: weekday → "you often miss {Mondays}";
"a patchy last week"; "it's slipped this month"; "{Mondays} are often hard"; "no streak going yet"; "missed last
time"; "still a new habit"; "no reminder set"; "weekends are harder"; "the last few days have been tough";
"not done in the last {n} scheduled days" (n ≥ 2); "it's been slipping lately"; "it's been hard to keep up for a
while" (only for habits ≥ 28 days old); "coming back from a rest day". If none applies, the most protective
feature: "on a {n}-day roll" (n ≥ 2); "a strong last week"; "a strong month"; "you're on a good run lately"; "a
strong track record"; "you're picking it back up". Otherwise "you're on track".

**Accuracy benchmark (2026-09-30, user request "good accuracy, not just for the CA"):** a realistic simulator
(`RealisticHistory`: habit-specific weekday slumps, a penalty after a not-kept day, shared rough spells, rest
days, unlogged misses) exposes its true keep probabilities, so an oracle scores the same pooled out-of-fold rows
as the model — the best any model could do. The features were chosen while looking at seeds 1–20 (in-sample:
AUC 0.918 of the oracle's), so the assertions run on held-out seeds 101–120 only: the model beats the baseline
AUC on ≥ 17 of 20 seeds and its mean Brier is below the baseline's; mean AUC ≥ 0.88 × the oracle's is a
regression guard set after measurement (in-sample ratio 0.919), not an accuracy claim. The oracle-Brier gap is
reported, not asserted. Seed 7 alone: ≥ 3 folds, AUC > baseline + 0.05, Brier < baseline. Measured on the held-out
seeds: AUC 0.721 (oracle 0.795, ratio 0.906, baseline 0.630), Brier 0.209 (baseline 0.232, oracle 0.179),
model ahead of the baseline on 20/20 seeds for both AUC and Brier.

**UI (Today, top of the list):**
- Card "Today's forecast" with ⓘ. Headline for the riskiest habit: "{Habit} needs attention —
  {reason}." Up to 3 tiles (habit colour dot/IconTile, "{p}%", "chance of missing", level word
  High ≥ 60 / Medium 30–59 / Low < 30 — level is always in words, not colour-only).
- Only habits due today and not DONE/SKIPPED; recomputed immediately on check-in. All done →
  collapses to "All clear for today ✓"; no habits due → hidden.
- Tile tap → Habit Detail. ⓘ → "How this works" sheet: the features in plain words, last trained,
  rows used, accuracy/AUC, "Runs only on your phone".
- Hidden when Settings → Show forecast is off.
- The coach (§5) receives the same risk values in its context.

## 7. Proxy (`proxy/`)

TypeScript Cloudflare Worker using the official `openai` npm SDK, `zod` for validation, Vitest for tests.

- Routes: `GET /v1/health`; `POST /v1/habits/suggest`, `/v1/review/weekly`, `/v1/coach`. Anything else
  → 404.
- Auth: `Authorization: Bearer ${APP_TOKEN}` (constant-time compare) and `X-Device-Id` (UUID) required
  → else 401 / 400.
- Body limit 32 KB; request validated with zod → 400 `{ error: "bad_request" }`.
- OpenAI call with structured outputs (strict JSON schema per endpoint); model from `OPENAI_MODEL`
  (default: a current small GPT model, confirmed against OpenAI docs when implemented); temperature
  modest; max output tokens per endpoint. Response re-validated with zod → on failure one retry, then
  502 `{ error: "upstream" }`.
- Coach: moderation check on the latest user message before the model call.
- Rate limits (KV namespace `AI_LIMITS`): per device 30 requests/day, global 500/day (both configurable
  vars); keys expire after 48 h. Over limit → 429 `{ error: "rate_limited", resetAt }`. Every success
  sets `X-AI-Remaining`. Health does not count.
- Prompts live in `proxy/src/prompts/*.ts`; each includes the output rules (lengths, allowed actions,
  only referenced habit ids).
- CORS off (mobile-only). No request logging of bodies.
- Secrets: `OPENAI_API_KEY`, `APP_TOKEN` via `wrangler secret put`; nothing secret in `wrangler.toml`.

## 8. Testing

- **Proxy (Vitest, OpenAI mocked):** auth missing/wrong, bad device id, oversized/invalid body, each
  endpoint's happy path, invalid model output → retry → 502, moderation flag → safe reply, per-device
  and global 429, `X-AI-Remaining`.
- **App unit tests (JUnit + Robolectric, existing style):**
  `HttpAiApi` with MockWebServer (200, 401, 429, 5xx, malformed JSON, timeout → Offline/Failed);
  `SuggestionSanitizer`; `AiActionValidator`; `AiContextBuilder` (no name/notes, caps);
  `FeatureExtractor` (no look-ahead leakage, rest days excluded); `RiskModel` (learns a synthetic
  "skips Mondays" pattern with held-out AUC > 0.7 (the synthetic data has 10% unpredictable random misses, so ~0.75 is its ceiling; measured 0.745), deterministic, explanations map correctly,
  starter model below threshold); ViewModels for all four features against a fake `AiApi`
  (loading/success/error/consent/undo); week-review availability and caching.
- **Emulator (controller):** every new screen in dark and light using Demo AI; 200 % font; expanded
  width. Live API is verified by the user after deploying the proxy.

## 9. Deployment and report

- `docs/DEPLOY.md`: install wrangler, create KV namespace, `wrangler secret put` both secrets,
  `wrangler deploy`, put the Worker URL + app token in `local.properties`, build signed release
  (`./gradlew :app:assembleRelease` with a keystore the user creates and whose passwords the user
  types), distribute via GitHub Releases or Firebase App Distribution, set an OpenAI monthly spend cap.
- Report draft `docs/report/Habitly-CA3-Report.docx`: introduction and objectives; app overview; the
  four AI features with screenshots; architecture diagram; ML model (features, training, evaluation
  metrics); LLM integration (prompts, schemas, safety); UI/UX and responsiveness; privacy and
  security; testing; deployment; limitations and future work; references. The student edits it into
  their own words.

## 10. Build order

1. Proxy (Worker, prompts, schemas, limits, tests)
2. App AI foundation (deps, BuildConfig, DTOs, HttpAiApi, DemoAiApi, gateway, consent, Settings)
3. Forecast (FeatureExtractor, RiskModel, repository, worker, Today strip, How-this-works sheet)
4. Create with AI
5. Quick coach sheet (+ action validator/runner)
6. Week in review
7. Deploy docs + release build config
8. Report draft
