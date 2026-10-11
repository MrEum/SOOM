"use client";

import { ArrowLeft, ArrowRight, CheckCircle2, KeyRound, Mail, ShieldCheck } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { z } from "zod";
import { failure, request } from "./authClient";
import "./recovery.css";
import { RecoveryHints, recoveryQuestions } from "./RecoveryHints";

const messageSchema = z.object({ message: z.string() });
const questionsSchema = z.object({
  questionNumbers: z.array(z.number().int().min(1).max(5)).min(1).max(5),
});
const foundEmailSchema = z.object({ maskedEmail: z.string() });

export function RecoveryScreen({ page, go }: { page: string; go: (page: string) => void }) {
  const reset = page === "reset-password";
  const findId = page === "find-id";
  const [recoveryAnswers, setRecoveryAnswers] = useState<string[]>(() =>
    recoveryQuestions.map(() => ""),
  );
  const [questionNumbers, setQuestionNumbers] = useState<number[]>([]);
  const [token] = useState(() => new URLSearchParams(window.location.search).get("token") ?? "");
  const [nickname, setNickname] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [validToken, setValidToken] = useState(false);
  const [checking, setChecking] = useState(reset);
  const [complete, setComplete] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!reset) return;
    let active = true;
    window.history.replaceState(null, "", "/reset-password#reset-password");
    async function validate() {
      try {
        const response = await request("password/validate", { token });
        if (!response.ok) await failure(response);
        if (active) setValidToken(true);
      } catch (error) {
        if (active)
          setError(error instanceof Error ? error.message : "링크를 확인하지 못했습니다.");
      } finally {
        if (active) setChecking(false);
      }
    }
    void validate();
    return () => {
      active = false;
    };
  }, [reset, token]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || complete || (reset && !validToken)) return;
    setError("");
    setMessage("");
    if (findId && questionNumbers.some((number) => !recoveryAnswers[number - 1].trim())) {
      setError("등록된 질문의 답변을 모두 입력해주세요.");
      return;
    }
    if (
      reset &&
      (!/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,20}$/.test(password) ||
        password !== confirmation)
    ) {
      setError(
        password !== confirmation
          ? "비밀번호가 서로 일치하지 않습니다."
          : "영문·숫자·특수문자를 포함한 8~20자로 입력해주세요.",
      );
      return;
    }
    setBusy(true);
    try {
      if (findId && questionNumbers.length === 0) {
        const response = await request("find-id/questions", { nickname: nickname.trim() });
        if (!response.ok) await failure(response);
        setQuestionNumbers(questionsSchema.parse(await response.json()).questionNumbers);
        return;
      }
      const response = await request(
        findId ? "find-id" : reset ? "password/reset" : "password/request",
        findId
          ? { nickname: nickname.trim(), recoveryAnswers }
          : reset
            ? { token, password }
            : { nickname: nickname.trim(), email: email.trim() },
      );
      if (!response.ok) await failure(response);
      const payload: unknown = await response.json();
      setMessage(
        findId ? foundEmailSchema.parse(payload).maskedEmail : messageSchema.parse(payload).message,
      );
      setRecoveryAnswers(recoveryQuestions.map(() => ""));
      setComplete(true);
      setPassword("");
      setConfirmation("");
    } catch (error) {
      setError(
        error instanceof Error ? error.message : "요청을 처리하지 못했습니다. 다시 시도해주세요.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="recovery-layout">
      <aside className="recovery-aside">
        <span className="recovery-eyebrow">SOOM · ACCOUNT</span>
        <h2>
          잠시 잊어도,
          <br />
          다시 이어갈 수 있도록.
        </h2>
        <p>
          차근차근 계정을 확인하고
          <br />
          나를 위한 일상으로 돌아오세요.
        </p>
        <div className="recovery-aside-note">
          <ShieldCheck size={20} />
          <span>소중한 계정, 안전하게 확인해요.</span>
        </div>
      </aside>
      <section className="recovery-card">
        <button className="text-button" onClick={() => go("login")}>
          <ArrowLeft size={16} /> 로그인으로
        </button>
        <span className="recovery-icon">
          {findId ? <Mail size={24} /> : <KeyRound size={24} />}
        </span>
        <h1>{findId ? "아이디 찾기" : reset ? "새 비밀번호 설정" : "비밀번호 찾기"}</h1>
        <p className="view-description">
          {findId
            ? "가입한 닉네임과 나만의 답변으로 이메일을 확인하세요."
            : reset
              ? "다음 로그인부터 사용할 비밀번호를 입력해주세요."
              : "가입한 이메일로 비밀번호를 다시 설정할 수 있는 링크를 보내드려요."}
        </p>
        {!reset && !complete && (
          <nav className="recovery-tabs" aria-label="계정 찾기">
            <button
              type="button"
              aria-current={findId ? "page" : undefined}
              onClick={() => go("find-id")}
            >
              아이디 찾기
            </button>
            <button
              type="button"
              aria-current={!findId ? "page" : undefined}
              onClick={() => go("forgot-password")}
            >
              비밀번호 찾기
            </button>
          </nav>
        )}
        {findId && !complete && (
          <ol className="recovery-steps">
            <li className={questionNumbers.length ? "done" : "current"}>
              <span>1</span>닉네임 확인
            </li>
            <li className={questionNumbers.length ? "current" : ""}>
              <span>2</span>힌트 답변
            </li>
          </ol>
        )}
        {checking ? (
          <p role="status">링크를 확인하고 있습니다…</p>
        ) : complete ? (
          <div className="recovery-result" role="status">
            <CheckCircle2 size={26} />
            {findId ? (
              <div>
                <p className="muted small">가입된 이메일</p>
                <strong className="recovered-email">{message}</strong>
              </div>
            ) : (
              <p>{message}</p>
            )}
            {findId && (
              <button className="outline-button" onClick={() => go("forgot-password")}>
                비밀번호 찾기
              </button>
            )}
            <button className="primary wide" onClick={() => go("login")}>
              로그인으로 이동
            </button>
          </div>
        ) : (
          <form className="form-stack" onSubmit={submit}>
            {!reset && (
              <>
                <label>
                  닉네임
                  <input
                    disabled={busy || (findId && questionNumbers.length > 0)}
                    placeholder="가입할 때 사용한 닉네임"
                    autoComplete="nickname"
                    type="text"
                    value={nickname}
                    minLength={2}
                    maxLength={20}
                    onChange={(event) => {
                      setNickname(event.target.value);
                      setError("");
                    }}
                    required
                  />
                </label>
                {!findId && (
                  <label>
                    이메일
                    <input
                      type="email"
                      autoComplete="email"
                      placeholder="로그인에 사용하는 이메일"
                      value={email}
                      maxLength={100}
                      onChange={(event) => {
                        setEmail(event.target.value);
                        setError("");
                      }}
                      required
                    />
                  </label>
                )}
                {findId && questionNumbers.length > 0 && (
                  <button
                    className="change-nickname"
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setQuestionNumbers([]);
                      setRecoveryAnswers(recoveryQuestions.map(() => ""));
                      setError("");
                    }}
                  >
                    다른 닉네임으로 확인
                  </button>
                )}
                {findId && questionNumbers.length > 0 && (
                  <RecoveryHints
                    questionNumbers={questionNumbers}
                    answers={recoveryAnswers}
                    onChange={(answers) => {
                      setRecoveryAnswers(answers);
                      setError("");
                    }}
                  />
                )}
              </>
            )}
            {reset && validToken && (
              <>
                <label>
                  새 비밀번호
                  <input
                    type="password"
                    autoComplete="new-password"
                    minLength={8}
                    maxLength={20}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    required
                  />
                </label>
                <p className="password-hint">영문·숫자·특수문자를 포함한 8~20자</p>
                <label>
                  새 비밀번호 확인
                  <input
                    type="password"
                    autoComplete="new-password"
                    minLength={8}
                    maxLength={20}
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                    required
                  />
                </label>
              </>
            )}
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            {(!reset || validToken) && (
              <button className="primary wide" disabled={busy} type="submit">
                {busy
                  ? "처리 중…"
                  : findId
                    ? questionNumbers.length
                      ? "가입한 이메일 확인"
                      : "내 질문 확인하기"
                    : reset
                      ? "비밀번호 변경"
                      : "재설정 링크 보내기"}
                <ArrowRight size={17} aria-hidden="true" />
              </button>
            )}
            {reset && !validToken && (
              <button
                className="outline-button"
                type="button"
                onClick={() => go("forgot-password")}
              >
                새 링크 요청
              </button>
            )}
          </form>
        )}
        {findId && !complete && (
          <p className="muted small recovery-note">
            답변이 모두 일치하면 이메일의 일부를 보여드려요.
          </p>
        )}
      </section>
    </main>
  );
}
