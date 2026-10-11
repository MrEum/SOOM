"use client";
import { Checkbox } from "@/components/ui/checkbox";
import { ArrowLeft, ArrowRight, Wind } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { useAuth } from "./AuthProvider";
import { RecoveryHints, recoveryQuestions } from "./RecoveryHints";
import "./recovery.css";
import { authApi } from "./signupClient";
const availabilityResponse = z.object({ available: z.boolean() });
const sendCodeResponse = z.object({
  message: z.string(),
  expiresIn: z.number().int().positive(),
  retryAfter: z.number().int().nonnegative(),
});
const verifyCodeResponse = z.object({
  verificationToken: z.string().min(1),
  expiresIn: z.number().int().positive(),
});
const signupResponse = z.object({ message: z.string() });
export function AuthScreen({ page, go }: { page: string; go: (p: string) => void }) {
  const { signIn, signOut, loading } = useAuth();
  const [guestBusy, setGuestBusy] = useState(false);
  const guestPending = useRef(false);
  async function startGuest() {
    if (guestPending.current || loading || busy) return;
    guestPending.current = true;
    setGuestBusy(true);
    try {
      await signOut();
      go("guest-start");
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "비회원으로 전환하지 못했어요. 다시 시도해주세요.",
      );
    } finally {
      guestPending.current = false;
      setGuestBusy(false);
    }
  }
  const [code, setCode] = useState("");
  const [verificationToken, setVerificationToken] = useState("");
  const [proofExpiresAt, setProofExpiresAt] = useState(0);
  const [mailBusy, setMailBusy] = useState(false);
  const [sentTo, setSentTo] = useState("");
  const [mailNotice, setMailNotice] = useState("");
  const [retryAt, setRetryAt] = useState(0);
  const [mailNow, setMailNow] = useState(Date.now());
  const emailRevision = useRef(0);
  const [signupComplete, setSignupComplete] = useState(false);
  const [busy, setBusy] = useState(false);
  const signup = page === "signup";
  const [recoveryAnswers, setRecoveryAnswers] = useState<string[]>(() =>
    recoveryQuestions.map(() => ""),
  );
  const [email, setEmail] = useState(""),
    [nickname, setNickname] = useState(""),
    [password, setPassword] = useState(""),
    [passwordConfirm, setPasswordConfirm] = useState(""),
    [checkedEmail, setCheckedEmail] = useState(""),
    [checkedNickname, setCheckedNickname] = useState(""),
    [remember, setRemember] = useState(false),
    [error, setError] = useState("");
  useEffect(() => {
    emailRevision.current++;
    setCode("");
    setVerificationToken("");
    setSentTo("");
    setMailNotice("");
    setRetryAt(0);
    setSignupComplete(false);
    setError("");
    setPassword("");
    setPasswordConfirm("");
    setRecoveryAnswers(recoveryQuestions.map(() => ""));
    setCheckedEmail("");
    setCheckedNickname("");
  }, [page]);
  async function check(kind: "email" | "nickname", value: string) {
    setError("");
    try {
      const result = await authApi(
        `check-${kind}?${kind}=${encodeURIComponent(value.trim())}`,
        availabilityResponse,
      );
      if (!result.available) throw new Error("이미 사용 중인 이메일 또는 닉네임입니다.");
      if (kind === "email") setCheckedEmail(value);
      else setCheckedNickname(value);
      toast.success("사용할 수 있어요.");
    } catch (error) {
      setError(error instanceof Error ? error.message : "서버에 연결할 수 없습니다.");
    }
  }
  useEffect(() => {
    const timer = setInterval(() => setMailNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    if (verificationToken && proofExpiresAt && mailNow >= proofExpiresAt && !signupComplete) {
      setVerificationToken("");
      setCode("");
      setMailNotice("인증 시간이 만료되었습니다. 인증번호를 다시 발송해주세요.");
    }
  }, [mailNow, proofExpiresAt, verificationToken, signupComplete]);
  async function sendCode() {
    if (mailBusy) return;
    const revision = emailRevision.current;
    const address = email.trim().toLowerCase();
    setError("");
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address) || address.length > 100) {
      setError("올바른 이메일을 입력해주세요.");
      return;
    }
    setMailBusy(true);
    try {
      const result = await authApi("email/send", sendCodeResponse, {
        email: address,
      });
      if (revision !== emailRevision.current) return;
      setSentTo(address);
      setVerificationToken("");
      setCode("");
      setMailNotice(
        "인증번호를 보냈습니다. 5분 안에 입력해주세요. 메일이 없다면 스팸함도 확인해주세요.",
      );
      setRetryAt(Date.now() + result.retryAfter * 1000);
      setMailNow(Date.now());
    } catch (e) {
      if (revision === emailRevision.current)
        setError(e instanceof Error ? e.message : "메일을 보내지 못했습니다.");
    } finally {
      setMailBusy(false);
    }
  }
  async function verifyCode() {
    if (mailBusy) return;
    if (!/^[0-9]{6}$/.test(code)) {
      setError("인증번호 6자리를 입력해주세요.");
      return;
    }
    const revision = emailRevision.current;
    setMailBusy(true);
    setError("");
    try {
      const result = await authApi("email/verify", verifyCodeResponse, {
        email: email.trim().toLowerCase(),
        code,
      });
      if (revision !== emailRevision.current) return;
      setVerificationToken(result.verificationToken);
      setProofExpiresAt(Date.now() + result.expiresIn * 1000);
      setCheckedEmail(email);
      setMailNotice("이메일 인증이 완료되었습니다. 10분 안에 회원가입을 완료해주세요.");
    } catch (e) {
      if (revision === emailRevision.current)
        setError(e instanceof Error ? e.message : "인증에 실패했습니다.");
    } finally {
      setMailBusy(false);
    }
  }
  const validPassword = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,20}$/.test(password);
  return (
    <main className={`auth-layout ${signup ? "signup-layout" : ""}`}>
      <section className="auth-story">
        <a href="#dashboard" onClick={() => go("dashboard")} className="brand">
          <span className="brand-mark">
            <Wind size={28} />
          </span>
          <strong>숨</strong>
          <span>soom</span>
        </a>
        <div>
          <span className="eyebrow">A FRESH START, EVERY DAY</span>
          <h1>
            나를 위한 변화,
            <br />한 번의 숨에서 시작.
          </h1>
          <p>
            어제보다 조금 더 가벼운 오늘.
            <br />
            당신의 건강한 여정을 함께할게요.
          </p>
          <Wind size={150} strokeWidth={0.7} />
        </div>
        <span>작은 시작이 만드는, 커다란 변화.</span>
      </section>
      <section className="auth-form">
        <button
          className="text-button"
          disabled={loading || busy || guestBusy}
          onClick={() => void startGuest()}
        >
          <ArrowLeft size={16} />
          {guestBusy ? "비회원으로 전환 중…" : "비회원으로 시작하기"}
        </button>
        <div>
          <h1>{signup ? "나를 위한 첫걸음" : "다시 만나 반가워요"}</h1>
          <p className="view-description">
            {signup
              ? "계정을 만들고, 나만의 건강한 변화를 시작하세요."
              : "오늘도 나를 위한 한 걸음을 이어가요."}
          </p>
          <form
            className="form-stack"
            onSubmit={async (e) => {
              e.preventDefault();
              if (busy || guestPending.current || (signup && signupComplete)) return;
              const form = e.currentTarget;
              setError("");
              if (signup) {
                if (!passwordConfirm || password !== passwordConfirm) {
                  setError("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
                  return;
                }
                if (!recoveryAnswers.some((answer) => answer.trim())) {
                  setError("아이디 찾기에 사용할 질문을 하나 이상 선택하고 답변해주세요.");
                  return;
                }
                if (!verificationToken) {
                  setError("이메일 인증을 먼저 완료해주세요.");
                  return;
                }
                if (checkedEmail !== email || checkedNickname !== nickname || !validPassword) {
                  setError("중복 확인과 비밀번호 조건을 확인해주세요.");
                  return;
                }
                setBusy(true);
                try {
                  await authApi("signup", signupResponse, {
                    email,
                    password,
                    nickname,
                    verificationToken,
                    recoveryAnswers,
                    name: new FormData(form).get("name"),
                  });
                  setSignupComplete(true);
                  setRecoveryAnswers(recoveryQuestions.map(() => ""));
                  setPasswordConfirm("");
                  setPassword("");
                  setCheckedEmail("");
                  setCheckedNickname("");
                  go("login");
                  toast.success("회원가입이 완료되었습니다. 로그인해주세요.");
                } catch (error) {
                  setError(error instanceof Error ? error.message : "서버에 연결할 수 없습니다.");
                } finally {
                  setBusy(false);
                }
              } else {
                setBusy(true);
                try {
                  const signedInUser = await signIn(email, password, remember);
                  setPassword("");
                  go(signedInUser.role === "ADMIN" ? "admin" : "dashboard");
                  toast.success("로그인되었습니다.");
                } catch (e) {
                  setError(e instanceof Error ? e.message : "서버에 연결할 수 없습니다.");
                } finally {
                  setBusy(false);
                }
              }
            }}
          >
            <label>
              이메일
              <div className="input-action">
                <input
                  type="email"
                  placeholder="hello@example.com"
                  value={email}
                  onChange={(e) => {
                    emailRevision.current++;
                    setEmail(e.target.value);
                    setCheckedEmail("");
                    setVerificationToken("");
                    setCode("");
                    setSentTo("");
                    setMailNotice("");
                    setRetryAt(0);
                  }}
                  required
                />
                {signup && (
                  <button
                    type="button"
                    className="outline-button"
                    onClick={() => void check("email", email)}
                  >
                    {checkedEmail === email && email ? "확인 완료" : "중복 확인"}
                  </button>
                )}
              </div>
            </label>
            {signup && (
              <>
                <div className="form-stack">
                  <button
                    type="button"
                    className="outline-button"
                    disabled={
                      mailBusy || busy || signupComplete || !!verificationToken || mailNow < retryAt
                    }
                    onClick={() => void sendCode()}
                  >
                    {verificationToken
                      ? "이메일 인증 완료"
                      : mailNow < retryAt
                        ? `${Math.ceil((retryAt - mailNow) / 1000)}초 후 재발송`
                        : mailBusy
                          ? "처리 중…"
                          : sentTo
                            ? "인증번호 재발송"
                            : "이메일 인증번호 발송"}
                  </button>
                  {sentTo && !verificationToken && (
                    <label>
                      인증번호
                      <div className="input-action">
                        <input
                          aria-label="이메일 인증번호"
                          inputMode="numeric"
                          autoComplete="one-time-code"
                          maxLength={6}
                          value={code}
                          onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, ""))}
                          placeholder="6자리 인증번호"
                        />
                        <button
                          type="button"
                          className="outline-button"
                          disabled={mailBusy || code.length !== 6}
                          onClick={() => void verifyCode()}
                        >
                          인증 확인
                        </button>
                      </div>
                    </label>
                  )}
                  <p
                    role="status"
                    aria-live="polite"
                    className={verificationToken ? "green" : "muted small"}
                  >
                    {mailNotice}
                  </p>
                </div>
                <label>
                  닉네임
                  <div className="input-action">
                    <input
                      value={nickname}
                      onChange={(e) => {
                        setNickname(e.target.value);
                        setCheckedNickname("");
                      }}
                      minLength={2}
                      maxLength={20}
                      required
                      placeholder="나를 표현하는 이름"
                    />
                    <button
                      type="button"
                      className="outline-button"
                      onClick={() => void check("nickname", nickname)}
                    >
                      {checkedNickname === nickname && nickname ? "확인 완료" : "중복 확인"}
                    </button>
                  </div>
                </label>
                <label>
                  이름
                  <input
                    name="name"
                    autoComplete="name"
                    placeholder="이름을 입력해주세요"
                    required
                    maxLength={50}
                  />
                </label>
              </>
            )}
            <label>
              비밀번호
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={signup ? "new-password" : "current-password"}
                placeholder="비밀번호를 입력해주세요"
                required
              />
            </label>
            {signup && (
              <p className={`password-hint ${validPassword ? "green" : ""}`}>
                {validPassword
                  ? "✓ 안전한 비밀번호 형식이에요."
                  : "영문, 숫자, 특수문자를 포함한 8~20자"}
              </p>
            )}
            {signup && (
              <label>
                비밀번호 확인
                <input
                  type="password"
                  name="passwordConfirm"
                  autoComplete="new-password"
                  value={passwordConfirm}
                  onChange={(e) => setPasswordConfirm(e.target.value)}
                  placeholder="비밀번호를 다시 입력해주세요"
                  required
                  aria-invalid={passwordConfirm.length > 0 && password !== passwordConfirm}
                  aria-describedby="password-confirm-hint"
                />
                <span
                  id="password-confirm-hint"
                  className={
                    passwordConfirm && password !== passwordConfirm
                      ? "form-error"
                      : "password-hint green"
                  }
                  aria-live="polite"
                >
                  {passwordConfirm
                    ? password === passwordConfirm
                      ? "✓ 비밀번호가 일치합니다."
                      : "비밀번호가 일치하지 않습니다."
                    : ""}
                </span>
              </label>
            )}
            {!signup && (
              <label className="check-label">
                <Checkbox checked={remember} onCheckedChange={(v) => setRemember(v === true)} />
                로그인 상태 유지 (최대 7일)
              </label>
            )}
            {signup && !signupComplete && (
              <RecoveryHints answers={recoveryAnswers} onChange={setRecoveryAnswers} signup />
            )}
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button
              type="submit"
              disabled={
                busy ||
                guestBusy ||
                (signup && (signupComplete || !passwordConfirm || password !== passwordConfirm))
              }
              className="primary wide"
            >
              {signup
                ? signupComplete
                  ? "가입 완료"
                  : busy
                    ? "가입 처리 중…"
                    : "회원가입"
                : "로그인"}
              <ArrowRight size={17} />
            </button>
          </form>
          {!signup && (
            <div className="recovery-links">
              <button onClick={() => go("find-id")}>아이디 찾기</button>
              <button onClick={() => go("forgot-password")}>비밀번호 찾기</button>
            </div>
          )}
          <p role="status" aria-live="polite" className="green">
            {signup && signupComplete
              ? "✓ 회원가입이 완료되었습니다. 숨에 오신 것을 환영합니다!"
              : ""}
          </p>
          <p className="auth-switch">
            {signup ? "이미 숨과 함께하고 있나요?" : "아직 숨과 함께하지 않으셨나요?"}
            <button onClick={() => go(signup ? "login" : "signup")}>
              {signup ? "로그인" : "회원가입"}
            </button>
          </p>
          <div className="demo-info">
            <strong>{signup ? "회원가입 안내" : "로그인 안내"}</strong>
            <p>
              {signup
                ? "회원가입 정보는 서버에 저장됩니다. 가입한 계정으로 로그인할 수 있습니다."
                : "가입한 이메일과 비밀번호를 입력해주세요."}
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
