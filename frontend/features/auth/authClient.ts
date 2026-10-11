import { z } from "zod";
export const userSchema = z.object({
  userId: z.number().int(),
  email: z.string(),
  name: z.string(),
  nickname: z.string(),
  role: z.string(),
});
const csrfSchema = z.object({ token: z.string(), headerName: z.string() });
const messageSchema = z.object({ message: z.string() });
export type CurrentUser = z.infer<typeof userSchema>;
export async function request(path: string, body?: unknown) {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    const csrfResponse = await fetch("/api/auth/csrf", {
      credentials: "include",
      cache: "no-store",
    });
    if (!csrfResponse.ok)
      throw new Error("로그인 보안 정보를 가져오지 못했습니다. 다시 시도해주세요.");
    const csrf = csrfSchema.parse(await csrfResponse.json());
    headers[csrf.headerName] = csrf.token;
    headers["Content-Type"] = "application/json";
  }
  return fetch(`/api/auth/${path}`, {
    credentials: "include",
    cache: "no-store",
    headers,
    method: body === undefined ? "GET" : "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
export async function failure(response: Response): Promise<never> {
  const payload: unknown = await response.json().catch(() => null);
  const error = messageSchema.safeParse(payload);
  throw new Error(
    error.success ? error.data.message : "요청을 처리하지 못했습니다. 다시 시도해주세요.",
  );
}
