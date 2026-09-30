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
