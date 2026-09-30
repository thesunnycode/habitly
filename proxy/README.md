# Habitly AI proxy

A small Cloudflare Worker that sits between the Habitly app and OpenAI. The app never holds an OpenAI key; it
sends a shared app token plus a per-install device id, and the Worker:

- checks `Authorization: Bearer <APP_TOKEN>` and an `X-Device-Id` UUID header,
- rejects bodies over 32 KB,
- enforces daily per-device and global caps (Workers KV) before calling the model,
- calls OpenAI with structured outputs (Responses API) and validates/trims the result,
- returns `429` when capped, `400` for bad requests, `502` when the model fails twice.

Every successful response carries an `x-ai-remaining` header (calls left today for this device).

All endpoints are `POST` with `Content-Type: application/json`. `GET /v1/health` returns `{"ok":true}`.

## POST /v1/habits/suggest

```json
{
  "text": "sleep better",
  "existingHabitNames": ["Read"],
  "iconKeys": ["sleep", "star"],
  "categories": ["Health"]
}
```

Response (1 to 4 habits):

```json
{
  "intent": "sleep",
  "habits": [{
    "name": "Lights out 23:00",
    "iconKey": "sleep",
    "colorSeed": 2,
    "category": "Health",
    "frequency": { "type": "DAILY", "days": null, "intervalDays": null },
    "targetCount": null,
    "unit": null,
    "reminderTime": "22:45",
    "why": "A fixed bedtime is easy to start."
  }]
}
```

`intent` is one of `sleep | fitness | focus | health | mind | other`. `frequency.type` is
`DAILY | SPECIFIC_DAYS | INTERVAL`.

## POST /v1/review/weekly

Request: `weekStart` (`YYYY-MM-DD`) and 1 to 20 habit summaries.

```json
{
  "weekStart": "2026-09-21",
  "habits": [{
    "id": 7, "name": "Workout", "category": "Fitness",
    "frequency": { "type": "DAILY", "days": null, "intervalDays": null },
    "targetCount": null, "unit": null, "reminderTime": "08:00",
    "createdDaysAgo": 30, "currentStreak": 2, "bestStreak": 5,
    "last7": { "done": 4, "rested": 0, "missed": 3, "due": 7 },
    "prev7": { "done": 5, "rested": 1, "missed": 1, "due": 7 },
    "weekdays": [{ "day": "MONDAY", "done": 0, "due": 4 }],
    "avgCheckInHour": 19, "dueToday": true, "todayStatus": null, "risk": 0.72
  }]
}
```

Response (3 to 5 pages, exactly one `summary` and it is first):

```json
{
  "pages": [
    { "kind": "summary", "headline": "A steady week", "body": "You did 4 of 7.", "habitId": null, "action": null },
    { "kind": "tip", "headline": "Move it earlier", "body": "You usually check in at 19:00.", "habitId": 7,
      "action": { "type": "set_reminder", "habitId": 7, "time": "19:00", "days": null, "date": null, "count": null, "label": "Move reminder" } }
  ]
}
```

`kind` is `summary | win | pattern | tip`. Action `type` is one of `set_reminder | set_days | set_rest_day |
set_goal | log_done | rest_today | open_habit`; unused action fields are `null`.

## POST /v1/coach

Request: `today` (`YYYY-MM-DD`), up to 20 habit summaries (same shape as above) and up to 6 messages. An empty
`messages` array asks for the opening message.

```json
{ "today": "2026-09-30", "habits": [], "messages": [{ "role": "user", "text": "I keep skipping workouts" }] }
```

Response (reply capped at 80 words, at most 2 actions, only for known habit ids):

```json
{ "reply": "Let's make it smaller today.", "actions": [] }
```

`role` is `user | coach`. If the latest user message is flagged by OpenAI moderation, the reply is a fixed safe
message and the model is not called.

## Local development

```
npm install
npm test
npm run typecheck
npx wrangler dev
```

Create a git-ignored `.dev.vars` in this folder and fill in the values yourself:

```
OPENAI_API_KEY=
APP_TOKEN=
```

Deployment steps are in `docs/DEPLOY.md`.
