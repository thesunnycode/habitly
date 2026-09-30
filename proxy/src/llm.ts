import OpenAI from "openai";
import { zodTextFormat } from "openai/helpers/zod";
import type { ZodType } from "zod";

export class UpstreamError extends Error {}

export interface Llm {
  parse<T>(schema: ZodType<T>, name: string, system: string, user: string, maxTokens: number): Promise<T>;
  moderate(text: string): Promise<boolean>;
}

export function openAiLlm(env: { OPENAI_API_KEY: string; OPENAI_MODEL: string }): Llm {
  const client = new OpenAI({ apiKey: env.OPENAI_API_KEY });
  return {
    async parse(schema, name, system, user, maxTokens) {
      const res = await client.responses.parse({
        model: env.OPENAI_MODEL,
        input: [{ role: "system", content: system }, { role: "user", content: user }],
        text: { format: zodTextFormat(schema as any, name) },
        max_output_tokens: maxTokens,
      });
      if (res.output_parsed == null) throw new UpstreamError("empty output");
      const checked = (schema as ZodType<any>).safeParse(res.output_parsed);
      if (!checked.success) throw new UpstreamError("output failed validation");
      return checked.data;
    },
    async moderate(text) {
      const res = await client.moderations.create({ model: "omni-moderation-latest", input: text });
      return res.results.some((r) => r.flagged);
    },
  };
}
