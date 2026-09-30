import { handle, type Env } from "./handler";
import { openAiLlm, type Llm } from "./llm";
import { ROUTES } from "./routes";

// Reuse the client across requests; rebuild only if the key or model changes.
let cached: { key: string; model: string; llm: Llm } | undefined;

function llmFor(env: Env): Llm {
  if (!cached || cached.key !== env.OPENAI_API_KEY || cached.model !== env.OPENAI_MODEL) {
    cached = { key: env.OPENAI_API_KEY, model: env.OPENAI_MODEL, llm: openAiLlm(env) };
  }
  return cached.llm;
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    return handle(req, env, { llm: llmFor(env), now: () => new Date(), routes: ROUTES });
  },
};
