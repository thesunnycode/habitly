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
