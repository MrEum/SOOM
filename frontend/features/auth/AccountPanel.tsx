"use client";
import { useState } from "react";
import { useAuth } from "./AuthProvider";
export function AccountPanel({ go }: { go: (page: string) => void }) {
  const { user, loading, error, signOut } = useAuth();
  const [busy, setBusy] = useState(false),
    [failure, setFailure] = useState("");
  if (loading) return <p role="status">로그인 정보를 확인하고 있습니다.</p>;
  if (!user)
    return (
      <section className="panel">
        <h2>로그인이 필요합니다</h2>
        <p>{error || "가입한 이메일과 비밀번호로 로그인해주세요."}</p>
        <button className="primary" onClick={() => go("login")}>
          로그인
        </button>
      </section>
    );
  return (
    <section className="panel profile-panel">
      <h2>{user.nickname}</h2>
      <p>{user.email}</p>
      <p>{user.name}</p>
      <p className="muted">
        계정 정보는 서버에서 조회합니다. 금연 기록·포인트·커뮤니티는 아직 브라우저 데모입니다.
      </p>
      {(failure || error) && <p role="alert">{failure || error}</p>}
      <button
        className="outline-button"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          setFailure("");
          try {
            await signOut();
            go("login");
          } catch (e) {
            setFailure(e instanceof Error ? e.message : "로그아웃하지 못했습니다.");
          } finally {
            setBusy(false);
          }
        }}
      >
        {busy ? "로그아웃 중…" : "로그아웃"}
      </button>
    </section>
  );
}
