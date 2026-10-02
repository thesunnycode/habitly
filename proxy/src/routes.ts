import type { z } from "zod";
import { Action, Frequency, SuggestRequest, ReviewRequest, CoachRequest, SuggestOutput, ReviewOutput, CoachOutput } from "./schemas";
import { SUGGEST_PROMPT, REVIEW_PROMPT, COACH_PROMPT } from "./prompts";
import { UpstreamError, type Llm } from "./llm";
import type { Routes } from "./handler";

export const SAFE_REPLY =
  "I can't help with that one, but I'm here for your habits and routines. If something feels heavy, please talk to someone you trust or a local helpline.";

const cut = (s: string, n: number) => (s.length <= n ? s : s.slice(0, n - 1).trimEnd() + "…");
const words = (s: string, n: number) => { const w = s.trim().split(/\s+/); return w.length <= n ? s.trim() : w.slice(0, n).join(" ") + "…"; };

type Act = z.infer<typeof Action>;
const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;
const YMD = /^\d{4}-\d{2}-\d{2}$/;

function validAction(a: Act): boolean {
  switch (a.type) {
    case "set_reminder": return a.time !== null && HHMM.test(a.time);
    case "set_days": return a.days !== null && a.days.length > 0;
    case "set_rest_day": return a.date !== null && YMD.test(a.date);
    case "set_goal": return a.count !== null && a.count >= 1 && a.count <= 999;
    default: return true;
  }
}
const cleanAction = (a: Act | null, ids: Set<number>, pageHabitId: number | null = null): Act | null =>
  a && ids.has(a.habitId) && (pageHabitId === null || a.habitId === pageHabitId) && validAction(a)
    ? { ...a, label: cut(a.label, 28) } : null;

type Freq = z.infer<typeof Frequency>;
function cleanFrequency(f: Freq): Freq {
  const daily: Freq = { type: "DAILY", days: null, intervalDays: null };
  if (f.type === "SPECIFIC_DAYS") return f.days && f.days.length > 0 ? { type: f.type, days: f.days, intervalDays: null } : daily;
  if (f.type === "INTERVAL") {
    return f.intervalDays !== null && f.intervalDays >= 2 && f.intervalDays <= 30 ? { type: f.type, days: null, intervalDays: f.intervalDays } : daily;
  }
  return daily;
}

async function suggest(body: unknown, llm: Llm) {
  const input = SuggestRequest.parse(body);
  const out = await llm.parse(SuggestOutput, "habit_suggestions", SUGGEST_PROMPT, JSON.stringify(input), 1500);
  const icon = (k: string) => (input.iconKeys.includes(k) ? k : input.iconKeys.includes("star") ? "star" : (input.iconKeys[0] ?? k));
  const category = (c: string) => (input.categories.includes(c) ? c : input.categories.includes("General") ? "General" : (input.categories[0] ?? c));
  const habits = out.habits
    .filter((h) => h.name.trim().length > 0)
    .slice(0, 4)
    .map((h) => {
      const countable = h.targetCount !== null && h.targetCount >= 2 && h.targetCount <= 999;
      return {
        ...h,
        name: cut(h.name.trim(), 40), why: cut(h.why, 90),
        iconKey: icon(h.iconKey), category: category(h.category),
        colorSeed: Math.min(11, Math.max(0, h.colorSeed)),
        reminderTime: h.reminderTime !== null && HHMM.test(h.reminderTime) ? h.reminderTime : null,
        frequency: cleanFrequency(h.frequency),
        targetCount: countable ? h.targetCount : null,
        unit: countable ? h.unit : null,
      };
    });
  if (habits.length === 0) throw new UpstreamError("no habits");
  return { intent: out.intent, habits };
}

async function review(body: unknown, llm: Llm) {
  const input = ReviewRequest.parse(body);
  const ids = new Set(input.habits.map((h) => h.id));
  const out = await llm.parse(ReviewOutput, "week_review", REVIEW_PROMPT, JSON.stringify(input), 2500);
  const pages = out.pages.slice(0, 5).map((p) => {
    const habitId = p.habitId !== null && ids.has(p.habitId) ? p.habitId : null;
    return { ...p, headline: cut(p.headline, 60), body: cut(p.body, 160), habitId, action: cleanAction(p.action, ids, habitId) };
  });
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
  const actions = out.actions.map((a) => cleanAction(a, ids)).filter((a): a is Act => a !== null).slice(0, 2);
  // Capped in characters as well as words: the app sends this reply back as history, limited to 600.
  return { reply: words(out.reply, 80).slice(0, 600), actions };
}

export const ROUTES: Routes = { "/v1/habits/suggest": suggest, "/v1/review/weekly": review, "/v1/coach": coach };
