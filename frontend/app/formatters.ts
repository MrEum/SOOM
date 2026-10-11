import type { QuitRecord } from "./appTypes";
export const formatAmount = (amount: number) => Math.floor(amount).toLocaleString("ko-KR");
export const formatDate = (timestamp: number) => new Date(timestamp).toLocaleDateString("ko-KR");
export function calculateSavings(record: QuitRecord, now: number) {
  const elapsed = Math.max(0, (record.end ?? now) - record.start);
  return (elapsed / 86400000) * (record.daily / record.packSize) * record.price;
}
