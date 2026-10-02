# Running Habitly with real AI on your phone

Habitly's AI features (Create with AI, Week in review, Quick coach) call OpenAI **directly from the
phone**, using an OpenAI key compiled into the APK. The forecast runs on the phone and needs no key.

This is a **personal build for one phone**. Anyone who gets the APK file can pull the key out of it, so:

- never share or upload the APK,
- set a **monthly spend limit** in the OpenAI dashboard before you start,
- keep the key only in `local.properties` (git-ignored), never in a committed file.

You paste the key yourself. Nothing in this repository contains a real key.

## 1. Prerequisites

- An OpenAI account with billing enabled and a monthly spend limit
- Android Studio (it brings the Android SDK, `adb` and a JDK)
- An Android phone with USB debugging enabled (step 3)

## 2. Put the key in `local.properties`

In the project root, open `local.properties` (it already holds `sdk.dir`) and add:

```
habitly.openaiKey=<paste your OpenAI API key here>
```

Optional, to use another model (default `gpt-5-mini`):

```
habitly.openaiModel=gpt-5-mini
```

These values are read at **build time** and compiled into the app, so change them, then rebuild.

## 3. Prepare the phone

1. Settings → About phone → tap **Build number** 7 times.
2. Settings → Developer options → turn on **USB debugging**.
3. Connect the phone by USB and accept "Allow USB debugging?".

## 4. Build and install

```bash
./gradlew :app:assembleDebug
```

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -d install -r app/build/outputs/apk/debug/app-debug.apk
```

`-d` picks the USB phone even if an emulator is running. The APK is
`app/build/outputs/apk/debug/app-debug.apk`; a debug build is all a single personal phone needs.

## 5. First run

Open an AI feature (for example the sparkle button on Today). The consent sheet explains what is sent;
tap **Allow**. After that the coach, Create with AI and Week in review use the real model.

## 6. Demo tips

- **No internet during a presentation:** in Settings, tap the "AI features" heading 5 times to reveal
  **Demo AI** (the main "AI features" switch must stay on). It answers every AI feature with canned
  responses and needs no network or consent; it stays visible while it is on.
- **Today's forecast** works fully offline — the model runs on the phone.
- A build made without `habitly.openaiKey` shows "AI isn't set up in this build" (Demo AI still works).

## 7. Limits and safety

- **Daily allowance:** 30 real AI calls per day per phone (`DAILY_AI_LIMIT` in `AiPrefs.kt`), counted
  on the phone and reset at local midnight. Demo AI does not count. When used up, the app shows
  "Daily AI limit reached — back tomorrow".
- **Moderation:** what you type to the coach is checked with OpenAI's moderation endpoint first; flagged
  text gets a fixed, supportive reply and the model is not called.
- **Shape checks:** answers use strict JSON schemas, are trimmed (word and character caps, at most 4
  suggestions, at most 2 coach actions) and are validated again against your data before any button
  is offered. An unusable answer is retried once.
- **If the key leaks:** revoke it in the OpenAI dashboard, create a new one, update `local.properties`
  and rebuild. Old APKs stop working.

## 8. Troubleshooting

| What you see | Likely cause | Fix |
|---|---|---|
| "AI isn't set up in this build" | `habitly.openaiKey` was empty when the app was built | Add it to `local.properties` and rebuild |
| "Something went wrong. Try again." | Wrong or revoked key (401), OpenAI error, an unusable answer twice, or a timeout | Check the key and your OpenAI billing; try again |
| "Daily AI limit reached — back tomorrow" | The phone's 30 calls are used, or OpenAI returned 429 (rate limit or no credit) | Wait until tomorrow, check OpenAI billing, or raise `DAILY_AI_LIMIT` and rebuild |
| "AI needs an internet connection" | The phone is offline | Connect, or switch on Demo AI |
| `adb` says "more than one device" | The emulator is also running | Keep the `-d` flag, or close the emulator |

## About `proxy/`

Earlier builds sent requests through a Cloudflare Worker in `proxy/` so the key never left a server.
The app no longer uses it; the folder is kept for reference only.
