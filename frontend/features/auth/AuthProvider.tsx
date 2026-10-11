"use client";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { failure, request, userSchema, type CurrentUser } from "./authClient";
export type { CurrentUser } from "./authClient";
type Auth = {
  user: CurrentUser | null;
  loading: boolean;
  error: string;
  signIn: (email: string, password: string, rememberMe: boolean) => Promise<CurrentUser>;
  signOut: () => Promise<void>;
};
const AuthContext = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const revision = useRef(0);
  const restore = useCallback(async () => {
    const current = revision.current;
    try {
      const response = await request("me");
      if (current !== revision.current) return;
      if (response.status === 401 || response.status === 403) {
        setUser(null);
        setError("");
        return;
      }
      if (!response.ok) await failure(response);
      const restored = userSchema.parse(await response.json());
      if (current === revision.current) {
        setUser(restored);
        setError("");
      }
    } catch {
      if (current === revision.current)
        setError("로그인 상태를 확인하지 못했습니다. 서버 연결을 확인해주세요.");
    } finally {
      if (current === revision.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    void restore();
    const refresh = () => void restore();
    window.addEventListener("focus", refresh);
    return () => window.removeEventListener("focus", refresh);
  }, [restore]);
  async function signIn(email: string, password: string, rememberMe: boolean) {
    revision.current++;
    const response = await request("login", {
      email,
      password,
      rememberMe,
    });
    if (!response.ok) await failure(response);
    const signedInUser = userSchema.parse(await response.json());
    setUser(signedInUser);
    setError("");
    setLoading(false);
    return signedInUser;
  }
  async function signOut() {
    revision.current++;
    const response = await request("logout", {});
    if (!response.ok) await failure(response);
    // 로그아웃 중 시작된 세션 조회 결과도 무시한다.
    revision.current++;
    setUser(null);
    setError("");
    setLoading(false);
  }
  return (
    <AuthContext.Provider value={{ user, loading, error, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("AuthProvider가 필요합니다.");
  return value;
}
