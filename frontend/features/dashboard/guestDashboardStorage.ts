import type { z } from "zod";
import type { QuitRecord } from "../records/useQuitRecords";
import { catalogSchema, emptyDashboard, snapshotSchema, type Snapshot } from "./dashboardSchemas";
type Catalog = z.infer<typeof catalogSchema>;
const GUEST_KEY = "soom-guest-dashboard-v1";
const today = () => new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });
function readGuestDashboard() {
  const raw = localStorage.getItem(GUEST_KEY);
  return raw ? snapshotSchema.parse(JSON.parse(raw)) : emptyDashboard();
}
export function saveGuestDashboard(data: Snapshot) {
  localStorage.setItem(GUEST_KEY, JSON.stringify(snapshotSchema.parse(data)));
}
export function syncGuestDashboard(records: QuitRecord[], catalog: Catalog) {
  const data = readGuestDashboard(),
    all = records;
  data.recordDetails = data.recordDetails.filter((d) => all.some((r) => r.id === d.id));
  data.wishes = data.wishes.filter((w) => all.some((r) => r.id === w.recordId));
  for (const r of all) {
    let detail = data.recordDetails.find((d) => d.id === r.id);
    if (!detail) {
      detail = { id: r.id, growth: 0, attendance: [], rewards: [] };
      data.recordDetails.push(detail);
    }
    for (const m of catalog.milestones) {
      if (
        ((r.end ?? Date.now()) - r.start) / 60000 < m.minutes ||
        detail.rewards.includes(m.minutes)
      )
        continue;
      detail.rewards.push(m.minutes);
      data.points += m.points;
      if (m.points > 0)
        data.transactions.unshift({ label: m.title + " 달성", amount: m.points, date: today() });
    }
  }
  saveGuestDashboard(data);
  return data;
}

export function updateGuestDashboard(
  path: string,
  input: Record<string, unknown>,
  records: QuitRecord[],
  catalog: Catalog,
) {
  const data = syncGuestDashboard(records, catalog);
  const recordId = Number(input.recordId);
  if (path === "/attendance") {
    const record = records.find((record) => record.id === recordId && !record.end);
    if (!record) throw new Error("진행 중인 금연 기록이 필요해요.");
    const detail = data.recordDetails.find((detail) => detail.id === recordId)!;
    if (detail.attendance.includes(today())) return data;
    detail.attendance.push(today());
    data.points += 50;
    data.transactions.unshift({ label: "오늘의 출석", amount: 50, date: today() });
    return data;
  }
  if (path === "/wishes") {
    const name = String(input.name ?? "").trim();
    const price = Number(input.price);
    if (
      !records.some((record) => record.id === recordId) ||
      !name ||
      name.length > 100 ||
      !Number.isSafeInteger(price) ||
      price < 1 ||
      price > 1000000000
    ) {
      throw new Error("목표 이름과 금액을 확인해주세요.");
    }
    if (!input.id) {
      data.wishes.unshift({ id: Date.now(), recordId, name, price });
      return data;
    }
    if (!data.wishes.some((wish) => wish.id === input.id))
      throw new Error("목표를 찾을 수 없어요.");
    data.wishes = data.wishes.map((wish) =>
      wish.id === input.id ? { ...wish, name, price } : wish,
    );
    return data;
  }
  if (/^\/wishes\/\d+\/delete$/.test(path)) {
    const id = Number(path.split("/")[2]);
    data.wishes = data.wishes.filter((wish) => wish.id !== id);
    return data;
  }
  throw new Error("금연 나무와 알림은 로그인 후 이용해주세요.");
}
