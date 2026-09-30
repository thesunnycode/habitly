import { handle, type Env } from "./handler";
import { openAiLlm } from "./llm";
import { ROUTES } from "./routes";

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    return handle(req, env, { llm: openAiLlm(env), now: () => new Date(), routes: ROUTES });
  },
};
