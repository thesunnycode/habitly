import type { ZodType } from "zod";

export class UpstreamError extends Error {}

export interface Llm {
  parse<T>(schema: ZodType<T>, name: string, system: string, user: string, maxTokens: number): Promise<T>;
  moderate(text: string): Promise<boolean>;
}

// Temporary stub; Task 2 replaces it with the OpenAI-backed implementation.
export function openAiLlm(_env: unknown): Llm {
  throw new Error("configured in Task 2");
}
