import { describe, it, expect } from "vitest";
import { z, ZodError } from "zod";
import { openAiLlm, UpstreamError } from "../src/llm";

const env = { OPENAI_API_KEY: "test-key", OPENAI_MODEL: "gpt-5-mini" };
const schema = z.object({ a: z.string() });
const llmWith = (client: unknown) => openAiLlm(env, client as never);
const parseThrowing = (e: unknown) => llmWith({ responses: { parse: async () => { throw e; } }, moderations: {} });
const call = (llm: ReturnType<typeof llmWith>) => llm.parse(schema, "n", "sys", "user", 100);

describe("openAiLlm", () => {
  it("wraps ZodError from the SDK parser as UpstreamError", async () => {
    await expect(call(parseThrowing(new ZodError([])))).rejects.toBeInstanceOf(UpstreamError);
  });
  it("wraps SyntaxError from the SDK parser as UpstreamError", async () => {
    await expect(call(parseThrowing(new SyntaxError("bad json")))).rejects.toBeInstanceOf(UpstreamError);
  });
  it("lets other errors through unchanged", async () => {
    const e = new Error("network");
    await expect(call(parseThrowing(e))).rejects.toBe(e);
  });
  it("null output_parsed is UpstreamError", async () => {
    const llm = llmWith({ responses: { parse: async () => ({ output_parsed: null }) }, moderations: {} });
    await expect(call(llm)).rejects.toBeInstanceOf(UpstreamError);
  });
  it("returns validated output", async () => {
    const llm = llmWith({ responses: { parse: async () => ({ output_parsed: { a: "x" } }) }, moderations: {} });
    expect(await call(llm)).toEqual({ a: "x" });
  });
  it("output failing our own validation is UpstreamError", async () => {
    const llm = llmWith({ responses: { parse: async () => ({ output_parsed: { a: 1 } }) }, moderations: {} });
    await expect(call(llm)).rejects.toBeInstanceOf(UpstreamError);
  });
  it("moderate returns true when any result is flagged", async () => {
    const llm = llmWith({ responses: {}, moderations: { create: async () => ({ results: [{ flagged: false }, { flagged: true }] }) } });
    expect(await llm.moderate("x")).toBe(true);
  });
  it("moderate returns false when nothing is flagged", async () => {
    const llm = llmWith({ responses: {}, moderations: { create: async () => ({ results: [{ flagged: false }] }) } });
    expect(await llm.moderate("x")).toBe(false);
  });
});
