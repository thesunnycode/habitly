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
