import { useCallback, useEffect, useRef, useState } from "react";
import { z } from "zod";
import { useAuth } from "../auth/AuthProvider";
import { failure } from "../auth/authClient";
import type { QuitRecord } from "../records/useQuitRecords";
import {
  saveGuestDashboard,
  syncGuestDashboard,
  updateGuestDashboard,
} from "./guestDashboardStorage";

import {
  catalogSchema,
  emptyDashboard,
  feedSchema,
  snapshotSchema,
  type Snapshot,
} from "./dashboardSchemas";

const errorText = (e: unknown) =>
  e instanceof Error ? e.message : "데이터를 처리하지 못했습니다.";
export function useDashboard(records: QuitRecord[], recordsReady: boolean) {
  const auth = useAuth();
  const owner = auth.loading
    ? "loading"
    : auth.error
      ? "error"
      : auth.user
        ? `member:${auth.user.userId}`
        : "guest";
  const activeOwner = useRef(owner);
  activeOwner.current = owner;
  const [personal, setPersonal] = useState<{
    owner: string;
    data: Snapshot;
    status: "loading" | "ready" | "error";
    error: string;
  }>({ owner: "", data: emptyDashboard(), status: "loading", error: "" });
  const [feed, setFeed] = useState<{ owner: string; data: z.infer<typeof feedSchema> }>({
    owner: "",
    data: { posts: [], reports: [] },
  });
  const [catalog, setCatalog] = useState<z.infer<typeof catalogSchema>>({
    milestones: [],
    quotes: [],
  });
  const [publicError, setPublicError] = useState("");
  const [busy, setBusy] = useState(false);
  const saving = useRef(false),
    requestKeys = useRef(new Map<string, string>()),
    personalRevision = useRef(0),
    publicRevision = useRef(0);
  const recordsRef = useRef(records);
  recordsRef.current = records;
  const catalogRef = useRef(catalog);
  catalogRef.current = catalog;
  const recordsKey = records
    .map((r) => `${r.id}:${r.start}:${r.end}:${r.daily}:${r.price}:${r.packSize}`)
    .join(",");
  const request = useCallback(
    async (path: string, body?: unknown) => {
      const headers: Record<string, string> = {};
      if (body !== undefined) {
        const csrfResponse = await fetch("/api/auth/csrf", {
          credentials: "include",
          cache: "no-store",
        });
        if (!csrfResponse.ok) await failure(csrfResponse);
        const csrf = z
          .object({ headerName: z.string(), token: z.string() })
          .parse(await csrfResponse.json());
        headers[csrf.headerName] = csrf.token;
        headers["Content-Type"] = "application/json";
      }
      if (activeOwner.current !== owner) throw new Error("계정이 변경됐습니다. 다시 시도해주세요.");
      const response = await fetch(path, {
        credentials: "include",
        cache: "no-store",
        headers,
        method: body === undefined ? "GET" : "POST",
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      if (!response.ok) await failure(response);
      const payload: unknown = await response.json();
      if (activeOwner.current !== owner) throw new Error("계정이 변경됐습니다. 다시 시도해주세요.");
      return payload;
    },
    [owner],
  );
  const refresh = useCallback(async () => {
    if (owner === "loading" || owner === "error" || !recordsReady) return;
    const revision = ++personalRevision.current;
    try {
      const data =
        owner === "guest"
          ? syncGuestDashboard(recordsRef.current, catalogRef.current)
          : snapshotSchema.parse(await request("/api/dashboard/sync", {}));
      if (activeOwner.current === owner && revision === personalRevision.current)
        setPersonal({ owner, data, status: "ready", error: "" });
    } catch (e) {
      if (activeOwner.current === owner && revision === personalRevision.current)
        setPersonal({ owner, data: emptyDashboard(), status: "error", error: errorText(e) });
    }
  }, [owner, recordsReady, request]);
  const refreshPublic = useCallback(async () => {
    const revision = ++publicRevision.current;
    try {
      const [rawFeed, rawCatalog] = await Promise.allSettled([
        request("/api/community").then((value) => feedSchema.parse(value)),
        request("/api/community/catalog").then((value) => catalogSchema.parse(value)),
      ]);
      if (activeOwner.current !== owner || revision !== publicRevision.current) return;
      if (rawFeed.status === "fulfilled") setFeed({ owner, data: rawFeed.value });
      if (rawCatalog.status === "fulfilled") setCatalog(rawCatalog.value);
      setPublicError(
        [rawFeed, rawCatalog]
          .filter((result) => result.status === "rejected")
          .map((result) => errorText(result.reason))
          .join(" "),
      );
    } catch (e) {
      if (activeOwner.current === owner && revision === publicRevision.current) {
        setFeed({ owner, data: { posts: [], reports: [] } });
        setPublicError(errorText(e));
      }
    }
  }, [owner, request]);
  useEffect(() => {
    requestKeys.current.clear();
    setPersonal({ owner, data: emptyDashboard(), status: "loading", error: "" });
  }, [owner]);
  useEffect(() => {
    void refresh();
  }, [refresh, recordsKey, catalog.milestones.length]);
  useEffect(() => {
    if (owner !== "loading") void refreshPublic();
  }, [owner, refreshPublic]);
  useEffect(() => {
    const refreshAll = () => {
      if (!saving.current) {
        void refresh();
        void refreshPublic();
      }
    };
    const timer = setInterval(refreshAll, 60000);
    window.addEventListener("focus", refreshAll);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", refreshAll);
    };
  }, [refresh, refreshPublic]);
  async function saveChanges(path: string, input: Record<string, unknown> = {}, community = false) {
    if (saving.current) throw new Error("저장 중입니다. 잠시 기다려주세요.");
    if (community && !path.endsWith("/view") && !auth.user)
      throw new Error("로그인 후 이용해주세요.");
    if (!community && (personal.owner !== owner || personal.status !== "ready"))
      throw new Error("데이터를 먼저 불러와주세요.");
    saving.current = true;
    setBusy(true);
    ++personalRevision.current;
    ++publicRevision.current;
    const key = owner + path + JSON.stringify(input);
    const requestId = requestKeys.current.get(key) ?? crypto.randomUUID();
    requestKeys.current.set(key, requestId);
    try {
      if (!community && owner === "guest") {
        const data = updateGuestDashboard(path, input, recordsRef.current, catalogRef.current);
        saveGuestDashboard(data);
        setPersonal({ owner, data, status: "ready", error: "" });
        requestKeys.current.delete(key);
        return;
      }
      const result = await request(community ? `/api/community${path}` : `/api/dashboard${path}`, {
        ...input,
        requestId,
      });
      if (community) {
        setFeed({ owner, data: feedSchema.parse(result) });
        requestKeys.current.delete(key);
        return;
      }
      setPersonal({ owner, data: snapshotSchema.parse(result), status: "ready", error: "" });
      requestKeys.current.delete(key);
    } finally {
      saving.current = false;
      setBusy(false);
    }
  }
  const visible =
    personal.owner === owner
      ? personal
      : { owner, data: emptyDashboard(), status: "loading" as const, error: "" };
  return {
    data: visible.data,
    status: visible.status,
    error: visible.error,
    publicError,
    catalog,
    feed: feed.owner === owner ? feed.data : { posts: [], reports: [] },
    busy,
    member: !!auth.user,
    saveChanges,
    refresh,
    refreshPublic,
  };
}
