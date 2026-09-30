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
