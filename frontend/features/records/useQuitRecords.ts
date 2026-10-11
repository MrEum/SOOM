import { useCallback, useEffect, useRef, useState } from "react";
import { z } from "zod";
import { useAuth } from "../auth/AuthProvider";
import { failure } from "../auth/authClient";

import {
  canImportGuest,
  KEY,
  readGuest,
  recordSchema,
  removeImportedGuest,
  resetGuest,
  writeGuest,
  type QuitRecord,
  type RecordInput,
} from "./guestRecordStorage";
export { readGuest, recordSchema, type QuitRecord, type RecordInput } from "./guestRecordStorage";

async function api(path = "", body?: unknown) {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    const response = await fetch("/api/auth/csrf", { credentials: "include", cache: "no-store" });
    if (!response.ok) await failure(response);
    const csrf = z
      .object({ headerName: z.string(), token: z.string() })
      .parse(await response.json());
    headers[csrf.headerName] = csrf.token;
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(`/api/quit-records${path}`, {
    credentials: "include",
    cache: "no-store",
    headers,
    method: body === undefined ? "GET" : "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) await failure(response);
  return response.json() as Promise<unknown>;
}
async function serverRecords(): Promise<QuitRecord[]> {
  const result = z
    .array(
      z.object({
        id: z.number(),
        start: z.number(),
        end: z.number().nullable(),
        daily: z.number(),
        price: z.number(),
        packSize: z.number(),
      }),
    )
    .parse(await api());
  return result.map((r) =>
    recordSchema.parse({ ...r, end: r.end ?? undefined, clientId: crypto.randomUUID() }),
  );
}
type Snapshot = {
  owner: string;
  status: "initial" | "loading" | "empty" | "ready" | "error";
  records: QuitRecord[];
  error: string;
};
const message = (error: unknown) =>
  error instanceof Error ? error.message : "기록을 처리하지 못했어요.";
export function useQuitRecords() {
  const auth = useAuth();
  const owner = auth.loading
    ? "pending"
    : auth.error
      ? "auth-error"
      : auth.user
        ? `user:${auth.user.userId}`
        : "guest";
  const current = useRef(owner);
  current.current = owner;
  const revision = useRef(0),
    inFlight = useRef(false);
  const [snapshot, setSnapshot] = useState<Snapshot>({
    owner: "pending",
    status: "initial",
    records: [],
    error: "",
  });
  const [busy, setBusy] = useState(false),
    [pending, setPending] = useState<QuitRecord[]>([]),
    [transferError, setTransferError] = useState("");
  const refresh = useCallback(async () => {
    const ticket = ++revision.current;
    setSnapshot({ owner, status: "loading", records: [], error: "" });
    if (owner === "pending") return;
    try {
      if (owner === "auth-error") throw new Error(auth.error);
      const records = owner === "guest" ? readGuest() : await serverRecords();
      if (current.current !== owner || revision.current !== ticket) return;
      setSnapshot({ owner, status: records.length ? "ready" : "empty", records, error: "" });
    } catch (error) {
      if (current.current === owner && revision.current === ticket)
        setSnapshot({ owner, status: "error", records: [], error: message(error) });
    }
  }, [owner, auth.error]);
  useEffect(() => {
    void refresh();
    setPending([]);
    setTransferError("");
    if (owner.startsWith("user:")) {
      try {
        setPending(readGuest());
      } catch {
        setTransferError("브라우저의 비회원 기록을 읽지 못했어요. 원본 데이터는 유지됩니다.");
      }
    }
  }, [owner, refresh]);
  useEffect(() => {
    const sync = (event: StorageEvent) => {
      if (event.key === KEY && owner === "guest") void refresh();
    };
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, [owner, refresh]);
  const visible: Snapshot =
    snapshot.owner === owner ? snapshot : { owner, status: "loading", records: [], error: "" };
  async function mutate(action: () => Promise<void>) {
    if (
      inFlight.current ||
      current.current !== owner ||
      !["ready", "empty"].includes(visible.status)
    )
      throw new Error("기록 조회가 끝난 뒤 다시 시도해주세요.");
    inFlight.current = true;
    setBusy(true);
    try {
      await action();
      if (current.current !== owner)
        throw new Error("계정이 변경됐어요. 현재 계정의 기록을 확인해주세요.");
      await refresh();
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  async function save(input: RecordInput, id?: number) {
    const validated = recordSchema.parse({ ...input, id: id ?? Date.now() });
    await mutate(async () => {
      if (owner === "guest") {
        const old = readGuest();
        if (id !== undefined && !old.some((r) => r.id === id))
          throw new Error("기록을 찾을 수 없어요.");
        if (validated.end !== undefined)
          throw new Error(
            "비회원은 현재 금연 기록만 저장할 수 있어요. 다시 시작하려면 리셋해주세요.",
          );
        writeGuest([
          id === undefined ? validated : { ...old.find((r) => r.id === id)!, ...validated },
        ]);
        return;
      }
      await api(id === undefined ? "" : `/${id}/update`, { ...input, end: input.end ?? null });
    });
  }
  async function remove(id: number) {
    await mutate(async () => {
      if (owner === "guest") writeGuest(readGuest().filter((r) => r.id !== id));
      else await api(`/${id}/delete`, {});
    });
  }
  async function reset() {
    await mutate(async () => {
      if (owner !== "guest") throw new Error("비회원 기록에서만 리셋할 수 있어요.");
      const record = readGuest()[0];
      if (!record) throw new Error("현재 금연 기록이 없어요.");
      resetGuest(record);
    });
  }
  async function transfer() {
    setTransferError("");
    try {
      await mutate(async () => {
        if (!owner.startsWith("user:")) throw new Error("로그인이 필요해요.");
        if (!canImportGuest(true, visible.status, visible.records))
          throw new Error("이미 진행 중인 금연 기록이 있어요.");
        for (const record of pending) {
          if (current.current !== owner) throw new Error("계정이 변경되어 이전을 중단했어요.");
          await api("/import", { ...record, end: record.end ?? null });
          // DB 재조회가 성공한 기록만 로컬에서 제거한다.
          const records = await serverRecords();
          if (current.current !== owner) throw new Error("계정이 변경되어 이전을 중단했어요.");
          setSnapshot({ owner, status: records.length ? "ready" : "empty", records, error: "" });
          removeImportedGuest(record.clientId);
          setPending((old) => old.filter((r) => r.clientId !== record.clientId));
        }
      });
    } catch (error) {
      setTransferError(message(error));
      await refresh();
    }
  }
  return {
    ...visible,
    busy,
    refresh,
    save,
    remove,
    reset,
    pending: canImportGuest(owner.startsWith("user:"), visible.status, visible.records)
      ? pending
      : [],
    transfer,
    transferError,
    dismiss: () => {
      setPending([]);
      setTransferError("");
    },
  };
}
