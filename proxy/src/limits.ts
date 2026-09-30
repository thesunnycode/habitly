const TTL_SECONDS = 172_800;

// KV is eventually consistent and get/put is not atomic, so a small overshoot of the
// limits under concurrent requests is acceptable for this abuse-protection cap.
export async function consume(kv: KVNamespace, deviceId: string, now: Date, deviceLimit: number, globalLimit: number) {
  const day = now.toISOString().slice(0, 10);
  const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
  const resetAt = next.toISOString();
  const deviceKey = `d:${day}:${deviceId}`;
  const globalKey = `g:${day}`;
  const device = Number((await kv.get(deviceKey)) ?? "0");
  const global = Number((await kv.get(globalKey)) ?? "0");
  if (device >= deviceLimit || global >= globalLimit) return { ok: false, remaining: 0, resetAt };
  await kv.put(deviceKey, String(device + 1), { expirationTtl: TTL_SECONDS });
  await kv.put(globalKey, String(global + 1), { expirationTtl: TTL_SECONDS });
  return { ok: true, remaining: Math.max(0, deviceLimit - device - 1), resetAt };
}
