import type { RecordInput } from "@/features/records/useQuitRecords";
import { useState } from "react";
import { toast } from "sonner";

import type { AppContext } from "@/app/appContext";

export function QuitRecordForm({ context, close }: { context: AppContext; close: () => void }) {
  const record = context.editRecord;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [clientId] = useState(() => record?.clientId ?? crypto.randomUUID());
  const local = (epoch: number) =>
    new Date(epoch - new Date(epoch).getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  return (
    <form
      className="form-stack"
      onSubmit={async (event) => {
        event.preventDefault();
        if (busy) return;
        setError("");
        const f = new FormData(event.currentTarget);
        const input: RecordInput = {
          start: new Date(String(f.get("start"))).getTime(),
          end: f.get("end") ? new Date(String(f.get("end"))).getTime() : undefined,
          daily: Number(f.get("daily")),
          price: Number(f.get("price")),
          packSize: Number(f.get("packSize")),
          clientId,
        };
        setBusy(true);
        try {
          await context.recordStore.save(input, record?.id);
          close();
          context.setEditRecord(null);
          context.go("dashboard");
          toast.success("금연 기록을 저장했어요.");
        } catch (error) {
          setError(
            error instanceof Error ? error.message : "저장하지 못했어요. 다시 시도해주세요.",
          );
        } finally {
          setBusy(false);
        }
      }}
    >
      <label>
        금연 시작일 · 시간
        <input
          name="start"
          type="datetime-local"
          required
          defaultValue={local(record?.start ?? Date.now())}
        />
      </label>
      {record && context.dashboardStore.member && (
        <label>
          금연 종료일 · 시간 (진행 중이면 비워두세요)
          <input
            name="end"
            type="datetime-local"
            defaultValue={record.end ? local(record.end) : ""}
          />
        </label>
      )}
      <div className="form-grid">
        <label>
          하루 평균 흡연량 (개비)
          <input
            name="daily"
            type="number"
            min="1"
            max="200"
            step="1"
            defaultValue={record?.daily ?? 15}
            required
          />
        </label>
        <label>
          담배 한 갑 가격 (원)
          <input
            name="price"
            type="number"
            min="1"
            max="100000"
            step="1"
            defaultValue={record?.price ?? 4500}
            required
          />
        </label>
      </div>
      <label>
        한 갑당 개비 수
        <input
          name="packSize"
          type="number"
          min="1"
          max="200"
          step="1"
          defaultValue={record?.packSize ?? 20}
          required
        />
      </label>
      {error && <p role="alert">{error}</p>}
      <button className="primary wide" disabled={busy || context.recordStore.busy} type="submit">
        {busy ? "저장 중…" : record ? "기록 수정 저장" : "금연 시작"}
      </button>
    </form>
  );
}
