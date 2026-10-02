# Deploying Habitly AI

Habitly's AI features (Create with AI, Week in review, Quick coach) talk to a small Cloudflare Worker in
`proxy/`, which calls OpenAI. The OpenAI key lives only in the Worker. The app holds a shared app token and
a per-install device id, never the key. The forecast runs on the phone and needs none of this.

You type every secret yourself. Nothing in this repository contains a real key or token.

## 1. Prerequisites

- Node.js 20 or newer
- A Cloudflare account (the free plan is enough)
- An OpenAI account with billing enabled. In the OpenAI dashboard, set a **monthly spend limit** before you
  go further. It is the hard ceiling on cost.
- Android Studio (or its bundled JDK) to build the APK

## 2. Deploy the proxy

Run these from the repository root.

1. Install and log in:
   ```bash
   cd proxy
   npm install
   npx wrangler login
   ```
2. Create the rate-limit store, then paste the `id` it prints into `proxy/wrangler.toml` (replace
   `set-by-wrangler-kv-namespace-create`):
   ```bash
   npx wrangler kv namespace create AI_LIMITS
   ```
3. Store the two secrets. Wrangler asks you to paste each value; it is not echoed and not saved in a file:
   ```bash
   npx wrangler secret put OPENAI_API_KEY
   npx wrangler secret put APP_TOKEN
   ```
   `OPENAI_API_KEY` is your OpenAI key. `APP_TOKEN` is any long random string (a password manager can
   generate one). Keep a copy of the token, because the app needs the same value in section 3.
4. Optional: change `OPENAI_MODEL` (default `gpt-5-mini`), `DEVICE_DAILY_LIMIT` (default 30) or
   `GLOBAL_DAILY_LIMIT` (default 500) under `[vars]` in `proxy/wrangler.toml`.
5. Deploy:
   ```bash
   npx wrangler deploy
   ```
   Note the URL it prints, of the form `https://habitly-ai.<your-subdomain>.workers.dev`.
6. Check it is alive:
   ```bash
   curl https://habitly-ai.<your-subdomain>.workers.dev/v1/health
   ```
   Expected: `{"ok":true}`.

To test the proxy locally instead, create a git-ignored `proxy/.dev.vars` containing `OPENAI_API_KEY=` and
`APP_TOKEN=` (fill them in yourself) and run `npx wrangler dev`. See `proxy/README.md` for the endpoints.

## 3. Point the app at the proxy

Add these two lines to `local.properties` in the project root (it is git-ignored):

```
habitly.aiBaseUrl=https://habitly-ai.<your-subdomain>.workers.dev
habitly.aiAppToken=<the same value you gave APP_TOKEN>
```

They are read at **build time** and compiled into the APK. Change them, then rebuild.

## 4. Build the APK

Debug build, for testing on a phone or emulator:

```bash
./gradlew :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.

Release build, for sharing with other people:

1. Create a signing key once and keep it **outside the repository**. A lost key means you can no longer
   update an installed app:
   ```bash
   keytool -genkeypair -v -keystore habitly-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias habitly
   ```
   If `keytool` is not on your PATH, it is in `bin/` of Android Studio's JDK (`jbr`).
2. Add the signing details to `local.properties`:
   ```
   habitly.keystore.path=C:/keys/habitly-release.jks
   habitly.keystore.password=<keystore password>
   habitly.key.alias=habitly
   habitly.key.password=<key password>
   ```
3. Build:
   ```bash
   ./gradlew :app:assembleRelease
   ```
   The APK is `app/build/outputs/apk/release/app-release.apk`.

Without the four signing properties the release build still succeeds but produces
`app-release-unsigned.apk`, which Android will not install. `*.jks` and `*.keystore` are git-ignored.

## 5. Share it

- **GitHub Releases:** create a release and attach `app-release.apk`. Testers download it and allow
  "install unknown apps" for their browser.
- **Firebase App Distribution:** upload the APK in the Firebase console and add testers by email.

## 6. Demo tips

- **No internet, or a proxy that is not deployed:** in Settings, tap the "AI features" heading 5 times to
  reveal the **Demo AI** switch. The main "AI features" switch must stay on. It answers every AI feature with canned responses and needs no consent or
  network. It also stays visible while it is switched on.
- **Today's forecast** works fully offline, because the model runs on the phone.
- With Demo AI off and no proxy configured, AI screens show "AI isn't set up in this build".

## 7. Security notes

- The app token inside an APK can be extracted by someone determined. Cost is bounded by three things:
  the per-device and global daily caps in `wrangler.toml`, and the OpenAI monthly spend limit.
- If the token is abused, rotate it and ship a new build:
  ```bash
  npx wrangler secret put APP_TOKEN
  ```
  then update `habitly.aiAppToken` in `local.properties` and rebuild. Old APKs stop working.
- Only anonymised habit summaries (names, schedules, counts) are sent. Notes and your name are not.
  The in-app consent sheet explains this before the first request.

## 8. Troubleshooting

| What you see | Likely cause | Fix |
|---|---|---|
| Proxy answers 401 | The token in the APK differs from the Worker's `APP_TOKEN` | Make `habitly.aiAppToken` match, rebuild |
| Proxy answers 400 | Missing or malformed `X-Device-Id`, bad JSON or body over 32 KB | Use the app; for curl tests send a UUID in `X-Device-Id` |
| 429, "Daily AI limit reached" | The device or global daily cap is used up | Caps reset at midnight UTC, or raise the limit in `wrangler.toml` and redeploy |
| "Something went wrong. Try again." | The app's message for any 401, 400, 502 or timeout | Check the rows above with `npx wrangler tail` running in `proxy/` |
| 502 | OpenAI failed twice: bad key, billing or spend limit reached, wrong model name | Run `npx wrangler tail` in `proxy/` and retry from the app |
| "AI isn't set up in this build" | `habitly.aiBaseUrl` or `habitly.aiAppToken` was empty when the app was built | Fill both in `local.properties` and rebuild |
| "AI needs an internet connection" | The phone is offline or the Worker URL is wrong | Check the URL with the `/v1/health` call |
| `app-release-unsigned.apk` will not install | No signing properties | Follow the release steps in section 4 |
