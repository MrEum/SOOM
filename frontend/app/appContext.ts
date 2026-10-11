import type { AppData, Post, QuitRecord } from "@/app/appTypes";
import type { useDashboard } from "@/features/dashboard/useDashboard";
import type { useQuitRecords } from "@/features/records/useQuitRecords";
import { type Dispatch, type SetStateAction } from "react";

export type ConfirmDialogState = {
  title: string;
  body: string;
  action: () => void;
};
export type AppContext = {
  dashboardStore: ReturnType<typeof useDashboard>;
  run: (
    path: string,
    input?: Record<string, unknown>,
    message?: string,
    community?: boolean,
  ) => Promise<boolean>;
  recordStore: ReturnType<typeof useQuitRecords>;
  editRecord: QuitRecord | null;
  setEditRecord: (record: QuitRecord | null) => void;
  data: AppData;
  setData: Dispatch<SetStateAction<AppData>>;
  now: number;
  active: QuitRecord | undefined;
  elapsed: number;
  savings: number;
  total: number;
  go: (id: string) => void;
  setModal: (s: string) => void;
  setConfirm: Dispatch<SetStateAction<ConfirmDialogState | null>>;
  selected: number | null;
  setSelected: (n: number | null) => void;
  post: Post | undefined;
  openPost: (p: Post) => void;
  search: string;
  setSearch: (s: string) => void;
  category: string;
  setCategory: (s: string) => void;
  reply: number | undefined;
  setReply: (n: number | undefined) => void;
  editing: Post | null;
  setEditing: (p: Post | null) => void;
  images: string[];
  setImages: Dispatch<SetStateAction<string[]>>;
  mask: (s: string) => string;
  nurture: (cost: number, growth: number) => void;
  attend: () => void;
  attended: boolean;
};
