# Habitly CA3 AI Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add four AI features to Habitly (Create with AI, Week in review, Quick coach, Today's forecast), backed by a Cloudflare Worker proxy to OpenAI and an on-device logistic-regression model, plus deployment docs and a report draft.

**Architecture:** The app talks only to a TypeScript Cloudflare Worker (`proxy/`) that owns prompts, strict JSON schemas, auth and daily caps; the OpenAI key is a Worker secret. App code lives in new packages `ai/` (network client, gateway, context builder, actions, demo mode, shared UI), `ml/` (features, model, forecast repository, worker) and feature UI packages. All UI uses the existing dark-grid design system.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.00), Material 3, Room 2.6.1, DataStore 1.1.1, WorkManager 2.9.1, OkHttp 4.12.0, kotlinx.serialization 1.7.3; proxy: TypeScript, Cloudflare Workers (wrangler), `openai` npm SDK (Responses API structured outputs), zod, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-30-habitly-ca3-ai-design.md` — read the section named in each task.

## Global Constraints

- Branch `feature/ca3-ai`. Every commit message ends with exactly: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- The OpenAI API key is never written to any file in the repo, never put in the APK, never printed. Only `wrangler secret put` (run by the user) sets it.
- `local.properties` holds `habitly.aiBaseUrl` and `habitly.aiAppToken`; it is git-ignored and must stay so. Empty values → the app reports "AI isn't set up in this build".
- Proxy endpoints and JSON field names are exactly those in §3–§5 and §7 of the spec and in the DTOs of Task 3 — the Kotlin DTOs and the zod schemas must match field-for-field.
- Weekday strings on the wire are `DayOfWeek.name` values (`"MONDAY"` … `"SUNDAY"`); dates are ISO `yyyy-MM-dd`; times are `HH:mm`.
- Per-device cap 30 requests/day, global cap 500/day (proxy vars `DEVICE_DAILY_LIMIT`, `GLOBAL_DAILY_LIMIT`). Request body limit 32 KB.
- Default model var `OPENAI_MODEL = "gpt-5-mini"`. Do not send `temperature` (not supported by GPT-5 reasoning models).
- Colours only from tokens (`MaterialTheme.colorScheme`, `LocalHabitlyColors`, `habitTones`/`HabitTonesFactory`, `HabitPalette`); no hard-coded hex in UI code except habit colour data.
- Every interactive element ≥ 48dp touch target; state never colour-only (always a word or icon too); all new screens work at 200% font scale and at Expanded width (wrap content in `CenteredContent` like existing full screens); motion respects `LocalReduceMotion`.
- No raw error text in UI; AI failures map to the copy in spec §2.2.
- Never send the user's name or habit notes to the proxy.
- Test commands: app `./gradlew :app:testDebugUnitTest -q` and `./gradlew :app:assembleDebug -q`; proxy `cd proxy && npm test` and `npm run typecheck`. Both suites green before every commit.
- Follow existing patterns: ViewModels depend on `HabitDataSource`, are created with `habitlyViewModel { … }` in `HabitlyNavHost`; VM tests use a private hand-written fake data source + `MainDispatcherRule`; DataStore tests use Robolectric.

## File map

```
proxy/
  package.json, tsconfig.json, wrangler.toml, vitest.config.ts, .gitignore, README.md
  src/index.ts            Worker entry: builds deps from env, calls handle()
  src/handler.ts          routing, auth, body limits, validation, limits, retry, errors
  src/limits.ts           KV daily counters
  src/llm.ts              Llm interface + OpenAI implementation
  src/schemas.ts          zod request/response schemas (wire contract)
  src/prompts.ts          system prompts per endpoint
  src/routes.ts           suggest / review / coach logic on top of Llm
  test/*.test.ts          Vitest
app/src/main/java/com/habitly/app/
  ai/AiDtos.kt AiResult.kt AiApi.kt HttpAiApi.kt AiJson.kt
  ai/AiPrefs.kt AiGateway.kt AiServices.kt DemoAiApi.kt
  ai/AiContextBuilder.kt AiContextSource.kt
  ai/actions/AiActionValidator.kt AiActionRunner.kt
  ai/ui/AiStates.kt ConsentSheet.kt
  ml/RiskFeatures.kt FeatureExtractor.kt RiskModel.kt RiskExplainer.kt Forecaster.kt
  ml/ForecastRepository.kt ForecastTrainWorker.kt
  ui/today/ForecastStrip.kt ForecastInfoSheet.kt
  ui/create/SuggestionSanitizer.kt CreateWithAiViewModel.kt CreateWithAiScreen.kt AiDraftStore.kt
  ui/coach/CoachViewModel.kt CoachSheet.kt
  ui/review/WeekReviewSchedule.kt WeekReviewViewModel.kt WeekReviewScreen.kt WeekReviewEntryViewModel.kt
  domain/HabitCategories.kt
docs/DEPLOY.md, docs/report/Habitly-CA3-Report.docx
```

---

### Task 1: Proxy — scaffold, auth, limits, health

**Files:**
- Create: `proxy/package.json`, `proxy/tsconfig.json`, `proxy/wrangler.toml`, `proxy/vitest.config.ts`, `proxy/.gitignore`, `proxy/src/index.ts`, `proxy/src/handler.ts`, `proxy/src/limits.ts`, `proxy/src/llm.ts` (interface only in this task), `proxy/test/fakes.ts`, `proxy/test/handler.test.ts`, `proxy/test/limits.test.ts`

**Interfaces:**
- Produces: `handle(req: Request, env: Env, deps: Deps): Promise<Response>`; `type Deps = { llm: Llm; now: () => Date; routes: Routes }`; `type Routes = Record<string, (body: unknown, llm: Llm) => Promise<unknown>>` (keys are paths, e.g. `"/v1/coach"`); `interface Llm { parse<T>(schema: ZodType<T>, name: string, system: string, user: string, maxTokens: number): Promise<T>; moderate(text: string): Promise<boolean> }`; `class UpstreamError extends Error`; `consume(kv, deviceId, now, deviceLimit, globalLimit): Promise<{ ok: boolean; remaining: number; resetAt: string }>`.

- [ ] **Step 1: Scaffold**

`proxy/package.json`:
```json
{
  "name": "habitly-ai-proxy",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "wrangler dev",
    "deploy": "wrangler deploy",
    "test": "vitest run",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": { "openai": "^6.0.0", "zod": "^3.25.0" },
  "devDependencies": {
    "@cloudflare/workers-types": "^4.20250101.0",
    "typescript": "^5.6.0",
    "vitest": "^3.0.0",
    "wrangler": "^4.0.0"
  }
}
```
Run `cd proxy && npm install`. If a listed major version does not resolve, install the latest available major and note it in the report.

`proxy/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022", "module": "ES2022", "moduleResolution": "Bundler",
    "strict": true, "skipLibCheck": true, "noEmit": true,
    "types": ["@cloudflare/workers-types"]
  },
  "include": ["src", "test"]
}
```

`proxy/wrangler.toml` (the KV id is filled in by the user during deployment, see Task 12):
```toml
name = "habitly-ai"
main = "src/index.ts"
compatibility_date = "2026-09-01"
compatibility_flags = ["nodejs_compat"]

[vars]
OPENAI_MODEL = "gpt-5-mini"
DEVICE_DAILY_LIMIT = "30"
GLOBAL_DAILY_LIMIT = "500"

[[kv_namespaces]]
binding = "AI_LIMITS"
id = "set-by-wrangler-kv-namespace-create"
```

`proxy/vitest.config.ts`:
```ts
import { defineConfig } from "vitest/config";
export default defineConfig({ test: { environment: "node" } });
```

`proxy/.gitignore`: `node_modules/`, `.wrangler/`, `.dev.vars`.

- [ ] **Step 2: Write `src/llm.ts` (interface + error only)**

```ts
import type { ZodType } from "zod";

export class UpstreamError extends Error {}

export interface Llm {
  parse<T>(schema: ZodType<T>, name: string, system: string, user: string, maxTokens: number): Promise<T>;
  moderate(text: string): Promise<boolean>;
}
```

- [ ] **Step 3: Write failing tests**

`proxy/test/fakes.ts`:
```ts
import type { Llm } from "../src/llm";

export class FakeKV {
  store = new Map<string, string>();
  async get(key: string) { return this.store.get(key) ?? null; }
  async put(key: string, value: string, _opts?: unknown) { this.store.set(key, value); }
}

export function fakeEnv(kv = new FakeKV()) {
  return {
    OPENAI_API_KEY: "test-key", APP_TOKEN: "app-token", OPENAI_MODEL: "gpt-5-mini",
    DEVICE_DAILY_LIMIT: "3", GLOBAL_DAILY_LIMIT: "5", AI_LIMITS: kv as unknown as KVNamespace,
  };
}

export const DEVICE = "0b6f3f8e-2a4c-4d7a-9a3e-6c1f2b9d8e11";

export function req(path: string, body: unknown, headers: Record<string, string> = {}) {
  return new Request(`https://proxy.test${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer app-token", "x-device-id": DEVICE, ...headers },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

export function llmReturning(...values: unknown[]): Llm & { calls: number } {
  const queue = [...values];
  return {
    calls: 0,
    async parse() { this.calls++; const v = queue.shift(); if (v instanceof Error) throw v; return v as never; },
    async moderate() { return false; },
  };
}
```

`proxy/test/handler.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { z } from "zod";
import { handle } from "../src/handler";
import { UpstreamError } from "../src/llm";
import { FakeKV, fakeEnv, req, llmReturning, DEVICE } from "./fakes";

const now = () => new Date("2026-09-30T10:00:00Z");
const echoRoutes = {
  "/v1/echo": async (body: unknown, llm: any) =>
    llm.parse(z.object({ ok: z.boolean() }), "echo", "sys", JSON.stringify(body), 100),
};

describe("handle", () => {
  it("health needs no auth and does not count", async () => {
    const env = fakeEnv();
    const res = await handle(new Request("https://proxy.test/v1/health"), env, { llm: llmReturning(), now, routes: echoRoutes });
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true });
  });

  it("unknown path is 404", async () => {
    const res = await handle(req("/v1/nope", {}), fakeEnv(), { llm: llmReturning(), now, routes: echoRoutes });
    expect(res.status).toBe(404);
  });

  it("missing or wrong token is 401", async () => {
    const res = await handle(req("/v1/echo", {}, { authorization: "Bearer wrong" }), fakeEnv(), { llm: llmReturning(), now, routes: echoRoutes });
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: "unauthorized" });
  });

  it("bad device id is 400", async () => {
    const res = await handle(req("/v1/echo", {}, { "x-device-id": "abc" }), fakeEnv(), { llm: llmReturning(), now, routes: echoRoutes });
    expect(res.status).toBe(400);
  });

  it("oversized or non-JSON body is 400", async () => {
    const big = JSON.stringify({ t: "x".repeat(33 * 1024) });
    const r1 = await handle(req("/v1/echo", big), fakeEnv(), { llm: llmReturning(), now, routes: echoRoutes });
    const r2 = await handle(req("/v1/echo", "{not json"), fakeEnv(), { llm: llmReturning(), now, routes: echoRoutes });
    expect(r1.status).toBe(400);
    expect(r2.status).toBe(400);
  });

  it("success returns route output and X-AI-Remaining", async () => {
    const res = await handle(req("/v1/echo", { a: 1 }), fakeEnv(), { llm: llmReturning({ ok: true }), now, routes: echoRoutes });
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true });
    expect(res.headers.get("x-ai-remaining")).toBe("2");
  });

  it("retries once on upstream failure, then 502", async () => {
    const llm = llmReturning(new UpstreamError("bad"), new UpstreamError("bad"));
    const res = await handle(req("/v1/echo", {}), fakeEnv(), { llm, now, routes: echoRoutes });
    expect(res.status).toBe(502);
    expect(await res.json()).toEqual({ error: "upstream" });
    expect(llm.calls).toBe(2);
  });

  it("recovers when the retry succeeds", async () => {
    const llm = llmReturning(new UpstreamError("bad"), { ok: true });
    const res = await handle(req("/v1/echo", {}), fakeEnv(), { llm, now, routes: echoRoutes });
    expect(res.status).toBe(200);
  });

  it("route validation errors are 400, not retried", async () => {
    const routes = { "/v1/echo": async () => { throw new z.ZodError([]); } };
    const res = await handle(req("/v1/echo", {}), fakeEnv(), { llm: llmReturning(), now, routes });
    expect(res.status).toBe(400);
  });

  it("per-device limit returns 429 with resetAt", async () => {
    const kv = new FakeKV();
    const env = fakeEnv(kv);
    const deps = { llm: llmReturning({ ok: true }, { ok: true }, { ok: true }, { ok: true }), now, routes: echoRoutes };
    for (let i = 0; i < 3; i++) expect((await handle(req("/v1/echo", {}), env, deps)).status).toBe(200);
    const res = await handle(req("/v1/echo", {}), env, deps);
    expect(res.status).toBe(429);
    expect(await res.json()).toEqual({ error: "rate_limited", resetAt: "2026-10-01T00:00:00.000Z" });
  });
});
```

`proxy/test/limits.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { consume } from "../src/limits";
import { FakeKV } from "./fakes";

const now = new Date("2026-09-30T23:59:00Z");

describe("consume", () => {
  it("counts per device and globally", async () => {
    const kv = new FakeKV() as unknown as KVNamespace;
    expect(await consume(kv, "a", now, 2, 3)).toEqual({ ok: true, remaining: 1, resetAt: "2026-10-01T00:00:00.000Z" });
    expect((await consume(kv, "a", now, 2, 3)).remaining).toBe(0);
    expect((await consume(kv, "a", now, 2, 3)).ok).toBe(false);
    expect((await consume(kv, "b", now, 2, 3)).ok).toBe(true);
    expect((await consume(kv, "c", now, 2, 3)).ok).toBe(false); // global 3 reached (a,a,b)
  });

  it("a new UTC day starts fresh", async () => {
    const kv = new FakeKV() as unknown as KVNamespace;
    await consume(kv, "a", now, 1, 10);
    expect((await consume(kv, "a", new Date("2026-10-01T00:00:01Z"), 1, 10)).ok).toBe(true);
  });
});
```

- [ ] **Step 4: Run tests — expect failure** (`cd proxy && npm test`): module `../src/handler` / `../src/limits` not found.

- [ ] **Step 5: Implement `src/limits.ts`**

```ts
const TTL_SECONDS = 172_800;

export async function consume(kv: KVNamespace, deviceId: string, now: Date, deviceLimit: number, globalLimit: number) {
  const day = now.toISOString().slice(0, 10);
  const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
  const resetAt = next.toISOString();
  const deviceKey = `d:${day}:${deviceId}`;
  const globalKey = `g:${day}`;
  const device = Number((await kv.get(deviceKey)) ?? "0");
  const global = Number((await kv.get(globalKey)) ?? "0");
  if (device >= deviceLimit || global >= globalLimit) return { ok: false, remaining: 0, resetAt };
  await kv.put(deviceKey, String(device + 1), { expirationTtl: TTL_SECONDS });
  await kv.put(globalKey, String(global + 1), { expirationTtl: TTL_SECONDS });
  return { ok: true, remaining: Math.max(0, deviceLimit - device - 1), resetAt };
}
```
(KV is eventually consistent; a small overshoot under concurrency is acceptable and should be noted in a code comment.)

- [ ] **Step 6: Implement `src/handler.ts`**

```ts
import { ZodError } from "zod";
import type { Llm } from "./llm";
import { UpstreamError } from "./llm";
import { consume } from "./limits";

export interface Env {
  OPENAI_API_KEY: string; APP_TOKEN: string; OPENAI_MODEL: string;
  DEVICE_DAILY_LIMIT: string; GLOBAL_DAILY_LIMIT: string; AI_LIMITS: KVNamespace;
}
export type Routes = Record<string, (body: unknown, llm: Llm) => Promise<unknown>>;
export type Deps = { llm: Llm; now: () => Date; routes: Routes };

const MAX_BODY = 32 * 1024;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });

function safeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const x = enc.encode(a), y = enc.encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

export async function handle(req: Request, env: Env, deps: Deps): Promise<Response> {
  const path = new URL(req.url).pathname;
  if (req.method === "GET" && path === "/v1/health") return json(200, { ok: true });
  const route = deps.routes[path];
  if (req.method !== "POST" || !route) return json(404, { error: "not_found" });

  const auth = req.headers.get("authorization") ?? "";
  if (!env.APP_TOKEN || !safeEqual(auth, `Bearer ${env.APP_TOKEN}`)) return json(401, { error: "unauthorized" });
  const deviceId = req.headers.get("x-device-id") ?? "";
  if (!UUID.test(deviceId)) return json(400, { error: "bad_request" });

  const text = await req.text();
  if (new TextEncoder().encode(text).length > MAX_BODY) return json(400, { error: "bad_request" });
  let body: unknown;
  try { body = JSON.parse(text); } catch { return json(400, { error: "bad_request" }); }

  const limit = await consume(env.AI_LIMITS, deviceId, deps.now(),
    Number(env.DEVICE_DAILY_LIMIT), Number(env.GLOBAL_DAILY_LIMIT));
  if (!limit.ok) return json(429, { error: "rate_limited", resetAt: limit.resetAt });

  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const out = await route(body, deps.llm);
      return json(200, out, { "x-ai-remaining": String(limit.remaining) });
    } catch (e) {
      // Request validation (routes call RequestSchema.parse) → 400, never retried.
      // Model-output problems are wrapped in UpstreamError by the Llm/routes; those and any
      // SDK/network error are retried once, then 502.
      if (e instanceof ZodError) return json(400, { error: "bad_request" });
    }
  }
  return json(502, { error: "upstream" });
}
```
(`UpstreamError` is imported for documentation only if unused — remove the import if the linter/tsc complains.) Note the limit is consumed before the model call, so failed calls still count; that is deliberate (abuse protection).

- [ ] **Step 7: Implement `src/index.ts`** (routes and the OpenAI Llm arrive in Task 2; for now wire an empty routes map)

```ts
import { handle, type Env } from "./handler";
import { openAiLlm } from "./llm";
import { ROUTES } from "./routes";

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    return handle(req, env, { llm: openAiLlm(env), now: () => new Date(), routes: ROUTES });
  },
};
```
Create a temporary `src/routes.ts` with `export const ROUTES = {};` and add to `llm.ts` a stub `export function openAiLlm(_env: unknown): Llm { throw new Error("configured in Task 2"); }` so typecheck passes. Task 2 replaces both.

- [ ] **Step 8: Run** `npm test` and `npm run typecheck` — all pass.

- [ ] **Step 9: Commit** `git add proxy && git commit -m "feat(proxy): worker scaffold with auth, body limits and daily caps"` (+ trailer).

---

### Task 2: Proxy — feature endpoints, prompts, OpenAI

**Files:**
- Create: `proxy/src/schemas.ts`, `proxy/src/prompts.ts`, `proxy/test/routes.test.ts`, `proxy/README.md`
- Modify: `proxy/src/llm.ts` (real `openAiLlm`), `proxy/src/routes.ts` (real routes)

**Interfaces:**
- Consumes: `Llm`, `UpstreamError`, `Routes` from Task 1.
- Produces: wire contract (must match Task 3 Kotlin DTOs exactly). `ROUTES` with keys `/v1/habits/suggest`, `/v1/review/weekly`, `/v1/coach`; exported `SAFE_REPLY` string.

- [ ] **Step 1: Write `src/schemas.ts`** (output schemas use `.nullable()`, never `.optional()`, because strict structured outputs require every property)

```ts
import { z } from "zod";

export const Weekday = z.enum(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]);
export const Frequency = z.object({
  type: z.enum(["DAILY", "SPECIFIC_DAYS", "INTERVAL"]),
  days: z.array(Weekday).nullable(),
  intervalDays: z.number().int().nullable(),
});
const Counts = z.object({ done: z.number().int(), rested: z.number().int(), missed: z.number().int(), due: z.number().int() });
export const HabitSummary = z.object({
  id: z.number().int(), name: z.string().max(60), category: z.string().max(40), frequency: Frequency,
  targetCount: z.number().int().nullable(), unit: z.string().max(20).nullable(), reminderTime: z.string().nullable(),
  createdDaysAgo: z.number().int(), currentStreak: z.number().int(), bestStreak: z.number().int(),
  last7: Counts, prev7: Counts,
  weekdays: z.array(z.object({ day: Weekday, done: z.number().int(), due: z.number().int() })).max(7),
  avgCheckInHour: z.number().int().nullable(), dueToday: z.boolean(),
  todayStatus: z.enum(["DONE", "SKIPPED", "MISSED", "PARTIAL"]).nullable(), risk: z.number().nullable(),
});
export const ActionType = z.enum(["set_reminder", "set_days", "set_rest_day", "set_goal", "log_done", "rest_today", "open_habit"]);
export const Action = z.object({
  type: ActionType, habitId: z.number().int(), time: z.string().nullable(), days: z.array(Weekday).nullable(),
  date: z.string().nullable(), count: z.number().int().nullable(), label: z.string(),
});

// --- requests (validated with .parse → ZodError → 400)
export const SuggestRequest = z.object({
  text: z.string().trim().min(3).max(300),
  existingHabitNames: z.array(z.string().max(60)).max(50),
  iconKeys: z.array(z.string().max(40)).max(100),
  categories: z.array(z.string().max(40)).max(20),
});
export const ReviewRequest = z.object({ weekStart: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), habits: z.array(HabitSummary).min(1).max(20) });
export const CoachRequest = z.object({
  today: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  habits: z.array(HabitSummary).max(20),
  messages: z.array(z.object({ role: z.enum(["user", "coach"]), text: z.string().max(600) })).max(6),
});

// --- model outputs (strict structured outputs)
export const SuggestOutput = z.object({
  intent: z.enum(["sleep", "fitness", "focus", "health", "mind", "other"]),
  habits: z.array(z.object({
    name: z.string(), iconKey: z.string(), colorSeed: z.number().int(), category: z.string(), frequency: Frequency,
    targetCount: z.number().int().nullable(), unit: z.string().nullable(), reminderTime: z.string().nullable(), why: z.string(),
  })),
});
export const ReviewOutput = z.object({
  pages: z.array(z.object({
    kind: z.enum(["summary", "win", "pattern", "tip"]), headline: z.string(), body: z.string(),
    habitId: z.number().int().nullable(), action: Action.nullable(),
  })),
});
export const CoachOutput = z.object({ reply: z.string(), actions: z.array(Action) });

export type SuggestOutputT = z.infer<typeof SuggestOutput>;
export type ReviewOutputT = z.infer<typeof ReviewOutput>;
export type CoachOutputT = z.infer<typeof CoachOutput>;
```

- [ ] **Step 2: Write `src/prompts.ts`**

```ts
const COMMON = `You are the assistant inside Habitly, a habit-tracking app. Be warm, concrete and brief.
Only reference habits by the ids given in the input. Never invent data. Plain text only, no markdown, no emoji except at most one.`;

const ACTIONS = `Allowed actions (use the exact type names; unused fields must be null; label is a 1-4 word button text):
- set_reminder: habitId, time "HH:mm"
- set_days: habitId, days (weekday names MONDAY..SUNDAY)
- set_rest_day: habitId, date "YYYY-MM-DD" (today or the next 7 days)
- set_goal: habitId, count (1-999) — only for habits that already have a targetCount
- log_done: habitId — only for a habit due today that is not DONE
- rest_today: habitId — only for a habit due today that is not DONE
- open_habit: habitId`;

export const SUGGEST_PROMPT = `${COMMON}
The user describes something they want to change. Classify it into one intent and propose 1 to 4 small, specific,
beginner-friendly habits. Use only iconKey values from the provided iconKeys list and only categories from the
provided categories list. colorSeed is an integer 0-11. Frequency: DAILY (days null, intervalDays null),
SPECIFIC_DAYS (days list, intervalDays null) or INTERVAL (days null, intervalDays 2-30). targetCount/unit only for
countable habits (e.g. 8 cups). reminderTime "HH:mm" when a time of day clearly helps, else null. name at most 40
characters. why is one short sentence (max 90 characters) explaining why this version is easy to start.
Do not repeat any of the existingHabitNames.`;

export const REVIEW_PROMPT = `${COMMON}
Write a short "week in review" story for the week starting weekStart from the per-habit summaries (last7 is the
reviewed week, prev7 the week before). Return 3 to 5 pages in this order: exactly one "summary" first, then at most
one "win", 0-2 "pattern" pages, 1-3 "tip" pages. headline max 60 characters, body max 160 characters.
Every tip must include one action that directly applies the tip. win/pattern/tip pages set habitId to the habit
they are about; summary uses null. Base every claim on the numbers given.
${ACTIONS}`;

export const COACH_PROMPT = `${COMMON}
You are a quick coach for today. Use today's habits (dueToday, todayStatus, risk = chance of missing today 0-1,
streaks). If messages is empty, write an opening message about today: what is left and which habit needs attention.
Otherwise answer the latest user message. reply at most 80 words. Offer at most 2 actions when they help right now.
Stay on habits, routines, motivation and general wellbeing. For medical, mental-health crisis or self-harm topics,
reply briefly and kindly, suggest talking to a professional or local helpline, and return no actions.
${ACTIONS}`;
```

- [ ] **Step 3: Write failing `test/routes.test.ts`**

```ts
import { describe, it, expect } from "vitest";
import { ZodError } from "zod";
import { ROUTES, SAFE_REPLY } from "../src/routes";
import { UpstreamError } from "../src/llm";
import { llmReturning } from "./fakes";

const habit = {
  id: 7, name: "Workout", category: "Fitness", frequency: { type: "DAILY", days: null, intervalDays: null },
  targetCount: null, unit: null, reminderTime: "08:00", createdDaysAgo: 30, currentStreak: 2, bestStreak: 5,
  last7: { done: 4, rested: 0, missed: 3, due: 7 }, prev7: { done: 5, rested: 1, missed: 1, due: 7 },
  weekdays: [{ day: "MONDAY", done: 0, due: 4 }], avgCheckInHour: 19, dueToday: true, todayStatus: null, risk: 0.72,
};
const action = (o: object) => ({ type: "set_reminder", habitId: 7, time: "19:00", days: null, date: null, count: null, label: "Move it", ...o });

describe("suggest", () => {
  const body = { text: "sleep better", existingHabitNames: ["Read"], iconKeys: ["sleep", "star"], categories: ["Health"] };
  const h = (o: object = {}) => ({ name: "Lights out 23:00", iconKey: "sleep", colorSeed: 2, category: "Health",
    frequency: { type: "DAILY", days: null, intervalDays: null }, targetCount: null, unit: null, reminderTime: "22:45", why: "Small start", ...o });

  it("rejects short text with ZodError", async () => {
    await expect(ROUTES["/v1/habits/suggest"]({ ...body, text: "hi" }, llmReturning())).rejects.toBeInstanceOf(ZodError);
  });
  it("caps to 4 habits and trims long text", async () => {
    const out: any = await ROUTES["/v1/habits/suggest"](body, llmReturning({ intent: "sleep", habits: [h(), h(), h(), h(), h({ name: "x".repeat(80) })] }));
    expect(out.habits).toHaveLength(4);
  });
  it("zero habits is an upstream error", async () => {
    await expect(ROUTES["/v1/habits/suggest"](body, llmReturning({ intent: "sleep", habits: [] }))).rejects.toBeInstanceOf(UpstreamError);
  });
});

describe("review", () => {
  const body = { weekStart: "2026-09-21", habits: [habit] };
  const page = (kind: string, o: object = {}) => ({ kind, headline: "H", body: "B", habitId: kind === "summary" ? null : 7, action: null, ...o });

  it("requires summary first and 3-5 pages", async () => {
    await expect(ROUTES["/v1/review/weekly"](body, llmReturning({ pages: [page("tip"), page("summary"), page("win")] }))).rejects.toBeInstanceOf(UpstreamError);
    const out: any = await ROUTES["/v1/review/weekly"](body, llmReturning({ pages: [page("summary"), page("win"), page("tip", { action: action({}) })] }));
    expect(out.pages).toHaveLength(3);
  });
  it("drops actions for unknown habit ids and truncates text", async () => {
    const out: any = await ROUTES["/v1/review/weekly"](body, llmReturning({ pages: [
      page("summary", { headline: "x".repeat(100) }), page("win"), page("tip", { action: action({ habitId: 99 }) })] }));
    expect(out.pages[0].headline.length).toBeLessThanOrEqual(60);
    expect(out.pages[2].action).toBeNull();
  });
});

describe("coach", () => {
  const body = { today: "2026-09-30", habits: [habit], messages: [] as any[] };

  it("opening message with at most 2 known-habit actions", async () => {
    const out: any = await ROUTES["/v1/coach"](body, llmReturning({ reply: "Hi", actions: [action({}), action({ habitId: 99 }), action({ type: "log_done", time: null }), action({})] }));
    expect(out.actions).toHaveLength(2);
    expect(out.actions.every((a: any) => a.habitId === 7)).toBe(true);
  });
  it("flagged user text returns the safe reply without calling the model", async () => {
    const llm = llmReturning();
    llm.moderate = async () => true;
    const out: any = await ROUTES["/v1/coach"]({ ...body, messages: [{ role: "user", text: "bad" }] }, llm);
    expect(out).toEqual({ reply: SAFE_REPLY, actions: [] });
    expect(llm.calls).toBe(0);
  });
  it("reply is capped at 80 words", async () => {
    const out: any = await ROUTES["/v1/coach"](body, llmReturning({ reply: Array(120).fill("word").join(" "), actions: [] }));
    expect(out.reply.split(/\s+/).length).toBeLessThanOrEqual(80);
  });
});
```

- [ ] **Step 4: Run — expect failure** (ROUTES is `{}`).

- [ ] **Step 5: Implement `src/routes.ts`**

```ts
import { SuggestRequest, ReviewRequest, CoachRequest, SuggestOutput, ReviewOutput, CoachOutput } from "./schemas";
import { SUGGEST_PROMPT, REVIEW_PROMPT, COACH_PROMPT } from "./prompts";
import { UpstreamError, type Llm } from "./llm";
import type { Routes } from "./handler";

export const SAFE_REPLY =
  "I can't help with that one, but I'm here for your habits and routines. If something feels heavy, please talk to someone you trust or a local helpline.";

const cut = (s: string, n: number) => (s.length <= n ? s : s.slice(0, n - 1).trimEnd() + "…");
const words = (s: string, n: number) => { const w = s.trim().split(/\s+/); return w.length <= n ? s.trim() : w.slice(0, n).join(" ") + "…"; };

async function suggest(body: unknown, llm: Llm) {
  const input = SuggestRequest.parse(body);
  const out = await llm.parse(SuggestOutput, "habit_suggestions", SUGGEST_PROMPT, JSON.stringify(input), 1500);
  const habits = out.habits.slice(0, 4).map((h) => ({ ...h, name: cut(h.name.trim(), 40), why: cut(h.why, 90) }));
  if (habits.length === 0) throw new UpstreamError("no habits");
  return { intent: out.intent, habits };
}

async function review(body: unknown, llm: Llm) {
  const input = ReviewRequest.parse(body);
  const ids = new Set(input.habits.map((h) => h.id));
  const out = await llm.parse(ReviewOutput, "week_review", REVIEW_PROMPT, JSON.stringify(input), 2500);
  const pages = out.pages.slice(0, 5).map((p) => ({
    ...p,
    headline: cut(p.headline, 60), body: cut(p.body, 160),
    habitId: p.habitId !== null && ids.has(p.habitId) ? p.habitId : null,
    action: p.action && ids.has(p.action.habitId) ? { ...p.action, label: cut(p.action.label, 28) } : null,
  }));
  if (pages.length < 3 || pages[0].kind !== "summary" || pages.filter((p) => p.kind === "summary").length !== 1) {
    throw new UpstreamError("bad review shape");
  }
  return { pages };
}

async function coach(body: unknown, llm: Llm) {
  const input = CoachRequest.parse(body);
  const last = input.messages.at(-1);
  if (last?.role === "user" && (await llm.moderate(last.text))) return { reply: SAFE_REPLY, actions: [] };
  const ids = new Set(input.habits.map((h) => h.id));
  const out = await llm.parse(CoachOutput, "coach_reply", COACH_PROMPT, JSON.stringify(input), 1200);
  const actions = out.actions.filter((a) => ids.has(a.habitId)).slice(0, 2).map((a) => ({ ...a, label: cut(a.label, 28) }));
  return { reply: words(out.reply, 80), actions };
}

export const ROUTES: Routes = { "/v1/habits/suggest": suggest, "/v1/review/weekly": review, "/v1/coach": coach };
```

- [ ] **Step 6: Implement the real `openAiLlm` in `src/llm.ts`** (verify `responses.parse`/`zodTextFormat` against the installed SDK's README; adjust call shape only if the installed version differs)

```ts
import OpenAI from "openai";
import { zodTextFormat } from "openai/helpers/zod";
import type { ZodType } from "zod";

export class UpstreamError extends Error {}

export interface Llm {
  parse<T>(schema: ZodType<T>, name: string, system: string, user: string, maxTokens: number): Promise<T>;
  moderate(text: string): Promise<boolean>;
}

export function openAiLlm(env: { OPENAI_API_KEY: string; OPENAI_MODEL: string }): Llm {
  const client = new OpenAI({ apiKey: env.OPENAI_API_KEY });
  return {
    async parse(schema, name, system, user, maxTokens) {
      const res = await client.responses.parse({
        model: env.OPENAI_MODEL,
        input: [{ role: "system", content: system }, { role: "user", content: user }],
        text: { format: zodTextFormat(schema as any, name) },
        max_output_tokens: maxTokens,
      });
      if (res.output_parsed == null) throw new UpstreamError("empty output");
      const checked = (schema as ZodType<any>).safeParse(res.output_parsed);
      if (!checked.success) throw new UpstreamError("output failed validation");
      return checked.data;
    },
    async moderate(text) {
      const res = await client.moderations.create({ model: "omni-moderation-latest", input: text });
      return res.results.some((r) => r.flagged);
    },
  };
}
```
Model-output validation failures become `UpstreamError` (retry → 502), never a 400.

- [ ] **Step 7: Write `proxy/README.md`** — what the Worker does, the three endpoints with example request/response JSON (copy the shapes from `schemas.ts`), local dev (`npx wrangler dev` with a git-ignored `.dev.vars` containing `OPENAI_API_KEY=` and `APP_TOKEN=` lines the user fills in), and a pointer to `docs/DEPLOY.md`.

- [ ] **Step 8: Run** `npm test` and `npm run typecheck` — all pass.

- [ ] **Step 9: Commit** `feat(proxy): suggest, weekly review and coach endpoints on OpenAI structured outputs`.

---

### Task 3: App — AI network foundation

**Files:**
- Modify: `build.gradle.kts` (root), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `ai/AiDtos.kt`, `ai/AiResult.kt`, `ai/AiApi.kt`, `ai/AiJson.kt`, `ai/HttpAiApi.kt`
- Test: `app/src/test/java/com/habitly/app/ai/HttpAiApiTest.kt`, `ai/AiDtosTest.kt`

(Paths under `ai/` are relative to `app/src/main/java/com/habitly/app/`.)

**Interfaces:**
- Produces: all DTOs below; `sealed interface AiResult<out T>`; `interface AiApi { suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse>; suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse>; suspend fun coach(request: CoachRequest): AiResult<CoachResponse> }`; `class HttpAiApi(baseUrl: String, appToken: String, deviceId: suspend () -> String, client: OkHttpClient = defaultClient(), json: Json = AiJson, io: CoroutineDispatcher = Dispatchers.IO) : AiApi`; `val AiJson: Json`; `BuildConfig.AI_BASE_URL`, `BuildConfig.AI_APP_TOKEN`.

- [ ] **Step 1: Gradle + manifest**

Root `build.gradle.kts` plugins block — add:
```kotlin
id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
```
`app/build.gradle.kts`:
```kotlin
import java.util.Properties
// plugins { … existing … }
plugins { id("org.jetbrains.kotlin.plugin.serialization") }   // add to the existing block

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// inside android { defaultConfig { … } }:
buildConfigField("String", "AI_BASE_URL", "\"${localProps.getProperty("habitly.aiBaseUrl", "")}\"")
buildConfigField("String", "AI_APP_TOKEN", "\"${localProps.getProperty("habitly.aiAppToken", "")}\"")
// dependencies:
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```
Manifest: add `<uses-permission android:name="android.permission.INTERNET" />` and, inside `<manifest>`, a `<queries>` block (needed for the voice input availability check in Task 9):
```xml
<queries>
    <intent><action android:name="android.speech.action.RECOGNIZE_SPEECH" /></intent>
</queries>
```
Confirm `local.properties` is listed in `.gitignore`.

- [ ] **Step 2: Write the DTOs (`ai/AiDtos.kt`)** — field names are the wire contract of Task 2's `schemas.ts`

```kotlin
package com.habitly.app.ai

import kotlinx.serialization.Serializable

@Serializable data class FrequencyDto(val type: String, val days: List<String>? = null, val intervalDays: Int? = null)
@Serializable data class CountsDto(val done: Int, val rested: Int, val missed: Int, val due: Int)
@Serializable data class WeekdayStatDto(val day: String, val done: Int, val due: Int)

@Serializable data class HabitSummaryDto(
    val id: Long, val name: String, val category: String, val frequency: FrequencyDto,
    val targetCount: Int? = null, val unit: String? = null, val reminderTime: String? = null,
    val createdDaysAgo: Int, val currentStreak: Int, val bestStreak: Int,
    val last7: CountsDto, val prev7: CountsDto, val weekdays: List<WeekdayStatDto>,
    val avgCheckInHour: Int? = null, val dueToday: Boolean, val todayStatus: String? = null, val risk: Double? = null,
)

@Serializable data class AiActionDto(
    val type: String, val habitId: Long, val time: String? = null, val days: List<String>? = null,
    val date: String? = null, val count: Int? = null, val label: String = "",
)

@Serializable data class SuggestRequest(val text: String, val existingHabitNames: List<String>, val iconKeys: List<String>, val categories: List<String>)
@Serializable data class SuggestedHabitDto(
    val name: String, val iconKey: String, val colorSeed: Int, val category: String, val frequency: FrequencyDto,
    val targetCount: Int? = null, val unit: String? = null, val reminderTime: String? = null, val why: String = "",
)
@Serializable data class SuggestResponse(val intent: String, val habits: List<SuggestedHabitDto>)

@Serializable data class ReviewRequest(val weekStart: String, val habits: List<HabitSummaryDto>)
@Serializable data class ReviewPageDto(val kind: String, val headline: String, val body: String, val habitId: Long? = null, val action: AiActionDto? = null)
@Serializable data class ReviewResponse(val pages: List<ReviewPageDto>)

@Serializable data class ChatMessageDto(val role: String, val text: String)
@Serializable data class CoachRequest(val today: String, val habits: List<HabitSummaryDto>, val messages: List<ChatMessageDto>)
@Serializable data class CoachResponse(val reply: String, val actions: List<AiActionDto> = emptyList())
```

`ai/AiJson.kt`:
```kotlin
package com.habitly.app.ai

import kotlinx.serialization.json.Json

val AiJson: Json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }
```
(`explicitNulls = true` so nullable request fields are sent as `null`, which the proxy's `.nullable()` schemas require.)

`ai/AiResult.kt`:
```kotlin
package com.habitly.app.ai

sealed interface AiResult<out T> {
    data class Success<T>(val value: T, val remaining: Int?) : AiResult<T>
    data object Offline : AiResult<Nothing>
    data class RateLimited(val resetAt: String?) : AiResult<Nothing>
    data class Failed(val reason: String) : AiResult<Nothing>
    /** No proxy URL/token in this build and Demo AI is off. */
    data object NotConfigured : AiResult<Nothing>
    /** The user has not accepted the AI consent sheet yet. */
    data object ConsentRequired : AiResult<Nothing>
    /** AI features are switched off in Settings. */
    data object Disabled : AiResult<Nothing>
}
```

`ai/AiApi.kt`:
```kotlin
package com.habitly.app.ai

