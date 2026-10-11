import { z } from "zod";
const apiErrorResponse = z.object({ message: z.string().min(1) });
export async function authApi<T>(path: string, schema: z.ZodType<T>, body?: unknown): Promise<T> {
  const response = await fetch(
    `/api/auth/${path}`,
    body
      ? {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }
      : undefined,
  );
  const result: unknown = await response.json();
  if (!response.ok) {
    const error = apiErrorResponse.safeParse(result);
    throw new Error(error.success ? error.data.message : "요청을 처리하지 못했습니다.");
  }
  const parsed = schema.safeParse(result);
  if (!parsed.success)
    throw new Error("서버 응답 형식이 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
  return parsed.data;
}
