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