interface AiApi {
    suspend fun suggestHabits(request: SuggestRequest): AiResult<SuggestResponse>
    suspend fun weeklyReview(request: ReviewRequest): AiResult<ReviewResponse>
    suspend fun coach(request: CoachRequest): AiResult<CoachResponse>
}
```

- [ ] **Step 3: Write failing `HttpAiApiTest`** (plain JUnit, MockWebServer)

```kotlin
package com.habitly.app.ai

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpAiApiTest {
    private val server = MockWebServer()
    private lateinit var api: HttpAiApi
    private val req = CoachRequest("2026-09-30", emptyList(), emptyList())

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        api = HttpAiApi(server.url("/").toString(), "tok", { "0b6f3f8e-2a4c-4d7a-9a3e-6c1f2b9d8e11" }, client)
    }
    @After fun tearDown() = server.shutdown()

    @Test fun successParsesBodyAndRemaining() = runTest {
        server.enqueue(MockResponse().setBody("""{"reply":"Hi","actions":[],"extra":1}""").addHeader("X-AI-Remaining", "12"))
        val r = api.coach(req)
        assertEquals(AiResult.Success(CoachResponse("Hi", emptyList()), 12), r)
        val recorded = server.takeRequest()
        assertEquals("/v1/coach", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("Authorization"))
        assertEquals("0b6f3f8e-2a4c-4d7a-9a3e-6c1f2b9d8e11", recorded.getHeader("X-Device-Id"))
        assertTrue(recorded.body.readUtf8().contains("\"messages\":[]"))
    }

    @Test fun rateLimitedCarriesResetAt() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate_limited","resetAt":"2026-10-01T00:00:00.000Z"}"""))
        assertEquals(AiResult.RateLimited("2026-10-01T00:00:00.000Z"), api.coach(req))
    }

    @Test fun unauthorizedAndServerErrorsFail() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(AiResult.Failed("unauthorized"), api.coach(req))
        assertEquals(AiResult.Failed("server"), api.coach(req))
    }

    @Test fun malformedJsonFails() = runTest {
        server.enqueue(MockResponse().setBody("""{"nope":true}"""))
        assertEquals(AiResult.Failed("bad_response"), api.coach(req))
    }

    @Test fun connectionFailureIsOffline() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(AiResult.Offline, api.coach(req))
    }

    @Test fun timeoutFails() = runTest {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
        assertEquals(AiResult.Failed("timeout"), api.coach(req))
    }

    @Test fun pathsPerEndpoint() = runTest {
        server.enqueue(MockResponse().setBody("""{"intent":"sleep","habits":[]}"""))
        server.enqueue(MockResponse().setBody("""{"pages":[]}"""))
        api.suggestHabits(SuggestRequest("sleep", emptyList(), emptyList(), emptyList()))
        api.weeklyReview(ReviewRequest("2026-09-21", emptyList()))
        assertEquals("/v1/habits/suggest", server.takeRequest().path)
        assertEquals("/v1/review/weekly", server.takeRequest().path)
    }
}
```

`AiDtosTest`: encode a `HabitSummaryDto` with nulls and assert the JSON contains `"unit":null` and `"weekdays":[`; decode a `ReviewResponse` whose page lacks `habitId` and `action` and assert both are null.

- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest --tests "com.habitly.app.ai.*" -q` — FAIL (HttpAiApi missing).

- [ ] **Step 5: Implement `ai/HttpAiApi.kt`**

```kotlin
package com.habitly.app.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

class HttpAiApi(
    baseUrl: String,
    private val appToken: String,
    private val deviceId: suspend () -> String,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = AiJson,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AiApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun suggestHabits(request: SuggestRequest) =
        post("/v1/habits/suggest", request, SuggestRequest.serializer(), SuggestResponse.serializer())
    override suspend fun weeklyReview(request: ReviewRequest) =
        post("/v1/review/weekly", request, ReviewRequest.serializer(), ReviewResponse.serializer())
    override suspend fun coach(request: CoachRequest) =
        post("/v1/coach", request, CoachRequest.serializer(), CoachResponse.serializer())

    private suspend fun <Q, R> post(path: String, body: Q, qs: KSerializer<Q>, rs: KSerializer<R>): AiResult<R> {
        val id = deviceId()
        return withContext(io) {
            val request = Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer $appToken")
                .header("X-Device-Id", id)
                .post(json.encodeToString(qs, body).toRequestBody(JSON))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> try {
                            AiResult.Success(json.decodeFromString(rs, text), response.header("X-AI-Remaining")?.toIntOrNull())
                        } catch (e: SerializationException) {
                            AiResult.Failed("bad_response")
                        } catch (e: IllegalArgumentException) {
                            AiResult.Failed("bad_response")
                        }
                        response.code == 429 -> AiResult.RateLimited(resetAt(text))
                        response.code == 401 -> AiResult.Failed("unauthorized")
                        else -> AiResult.Failed("server")
                    }
                }
            } catch (e: InterruptedIOException) {
                AiResult.Failed("timeout")
            } catch (e: IOException) {
                AiResult.Offline
            }
        }
    }

    private fun resetAt(text: String): String? = runCatching {
        json.parseToJsonElement(text).jsonObject["resetAt"]?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        private val JSON = "application/json".toMediaType()
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
```
Note `SocketTimeoutException` extends `InterruptedIOException`, and OkHttp's call timeout throws `InterruptedIOException` — both map to `Failed("timeout")`; everything else IO → `Offline`. If `connectionFailureIsOffline` surfaces as another exception type, map it to Offline without changing the timeout case.

- [ ] **Step 6: Run the tests, then the full suite and `assembleDebug`.** All green.

- [ ] **Step 7: Commit** `feat(ai): OkHttp client and wire DTOs for the Habitly AI proxy`.

---

### Task 4: App — AI prefs, gateway, demo mode, context builder

**Files:**
- Create: `ai/AiPrefs.kt`, `ai/AiGateway.kt`, `ai/DemoAiApi.kt`, `ai/AiContextBuilder.kt`, `ai/AiContextSource.kt`, `ai/AiServices.kt`, `domain/HabitCategories.kt`
- Modify: `ui/addedit/AddEditHabitScreen.kt:99` (use `DEFAULT_CATEGORIES` from domain instead of the private list)
- Test: `ai/AiGatewayTest.kt`, `ai/AiContextBuilderTest.kt`, `ai/AiPrefsTest.kt` (Robolectric), `ai/DemoAiApiTest.kt`

**Interfaces:**
- Consumes: Task 3 DTOs/`AiApi`/`AiResult`/`HttpAiApi`.
- Produces:
  - `interface AiSettingsSource { val aiEnabled: Flow<Boolean>; val aiConsent: Flow<Boolean>; val demoAi: Flow<Boolean> }`
  - `class AiPrefs(context: Context) : AiSettingsSource` with `showForecast: Flow<Boolean>`, `reviewBannerDismissedWeek: Flow<String?>`, `forecastModelJson: Flow<String?>`, `reviewCacheJson: Flow<String?>`, suspend setters `setAiEnabled`, `setAiConsent`, `setDemoAi`, `setShowForecast`, `setReviewBannerDismissedWeek(week: String)`, `setForecastModelJson(json: String)`, `setReviewCacheJson(json: String)`, and `suspend fun installId(): String`.
  - `class AiGateway(settings: AiSettingsSource, http: AiApi?, demo: AiApi) { val remaining: StateFlow<Int?>; suspend fun <T> call(block: suspend AiApi.() -> AiResult<T>): AiResult<T> }`
  - `object AiContextBuilder { fun build(habits: List<HabitEntity>, logs: List<HabitLogEntity>, asOf: LocalDate, risks: Map<Long, Double> = emptyMap(), zone: ZoneId = ZoneId.systemDefault()): List<HabitSummaryDto> }`
  - `class AiContextSource(data: HabitDataSource, risks: suspend () -> Map<Long, Double> = { emptyMap() }) { suspend fun summaries(asOf: LocalDate): List<HabitSummaryDto> }`
  - `object AiServices { fun prefs(context: Context): AiPrefs; fun gateway(context: Context): AiGateway }` (process-wide singletons built from `applicationContext`)
  - `val DEFAULT_CATEGORIES: List<String>` in `com.habitly.app.domain`

- [ ] **Step 1: Extract categories.** `domain/HabitCategories.kt`:
```kotlin
package com.habitly.app.domain

val DEFAULT_CATEGORIES: List<String> = listOf("Health", "Fitness", "Mind", "Learning", "Productivity", "General")
```
Replace the private list in `AddEditHabitScreen.kt:99` with this import. Run the suite (no behaviour change).

- [ ] **Step 2: Write `AiPrefs`** following the `SettingsPrefs` pattern (same `context.habitlyDataStore`):
```kotlin
package com.habitly.app.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.habitly.app.data.prefs.habitlyDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

interface AiSettingsSource {
    val aiEnabled: Flow<Boolean>
    val aiConsent: Flow<Boolean>
    val demoAi: Flow<Boolean>
}

class AiPrefs(private val context: Context) : AiSettingsSource {
    private val store get() = context.habitlyDataStore

    override val aiEnabled = store.data.map { it[AI_ENABLED] ?: true }
    override val aiConsent = store.data.map { it[AI_CONSENT] ?: false }
    override val demoAi = store.data.map { it[DEMO_AI] ?: false }
    val showForecast = store.data.map { it[SHOW_FORECAST] ?: true }
    val reviewBannerDismissedWeek = store.data.map { it[REVIEW_DISMISSED] }
    val forecastModelJson = store.data.map { it[FORECAST_MODEL] }
    val reviewCacheJson = store.data.map { it[REVIEW_CACHE] }

    suspend fun setAiEnabled(v: Boolean) { store.edit { it[AI_ENABLED] = v } }
    suspend fun setAiConsent(v: Boolean) { store.edit { it[AI_CONSENT] = v } }
    suspend fun setDemoAi(v: Boolean) { store.edit { it[DEMO_AI] = v } }
    suspend fun setShowForecast(v: Boolean) { store.edit { it[SHOW_FORECAST] = v } }
    suspend fun setReviewBannerDismissedWeek(week: String) { store.edit { it[REVIEW_DISMISSED] = week } }
    suspend fun setForecastModelJson(json: String) { store.edit { it[FORECAST_MODEL] = json } }
    suspend fun setReviewCacheJson(json: String) { store.edit { it[REVIEW_CACHE] = json } }

    suspend fun installId(): String {
        store.data.first()[INSTALL_ID]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        var result = fresh
        store.edit { prefs -> result = prefs[INSTALL_ID] ?: fresh.also { prefs[INSTALL_ID] = it } }
        return result
    }

    private companion object {
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_CONSENT = booleanPreferencesKey("ai_consent")
        val DEMO_AI = booleanPreferencesKey("demo_ai")
        val SHOW_FORECAST = booleanPreferencesKey("show_forecast")
        val REVIEW_DISMISSED = stringPreferencesKey("review_banner_dismissed_week")
        val FORECAST_MODEL = stringPreferencesKey("forecast_model_json")
        val REVIEW_CACHE = stringPreferencesKey("review_cache_json")
        val INSTALL_ID = stringPreferencesKey("install_id")
    }
}
```
Check whether `SettingsViewModel.onResetAllData()` / `resetAll()` clears the whole DataStore; if it does, that is fine (AI prefs reset too). If it doesn't, leave AI prefs alone (reset is about habit data).

`AiPrefsTest` (Robolectric, clear DataStore in `@Before` like `SettingsPrefsTest`): defaults (`aiEnabled` true, `aiConsent` false, `demoAi` false, `showForecast` true); setters round-trip; `installId()` returns the same UUID on two calls and matches the UUID regex.

- [ ] **Step 3: Write failing `AiGatewayTest`**

```kotlin
package com.habitly.app.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AiGatewayTest {
    private class Settings(enabled: Boolean = true, consent: Boolean = true, demo: Boolean = false) : AiSettingsSource {
        override val aiEnabled = MutableStateFlow(enabled)
        override val aiConsent = MutableStateFlow(consent)
        override val demoAi = MutableStateFlow(demo)
    }
    private class Api(val tag: String) : AiApi {
        var calls = 0
        override suspend fun suggestHabits(request: SuggestRequest) = AiResult.Failed("unused")
        override suspend fun weeklyReview(request: ReviewRequest) = AiResult.Failed("unused")
        override suspend fun coach(request: CoachRequest): AiResult<CoachResponse> { calls++; return AiResult.Success(CoachResponse(tag), 7) }
    }
    private val req = CoachRequest("2026-09-30", emptyList(), emptyList())

    @Test fun disabledWins() = runTest {
        val g = AiGateway(Settings(enabled = false, demo = true), Api("http"), Api("demo"))
        assertEquals(AiResult.Disabled, g.call { coach(req) })
    }
    @Test fun demoNeedsNoConsentOrConfig() = runTest {
        val g = AiGateway(Settings(consent = false, demo = true), null, Api("demo"))
        assertEquals("demo", (g.call { coach(req) } as AiResult.Success).value.reply)
    }
    @Test fun consentRequiredBeforeHttp() = runTest {
        val http = Api("http")
        val g = AiGateway(Settings(consent = false), http, Api("demo"))
        assertEquals(AiResult.ConsentRequired, g.call { coach(req) })
        assertEquals(0, http.calls)
    }
    @Test fun notConfiguredWithoutHttp() = runTest {
        assertEquals(AiResult.NotConfigured, AiGateway(Settings(), null, Api("demo")).call { coach(req) })
    }
    @Test fun successUpdatesRemaining() = runTest {
        val g = AiGateway(Settings(), Api("http"), Api("demo"))
        g.call { coach(req) }
        assertEquals(7, g.remaining.value)
    }
}
```

- [ ] **Step 4: Implement `AiGateway`**
```kotlin
package com.habitly.app.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class AiGateway(
    private val settings: AiSettingsSource,
    private val http: AiApi?,
    private val demo: AiApi,
) {
    private val _remaining = MutableStateFlow<Int?>(null)
    val remaining: StateFlow<Int?> = _remaining.asStateFlow()

    suspend fun <T> call(block: suspend AiApi.() -> AiResult<T>): AiResult<T> {
        if (!settings.aiEnabled.first()) return AiResult.Disabled
        if (settings.demoAi.first()) return demo.block()
        if (!settings.aiConsent.first()) return AiResult.ConsentRequired
        val api = http ?: return AiResult.NotConfigured
        return api.block().also { if (it is AiResult.Success) _remaining.value = it.remaining }
    }
}
```

- [ ] **Step 5: Write failing `AiContextBuilderTest`** — build a daily habit created 20 days before `asOf = 2026-09-30` with logs: DONE on 5 of the last 7 days, SKIPPED on 1, nothing on 1; previous 7 days DONE 3. Assert:
  - `last7 == CountsDto(done = 5, rested = 1, missed = 1, due = 7)`; `prev7.done == 3`.
  - `weekdays` has 7 entries in MONDAY..SUNDAY order covering the last 28 days (`due` counts only days on/after the habit start).
  - `currentStreak`/`bestStreak` equal `StreakCalculator` results for the same records.
  - `avgCheckInHour` = rounded mean hour of `completedAt` for DONE logs in the last 28 days (null when none).
  - `dueToday`/`todayStatus` reflect the `asOf` day; `risk` comes from the map; `createdDaysAgo == 20`.
  - A SPECIFIC_DAYS habit maps `frequencyDays = "MONDAY,FRIDAY"` to `FrequencyDto("SPECIFIC_DAYS", listOf("MONDAY","FRIDAY"), null)`.
  - Archived habits are excluded; at most 20 habits are returned (active first by `sortOrder`).
  - The serialized JSON of the result contains neither a log `note` value nor the word from a note (put a note "secret-note" on one log and assert `AiJson.encodeToString(result)` does not contain it).
  - Days before the habit's start (earlier of creation day and first log) are neither due nor missed.

- [ ] **Step 6: Implement `AiContextBuilder`**
```kotlin
package com.habitly.app.ai

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionRecord
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.HabitSchedule
import com.habitly.app.domain.StreakCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object AiContextBuilder {
    const val MAX_HABITS = 20

    fun build(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        asOf: LocalDate,
        risks: Map<Long, Double> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HabitSummaryDto> {
        val byHabit = logs.groupBy { it.habitId }
        return habits.filter { !it.isArchived }.sortedBy { it.sortOrder }.take(MAX_HABITS).map { habit ->
            val habitLogs = byHabit[habit.id].orEmpty()
            val statuses = habitLogs.associate { LocalDate.parse(it.date) to it.status }
            val created = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
            val start = (statuses.keys + created).min()
            val schedule = HabitSchedule.parse(habit.frequencyType, habit.frequencyDays, habit.intervalDays, start)
            fun counts(from: LocalDate, to: LocalDate): CountsDto {
                var done = 0; var rested = 0; var missed = 0; var due = 0
                var d = from
                while (!d.isAfter(to)) {
                    if (!d.isBefore(start) && schedule.isDue(d)) {
                        due++
                        when (statuses[d]) {
                            CompletionStatus.DONE -> done++
                            CompletionStatus.SKIPPED -> rested++
                            null -> if (d.isBefore(asOf)) missed++
                            else -> missed++
                        }
                    }
                    d = d.plusDays(1)
                }
                return CountsDto(done, rested, missed, due)
            }
            val weekdays = DayOfWeek.entries.map { day ->
                var done = 0; var due = 0
                for (i in 0 until 28) {
                    val d = asOf.minusDays(i.toLong())
                    if (d.dayOfWeek == day && !d.isBefore(start) && schedule.isDue(d)) {
                        due++
                        if (statuses[d] == CompletionStatus.DONE) done++
                    }
                }
                WeekdayStatDto(day.name, done, due)
            }
            val records = statuses.map { (d, s) -> CompletionRecord(d, s) }.sortedBy { it.date }
            val hours = habitLogs
                .filter { it.status == CompletionStatus.DONE && it.completedAt != null &&
                    !LocalDate.parse(it.date).isBefore(asOf.minusDays(27)) }
                .map { Instant.ofEpochMilli(it.completedAt!!).atZone(zone).hour }
            HabitSummaryDto(
                id = habit.id, name = habit.name, category = habit.category,
                frequency = FrequencyDto(
                    type = habit.frequencyType.name,
                    days = habit.frequencyDays?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
                    intervalDays = habit.intervalDays,
                ),
                targetCount = habit.targetCount, unit = habit.unit?.takeIf { it.isNotBlank() }, reminderTime = habit.reminderTime,
                createdDaysAgo = ChronoUnit.DAYS.between(created, asOf).toInt().coerceAtLeast(0),
                currentStreak = StreakCalculator.currentStreak(records, asOf, schedule),
                bestStreak = StreakCalculator.bestStreak(records),
                last7 = counts(asOf.minusDays(6), asOf),
                prev7 = counts(asOf.minusDays(13), asOf.minusDays(7)),
                weekdays = weekdays,
                avgCheckInHour = if (hours.isEmpty()) null else hours.average().roundToInt(),
                dueToday = !asOf.isBefore(start) && schedule.isDue(asOf),
                todayStatus = statuses[asOf]?.name,
                risk = risks[habit.id],
            )
        }
    }
}
```
(`PARTIAL` on a past day counts as missed, matching `Frequency.kt`'s doc. Verify `HabitSchedule.parse`'s `startDate` semantics for INTERVAL habits and pass `start` consistently.)

`ai/AiContextSource.kt`:
```kotlin
package com.habitly.app.ai

import com.habitly.app.data.repository.HabitDataSource
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class AiContextSource(
    private val data: HabitDataSource,
    private val risks: suspend () -> Map<Long, Double> = { emptyMap() },
) {
    suspend fun summaries(asOf: LocalDate): List<HabitSummaryDto> =
        AiContextBuilder.build(data.observeAllHabits().first(), data.observeAllLogs().first(), asOf, risks())
}
```

- [ ] **Step 7: `DemoAiApi`** — realistic canned responses, built only from the request (no network). `class DemoAiApi(private val delayMs: Long = 700) : AiApi`:
  - `suggestHabits`: pick intent by keyword in `text.lowercase()` (`sleep|bed|tired` → sleep; `fit|gym|run|workout|exercise` → fitness; `phone|screen|scroll|focus` → focus; `water|drink|eat|health` → health; `calm|stress|meditat|mind` → mind; else other), return 2–3 fixed habits per intent, e.g. sleep: "Lights out by 23:00" (`sleep`, seed 2, Health, DAILY, reminder "22:45", why "A fixed bedtime is the easiest sleep win"), "No phone in bed" (`phone_off`, seed 9, Health, DAILY, reminder "22:30", why "Screens keep your brain awake"). Only use icon keys that exist in `HABIT_ICON_KEYS` (see list in the brief) and skip names already in `existingHabitNames`.
  - `weeklyReview`: use the request's habits. Summary page from total `last7` done/due ("Solid week — {pct}% kept"); a win page for the habit with the highest `currentStreak`; a pattern page for the weekday with the lowest done/due ratio ("{Weekday}s are your toughest day"); one tip page for the habit with the most `last7.missed` with action `set_reminder` to `avgCheckInHour ?: 19` formatted `HH:00`, label "Move reminder". 3–4 pages.
  - `coach`: opening (no messages) → "You have {n} habits left today. {riskiest name} is the one to watch — a short version still counts." with actions `log_done(riskiest)` label "Log it now" and `rest_today(riskiest)` label "Rest day"; if all done → "Everything's done today — nice work." no actions. For a user message: fixed friendly replies keyed by the quick chips ("Plan my day", "Why do I slip?", "Motivate me", "Which habit first?") using the request's numbers; any other text → a generic encouraging reply.
  - Every result is `AiResult.Success(value, remaining = null)` after `delay(delayMs)`.
  `DemoAiApiTest` (delayMs = 0): suggest "I want to sleep better" returns intent "sleep" and ≥ 2 habits whose iconKey ∈ `HABIT_ICON_KEYS`; review with 2 habits returns a summary first and only known habit ids; coach opening references the riskiest habit id in its actions.

- [ ] **Step 8: `AiServices`**
```kotlin
package com.habitly.app.ai

import android.content.Context
import com.habitly.app.BuildConfig

object AiServices {
    @Volatile private var prefs: AiPrefs? = null
    @Volatile private var gateway: AiGateway? = null

    fun prefs(context: Context): AiPrefs =
        prefs ?: synchronized(this) { prefs ?: AiPrefs(context.applicationContext).also { prefs = it } }

    fun gateway(context: Context): AiGateway = gateway ?: synchronized(this) {
        gateway ?: run {
            val p = prefs(context)
            val http = if (BuildConfig.AI_BASE_URL.isBlank() || BuildConfig.AI_APP_TOKEN.isBlank()) null
            else HttpAiApi(BuildConfig.AI_BASE_URL, BuildConfig.AI_APP_TOKEN, deviceId = { p.installId() })
            AiGateway(p, http, DemoAiApi())
        }.also { gateway = it }
    }
}
```

- [ ] **Step 9: Run full suite + assembleDebug; commit** `feat(ai): consent-aware gateway, demo mode and anonymised habit context`.

---

### Task 5: App — shared AI UI, consent sheet, Settings section

**Files:**
- Create: `ai/ui/AiStates.kt`, `ai/ui/ConsentSheet.kt`
- Modify: `ui/components/SettingsGroup.kt` (optional `onTitleClick`), `ui/settings/SettingsUiState.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `ui/navigation/HabitlyNavHost.kt` (Settings wiring)
- Test: `ai/ui/AiStatesTest.kt`, update `ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `AiResult`, `AiPrefs`, `AiServices` (Task 4).
- Produces (used by Tasks 7, 9, 10, 11):
  - `fun aiErrorMessage(result: AiResult<*>): String?` — null for Success/ConsentRequired
  - `@Composable fun AiErrorCard(result: AiResult<*>, onRetry: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun AiSkeleton(lines: Int = 3, modifier: Modifier = Modifier)`
  - `@Composable fun AiRemainingNote(remaining: Int?, modifier: Modifier = Modifier)` — renders only when `remaining != null && remaining <= 3`: "{n} AI requests left today" (singular "1 AI request left today")
  - `@Composable fun AiSparkleIcon(modifier: Modifier = Modifier)` — `Icons.Rounded.AutoAwesome` tinted `MaterialTheme.colorScheme.primary`
  - `@Composable fun ConsentSheet(onAllow: () -> Unit, onDismiss: () -> Unit, readOnly: Boolean = false)`
  - `const val CONSENT_TEXT_SENT`, `CONSENT_TEXT_NOT_SENT`, `CONSENT_TEXT_WHERE` (strings reused in the report)

- [ ] **Step 1: Failing `AiStatesTest`**
```kotlin
@Test fun messagesMatchSpecCopy() {
    assertEquals("AI needs an internet connection", aiErrorMessage(AiResult.Offline))
    assertEquals("Daily AI limit reached — back tomorrow", aiErrorMessage(AiResult.RateLimited(null)))
    assertEquals("AI isn't set up in this build — turn on Demo AI in Settings", aiErrorMessage(AiResult.NotConfigured))
    assertEquals("AI features are turned off in Settings", aiErrorMessage(AiResult.Disabled))
    assertEquals("Something went wrong. Try again.", aiErrorMessage(AiResult.Failed("server")))
    assertNull(aiErrorMessage(AiResult.ConsentRequired))
    assertNull(aiErrorMessage(AiResult.Success(Unit, null)))
}
```

- [ ] **Step 2: Implement `ai/ui/AiStates.kt`**
  - `aiErrorMessage` as tested.
  - `AiErrorCard`: `Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer)` → Column(padding 16dp): an icon (`Icons.Rounded.CloudOff` for Offline, `Icons.Rounded.HourglassEmpty` for RateLimited, `Icons.Rounded.Settings` for NotConfigured/Disabled, `Icons.Rounded.ErrorOutline` otherwise) + message text (`bodyLarge`), then `SecondaryButton("Try again", onRetry, leadingIcon = Icons.Rounded.Refresh)` — hidden for Disabled/NotConfigured/RateLimited (retry cannot help). Semantics: `liveRegion = LiveRegionMode.Polite` on the message.
  - `AiSkeleton`: Column of `lines` rounded bars (height 14dp, widths 100%/85%/60% cycling, colour `LocalHabitlyColors.current.gridEmpty`), alpha animated 0.4↔0.9 with `rememberInfiniteTransition` (static 0.6 when `LocalReduceMotion.current`). `semantics { contentDescription = "Loading AI response" }`.
  - `AiRemainingNote`, `AiSparkleIcon` as in Interfaces.

- [ ] **Step 3: `ConsentSheet`** (uses `HabitlySheet(onDismiss, title = "Before you use AI")`):
  - Three rows with icons: "What's sent" → `CONSENT_TEXT_SENT = "Habit names, schedules, reminder times and daily done / rest / missed counts."`; "Never sent" → `CONSENT_TEXT_NOT_SENT = "Your name and your notes."`; "Where it goes" → `CONSENT_TEXT_WHERE = "Habitly's server, which asks OpenAI and returns suggestions. Nothing is stored there."`
  - Footer line (bodySmall): "Today's forecast never leaves your phone."
  - Buttons: `PrimaryButton("Allow", onAllow)` and `SecondaryButton("Not now", onDismiss)`; in `readOnly` mode only a "Close" SecondaryButton.
  - Previews dark + light.

- [ ] **Step 4: Settings.**
  - `SettingsGroup`: add `onTitleClick: (() -> Unit)? = null`; when set, the title Text gets `Modifier.clickable(onClickLabel = null, role = null) { onTitleClick() }` (no visual change).
  - `SettingsUiState`: add `aiEnabled: Boolean = true`, `showForecast: Boolean = true`, `demoAi: Boolean = false`, `demoAiRevealed: Boolean = false`.
  - `SettingsViewModel`: add constructor param `aiPrefs: AiPrefs` (after `repository`). Combine the existing state with `aiPrefs.aiEnabled`, `aiPrefs.showForecast`, `aiPrefs.demoAi` (nest two `combine` calls). New: `fun onAiEnabledToggle(v: Boolean)`, `fun onShowForecastToggle(v: Boolean)`, `fun onDemoAiToggle(v: Boolean)`, `fun onAiHeaderTapped()` — counts taps in the VM; on the 5th sets `demoAiRevealed = true` (also true whenever `demoAi` is already on).
  - `SettingsScreen`: new params `onAiEnabledToggle`, `onShowForecastToggle`, `onDemoAiToggle`, `onAiHeaderTapped`. New `SettingsGroup(title = "AI features", onTitleClick = onAiHeaderTapped)` placed after "Reminders", rows:
    - "AI features" (icon `AutoAwesome`, supporting "Create with AI, coach and weekly review") + `HabitlySwitch(uiState.aiEnabled, onAiEnabledToggle)`
    - "Show forecast" (icon `Insights`, supporting "Today's on-device risk forecast") + switch
    - "What's shared" (icon `Shield`, onClick opens `ConsentSheet(readOnly = true)`)
    - when `demoAiRevealed`: "Demo AI" (icon `Science`, supporting "Canned answers for offline demos") + switch
  - NavHost Settings wiring: pass `AiServices.prefs(appContext)`.
  - Update `SettingsViewModelTest` for the new constructor param (real `AiPrefs` on the Robolectric context) and add tests: toggles persist; 4 header taps do not reveal, the 5th does.

- [ ] **Step 5: Full suite + assembleDebug; commit** `feat(ai): consent sheet, shared AI states and Settings AI section`.

---
### Task 6: ML — features, logistic regression, explanations

**Files:**
- Create: `ml/RiskFeatures.kt`, `ml/FeatureExtractor.kt`, `ml/RiskModel.kt`, `ml/RiskExplainer.kt`
- Test: `ml/FeatureExtractorTest.kt`, `ml/RiskModelTest.kt`, `ml/RiskExplainerTest.kt`, `ml/SyntheticHistory.kt` (test helper) — plain JUnit

**Interfaces:**
- Produces:
  - `const val FEATURE_COUNT = 15`; `val FEATURE_NAMES: List<String>`; indices `F_RATE7 = 7, F_RATE28 = 8, F_SAME_WEEKDAY = 9, F_STREAK = 10, F_MISSED_PREV = 11, F_AGE = 12, F_REMINDER = 13, F_WEEKEND = 14` (0..6 = Monday..Sunday one-hot)
  - `class TrainingRow(val habitId: Long, val date: LocalDate, val x: DoubleArray, val y: Int)`
  - `data class HabitHistory(val habit: HabitEntity, val start: LocalDate, val schedule: HabitSchedule, val statuses: Map<LocalDate, CompletionStatus>)`
  - `object FeatureExtractor { fun histories(habits, logs, zone = ZoneId.systemDefault()): List<HabitHistory>; fun rows(habits, logs, today: LocalDate, zone = ZoneId.systemDefault(), lookbackDays: Int = 90): List<TrainingRow>; fun features(h: HabitHistory, date: LocalDate): DoubleArray }`
  - `@Serializable data class ModelMetrics(val accuracy: Double, val auc: Double?, val brier: Double, val testRows: Int)`
  - `@Serializable data class RiskModel(weights: List<Double>, bias: Double, means: List<Double>, stds: List<Double>, trainedAt: Long, rows: Int, metrics: ModelMetrics? = null, isStarter: Boolean = false)` with `predict(x)`, `contributions(x)`, `standardized(x, i)`; companion `MIN_ROWS = 40`, `starter(now)`, `train(rows, now, lambda = 0.01, epochs = 400, lr = 0.1)`, `fit(rows, today, now)`, `evaluate(model, test)`, `auc(scores, labels): Double?`
  - `object RiskExplainer { fun reason(model: RiskModel, x: DoubleArray, date: LocalDate): String }`
  - Test helper `object SyntheticHistory { fun mondaySkipper(today: LocalDate, days: Int, habits: Int, random: Random): Pair<List<HabitEntity>, List<HabitLogEntity>> }` (reused by Task 7 tests)

- [ ] **Step 1: `ml/RiskFeatures.kt`**
```kotlin
package com.habitly.app.ml

import java.time.LocalDate

const val FEATURE_COUNT = 15
const val F_RATE7 = 7
const val F_RATE28 = 8
const val F_SAME_WEEKDAY = 9
const val F_STREAK = 10
const val F_MISSED_PREV = 11
const val F_AGE = 12
const val F_REMINDER = 13
const val F_WEEKEND = 14

val FEATURE_NAMES: List<String> = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    "Last 7 due days kept", "Last 28 due days kept", "Same weekday, last 4 weeks", "Streak going in",
    "Missed the previous due day", "Habit age", "Has a reminder", "Weekend",
)

class TrainingRow(val habitId: Long, val date: LocalDate, val x: DoubleArray, val y: Int)
```

- [ ] **Step 2: Test helper `SyntheticHistory`** (`app/src/test/java/com/habitly/app/ml/SyntheticHistory.kt`): DAILY habits named "Habit 1..n", ids 1..n, `createdAt` = `today.minusDays(days)` at start of day UTC, `reminderTime = null`, colours from `HabitPalette.seeds`; for each day from creation to yesterday: Monday → a `MISSED` log; otherwise a DONE log with `completedAt` at 19:00 UTC unless `random.nextDouble() < 0.1` (no log).

- [ ] **Step 3: Failing `FeatureExtractorTest`** — helpers `habit(id, createdDaysAgo, type = DAILY, days = null, reminder = null)` and `log(habitId, date, status)`; `today = 2026-09-30` (Wednesday), `zone = ZoneOffset.UTC`:
  - `rowsExcludeTodayRestDaysAndDaysBeforeStart`: daily habit created 10 days ago, SKIPPED on one day → 9 rows; no row dated `today`.
  - `labelIsOneUnlessDone`: DONE → y 0; MISSED, PARTIAL and no log → y 1.
  - `noLookAhead`: features for day D are identical with full logs vs logs filtered to dates `< D`.
  - `defaultsWithoutHistory`: first due day → rate7 = rate28 = sameWeekday = 0.5, streak feature 0, missedPrev 0.
  - `streakAndMissedPrev`: DONE on D−3..D−1 → `x[F_STREAK] == ln(4.0)`, `x[F_MISSED_PREV] == 0.0`; MISSED on D−1 → missedPrev 1, streak 0.
  - `weekdayOneHotAndWeekend`: a Saturday row has `x[5] == 1.0`, other weekday slots 0, `x[F_WEEKEND] == 1.0`.
  - `specificDaysOnlyDueDays`: MONDAY,FRIDAY habit → rows only on Mondays and Fridays.
  - `lookbackCapped`: habit created 200 days ago → earliest row date ≥ today − 90.
  - `startIsEarlierOfCreationAndFirstLog`: a log 5 days before `createdAt` produces a row for that day.

- [ ] **Step 4: Implement `FeatureExtractor`**
```kotlin
package com.habitly.app.ml

import com.habitly.app.data.local.HabitEntity
import com.habitly.app.data.local.HabitLogEntity
import com.habitly.app.domain.CompletionStatus
import com.habitly.app.domain.HabitSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ln

data class HabitHistory(
    val habit: HabitEntity,
    val start: LocalDate,
    val schedule: HabitSchedule,
    val statuses: Map<LocalDate, CompletionStatus>,
)

object FeatureExtractor {
    private const val WALK_BACK_DAYS = 120L

    fun histories(habits: List<HabitEntity>, logs: List<HabitLogEntity>, zone: ZoneId = ZoneId.systemDefault()): List<HabitHistory> {
        val byHabit = logs.groupBy { it.habitId }
        return habits.map { habit ->
            val statuses = byHabit[habit.id].orEmpty().associate { LocalDate.parse(it.date) to it.status }
            val created = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
            val start = (statuses.keys + created).min()
            HabitHistory(habit, start, HabitSchedule.parse(habit.frequencyType, habit.frequencyDays, habit.intervalDays, start), statuses)
        }
    }

    fun rows(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        lookbackDays: Int = 90,
    ): List<TrainingRow> = histories(habits, logs, zone).flatMap { h ->
        val from = maxOf(h.start, today.minusDays(lookbackDays.toLong()))
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it.isBefore(today) }
            .filter { h.schedule.isDue(it) && h.statuses[it] != CompletionStatus.SKIPPED }
            .map { d -> TrainingRow(h.habit.id, d, features(h, d), if (h.statuses[d] == CompletionStatus.DONE) 0 else 1) }
            .toList()
    }

    /** Uses only data strictly before [date]. */
    fun features(h: HabitHistory, date: LocalDate): DoubleArray {
        val previous = ArrayList<Boolean>(28) // most recent first; true = kept
        val stop = maxOf(h.start, date.minusDays(WALK_BACK_DAYS))
        var d = date.minusDays(1)
        while (!d.isBefore(stop) && previous.size < 28) {
            if (h.schedule.isDue(d) && h.statuses[d] != CompletionStatus.SKIPPED) previous += h.statuses[d] == CompletionStatus.DONE
            d = d.minusDays(1)
        }
        fun rate(values: List<Boolean>) = if (values.isEmpty()) 0.5 else values.count { it }.toDouble() / values.size
        val sameWeekday = (1L..4L).map { date.minusWeeks(it) }
            .filter { !it.isBefore(h.start) && h.schedule.isDue(it) && h.statuses[it] != CompletionStatus.SKIPPED }
            .map { h.statuses[it] == CompletionStatus.DONE }
        val streak = previous.takeWhile { it }.size
        return DoubleArray(FEATURE_COUNT).also { x ->
            x[date.dayOfWeek.value - 1] = 1.0
            x[F_RATE7] = rate(previous.take(7))
            x[F_RATE28] = rate(previous)
            x[F_SAME_WEEKDAY] = rate(sameWeekday)
            x[F_STREAK] = ln(1.0 + streak)
            x[F_MISSED_PREV] = if (previous.isNotEmpty() && !previous[0]) 1.0 else 0.0
            x[F_AGE] = ln(1.0 + ChronoUnit.DAYS.between(h.start, date))
            x[F_REMINDER] = if (h.habit.reminderTime != null) 1.0 else 0.0
            x[F_WEEKEND] = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) 1.0 else 0.0
        }
    }
}
```
Run the FeatureExtractor tests — PASS.

- [ ] **Step 5: Failing `RiskModelTest`**
```kotlin
package com.habitly.app.ml

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ln
import kotlin.random.Random

class RiskModelTest {
    private val today = LocalDate.of(2026, 9, 30)

    private fun mondayRows(): List<TrainingRow> {
        val (habits, logs) = SyntheticHistory.mondaySkipper(today, days = 120, habits = 3, random = Random(42))
        return FeatureExtractor.rows(habits, logs, today, ZoneOffset.UTC)
    }

    @Test fun learnsMondayPatternWithGoodHeldOutAuc() {
        val model = RiskModel.fit(mondayRows(), today, now = 1L)
        assertFalse(model.isStarter)
        val metrics = requireNotNull(model.metrics)
        assertTrue("auc=${metrics.auc}", requireNotNull(metrics.auc) > 0.7)
        assertTrue(metrics.brier < 0.2)
    }

    @Test fun mondayRiskAboveTuesday() {
        val rows = mondayRows()
        val model = RiskModel.fit(rows, today, now = 1L)
        val monday = rows.last { it.date.dayOfWeek == DayOfWeek.MONDAY }
        val tuesday = rows.last { it.date.dayOfWeek == DayOfWeek.TUESDAY }
        assertTrue(model.predict(monday.x) > model.predict(tuesday.x) + 0.3)
    }

    @Test fun deterministic() {
        val rows = mondayRows()
        assertEquals(RiskModel.train(rows, 1L), RiskModel.train(rows, 1L))
    }

    @Test fun tooFewRowsOrOneClassUsesStarter() {
        val rows = mondayRows()
        assertTrue(RiskModel.fit(rows.take(39), today, 1L).isStarter)
        assertTrue(RiskModel.fit(rows.filter { it.y == 0 }, today, 1L).isStarter)
    }

    @Test fun starterIsSensible() {
        val starter = RiskModel.starter(0L)
        val good = DoubleArray(FEATURE_COUNT).also { it[F_RATE7] = 1.0; it[F_RATE28] = 1.0; it[F_SAME_WEEKDAY] = 1.0; it[F_STREAK] = ln(8.0) }
        val bad = DoubleArray(FEATURE_COUNT).also { it[F_MISSED_PREV] = 1.0 }
        assertTrue(starter.predict(good) < 0.3)
        assertTrue(starter.predict(bad) > 0.6)
    }

    @Test fun aucHandlesTiesAndSingleClass() {
        assertEquals(1.0, RiskModel.auc(listOf(0.9, 0.1), listOf(1, 0))!!, 1e-9)
        assertEquals(0.5, RiskModel.auc(listOf(0.5, 0.5), listOf(1, 0))!!, 1e-9)
        assertNull(RiskModel.auc(listOf(0.3, 0.4), listOf(1, 1)))
    }

    @Test fun roundTripsThroughJson() {
        val model = RiskModel.fit(mondayRows(), today, 1L)
        val json = Json.encodeToString(RiskModel.serializer(), model)
        assertEquals(model, Json.decodeFromString(RiskModel.serializer(), json))
    }
}
```

- [ ] **Step 6: Implement `RiskModel`**
```kotlin
package com.habitly.app.ml

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.sqrt

@Serializable
data class ModelMetrics(val accuracy: Double, val auc: Double?, val brier: Double, val testRows: Int)

@Serializable
data class RiskModel(
    val weights: List<Double>,
    val bias: Double,
    val means: List<Double>,
    val stds: List<Double>,
    val trainedAt: Long,
    val rows: Int,
    val metrics: ModelMetrics? = null,
    val isStarter: Boolean = false,
) {
    fun standardized(x: DoubleArray, i: Int): Double = (x[i] - means[i]) / stds[i]

    fun contributions(x: DoubleArray): DoubleArray = DoubleArray(FEATURE_COUNT) { i -> weights[i] * standardized(x, i) }

    fun predict(x: DoubleArray): Double = sigmoid(bias + contributions(x).sum())

    companion object {
        const val MIN_ROWS = 40
        private const val HOLDOUT_DAYS = 7L

        private fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

        /** Hand-set weights used until there is enough personal history (≥ 40 rows, both classes). */
        fun starter(now: Long): RiskModel {
            val w = DoubleArray(FEATURE_COUNT)
            w[F_RATE7] = -2.5; w[F_RATE28] = -1.0; w[F_SAME_WEEKDAY] = -1.0; w[F_STREAK] = -0.4; w[F_MISSED_PREV] = 1.2
            return RiskModel(w.toList(), bias = 1.6, means = List(FEATURE_COUNT) { 0.0 }, stds = List(FEATURE_COUNT) { 1.0 },
                trainedAt = now, rows = 0, isStarter = true)
        }

        /** L2-regularised batch gradient descent on z-scored features. Deterministic. */
        fun train(rows: List<TrainingRow>, now: Long, lambda: Double = 0.01, epochs: Int = 400, lr: Double = 0.1): RiskModel {
            val m = rows.size
            val means = DoubleArray(FEATURE_COUNT) { i -> rows.sumOf { it.x[i] } / m }
            val stds = DoubleArray(FEATURE_COUNT) { i ->
                val s = sqrt(rows.sumOf { (it.x[i] - means[i]).let { d -> d * d } } / m)
                if (s < 1e-9) 1.0 else s
            }
            val z = rows.map { r -> DoubleArray(FEATURE_COUNT) { i -> (r.x[i] - means[i]) / stds[i] } }
            val w = DoubleArray(FEATURE_COUNT)
            var b = 0.0
            repeat(epochs) {
                val gw = DoubleArray(FEATURE_COUNT)
                var gb = 0.0
                for (k in 0 until m) {
                    var s = b
                    for (i in 0 until FEATURE_COUNT) s += w[i] * z[k][i]
                    val err = sigmoid(s) - rows[k].y
                    for (i in 0 until FEATURE_COUNT) gw[i] += err * z[k][i]
                    gb += err
                }
                for (i in 0 until FEATURE_COUNT) w[i] -= lr * (gw[i] / m + lambda * w[i])
                b -= lr * gb / m
            }
            return RiskModel(w.toList(), b, means.toList(), stds.toList(), now, m)
        }

        /** Holds out the last 7 days to measure, then retrains on everything for use. */
        fun fit(rows: List<TrainingRow>, today: LocalDate, now: Long): RiskModel {
            if (rows.size < MIN_ROWS || rows.none { it.y == 1 } || rows.none { it.y == 0 }) return starter(now)
            val cutoff = today.minusDays(HOLDOUT_DAYS)
            val (test, trainRows) = rows.partition { !it.date.isBefore(cutoff) }
            val metrics = if (test.isNotEmpty() && trainRows.any { it.y == 1 } && trainRows.any { it.y == 0 }) {
                evaluate(train(trainRows, now), test)
            } else null
            return train(rows, now).copy(metrics = metrics)
        }

        fun evaluate(model: RiskModel, test: List<TrainingRow>): ModelMetrics {
            val p = test.map { model.predict(it.x) }
            val y = test.map { it.y }
            val accuracy = p.indices.count { (p[it] >= 0.5) == (y[it] == 1) }.toDouble() / test.size
            val brier = p.indices.sumOf { (p[it] - y[it]).let { d -> d * d } } / test.size
            return ModelMetrics(accuracy, auc(p, y), brier, test.size)
        }

        /** Mann–Whitney AUC with average ranks for ties; null when a class is missing. */
        fun auc(scores: List<Double>, labels: List<Int>): Double? {
            val pos = labels.count { it == 1 }
            val neg = labels.size - pos
            if (pos == 0 || neg == 0) return null
            val order = scores.indices.sortedBy { scores[it] }
            val ranks = DoubleArray(scores.size)
            var i = 0
            while (i < order.size) {
                var j = i
                while (j + 1 < order.size && scores[order[j + 1]] == scores[order[i]]) j++
                val avg = (i + j) / 2.0 + 1.0
                for (k in i..j) ranks[order[k]] = avg
                i = j + 1
            }
            val sumPos = labels.indices.filter { labels[it] == 1 }.sumOf { ranks[it] }
            return (sumPos - pos * (pos + 1) / 2.0) / (pos.toDouble() * neg)
        }
    }
}
```
Run the RiskModel tests — PASS.

- [ ] **Step 7: Failing `RiskExplainerTest`**
  - Monday-trained model: the reason for the most recent Monday row is `"you often miss Mondays"`.
  - Hand-built single-feature models (helper `model(weights: Map<Int, Double>, means: Map<Int, Double> = emptyMap(), bias = 0.0)` with stds 1 and means 0 unless given) so each case is unambiguous:
    - `F_MISSED_PREV` weight +1.2, `x[F_MISSED_PREV] = 1` → `"missed last time"`
    - `F_STREAK` weight −1.0, `x[F_STREAK] = ln(6.0)` → `"on a 5-day roll"`
    - `F_RATE7` weight −2.0, `means[F_RATE7] = 0.8`, `x[F_RATE7] = 0.2` → `"a patchy last week"`
    - `F_REMINDER` weight −1.0, `means[F_REMINDER] = 0.5`, `x[F_REMINDER] = 0` → `"no reminder set"`
    - all weights 0 → `"you're on track"`
  - Never another weekday: Tuesday weight −3, `means[1] = 0.14`, `stds` 1; a Wednesday row (`x[2] = 1`, `x[1] = 0`) gives Tuesday a positive contribution, but the reason must not contain "Tuesday" (falls through to `"you're on track"`).

- [ ] **Step 8: Implement `RiskExplainer`**
```kotlin
package com.habitly.app.ml

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

object RiskExplainer {
    private const val MIN_CONTRIBUTION = 0.05

    fun reason(model: RiskModel, x: DoubleArray, date: LocalDate): String {
        val c = model.contributions(x)
        val z = DoubleArray(FEATURE_COUNT) { model.standardized(x, it) }
        for (i in c.indices.sortedByDescending { c[it] }) {
            if (c[i] <= MIN_CONTRIBUTION) break
            riskReason(i, x, z, date)?.let { return it }
        }
        for (i in c.indices.sortedBy { c[it] }) {
            if (c[i] >= -MIN_CONTRIBUTION) break
            when {
                i == F_STREAK && streakOf(x) >= 2 -> return "on a ${streakOf(x)}-day roll"
                (i == F_RATE7 || i == F_RATE28) && z[i] > 0 -> return "a strong last week"
            }
        }
        return "you're on track"
    }

    private fun riskReason(i: Int, x: DoubleArray, z: DoubleArray, date: LocalDate): String? = when {
        i in 0..6 -> if (x[i] == 1.0) "you often miss ${plural(DayOfWeek.of(i + 1))}" else null
        i == F_RATE7 && z[i] < 0 -> "a patchy last week"
        i == F_RATE28 && z[i] < 0 -> "it's slipped this month"
        i == F_SAME_WEEKDAY && z[i] < 0 -> "${plural(date.dayOfWeek)} have been hard lately"
        i == F_STREAK && z[i] < 0 -> "no streak going yet"
        i == F_MISSED_PREV && x[i] == 1.0 -> "missed last time"
        i == F_AGE && z[i] < 0 -> "still a new habit"
        i == F_REMINDER && x[i] == 0.0 -> "no reminder set"
        i == F_WEEKEND && x[i] == 1.0 -> "weekends are harder"
        else -> null
    }

    private fun streakOf(x: DoubleArray) = (exp(x[F_STREAK]) - 1.0).roundToInt()
    private fun plural(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s"
}
```

- [ ] **Step 9: Run tests, full suite, assembleDebug. Commit** `feat(ml): on-device logistic-regression miss-risk model with explanations`.

---

### Task 7: Forecast — repository, worker, Today strip, info sheet (spec §6)

**Files:**
- Create: `ml/Forecaster.kt`, `ml/ForecastRepository.kt`, `ml/ForecastTrainWorker.kt`, `ui/today/ForecastStrip.kt`, `ui/today/ForecastInfoSheet.kt`
- Modify: `HabitlyApp.kt`, `ui/today/TodayUiState.kt`, `ui/today/TodayViewModel.kt`, `ui/today/TodayScreen.kt`, `ui/navigation/HabitlyNavHost.kt`
- Test: `ml/ForecasterTest.kt`, `ml/ForecastRepositoryTest.kt`, `ml/ForecastTrainWorkerTest.kt` (Robolectric), update `ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: Task 6; `AiPrefs.forecastModelJson`, `setForecastModelJson`, `showForecast`; `AiServices.prefs` (Task 4); `AiSparkleIcon` (Task 5).
- Produces:
  - `enum class RiskLevel(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low"); companion object { fun of(p: Double): RiskLevel } }` (p ≥ 0.6 HIGH, ≥ 0.3 MEDIUM, else LOW)
  - `data class HabitRisk(val habitId: Long, val name: String, val iconKey: String, val colorHex: String, val probability: Double, val level: RiskLevel, val reason: String) { val percent: Int get() = (probability * 100).roundToInt() }`
  - `data class Forecast(val items: List<HabitRisk>, val isLearning: Boolean, val dueToday: Int, val trainedAt: Long, val rows: Int, val metrics: ModelMetrics?) { val allClear: Boolean get() = items.isEmpty() && dueToday > 0 }`
  - `object Forecaster { fun today(model: RiskModel, habits: List<HabitEntity>, logs: List<HabitLogEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Forecast }`
  - `class ForecastRepository(data: HabitDataSource, modelJson: Flow<String?>, saveModelJson: suspend (String) -> Unit, today: () -> LocalDate = { LocalDate.now() }, clock: () -> Long = System::currentTimeMillis, zone: ZoneId = ZoneId.systemDefault(), compute: CoroutineContext = Dispatchers.Default)` with `fun observeToday(): Flow<Forecast>`, `suspend fun train(): RiskModel`, `suspend fun refreshIfStale(maxAgeMs: Long = 86_400_000L)`, `suspend fun todayRisks(): Map<Long, Double>`
  - `object ForecastServices { fun repository(context: Context): ForecastRepository }`
  - `object ForecastScheduler { fun schedule(context: Context) }`
  - `TodayUiState.forecast: Forecast?`

- [ ] **Step 1: Failing `ForecasterTest`** (starter model, `today` fixed, UTC):
  - Only habits due today and not DONE/SKIPPED appear, sorted by probability descending; `dueToday` counts every due habit.
  - All due habits DONE → `items` empty and `allClear`; no habit due → `dueToday == 0` and `!allClear`.
  - `isLearning == model.isStarter`; `percent` rounds; `RiskLevel.of(0.6) == HIGH`, `of(0.59) == MEDIUM`, `of(0.3) == MEDIUM`, `of(0.29) == LOW`.
  - Archived habits never appear; a habit whose start is after today never appears.

- [ ] **Step 2: Implement `Forecaster`**
```kotlin
object Forecaster {
    fun today(model: RiskModel, habits: List<HabitEntity>, logs: List<HabitLogEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Forecast {
        var due = 0
        val items = FeatureExtractor.histories(habits.filter { !it.isArchived }, logs, zone).mapNotNull { h ->
            if (today.isBefore(h.start) || !h.schedule.isDue(today)) return@mapNotNull null
            due++
            val status = h.statuses[today]
            if (status == CompletionStatus.DONE || status == CompletionStatus.SKIPPED) return@mapNotNull null
            val x = FeatureExtractor.features(h, today)
            val p = model.predict(x)
            HabitRisk(h.habit.id, h.habit.name, h.habit.icon, h.habit.colorHex, p, RiskLevel.of(p), RiskExplainer.reason(model, x, today))
        }
        return Forecast(items.sortedByDescending { it.probability }, model.isStarter, due, model.trainedAt, model.rows, model.metrics)
    }
}
```
Put `RiskLevel`, `HabitRisk` and `Forecast` in the same file.

- [ ] **Step 3: Failing `ForecastRepositoryTest`** — fake `HabitDataSource` (only `observeAllHabits`/`observeAllLogs` backed by `MutableStateFlow`s; other members `TODO()`-free no-ops returning defaults), `MutableStateFlow<String?>` for the stored JSON, `compute = StandardTestDispatcher` from `runTest`:
  - `refreshIfStale` with nothing stored trains and saves JSON that decodes to a non-starter model for `SyntheticHistory.mondaySkipper(today, 120, 3, Random(42))`.
  - stored model 1 h old → no retrain (JSON unchanged); 25 h old → retrained (`trainedAt` updated).
  - `observeToday` re-emits when logs change: add a DONE log for the first item → it disappears from the next emission.
  - corrupt stored JSON → `observeToday` still emits (starter, `isLearning == true`), no exception.
  - `todayRisks()` maps each item's habit id → probability.

- [ ] **Step 4: Implement `ForecastRepository` + `ForecastServices`**
```kotlin
class ForecastRepository(
    private val data: HabitDataSource,
    private val modelJson: Flow<String?>,
    private val saveModelJson: suspend (String) -> Unit,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val compute: CoroutineContext = Dispatchers.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val model: Flow<RiskModel?> = modelJson.map { text ->
        text?.let { runCatching { json.decodeFromString(RiskModel.serializer(), it) }.getOrNull() }
    }

    fun observeToday(): Flow<Forecast> =
        combine(model, data.observeAllHabits(), data.observeAllLogs()) { m, habits, logs ->
            Forecaster.today(m ?: RiskModel.starter(0L), habits, logs, today(), zone)
        }.flowOn(compute)

    suspend fun train(): RiskModel = withContext(compute) {
        val rows = FeatureExtractor.rows(data.observeAllHabits().first(), data.observeAllLogs().first(), today(), zone)
        RiskModel.fit(rows, today(), clock())
    }.also { saveModelJson(json.encodeToString(RiskModel.serializer(), it)) }

    suspend fun refreshIfStale(maxAgeMs: Long = 86_400_000L) {
        val current = model.first()
        if (current == null || clock() - current.trainedAt > maxAgeMs) train()
    }

    suspend fun todayRisks(): Map<Long, Double> = observeToday().first().items.associate { it.habitId to it.probability }
}

object ForecastServices {
    @Volatile private var instance: ForecastRepository? = null
    fun repository(context: Context): ForecastRepository = instance ?: synchronized(this) {
        instance ?: run {
            val app = context.applicationContext
            val db = AppDatabase.getInstance(app)
            val prefs = AiServices.prefs(app)
            ForecastRepository(
                HabitRepository(db.habitDao(), db.habitLogDao(), db.achievementDao()),
                prefs.forecastModelJson,
                prefs::setForecastModelJson,
            )
        }.also { instance = it }
    }
}
```

- [ ] **Step 5: Worker + scheduling** (`ml/ForecastTrainWorker.kt`):
```kotlin
class ForecastTrainWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ForecastServices.repository(applicationContext).train()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

object ForecastScheduler {
    private const val WORK_NAME = "forecast_train"
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ForecastTrainWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
```
Call `ForecastScheduler.schedule(this)` in `HabitlyApp.onCreate` after `ReminderScheduler.schedule(this)`. `ForecastTrainWorkerTest` (Robolectric, `TestListenableWorkerBuilder<ForecastTrainWorker>(context).build().doWork()`) → `Result.success()` on an empty database.

- [ ] **Step 6: TodayViewModel.** New constructor params after `notifications`: `forecast: Flow<Forecast?> = flowOf(null)`, `showForecast: Flow<Boolean> = flowOf(true)`. Combine with the existing state so `TodayUiState.forecast = if (show && state.isViewingToday) forecast else null` (add `val forecast: Forecast? = null` to `TodayUiState`). Tests: passes through when viewing today; null when `showForecast` emits false; null after `onSelectDate(D.minusDays(1))`.

- [ ] **Step 7: `ForecastStrip`** — `@Composable fun ForecastStrip(forecast: Forecast, onHabitClick: (Long) -> Unit, onInfo: () -> Unit, modifier: Modifier = Modifier)`:
  - `Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer)`, inner padding 16dp, `Column(verticalArrangement = spacedBy(12.dp))`.
  - Title row: `AiSparkleIcon()`, "Today's forecast" (`titleMedium`, `semantics { heading() }`), spacer, `IconButton(onClick = onInfo) { Icon(Icons.Rounded.Info, contentDescription = "How the forecast works") }`. When `isLearning`: `labelSmall` "Learning your patterns…" under the title in `onSurfaceVariant`.
  - `allClear` → `Row { Icon(Icons.Rounded.CheckCircle, null, tint = LocalHabitlyColors.current.success) ; Text("All clear for today ✓") }` and nothing else.
  - Otherwise headline (`bodyLarge`, `FontWeight.SemiBold`) for `items.first()`: HIGH/MEDIUM → "{name} needs attention — {reason}."; LOW → "Looking good — {reason}."
  - Tiles: `FlowRow(horizontalArrangement = spacedBy(8.dp), verticalArrangement = spacedBy(8.dp), maxItemsInEachRow = 3)`, up to 3 items, each `Modifier.weight(1f)` (FlowRow supports weight), `clip(RoundedCornerShape(16.dp))`, background `habitTones(colorHex).low`, `clickable(role = Role.Button, onClickLabel = "Open ${name}") { onHabitClick(habitId) }`, `heightIn(min = 48.dp)`, padding 12dp. Content column: `IconTile(iconKey, colorHex, size = 28.dp)`, "{percent}%" (`titleLarge`, `onSurface`), "chance of missing" (`labelSmall`, `onSurfaceVariant`), level label (`labelMedium`, bold, `onSurface`). `semantics(mergeDescendants = true) { contentDescription = "$name: $percent% chance of missing, ${level.label} risk. $reason" }`.
  - Previews dark/light × {normal, learning, all clear}.

- [ ] **Step 8: `ForecastInfoSheet`** — `@Composable fun ForecastInfoSheet(forecast: Forecast, onDismiss: () -> Unit, now: Long = System.currentTimeMillis())` using `HabitlySheet(onDismiss, title = "How the forecast works")`:
  - Intro: "A small model on your phone learns from your own check-ins which habits you tend to miss, and when."
  - "What it looks at" (`titleSmall`) + bullet lines: "The day of the week", "How you've done lately (last 7 and 28 due days, and the same weekday)", "Your current streak and whether you missed last time", "How new the habit is", "Whether it has a reminder".
  - "How good it is": `metrics != null` → "Accuracy ${(accuracy*100).roundToInt()}% · AUC ${"%.2f".format(auc)} on your last 7 days (${testRows} check-ins)" (omit the AUC part when null); else "Not enough history to measure yet".
  - Learning → "Using starter settings until there are 40 past check-ins"; else "Trained on ${rows} past check-ins · updated ${DateUtils.getRelativeTimeSpanString(trainedAt, now, MINUTE_IN_MILLIS)}".
  - Footer row: `Icons.Rounded.Lock` + "Runs only on your phone. Nothing is sent anywhere."

- [ ] **Step 9: Wire into Today.**
  - `TodayScreen`: hold `var showForecastInfo by rememberSaveable { mutableStateOf(false) }`. Add `item("forecast") { ForecastStrip(…, onHabitClick = onHabitClick, onInfo = { showForecastInfo = true }) }` right after `item("week")` in the compact list; in the expanded layout put it in the left column under the week strip. Show only when `uiState.forecast != null && uiState.dueCount > 0`. Render `ForecastInfoSheet` when `showForecastInfo`.
  - `HabitlyNavHost` Today destination: `val forecastRepo = remember { ForecastServices.repository(appContext) }`, `LaunchedEffect(Unit) { forecastRepo.refreshIfStale() }`, pass `forecast = forecastRepo.observeToday()` and `showForecast = AiServices.prefs(appContext).showForecast` to `TodayViewModel`.

- [ ] **Step 10: Full suite + assembleDebug. Commit** `feat(forecast): Today's forecast strip backed by the on-device model`.

---

### Task 8: AI actions — validator and runner (spec §2.4)

**Files:**
- Create: `ai/actions/AiActionValidator.kt`, `ai/actions/AiActionRunner.kt`
- Test: `ai/actions/AiActionValidatorTest.kt`, `ai/actions/AiActionRunnerTest.kt`

**Interfaces:**
- Consumes: `AiActionDto` (Task 3), `HabitDataSource`, `HabitSchedule`, `ScheduleLabel`.
- Produces:
```kotlin
sealed interface ValidAction {
    val habitId: Long
    val label: String
    data class SetReminder(override val habitId: Long, override val label: String, val time: String) : ValidAction
    data class SetDays(override val habitId: Long, override val label: String, val days: Set<DayOfWeek>) : ValidAction
    data class SetRestDay(override val habitId: Long, override val label: String, val date: LocalDate) : ValidAction
    data class SetGoal(override val habitId: Long, override val label: String, val target: Int) : ValidAction
    data class LogDone(override val habitId: Long, override val label: String) : ValidAction
    data class RestToday(override val habitId: Long, override val label: String) : ValidAction
    data class OpenHabit(override val habitId: Long, override val label: String) : ValidAction
}

data class ValidationContext(val habits: Map<Long, HabitEntity>, val statuses: Map<Pair<Long, LocalDate>, CompletionStatus>)

object AiActionValidator {
    fun validate(action: AiActionDto, ctx: ValidationContext, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ValidAction?
    suspend fun snapshot(data: HabitDataSource): ValidationContext   // active habits + all logs
}

sealed interface ActionOutcome {
    data class Applied(val message: String, val undo: suspend () -> Unit) : ActionOutcome
    data class Navigate(val habitId: Long) : ActionOutcome
    data object Failed : ActionOutcome
}

class AiActionRunner(private val data: HabitDataSource) {
    suspend fun run(action: ValidAction, today: LocalDate): ActionOutcome
}
```

- [ ] **Step 1: Failing `AiActionValidatorTest`** — one test per rule:
  - Unknown or archived habit → null for every type.
  - `set_reminder`: "19:00" → `SetReminder`; "25:00", "7pm", null → null.
  - `set_days`: `["FRIDAY","MONDAY","MONDAY"]` → `SetDays({MONDAY, FRIDAY})`; `[]`, null or `["FUNDAY"]` → null.
  - `set_rest_day`: today and today+7 valid when the habit is due that day; today+8, yesterday, a non-due day, "2026-13-01" → null.
  - `set_goal`: 1 and 999 valid when the habit has `targetCount != null`; 0, 1000, null count, or a habit without a goal → null.
  - `log_done` / `rest_today`: due today and not DONE → valid; already DONE or not due today → null.
  - `open_habit`: valid for any active habit.
  - Unknown type → null.
  - Blank label → default ("Set reminder", "Change days", "Plan a rest day", "Set goal", "Log it", "Rest today", "Open habit"); labels trimmed and capped at 28 chars.

- [ ] **Step 2: Implement `AiActionValidator`** — due checks via `HabitSchedule.parse(habit.frequencyType, habit.frequencyDays, habit.intervalDays, createdDate)` where `createdDate = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()`; time via `runCatching { LocalTime.parse(t, DateTimeFormatter.ofPattern("HH:mm")) }.isSuccess && t.length == 5`; days via `runCatching { DayOfWeek.valueOf(it) }.getOrNull()`; dates via `runCatching { LocalDate.parse(it) }.getOrNull()`. `snapshot`: `habits = observeAllHabits().first().filter { !it.isArchived }.associateBy { it.id }`, `statuses = observeAllLogs().first().associate { (it.habitId to LocalDate.parse(it.date)) to it.status }`.

- [ ] **Step 3: Failing `AiActionRunnerTest`** — fake data source that records calls and holds a mutable habit list + status map:
  - `SetReminder` → `setReminder(id, "19:00")`, message "Reminder moved to 19:00"; `undo()` → `setReminder(id, previous)` (previous may be null).
  - `SetDays({FRIDAY, MONDAY})` → `updateHabit(copy(frequencyType = SPECIFIC_DAYS, frequencyDays = "MONDAY,FRIDAY", intervalDays = null))` (sorted by `DayOfWeek` ordinal, comma-joined — same format as `AddEditHabitViewModel`); message "Now on Mon, Fri"; `undo()` → `updateHabit(original)`.
  - `SetGoal(5)` → `updateHabit(copy(targetCount = 5))`, message "Goal set to 5"; undo restores.
  - `SetRestDay(date)` / `RestToday` → `setStatus(id, date, SKIPPED)`; messages "Rest day planned for Fri 3 Oct" / "Rest day for {name}"; `undo()` → `setStatus(id, date, previousStatus)` (null deletes the log).
  - `LogDone` → `setStatus(id, today, DONE)`, message "Logged {name}"; undo → previous status.
  - `OpenHabit` → `Navigate(id)`.
  - Repository throws → `Failed`.

- [ ] **Step 4: Implement `AiActionRunner`** — load the habit with `data.getHabit(id)` (null → `Failed`), previous status from `observeAllLogs().first()` for (id, date). Message for SetDays uses `HabitSchedule(FrequencyType.SPECIFIC_DAYS, days).label()`; SetRestDay date format `DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)`. Wrap each branch: `catch (e: CancellationException) { throw e } catch (e: Exception) { ActionOutcome.Failed }`.

- [ ] **Step 5: Full suite; commit** `feat(ai): validate and apply AI-proposed actions with undo`.

---

### Task 9: Create with AI (spec §3)

**Files:**
- Create: `ui/create/SuggestionSanitizer.kt`, `ui/create/AiDraftStore.kt`, `ui/create/CreateWithAiUiState.kt`, `ui/create/CreateWithAiViewModel.kt`, `ui/create/CreateWithAiScreen.kt`
- Modify: `domain/HabitTemplate.kt` (add `intervalDays`), `ui/addedit/AddEditHabitViewModel.kt` (`draft` param + interval from template), `ui/navigation/Destinations.kt`, `ui/navigation/HabitlyNavHost.kt`, `ui/today/TemplateSheet.kt`, `ui/today/TodayScreen.kt` (empty state line + pass-through), `ui/habits/HabitsScreen.kt` (header button)
- Test: `ui/create/SuggestionSanitizerTest.kt`, `ui/create/CreateWithAiViewModelTest.kt`, `ui/create/AiDraftStoreTest.kt`, update `ui/navigation/DestinationsTest.kt`, `ui/addedit/AddEditHabitViewModelTest.kt`

**Interfaces:**
- Consumes: `AiGateway`, `AiResult`, `SuggestRequest/SuggestResponse` (Tasks 3–4), `aiErrorMessage`, `AiErrorCard`, `AiSkeleton`, `AiRemainingNote`, `ConsentSheet`, `AiSparkleIcon` (Task 5), `DEFAULT_CATEGORIES`, `HABIT_ICON_KEYS`, `HabitPalette.seeds`, `FrequencyValidator`, `HabitTemplate.toEntity`.
- Produces:
  - `HabitTemplate(… , val intervalDays: Int? = null)` (new last param; `toEntity` writes it; `AddEditHabitViewModel.onTemplateSelected` copies it)
  - `data class Suggestion(val template: HabitTemplate, val why: String)`
  - `object SuggestionSanitizer { fun sanitize(response: SuggestResponse, existingNames: Collection<String>, iconKeys: Collection<String>, palette: List<String> = HabitPalette.seeds): List<Suggestion>; fun categoryFor(intent: String): String; fun iconFor(intent: String): String }`
  - `object AiDraftStore { fun put(template: HabitTemplate): String; fun take(key: String): HabitTemplate? }`
  - `Destination.CreateWithAi` (route `"create_with_ai"`); `Destination.AddEditHabit` route becomes `"add_edit_habit?habitId={habitId}&template={template}&draft={draft}"` with `createRoute(habitId: Long? = null, template: String? = null, draft: String? = null)`
  - `AddEditHabitViewModel(repository, habitId, templateKey, draft: HabitTemplate? = null)`
  - `TemplateSheet(onDismiss, onPick, onCreateWithAi: (() -> Unit)? = null)`

- [ ] **Step 1: `HabitTemplate.intervalDays`.** Add `val intervalDays: Int? = null` as the last constructor param; in `toEntity` set `intervalDays = if (frequencyType == FrequencyType.INTERVAL) intervalDays else null`; in `AddEditHabitViewModel.onTemplateSelected` copy it into `AddEditUiState.intervalDays`. Add a test to `AddEditHabitViewModelTest`: an INTERVAL template (every 3 days) prefills `frequencyType = INTERVAL, intervalDays = 3`. Existing templates are unaffected (default null).

- [ ] **Step 2: Failing `SuggestionSanitizerTest`**
  - Name trimmed, capped at 40 chars; blank names dropped; names equal (case-insensitive, trimmed) to `existingNames` or to an earlier suggestion dropped; at most 4 returned.
  - Unknown `iconKey` → `iconFor(intent)` (sleep→"sleep", fitness→"fitness_center", focus→"phone_off", health→"water_drop", mind→"self_improvement", other→"star"); known key kept.
  - `colorSeed` −1 → `palette[0]`, 99 → `palette.last()`, 3 → `palette[3]`.
  - Category not in `DEFAULT_CATEGORIES` → `categoryFor(intent)` (sleep→Health, fitness→Fitness, focus→Productivity, health→Health, mind→Mind, other→General).
  - `SPECIFIC_DAYS` with `["MONDAY","FRIDAY"]` → `specificDays = {MONDAY, FRIDAY}`; with `[]` or invalid names → falls back to DAILY; `INTERVAL` 3 → interval 3; `INTERVAL` 1 or 45 → DAILY (use `FrequencyValidator.isValid`).
  - `targetCount` 8 + unit "cups" kept; `targetCount` 1, 0 or 1000 → both null; unit trimmed to 20 chars; unit without target dropped.
  - `reminderTime` "09:00" kept; "9am" / "24:00" → null.
  - `why` trimmed, capped at 90 chars.

- [ ] **Step 3: Implement `SuggestionSanitizer`** as tested. The template's `colorHex` = palette entry; `key` is derived by `HabitTemplate` itself.

- [ ] **Step 4: `AiDraftStore`** — process-wide `ConcurrentHashMap<String, HabitTemplate>` + `AtomicInteger`; `put` returns `"d${n}"`; `take` removes and returns. Test: put→take returns the template; second take returns null.

- [ ] **Step 5: Navigation + AddEdit draft.**
  - `Destinations.kt`: add `data object CreateWithAi : Destination("create_with_ai")`; change AddEdit to the new route/`createRoute(habitId, template, draft)` (`draft.orEmpty()` like `template`). Update `DestinationsTest` for the new route format.
  - `AddEditHabitViewModel`: new last param `draft: HabitTemplate? = null`; init: `if (habitId == null) { (draft ?: templateKey?.let(HabitTemplates::byKey))?.let(::onTemplateSelected); resetBaseline() }`. Test: a draft prefills name/icon/colour/reminder and `isDirty == false` after init.
  - `HabitlyNavHost` AddEdit: add `navArgument("draft") { type = NavType.StringType; defaultValue = "" }`; resolve `val draft = remember(entry) { entry.arguments?.getString("draft")?.takeIf { it.isNotEmpty() }?.let(AiDraftStore::take) }` and pass it to the VM. (`take` removes it, so `remember` keeps it across recompositions; config changes keep the VM.)

- [ ] **Step 6: Failing `CreateWithAiViewModelTest`** — fake `HabitDataSource` (records `addHabit`/`deleteHabit`, `observeAllHabits` returns a `MutableStateFlow`), real `AiGateway` over fake `AiSettingsSource` + fake `AiApi` returning scripted results:
  - `suggestDisabledUnderThreeChars`: `canSuggest` false for "hi", true for "run".
  - `chipFillsText`: `onChip("Sleep better")` sets `text = "Sleep better"`.
  - `successShowsSanitizedCards`: the request carries trimmed text, existing active habit names, `HABIT_ICON_KEYS`, `DEFAULT_CATEGORIES`; the state has `intent`, 2 NEW cards, `isLoading == false`.
  - `loadingState`: while the fake API suspends, `isLoading == true` and `cards` empty.
  - `emptyAfterSanitizingIsAnError`: response with only duplicate names → `error` set, message "No new ideas this time — try describing it differently" (use a dedicated `emptyMessage` field rather than `AiResult`).
  - `consentFlow`: `ConsentRequired` → `needsConsent == true`; `onConsentAllowed()` calls the consent setter, clears `needsConsent` and retries (fake API called once more).
  - `addAndUndo`: `onAdd(key)` → `addHabit` called with `template.toEntity(now)`, card `ADDED` with the returned id; `onUndo(key)` → `deleteHabit` with that entity, card back to NEW.
  - `addAll`: adds every NEW card (not dismissed/added ones).
  - `dismiss`: card becomes DISMISSED and is hidden from `visibleCards`.
  - `editReturnsDraftKey`: `onEdit(key)` returns a key that `AiDraftStore.take` resolves to the card's template.
  - `errorsSurface`: `Offline` → `error == AiResult.Offline`; `onTryAgain()` re-sends the same text.
  - `remainingFromGateway`: success with remaining 2 → `remaining == 2`.

- [ ] **Step 7: Implement state + VM.**
```kotlin
enum class CardStatus { NEW, ADDED, DISMISSED }
data class SuggestionCard(val key: Int, val suggestion: Suggestion, val status: CardStatus = CardStatus.NEW, val habitId: Long? = null)
data class CreateWithAiUiState(
    val text: String = "",
    val isLoading: Boolean = false,
    val error: AiResult<*>? = null,
    val emptyMessage: String? = null,
    val needsConsent: Boolean = false,
    val intent: String? = null,
    val cards: List<SuggestionCard> = emptyList(),
    val remaining: Int? = null,
) {
    val canSuggest: Boolean get() = text.trim().length >= 3 && !isLoading
    val visibleCards: List<SuggestionCard> get() = cards.filter { it.status != CardStatus.DISMISSED }
    val hasNew: Boolean get() = cards.any { it.status == CardStatus.NEW }
}
const val MAX_PROMPT_CHARS = 300
val PROMPT_CHIPS = listOf("Sleep better", "Get fit", "Less screen time", "Read more", "Drink more water", "Feel calmer")

class CreateWithAiViewModel(
    private val gateway: AiGateway,
    private val data: HabitDataSource,
    private val allowConsent: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel()
```
Public API: `uiState: StateFlow<CreateWithAiUiState>`, `onTextChange(String)` (cap at `MAX_PROMPT_CHARS`), `onChip(String)`, `onSuggest()`, `onTryAgain()`, `onConsentAllowed()`, `onConsentDismissed()`, `onAdd(key)`, `onUndo(key)`, `onAddAll()`, `onDismiss(key)`, `fun onEdit(key: Int): String?`. `onSuggest` ignores calls while loading; stores the last sent text for `onTryAgain`.

- [ ] **Step 8: `CreateWithAiScreen`** — `@Composable fun CreateWithAiScreen(uiState, onTextChange, onChip, onSuggest, onTryAgain, onAdd, onUndo, onAddAll, onDismiss, onEdit: (Int) -> Unit, onConsentAllowed, onConsentDismissed, onBack)`:
  - `ScreenHeader(title = "Create with AI", onBack = onBack)`, then scrolling `LazyColumn` (content padding 20dp horizontal, 24dp bottom, `imePadding()`):
    - Hero text: "What do you want to change?" (`headlineSmall`), "Say it in your own words" (`bodyMedium`, `onSurfaceVariant`).
    - Input card (`surfaceContainer`, 20dp corners): `TextField` (min 3 lines, max 6, placeholder "I want to sleep before midnight…", transparent container, `KeyboardOptions(imeAction = ImeAction.Done, capitalization = Sentences)`), counter "n/300" only when `text.length > 250`; trailing row with a mic `IconButton` (contentDescription "Speak instead of typing") shown only if a speech recogniser exists: `remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }`; launch with `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())` using `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, `EXTRA_PROMPT = "What do you want to change?"`; result `EXTRA_RESULTS[0]` → `onTextChange`.
    - Chips: `FlowRow` of `FilterChip`-styled `AssistChip`s for `PROMPT_CHIPS` (each ≥ 48dp tall via `minimumInteractiveComponentSize`).
    - `PrimaryButton("Suggest", onSuggest, enabled = uiState.canSuggest, leadingIcon = Icons.Rounded.AutoAwesome)`, full width.
    - `AiRemainingNote(uiState.remaining)`.
    - Loading → `AiSkeleton(lines = 3)` inside two card-shaped placeholders.
    - Error → `AiErrorCard(uiState.error, onTryAgain)`; `emptyMessage` → same card style with text + "Try again".
    - Results: section label "AI suggests {n} habit(s)" with an intent chip (`intent.replaceFirstChar { it.uppercase() }`), then one `SuggestionCardView` per visible card: `IconTile(template.icon, template.colorHex, size = 40.dp)`, name (`titleMedium`), schedule line built from `HabitSchedule(type, days, interval).label()` + goal ("8 cups") + reminder ("09:00") joined with " · ", why (`bodySmall`, `onSurfaceVariant`); actions row: NEW → `PrimaryButton("Add")`, `SecondaryButton("Edit")`, `IconButton(Icons.Rounded.Close, "Dismiss {name}")`; ADDED → check icon + "Added" + `TextButton("Undo")`. Animate status changes with `AnimatedContent` (snap under reduce motion).
    - Footer when `hasNew`: `SecondaryButton("Add all")` and `TextButton("Try again")`.
  - `ConsentSheet` when `needsConsent`.
  - Wrap in `CenteredContent`. Previews: empty, loading, results (dark/light).

- [ ] **Step 9: Entry points + NavHost.**
  - `TemplateSheet`: new param `onCreateWithAi: (() -> Unit)? = null`; when non-null render an `AiTile` row above `ScratchTile` styled like `ScratchTile` (64dp row, 36dp rounded icon box with `AiSparkleIcon`, title "Create with AI", subtitle "Describe a goal and get habit ideas").
  - `TodayScreen` + `HabitsScreen`: new param `onCreateWithAi: () -> Unit = {}` passed to their `TemplateSheet`; Habits header `actions` gets `IconButton(onClick = onCreateWithAi) { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Create with AI") }` before the Add button; `TodayEmptyState` gets a `TextButton(onClick = onCreateWithAi) { Text("✨ Describe a goal and let AI suggest habits") }` under the starter templates.
  - `HabitlyNavHost`: `composable(Destination.CreateWithAi.route) { … }` building `CreateWithAiViewModel(AiServices.gateway(appContext), repository, allowConsent = { AiServices.prefs(appContext).setAiConsent(true) })`, wrapped in `CenteredContent`; `onEdit = { key -> viewModel.onEdit(key)?.let { navController.navigate(Destination.AddEditHabit.createRoute(draft = it)) } }`; Today/Habits pass `onCreateWithAi = { navController.navigate(Destination.CreateWithAi.route) }` and the sheet closes itself before navigating.

- [ ] **Step 10: Full suite + assembleDebug; commit** `feat(create): Create with AI screen with suggestion cards, voice input and drafts`.

---

### Task 10: Quick coach sheet (spec §5)

**Files:**
- Create: `ui/coach/CoachUiState.kt`, `ui/coach/CoachViewModel.kt`, `ui/coach/CoachSheet.kt`
- Modify: `ui/today/TodayScreen.kt` (header button), `ui/navigation/HabitlyNavHost.kt`
- Test: `ui/coach/CoachViewModelTest.kt`

**Interfaces:**
- Consumes: `AiGateway`, `CoachRequest/CoachResponse/ChatMessageDto`, `AiContextSource` (with `ForecastRepository::todayRisks` as the risk source), `AiActionValidator`, `ValidationContext`, `AiActionRunner`, `ActionOutcome`, shared AI UI.
- Produces:
```kotlin
enum class CoachRole { USER, COACH }
data class CoachMessage(val id: Int, val role: CoachRole, val text: String, val actions: List<ValidAction> = emptyList())
data class CoachUiState(
    val messages: List<CoachMessage> = emptyList(),
    val input: String = "",
    val isTyping: Boolean = false,
    val error: AiResult<*>? = null,
    val needsConsent: Boolean = false,
    val remaining: Int? = null,
) { val canSend: Boolean get() = input.isNotBlank() && !isTyping }
sealed interface CoachEvent {
    data class Applied(val message: String, val undo: suspend () -> Unit) : CoachEvent
    data class OpenHabit(val habitId: Long) : CoachEvent
    data object ActionFailed : CoachEvent
}
val COACH_CHIPS = listOf("Plan my day", "Why do I slip?", "Motivate me", "Which habit first?")
class CoachViewModel(
    private val gateway: AiGateway,
    private val context: AiContextSource,
    private val validation: suspend () -> ValidationContext,
    private val runner: AiActionRunner,
    private val allowConsent: suspend () -> Unit,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    val uiState: StateFlow<CoachUiState>
    val events: Flow<CoachEvent>          // Channel-backed, consumed once
    fun open()                            // requests the opening message if messages is empty
    fun onInputChange(text: String)       // capped at 300
    fun onSend()
    fun onChip(text: String)              // sends the chip text immediately
    fun onAction(messageId: Int, action: ValidAction)
    fun onRetry()
    fun onConsentAllowed()
    fun onConsentDismissed()
    fun reset()                           // clears the conversation when the sheet closes
}
```
- `TodayScreen` new param `onOpenCoach: () -> Unit = {}`; `TodayHeader` gets a trailing `IconButton(onClick = onOpenCoach) { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Open coach") }` after the ProgressRing (always shown when viewing today).

- [ ] **Step 1: Failing `CoachViewModelTest`** (fake AiApi scripted; fake data source; `AiContextSource(fake)`; `validation` returns a context with habit 7 due today):
  - `openRequestsOpeningOnce`: `open()` sends `CoachRequest(today, summaries, messages = [])`, appends a COACH message; a second `open()` does nothing.
  - `typingIndicatorWhileWaiting`.
  - `sendAppendsUserThenCoach`: input "Why do I slip?" → messages USER then COACH; input cleared; request `messages` = last ≤ 6 entries mapped to roles "user"/"coach" (build 8 messages and assert only the last 6 go out).
  - `actionsAreValidated`: response actions `[log_done(7), set_reminder(99, "19:00")]` → message has only the `LogDone` action.
  - `onActionApplies`: runner outcome Applied → `events` emits `CoachEvent.Applied`; the action is removed from that message.
  - `openHabitEmitsNavigation`.
  - `errorsAndRetry`: Offline → `error` set, `isTyping` false; `onRetry()` repeats the last request (opening or the last user message) without duplicating the user bubble.
  - `consent`: ConsentRequired → `needsConsent`; `onConsentAllowed()` → setter called, request retried.
  - `resetClearsConversation`.

- [ ] **Step 2: Implement `CoachViewModel`.** Keep `lastRequestMessages: List<ChatMessageDto>` for retry. For each response: `val ctx = validation(); val actions = response.actions.mapNotNull { AiActionValidator.validate(it, ctx, today()) }.take(2)`.

- [ ] **Step 3: `CoachSheet`** — `@Composable fun CoachSheet(uiState, onInputChange, onSend, onChip, onAction: (Int, ValidAction) -> Unit, onRetry, onConsentAllowed, onConsentDismissed, onDismiss)`:
  - `HabitlySheet(onDismiss, title = "Quick coach", skipPartiallyExpanded = false)`.
  - Messages column (`LazyColumn`, `heightIn(max = 420.dp)`, auto-scroll to the last item when `messages.size` changes): COACH bubbles left-aligned, `surfaceContainerHigh`, 20dp corners with a small `AiSparkleIcon` avatar; USER bubbles right-aligned, `primaryContainer`. Under a COACH bubble, its actions as `PrimaryButton`/`SecondaryButton` pairs (first primary). Each bubble has `semantics { liveRegion = LiveRegionMode.Polite }` for new COACH messages.
  - Typing indicator: three 8dp dots animating alpha in sequence (static when `LocalReduceMotion`), `contentDescription = "Coach is typing"`.
  - Errors: `AiErrorCard(error, onRetry)`; `AiRemainingNote(remaining)`.
  - Chips row (`LazyRow`) with `COACH_CHIPS`, hidden while typing.
  - Input row: `OutlinedTextField` (single line up to 3, placeholder "Ask anything…", `imeAction = Send`) + `IconButton(onSend, enabled = canSend) { Icon(Icons.AutoMirrored.Rounded.Send, "Send") }`; the sheet content uses `imePadding()`.
  - `ConsentSheet` when `needsConsent`.
  - New COACH replies fade in (`AnimatedVisibility(fadeIn())`, none under reduce motion).
  - Previews: opening message with actions, typing, error (dark/light).

- [ ] **Step 4: Wire in NavHost (Today destination).**
```kotlin
var coachOpen by rememberSaveable { mutableStateOf(false) }
val forecastRepo = remember { ForecastServices.repository(appContext) }   // already present from Task 7
val coachVm = habitlyViewModel(key = "coach") {
    CoachViewModel(
        gateway = AiServices.gateway(appContext),
        context = AiContextSource(repository, risks = forecastRepo::todayRisks),
        validation = { AiActionValidator.snapshot(repository) },
        runner = AiActionRunner(repository),
        allowConsent = { AiServices.prefs(appContext).setAiConsent(true) },
    )
}
val snackbar = LocalSnackbarHostState.current
LaunchedEffect(coachVm) {
    coachVm.events.collect { event ->
        when (event) {
            is CoachEvent.Applied -> launch {
                val r = snackbar.showSnackbar(event.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) event.undo()
            }
            is CoachEvent.OpenHabit -> { coachOpen = false; navController.navigate(Destination.HabitDetail.createRoute(event.habitId)) }
            CoachEvent.ActionFailed -> launch { snackbar.showSnackbar("Couldn't apply that — try again") }
        }
    }
}
if (coachOpen) {
    LaunchedEffect(Unit) { coachVm.open() }
    val state by coachVm.uiState.collectAsState()
    CoachSheet(state, …, onDismiss = { coachOpen = false; coachVm.reset() })
}
```
Pass `onOpenCoach = { coachOpen = true }` to `TodayScreen`. Snackbars appear above the sheet only after it closes if needed — acceptable; if the snackbar is hidden behind the sheet, show the "Applied ✓" confirmation inline under the bubble as well (`CoachMessage.appliedNote: String?`), keeping the snackbar for Undo.

- [ ] **Step 5: Full suite + assembleDebug; commit** `feat(coach): quick coach bottom sheet on Today with validated actions`.

---

### Task 11: Week in review (spec §4)

**Files:**
- Create: `ui/review/WeekReviewSchedule.kt`, `ui/review/ReviewCache.kt`, `ui/review/WeekReviewUiState.kt`, `ui/review/WeekReviewViewModel.kt`, `ui/review/WeekReviewScreen.kt`, `ui/review/WeekReviewEntryViewModel.kt`, `ui/review/WeekReviewBanner.kt`
- Modify: `ui/stats/StatsHero.kt` (make `WindowGrid` + `GridModel` usable: change `private fun WindowGrid` to `internal`), `ui/stats/StatsScreen.kt` (link), `ui/today/TodayScreen.kt` (banner item), `ui/navigation/Destinations.kt`, `ui/navigation/HabitlyNavHost.kt`
- Test: `ui/review/WeekReviewScheduleTest.kt`, `ui/review/ReviewCacheTest.kt`, `ui/review/WeekReviewViewModelTest.kt`, `ui/review/WeekReviewEntryViewModelTest.kt`

**Interfaces:**
- Consumes: gateway, context source, validator/runner, shared AI UI, `SettingsPrefs.weekStart`, `AiPrefs.reviewCacheJson`/`setReviewCacheJson`/`reviewBannerDismissedWeek`/`setReviewBannerDismissedWeek`, `GridModel`, `WindowGrid`, `DaySlice`, `StatsPeriod`.
- Produces:
```kotlin
object WeekReviewSchedule {
    const val MIN_TRACKING_DAYS = 3
    fun currentWeekStart(date: LocalDate, weekStart: DayOfWeek): LocalDate
    /** Start of the week to review: the current week from its last day at 18:00, otherwise the previous week. */
    fun reviewWeekStart(now: LocalDateTime, weekStart: DayOfWeek): LocalDate
    fun reviewAsOf(reviewStart: LocalDate, today: LocalDate): LocalDate          // min(reviewStart + 6, today)
    fun bannerVisible(now: LocalDateTime, weekStart: DayOfWeek, dismissedWeek: String?, trackingDays: Int): Boolean
    fun trackingDays(habits: List<HabitEntity>, logs: List<HabitLogEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int
}
object ReviewCache {
    fun decode(json: String?): Map<String, ReviewResponse>
    fun encode(map: Map<String, ReviewResponse>): String
    fun put(map: Map<String, ReviewResponse>, week: String, review: ReviewResponse, keep: Int = 4): Map<String, ReviewResponse>
}
data class HabitVisual(val id: Long, val name: String, val iconKey: String, val colorHex: String, val currentStreak: Int, val weekdayRates: List<Float>) // Monday..Sunday, done/due (0 when due == 0)
enum class PageKind { SUMMARY, WIN, PATTERN, TIP, CLOSE }
data class ReviewPageUi(val kind: PageKind, val headline: String, val body: String, val habit: HabitVisual? = null, val action: ValidAction? = null, val applied: Boolean = false)
data class WeekReviewUiState(
    val weekStart: LocalDate? = null,
    val weekLabel: String = "",                 // e.g. "22–28 Sep"
    val pages: List<ReviewPageUi> = emptyList(),
    val stackDays: List<LocalDate> = emptyList(),
    val stacks: Map<LocalDate, List<DaySlice>> = emptyMap(),
    val slotCount: Int = 0,
    val isLoading: Boolean = false,
    val error: AiResult<*>? = null,
    val needsConsent: Boolean = false,
    val notEnoughHistory: Boolean = false,
    val remaining: Int? = null,
)
sealed interface ReviewEvent { data class Applied(val message: String, val undo: suspend () -> Unit) : ReviewEvent; data object ActionFailed : ReviewEvent }
class WeekReviewViewModel(
    gateway: AiGateway, context: AiContextSource, data: HabitDataSource,
    cacheJson: Flow<String?>, saveCacheJson: suspend (String) -> Unit, weekStart: Flow<DayOfWeek>,
    validation: suspend () -> ValidationContext, runner: AiActionRunner, allowConsent: suspend () -> Unit,
    now: () -> LocalDateTime = { LocalDateTime.now() }, zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    val uiState: StateFlow<WeekReviewUiState>; val events: Flow<ReviewEvent>
    fun onApply(pageIndex: Int); fun onRegenerate(); fun onRetry(); fun onConsentAllowed(); fun onConsentDismissed()
}
data class WeekReviewEntryState(val bannerVisible: Boolean = false, val available: Boolean = false, val weekStart: String? = null)
class WeekReviewEntryViewModel(data: HabitDataSource, weekStart: Flow<DayOfWeek>, dismissedWeek: Flow<String?>, dismiss: suspend (String) -> Unit, now: () -> LocalDateTime = { LocalDateTime.now() }) : ViewModel() {
    val uiState: StateFlow<WeekReviewEntryState>; fun onDismissBanner()
}
```
- `Destination.WeekReview` (route `"week_review"`).

- [ ] **Step 1: Failing `WeekReviewScheduleTest`** (weekStart MONDAY unless stated; 2026-09-28 is a Monday):
  - `reviewWeekStart(Sun 2026-10-04 17:59)` = 2026-09-21 (previous week); `(Sun 2026-10-04 18:00)` = 2026-09-28 (current week); `(Mon 2026-10-05 09:00)` = 2026-09-28.
  - With weekStart SUNDAY: `(Sat 2026-10-03 18:30)` = 2026-09-27.
  - `bannerVisible`: true on the last day ≥ 18:00, on the first two days of the next week; false on day 3 (Wednesday), false before 18:00 on the last day, false when `dismissedWeek` equals the review week, false when `trackingDays < 3`.
  - `reviewAsOf(2026-09-28, today = 2026-10-04)` = 2026-10-04; `(2026-09-21, 2026-10-05)` = 2026-09-27.
  - `trackingDays`: habit created 2 days ago → 3 (inclusive of today); no habits → 0; earliest of creation and first log.

- [ ] **Step 2: Implement `WeekReviewSchedule`** (`currentWeekStart = date.with(TemporalAdjusters.previousOrSame(weekStart))`).

- [ ] **Step 3: Failing `ReviewCacheTest`** — decode null/garbage → empty map; encode/decode round trip; `put` keeps the 4 most recent keys (ISO dates sort lexicographically) and replaces an existing key.

- [ ] **Step 4: Implement `ReviewCache`** with `AiJson` and `MapSerializer(String.serializer(), ReviewResponse.serializer())`.

- [ ] **Step 5: Failing `WeekReviewViewModelTest`** (fake data with 2 habits and 2 weeks of logs; fixed `now = 2026-10-05T09:00`, weekStart MONDAY; in-memory cache flow):
  - `usesCacheWithoutCalling`: cache contains week 2026-09-28 → pages built, fake API not called.
  - `fetchesAndCachesWhenMissing`: request `weekStart = "2026-09-28"`, summaries built with `asOf = 2026-10-04`; result saved via `saveCacheJson`; pages = LLM pages mapped + a final CLOSE page ("See you next week", body "Your next review is ready on Sunday evening." using the localised last weekday name).
  - `pagesMapped`: `habitId` resolves to `HabitVisual` (name, icon, colour, streak, weekday rates); unknown ids → `habit == null`; tip actions validated (invalid → null, and a TIP page without a valid action still shows but without Apply).
  - `stacksForSummary`: `stackDays` = the 7 review days; `stacks[day]` has one `DaySlice(colorHex, rested = false, name)` per DONE habit and `rested = true` for SKIPPED; `slotCount` = active habit count.
  - `applyRunsActionOnce`: `onApply(i)` → runner called, `pages[i].applied == true`, event Applied emitted; second `onApply(i)` ignored.
  - `regenerateBypassesCache`: `onRegenerate()` calls the API even with a cached entry and replaces it.
  - `notEnoughHistory`: tracking days < 3 → `notEnoughHistory == true`, API not called.
  - `consent` and `errors` as in Task 9/10 (ConsentRequired → needsConsent → allow → retry; Offline → error, onRetry).

- [ ] **Step 6: Implement `WeekReviewViewModel`.** On init: collect `weekStart.first()`, compute `reviewStart = WeekReviewSchedule.reviewWeekStart(now(), ws)`, `asOf = reviewAsOf(reviewStart, now().toLocalDate())`, `weekLabel` via `DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)` ("22–28 Sep", or "29 Sep – 5 Oct" across months); check tracking days; then cache or fetch. Visuals from `AiContextBuilder.build(...)` output (weekday rates = done/due) + entities (icon/colour).

- [ ] **Step 7: `WeekReviewScreen`** — `@Composable fun WeekReviewScreen(uiState, onClose, onApply: (Int) -> Unit, onRegenerate, onRetry, onConsentAllowed, onConsentDismissed, weekStart: DayOfWeek)`:
  - Full screen `Box`; top bar: segmented progress (one 3dp rounded bar per page, filled up to the current page with `onSurface`, rest `gridEmpty`), then a row with "Week in review · {n} of {total}" (`labelLarge`), overflow `IconButton(Icons.Rounded.MoreVert, "More")` → `DropdownMenu` item "Regenerate", and `IconButton(Icons.Rounded.Close, "Close")`.
  - `HorizontalPager(state = rememberPagerState { pages.size })`; page content centred vertically, padding 24dp: kind eyebrow (`labelMedium`, uppercase: "This week", "Win", "Pattern", "Tip", "Next week"), headline (`displaySmall` at compact width; `headlineLarge` when font scale > 1.3), body (`bodyLarge`), then the visual:
    - SUMMARY → `WindowGrid(GridModel(period = StatsPeriod.WEEK, colorHex = null, habitName = null, window = stackDays, stacks = stacks, levels = emptyMap(), today = stackDays.last(), slotCount = slotCount, habitStart = null), weekStart)` in a card.
    - WIN → `IconTile(size = 64.dp)` + "{streak}-day streak" (`headlineMedium`).
    - PATTERN → 7 vertical bars (height = rate, min 4dp) in `habitTones(colorHex).full` on `gridEmpty` tracks, with M T W T F S S labels; semantics "Monday 80%, …".
    - TIP → `PrimaryButton(action.label, onApply)` + `TextButton("Skip")` (advances the pager); when `applied` → check + "Applied ✓".
    - CLOSE → `PrimaryButton("Done", onClose)`.
  - Bottom row: `TextButton("Previous")` / `TextButton("Next")` (hidden at the ends) for accessibility; pager scroll uses `animateScrollToPage`, or `scrollToPage` under reduce motion.
  - Each page `semantics(mergeDescendants = true)` with eyebrow + headline + body.
  - Loading → centred `AiSkeleton(lines = 4)` with "Writing your week in review…"; error → `AiErrorCard`; `notEnoughHistory` → `EmptyState(icon = AutoAwesome, title = "Not enough history yet", message = "Your first review appears after 3 days of tracking.")`; `ConsentSheet` when needed.
  - Previews: summary page, tip page, loading (dark/light).

- [ ] **Step 8: Entry points.**
  - `WeekReviewEntryViewModel` (tests: banner visible Monday 09:00 with 5 tracking days; hidden after `onDismissBanner()` which stores the review week; `available == trackingDays >= 3`).
  - `WeekReviewBanner(onOpen, onDismiss)`: slim `Surface` row (`primaryContainer`, 16dp corners, ≥ 56dp), `AiSparkleIcon` + "Your week in review is ready" + `TextButton("Open")` + `IconButton(Icons.Rounded.Close, "Dismiss")`.
  - `TodayScreen`: new params `showReviewBanner: Boolean = false`, `onOpenReview: () -> Unit = {}`, `onDismissReview: () -> Unit = {}`; `item("review")` after the forecast item when `showReviewBanner && uiState.isViewingToday`.
  - `StatsScreen`: new param `onOpenWeekReview: (() -> Unit)? = null` and `weekReviewAvailable: Boolean = true`; add `item("review")` right after the hero: a `SettingsRow`-style tappable row with `AiSparkleIcon`, "Week in review", supporting "Last week, told as a story" (or "Available after 3 days of tracking" and disabled when not available).
  - `Destinations.kt`: `data object WeekReview : Destination("week_review")`; `HabitlyNavHost`: composable building `WeekReviewViewModel(AiServices.gateway(ctx), AiContextSource(repository), repository, aiPrefs.reviewCacheJson, aiPrefs::setReviewCacheJson, prefs.weekStart, { AiActionValidator.snapshot(repository) }, AiActionRunner(repository), { aiPrefs.setAiConsent(true) })`, events → snackbar with Undo (same pattern as Task 10); Today and Insights pass the navigation lambdas; the entry VM is created in the Today destination with `habitlyViewModel(key = "reviewEntry")`.

- [ ] **Step 9: Full suite + assembleDebug; commit** `feat(review): story-style Week in review with cached pages and Apply actions`.

---

### Task 12: Deployment docs and release build

**Files:**
- Create: `docs/DEPLOY.md`
- Modify: `app/build.gradle.kts` (optional release signing from `local.properties`), `README.md` if present (link to DEPLOY.md), `.gitignore` (ensure `*.jks`, `*.keystore`, `local.properties`, `proxy/.dev.vars` are ignored)

- [ ] **Step 1: Release signing (optional, never committed).** In `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        val path = localProps.getProperty("habitly.keystore.path")
        if (path != null) create("release") {
            storeFile = file(path)
            storePassword = localProps.getProperty("habitly.keystore.password")
            keyAlias = localProps.getProperty("habitly.key.alias")
            keyPassword = localProps.getProperty("habitly.key.password")
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // keep the existing release settings
        }
    }
}
```
Without those properties `assembleRelease` still builds (unsigned). Verify `./gradlew :app:assembleRelease -q` succeeds without the properties.

- [ ] **Step 2: `docs/DEPLOY.md`** with numbered, copy-pasteable steps (the user types every secret themselves; the doc never contains real values):
  1. Prerequisites: Node 20+, a Cloudflare account, an OpenAI account with billing and a **monthly spend limit** set in the OpenAI dashboard.
  2. `cd proxy && npm install`, `npx wrangler login`.
  3. `npx wrangler kv namespace create AI_LIMITS` → paste the printed id into `wrangler.toml`.
  4. `npx wrangler secret put OPENAI_API_KEY` (paste the key when prompted) and `npx wrangler secret put APP_TOKEN` (any long random string, e.g. from a password manager).
  5. Optional: change `OPENAI_MODEL` in `wrangler.toml`.
  6. `npx wrangler deploy` → note the `https://habitly-ai.<subdomain>.workers.dev` URL; check `curl https://…/v1/health` returns `{"ok":true}`.
  7. In the project's `local.properties` add `habitly.aiBaseUrl=https://…workers.dev` and `habitly.aiAppToken=<the same APP_TOKEN>`.
  8. Build: `./gradlew :app:assembleDebug` for testing, or create a keystore with `keytool -genkeypair -v -keystore habitly-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias habitly` (stored outside the repo), add the four `habitly.keystore.*`/`habitly.key.*` properties, then `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
  9. Distribute: GitHub Releases (attach the APK) or Firebase App Distribution (upload the APK, add tester emails).
  10. Demo tips: Settings → tap "AI features" 5 times → Demo AI for offline presentations; the forecast works offline.
  11. Security notes: the app token in the APK can be extracted — the per-device and global daily caps and the OpenAI spend limit bound the cost; rotate `APP_TOKEN` with `wrangler secret put` + rebuild if abused.
  12. Troubleshooting table: 401 (token mismatch), 429 (daily cap), 502 (model/key problem — check `npx wrangler tail`), "AI isn't set up in this build" (empty `local.properties` values at build time).

- [ ] **Step 3: Commit** `docs: deployment guide and optional release signing`.

---

### Task 13: Verification pass and screenshots (controller)

- [ ] Run both suites and `assembleDebug`.
- [ ] Emulator (Demo AI on, then off to see the "not set up" state): Today with forecast (normal, learning via a fresh install, all clear), forecast info sheet, coach sheet (opening, a reply, an applied action + Undo, error), Create with AI (empty, loading, results, added + undo, edit → AddEdit prefilled), Week in review (each page kind, applied tip, regenerate), Settings AI section (including the 5-tap reveal), consent sheet — dark and light, 200 % font on Create with AI and the coach sheet, Expanded width on Today and Week in review.
- [ ] Save screenshots to `docs/screenshots/2026-10-ca3/` with descriptive names; restore emulator font scale and size.
- [ ] Fix anything found via the owning task's implementer.

### Task 14: Report draft (controller, `anthropic-skills:docx`)

- [ ] Write `docs/report/Habitly-CA3-Report.docx` with sections: 1 Introduction and objectives · 2 App overview · 3 AI features (one subsection per feature with screenshots and the AI type it demonstrates) · 4 Architecture (diagram of app ↔ proxy ↔ OpenAI and the on-device model) · 5 ML model (features table from `FEATURE_NAMES`, training method, starter model, evaluation: accuracy/AUC/Brier on the synthetic Monday dataset from `RiskModelTest` plus how metrics appear in-app) · 6 LLM integration (endpoints, JSON schemas, prompts summary, validation, moderation, safety copy) · 7 UI/UX and responsiveness · 8 Privacy and security (consent text constants, key handling, caps) · 9 Testing (suite counts, what is covered) · 10 Deployment (summary of DEPLOY.md) · 11 Limitations and future work · 12 References (OpenAI API docs, Cloudflare Workers docs, Android docs for Compose/WorkManager/DataStore, logistic regression reference).
- [ ] Mark clearly on the title page: "Draft — edit into your own words before submitting."
- [ ] Commit `docs: CA3 report draft`.
