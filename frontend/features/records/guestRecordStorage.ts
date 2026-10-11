import { z } from "zod";

export const recordSchema = z
  .object({
    id: z.number().int().positive(),
    start: z.number().finite().nonnegative(),
    end: z.number().finite().nonnegative().optional(),
    daily: z.number().int().min(1).max(200),
    price: z.number().int().min(1).max(100000),
    packSize: z.number().int().min(1).max(200).default(20),
    clientId: z.string().uuid(),
    growth: z.number().default(0),
    attendance: z.array(z.string()).default([]),
    rewards: z.array(z.number()).default([]),
  })
  .refine(
    (r) =>
      r.start <= Date.now() && (r.end === undefined || (r.end >= r.start && r.end <= Date.now())),
    "기록 날짜를 확인해주세요.",
  );
export type QuitRecord = z.infer<typeof recordSchema>;
export type RecordInput = Pick<
  QuitRecord,
  "start" | "end" | "daily" | "price" | "packSize" | "clientId"
>;
export const KEY = "soom-guest-quit-records-v1";
const listSchema = z
  .array(recordSchema)
  .refine(
    (records) => records.filter((r) => r.end === undefined).length <= 1,
    "진행 중인 기록이 여러 개예요.",
  );
export function readGuest(): QuitRecord[] {
  const stored = localStorage.getItem(KEY);
  if (stored !== null) {
    const records = currentGuest(JSON.parse(stored));
    localStorage.setItem(KEY, JSON.stringify(records));
    return records;
  }
  const legacy = localStorage.getItem("soom-ui-v2");
  if (!legacy) return [];
  const parsed: unknown = JSON.parse(legacy);
  const old = z
    .object({ records: z.array(z.object({ id: z.number() }).passthrough()).optional() })
    .parse(parsed);
  // 구버전 샘플(ID 1, 2)은 이전하지 않는다.
  const records = currentGuest(
    (old.records ?? [])
      .filter((r) => r.id !== 1 && r.id !== 2)
      .map((r) => ({ ...r, clientId: crypto.randomUUID() })),
  );
  localStorage.setItem(KEY, JSON.stringify(records));
  return records;
}
export function currentGuest(value: unknown): QuitRecord[] {
  return listSchema.parse(value).filter((record) => record.end === undefined);
}
export function writeGuest(records: QuitRecord[]) {
  localStorage.setItem(KEY, JSON.stringify(currentGuest(records)));
}
export function removeImportedGuest(clientId: string) {
  const remaining = readGuest().filter((record) => record.clientId !== clientId);
  // 빈 배열을 남겨 구버전 기록의 재이전을 막는다.
  writeGuest(remaining);
  if (!remaining.length) localStorage.removeItem("soom-guest-dashboard-v1");
}
export function resetGuest(record: QuitRecord) {
  const next = recordSchema.parse({
    ...record,
    id: Math.max(Date.now(), record.id + 1),
    start: Date.now(),
    end: undefined,
    clientId: crypto.randomUUID(),
    growth: 0,
    attendance: [],
    rewards: [],
  });
  writeGuest([next]);
  localStorage.removeItem("soom-guest-dashboard-v1");
  return next;
}
export function canImportGuest(member: boolean, status: string, records: QuitRecord[]) {
  return (
    member &&
    ["ready", "empty"].includes(status) &&
    !records.some((record) => record.end === undefined)
  );
}
