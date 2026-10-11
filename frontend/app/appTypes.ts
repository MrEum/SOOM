import type { QuitRecord } from "@/features/records/useQuitRecords";
export type { QuitRecord };
export type Comment = {
  edited?: boolean;
  replyTo?: number;
  deleted?: boolean;
  mine?: boolean;
  id: number;
  name: string;
  content: string;
  parent?: number;
};
export type Post = {
  edited?: boolean;
  mine?: boolean;
  id: number;
  category: string;
  title: string;
  content: string;
  name: string;
  likes: number;
  liked: boolean;
  views: number;
  comments: Comment[];
  images: string[];
  created: number;
  deleted?: boolean;
};
export type Wish = {
  id: number;
  name: string;
  price: number;
  recordId: number;
};
export type AppData = {
  records: QuitRecord[];
  points: number;
  wishes: Wish[];
  posts: Post[];
  reports: string[];
  transactions: {
    label: string;
    amount: number;
    date: string;
  }[];
};
