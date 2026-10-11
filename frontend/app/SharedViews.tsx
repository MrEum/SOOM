import { Sprout, Wallet } from "lucide-react";

import type { AppContext } from "@/app/appContext";
import { formatAmount } from "@/app/formatters";

export function EmptyState({
  text,
  action,
  onClick,
}: {
  text: string;
  action?: string;
  onClick?: () => void;
}) {
  return (
    <div className="empty-state">
      <Sprout size={40} />
      <p>{text}</p>
      {action && (
        <button className="primary" onClick={onClick}>
          {action}
        </button>
      )}
    </div>
  );
}
export function SavingsSummary({ context }: { context: AppContext }) {
  return (
    <div className="money-summary">
      <div>
        <span>현재 기록의 절약 금액</span>
        <strong>₩ {formatAmount(context.savings)}</strong>
      </div>
      <div>
        <span>
          {context.dashboardStore.member
            ? "과거 기록을 포함한 누적 절약 금액"
            : "현재 금연 기록의 절약 금액"}
        </span>
        <strong>₩ {formatAmount(context.total)}</strong>
      </div>
      <Wallet size={44} strokeWidth={1} />
    </div>
  );
}
