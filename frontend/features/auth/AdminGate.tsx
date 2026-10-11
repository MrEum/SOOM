"use client";
import { useEffect, useState, type ReactNode } from "react";
import { useAuth } from "./AuthProvider";
export function AdminGate({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const [allowed, setAllowed] = useState(false),
    [message, setMessage] = useState("관리자 권한을 확인하고 있습니다.");
  useEffect(() => {
    let active = true;
    setAllowed(false);
    if (!user || user.role !== "ADMIN") {
      setMessage("관리자 계정으로 로그인해야 접근할 수 있습니다.");
      return;
    }
    fetch("/api/admin/access", { credentials: "include", cache: "no-store" })
      .then((response) => {
        if (active) {
          setAllowed(response.ok);
          setMessage(response.ok ? "" : "관리자 권한을 확인할 수 없습니다. 다시 로그인해주세요.");
        }
      })
      .catch(() => {
        if (active) setMessage("서버에 연결할 수 없습니다.");
      });
    return () => {
      active = false;
    };
  }, [user]);
  if (loading || !allowed || user?.role !== "ADMIN")
    return (
      <section className="panel">
        <p role="status">{loading ? "로그인 확인 중…" : message}</p>
        {!loading && (
          <a className="outline-button" href="#login">
            로그인
          </a>
        )}
      </section>
    );
  return <>{children}</>;
}
