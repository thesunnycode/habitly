import { ZodError } from "zod";
import type { Llm } from "./llm";
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

  // The limit is consumed before the model call, so failed calls still count (abuse protection).
  const limit = await consume(env.AI_LIMITS, deviceId, deps.now(),
    Number(env.DEVICE_DAILY_LIMIT), Number(env.GLOBAL_DAILY_LIMIT));
  if (!limit.ok) return json(429, { error: "rate_limited", resetAt: limit.resetAt });

  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const out = await route(body, deps.llm);
      return json(200, out, { "x-ai-remaining": String(limit.remaining) });
    } catch (e) {
      // Request validation (routes call RequestSchema.parse) -> 400, never retried.
      // Model-output problems are wrapped in UpstreamError by the Llm/routes; those and any
      // SDK/network error are retried once, then 502.
      if (e instanceof ZodError) return json(400, { error: "bad_request" });
    }
  }
  return json(502, { error: "upstream" });
}
