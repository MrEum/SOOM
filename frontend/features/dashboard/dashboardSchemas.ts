import { z } from "zod";
const recordDetailsSchema = z.object({
  id: z.number(),
  growth: z.number(),
  attendance: z.array(z.string()),
  rewards: z.array(z.number()),
});
export const snapshotSchema = z.object({
  unreadCount: z.number().default(0),
  points: z.number(),
  wishes: z.array(
    z.object({ id: z.number(), name: z.string(), price: z.number(), recordId: z.number() }),
  ),
  transactions: z.array(z.object({ label: z.string(), amount: z.number(), date: z.string() })),
  recordDetails: z.array(recordDetailsSchema),
  notifications: z.array(
    z.object({ id: z.number(), content: z.string(), read: z.boolean(), date: z.string() }),
  ),
});
const commentSchema = z.object({
  edited: z.boolean().default(false),
  replyTo: z
    .number()
    .nullish()
    .transform((v) => v ?? undefined),
  id: z.number(),
  name: z.string(),
  content: z.string(),
  parent: z
    .number()
    .nullable()
    .transform((v) => v ?? undefined),
  mine: z.boolean(),
  deleted: z.boolean().default(false),
});
const postSchema = z.object({
  edited: z.boolean().default(false),
  id: z.number(),
  category: z.string(),
  title: z.string(),
  content: z.string(),
  name: z.string(),
  likes: z.number(),
  liked: z.boolean(),
  views: z.number(),
  comments: z.array(commentSchema),
  images: z.array(z.string()),
  created: z.number(),
  mine: z.boolean(),
});
export const feedSchema = z.object({ posts: z.array(postSchema), reports: z.array(z.string()) });
export const catalogSchema = z.object({
  milestones: z.array(
    z.object({
      id: z.number(),
      minutes: z.number(),
      title: z.string(),
      label: z.string(),
      points: z.number(),
      description: z.string().default(""),
    }),
  ),
  quotes: z.array(z.string()),
});
export type Snapshot = z.infer<typeof snapshotSchema>;
export const emptyDashboard = (): Snapshot => ({
  unreadCount: 0,
  points: 0,
  wishes: [],
  transactions: [],
  recordDetails: [],
  notifications: [],
});
