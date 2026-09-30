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
