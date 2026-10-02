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
    const out: any = await ROUTES["/v1/habits/suggest"](body, llmReturning({ intent: "sleep", habits: [h({ name: "x".repeat(80), why: "y".repeat(200) }), h(), h(), h(), h()] }));
    expect(out.habits).toHaveLength(4);
    expect(out.habits[0].name.length).toBeLessThanOrEqual(40);
    expect(out.habits[0].why.length).toBeLessThanOrEqual(90);
  });
  const one = async (o: object, b = body) => {
    const out: any = await ROUTES["/v1/habits/suggest"](b, llmReturning({ intent: "sleep", habits: [h(o)] }));
    return out.habits[0];
  };
  it("nulls a malformed reminderTime and keeps a valid one", async () => {
    expect((await one({ reminderTime: "25:99" })).reminderTime).toBeNull();
    expect((await one({ reminderTime: "7:30" })).reminderTime).toBeNull();
    expect((await one({ reminderTime: "07:30" })).reminderTime).toBe("07:30");
  });
  it("clamps colorSeed to 0..11", async () => {
    expect((await one({ colorSeed: 40 })).colorSeed).toBe(11);
    expect((await one({ colorSeed: -3 })).colorSeed).toBe(0);
  });
  it("replaces unknown iconKey with star, else first key", async () => {
    expect((await one({ iconKey: "nope" })).iconKey).toBe("star");
    expect((await one({ iconKey: "nope" }, { ...body, iconKeys: ["a", "b"] })).iconKey).toBe("a");
    expect((await one({ iconKey: "sleep" })).iconKey).toBe("sleep");
  });
  it("replaces unknown category with General, else first category", async () => {
    expect((await one({ category: "Nope" }, { ...body, categories: ["Health", "General"] })).category).toBe("General");
    expect((await one({ category: "Nope" })).category).toBe("Health");
  });
  it("fixes frequency consistency", async () => {
    const f = async (frequency: object) => (await one({ frequency })).frequency;
    const daily = { type: "DAILY", days: null, intervalDays: null };
    expect(await f({ type: "DAILY", days: ["MONDAY"], intervalDays: 3 })).toEqual(daily);
    expect(await f({ type: "SPECIFIC_DAYS", days: [], intervalDays: null })).toEqual(daily);
    expect(await f({ type: "SPECIFIC_DAYS", days: null, intervalDays: null })).toEqual(daily);
    expect(await f({ type: "SPECIFIC_DAYS", days: ["MONDAY"], intervalDays: 4 })).toEqual({ type: "SPECIFIC_DAYS", days: ["MONDAY"], intervalDays: null });
    expect(await f({ type: "INTERVAL", days: null, intervalDays: 1 })).toEqual(daily);
    expect(await f({ type: "INTERVAL", days: null, intervalDays: 31 })).toEqual(daily);
    expect(await f({ type: "INTERVAL", days: ["MONDAY"], intervalDays: 3 })).toEqual({ type: "INTERVAL", days: null, intervalDays: 3 });
  });
  it("targetCount must be 2..999, and unit follows it", async () => {
    const a = await one({ targetCount: 1, unit: "cups" });
    expect([a.targetCount, a.unit]).toEqual([null, null]);
    const b = await one({ targetCount: 1000, unit: "cups" });
    expect([b.targetCount, b.unit]).toEqual([null, null]);
    const c = await one({ targetCount: 8, unit: "cups" });
    expect([c.targetCount, c.unit]).toEqual([8, "cups"]);
  });
  it("drops habits with an empty name, and errors if none remain", async () => {
    const out: any = await ROUTES["/v1/habits/suggest"](body, llmReturning({ intent: "sleep", habits: [h({ name: "   " }), h()] }));
    expect(out.habits).toHaveLength(1);
    await expect(ROUTES["/v1/habits/suggest"](body, llmReturning({ intent: "sleep", habits: [h({ name: " " })] }))).rejects.toBeInstanceOf(UpstreamError);
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

describe("review (more)", () => {
  const body = { weekStart: "2026-09-21", habits: [habit] };
  const page = (kind: string, o: object = {}) => ({ kind, headline: "H", body: "B", habitId: kind === "summary" ? null : 7, action: null, ...o });
  const run = (pages: unknown[]): Promise<any> => ROUTES["/v1/review/weekly"](body, llmReturning({ pages })) as Promise<any>;

  it("cuts body to 160 and action label to 28", async () => {
    const out = await run([page("summary", { body: "b".repeat(300) }), page("win"), page("tip", { action: action({ label: "l".repeat(60) }) })]);
    expect(out.pages[0].body.length).toBeLessThanOrEqual(160);
    expect(out.pages[2].action.label.length).toBeLessThanOrEqual(28);
  });
  it("cuts more than 5 pages to 5", async () => {
    const out = await run([page("summary"), page("win"), page("pattern"), page("pattern"), page("tip"), page("tip"), page("tip")]);
    expect(out.pages).toHaveLength(5);
  });
  it("two summaries is an upstream error", async () => {
    await expect(run([page("summary"), page("summary"), page("tip")])).rejects.toBeInstanceOf(UpstreamError);
  });
  it("drops an action whose habitId differs from the page habitId", async () => {
    const two = { weekStart: "2026-09-21", habits: [habit, { ...habit, id: 8 }] };
    const out: any = await ROUTES["/v1/review/weekly"](two, llmReturning({ pages: [page("summary"), page("win"), page("tip", { action: action({ habitId: 8 }) })] }));
    expect(out.pages[2].action).toBeNull();
  });
});

describe("action validation", () => {
  const body = { today: "2026-09-30", habits: [habit], messages: [] as any[] };
  const kept = async (a: object) => {
    const out: any = await ROUTES["/v1/coach"](body, llmReturning({ reply: "Hi", actions: [action(a)] }));
    return out.actions.length === 1;
  };
  it("set_reminder needs HH:mm", async () => {
    expect(await kept({ time: "19:00" })).toBe(true);
    expect(await kept({ time: "24:00" })).toBe(false);
    expect(await kept({ time: "9:00" })).toBe(false);
    expect(await kept({ time: null })).toBe(false);
  });
  it("set_days needs non-empty days", async () => {
    expect(await kept({ type: "set_days", time: null, days: ["MONDAY"] })).toBe(true);
    expect(await kept({ type: "set_days", time: null, days: [] })).toBe(false);
    expect(await kept({ type: "set_days", time: null, days: null })).toBe(false);
  });
  it("set_rest_day needs a date", async () => {
    expect(await kept({ type: "set_rest_day", time: null, date: "2026-10-01" })).toBe(true);
    expect(await kept({ type: "set_rest_day", time: null, date: "tomorrow" })).toBe(false);
    expect(await kept({ type: "set_rest_day", time: null, date: null })).toBe(false);
  });
  it("set_goal needs count 1..999", async () => {
    expect(await kept({ type: "set_goal", time: null, count: 5 })).toBe(true);
    expect(await kept({ type: "set_goal", time: null, count: 0 })).toBe(false);
    expect(await kept({ type: "set_goal", time: null, count: 1000 })).toBe(false);
    expect(await kept({ type: "set_goal", time: null, count: null })).toBe(false);
  });
  it("log_done, rest_today and open_habit need only habitId", async () => {
    for (const type of ["log_done", "rest_today", "open_habit"]) expect(await kept({ type, time: null })).toBe(true);
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
  it("skips moderation when the last message is from the coach", async () => {
    const llm = llmReturning({ reply: "ok", actions: [] });
    let moderated = false;
    llm.moderate = async () => { moderated = true; return true; };
    const out: any = await ROUTES["/v1/coach"]({ ...body, messages: [{ role: "user", text: "hi" }, { role: "coach", text: "hello" }] }, llm);
    expect(moderated).toBe(false);
    expect(out.reply).toBe("ok");
  });
  it("cuts action labels to 28", async () => {
    const out: any = await ROUTES["/v1/coach"](body, llmReturning({ reply: "Hi", actions: [action({ label: "l".repeat(60) })] }));
    expect(out.actions[0].label.length).toBeLessThanOrEqual(28);
  });
  it("reply is capped at 80 words", async () => {
    const long: any = await ROUTES["/v1/coach"](body, llmReturning({ reply: Array(80).fill("x".repeat(20)).join(" "), actions: [] }));
    expect(long.reply.length).toBeLessThanOrEqual(600);
    const out: any = await ROUTES["/v1/coach"](body, llmReturning({ reply: Array(120).fill("word").join(" "), actions: [] }));
    expect(out.reply.split(/\s+/).length).toBeLessThanOrEqual(80);
  });
});
