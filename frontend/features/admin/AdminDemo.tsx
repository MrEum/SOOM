import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ArrowLeft, ArrowRight, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import type { AppContext } from "@/app/appContext";
import { calculateSavings, formatAmount, formatDate } from "@/app/formatters";

import { EmptyState } from "@/app/SharedViews";

type Member = {
  id: number;
  name: string;
  email: string;
  joined: string;
  status: string;
  reports: number;
  days: number;
  saved: number;
};
function MonthlySavings({ context }: { context: AppContext }) {
  const values = Array.from({ length: 6 }, (_, i) => {
    const month = new Date(context.now);
    month.setMonth(month.getMonth() - 5 + i, 1);
    month.setHours(0, 0, 0, 0);
    const until = new Date(month);
    until.setMonth(until.getMonth() + 1);
    const amount = context.data.records.reduce((sum, r) => {
      const end = Math.min(r.end ?? context.now, until.getTime());
      const start = Math.max(r.start, month.getTime());
      return sum + (((Math.max(0, end - start) / 86400000) * r.daily) / r.packSize) * r.price;
    }, 0);
    return { label: `${month.getMonth() + 1}월`, amount };
  });
  const max = Math.max(1, ...values.map((v) => v.amount));
  return (
    <section className="panel">
      <h2 className="section-title">월별 절약 금액 추이</h2>
      <p className="muted small spaced">데모 금연 기록을 월별 기간으로 나눠 계산한 금액이에요.</p>
      <div className="bar-chart">
        {values.map((v, i) => (
          <div key={i}>
            <strong>₩{formatAmount(v.amount)}</strong>
            <div className="bar" style={{ height: Math.max(3, (v.amount / max) * 150) }} />
            <span>{v.label}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
export function AdminDemo({ context }: { context: AppContext }) {
  const [tab, setTab] = useState("overview"),
    [query, setQuery] = useState(""),
    [period, setPeriod] = useState("all"),
    [member, setMember] = useState<Member | null>(null),
    [detailTab, setDetailTab] = useState("records"),
    [members, setMembers] = useState<Member[]>([
      {
        id: 1,
        name: "숨쉬는고래",
        email: "demo@soom.app",
        joined: "2026. 9. 1.",
        status: "ACTIVE",
        reports: 0,
        days: 14,
        saved: 48161,
      },
      {
        id: 2,
        name: "초록한걸음",
        email: "green@example.com",
        joined: "2026. 8. 21.",
        status: "ACTIVE",
        reports: 1,
        days: 38,
        saved: 171000,
      },
      {
        id: 3,
        name: "맑은하루",
        email: "clear@example.com",
        joined: "2026. 9. 8.",
        status: "ACTIVE",
        reports: 0,
        days: 20,
        saved: 90000,
      },
      {
        id: 4,
        name: "느리게걷기",
        email: "slow@example.com",
        joined: "2026. 9. 12.",
        status: "SUSPENDED",
        reports: 6,
        days: 16,
        saved: 72000,
      },
    ]),
    [processed, setProcessed] = useState<string[]>([]);
  const [adminReady, setAdminReady] = useState(false),
    [memberSort, setMemberSort] = useState(false);
  useEffect(() => {
    try {
      const saved = localStorage.getItem("soom-admin-demo");
      if (saved) {
        const d = JSON.parse(saved);
        setMembers(d.members);
        setProcessed(d.processed);
      }
    } catch {}
    setAdminReady(true);
  }, []);
  useEffect(() => {
    if (adminReady) localStorage.setItem("soom-admin-demo", JSON.stringify({ members, processed }));
  }, [members, processed, adminReady]);
  const setStatus = (m: Member, status: string) => {
    setMembers((old) => old.map((x) => (x.id === m.id ? { ...x, status } : x)));
    setMember({ ...m, status });
    if (status === "ACTIVE")
      setProcessed((old) => [
        ...new Set([
          ...old,
          ...context.data.reports.filter((r) => {
            const [type, id] = r.split("|")[0].split(":");
            const author =
              type === "POST"
                ? context.data.posts.find((p) => p.id === Number(id))?.name
                : context.data.posts.flatMap((p) => p.comments).find((x) => x.id === Number(id))
                    ?.name;
            return author === m.name;
          }),
        ]),
      ]);
    toast.success(
      status === "ACTIVE"
        ? "정지를 해제했어요."
        : status === "DELETED"
          ? "탈퇴 처리했어요."
          : "계정을 정지했어요.",
    );
  };
  const pending = context.data.reports.filter((r) => !processed.includes(r));
  const tabs = [
    { id: "overview", label: "대시보드" },
    { id: "members", label: "회원 관리" },
    { id: "reports", label: "신고 · 콘텐츠 관리" },
  ];
  return (
    <div className="view-stack">
      <p className="view-description">
        서비스의 건강한 변화를 한눈에 확인하세요.{" "}
        <span className="demo-badge">관리자 권한 체험용 샘플 데이터</span>
      </p>
      <Tabs
        value={tab}
        onValueChange={(v) => {
          setTab(v);
          setMember(null);
        }}
      >
        <TabsList>
          {tabs.map((t) => (
            <TabsTrigger key={t.id} value={t.id}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>
      {tab === "overview" && (
        <>
          <div className="admin-stats">
            {[
              { label: "전체 회원", value: members.length },
              {
                label: "진행 중인 회원",
                value: members.filter((m) => m.status === "ACTIVE").length,
              },
              {
                label: "정지 / 탈퇴 회원",
                value:
                  members.filter((m) => m.status === "SUSPENDED").length +
                  " / " +
                  members.filter((m) => m.status === "DELETED").length,
              },
              {
                label: "게시글 / 댓글",
                value:
                  context.data.posts.filter((p) => !p.deleted).length +
                  " / " +
                  context.data.posts.reduce((s, p) => s + p.comments.length, 0),
              },
              { label: "미처리 신고", value: pending.length },
              { label: "누적 금연 기록", value: context.data.records.length },
            ].map((x) => (
              <section className="panel" key={x.label}>
                <span className="muted small">{x.label}</span>
                <strong>{x.value}</strong>
              </section>
            ))}
          </div>
          <div className="two-column">
            <section className="panel">
              <div className="section-toolbar">
                <h2 className="section-title">금연 유지 기간 분포</h2>
                <Select value={period} onValueChange={setPeriod}>
                  <SelectTrigger className="sort-select">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {[
                      { id: "all", label: "전체" },
                      { id: "7", label: "1주 이상" },
                      { id: "30", label: "1개월 이상" },
                      { id: "90", label: "3개월 이상" },
                      { id: "180", label: "6개월 이상" },
                      { id: "365", label: "1년 이상" },
                    ].map((x) => (
                      <SelectItem key={x.id} value={x.id}>
                        {x.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="bar-chart">
                {[
                  { label: "1주 미만", min: 0, max: 7 },
                  { label: "1주+", min: 7, max: 30 },
                  { label: "1개월+", min: 30, max: 90 },
                  { label: "3개월+", min: 90, max: 180 },
                  { label: "6개월+", min: 180, max: 365 },
                  { label: "1년+", min: 365, max: Infinity },
                ].map((b) => {
                  const count = members.filter(
                    (m) =>
                      m.status === "ACTIVE" &&
                      m.days >= b.min &&
                      m.days < b.max &&
                      (period === "all" || m.days >= Number(period)),
                  ).length;
                  return (
                    <div key={b.label}>
                      <strong>{count}명</strong>
                      <div className="bar" style={{ height: count * 55 + 3 }} />
                      <span>{b.label}</span>
                    </div>
                  );
                })}
              </div>
            </section>
            <section className="panel">
              <h2 className="section-title">누적 절약 금액</h2>
              <div className="finance-value">
                ₩{" "}
                {formatAmount(
                  members.reduce((s, m) => s + (m.id === 1 ? context.total : m.saved), 0),
                )}
              </div>
              <p className="muted small">
                인당 평균 ₩{" "}
                {formatAmount(
                  members.reduce((s, m) => s + (m.id === 1 ? context.total : m.saved), 0) /
                    members.length,
                )}
              </p>
              <div className="ranking">
                {[...members]
                  .sort((a, b) => b.saved - a.saved)
                  .map((m, i) => (
                    <div className="row" key={m.id}>
                      <span>
                        <b>{i + 1}</b> {m.name}
                      </span>
                      <strong>₩ {formatAmount(m.id === 1 ? context.total : m.saved)}</strong>
                    </div>
                  ))}
              </div>
            </section>
          </div>
          <MonthlySavings context={context} />
          <section className="panel">
            <h2 className="section-title">최근 활동</h2>
            <div className="activity-list">
              {context.data.transactions.slice(0, 5).map((t, i) => (
                <div key={i}>
                  <span className="activity-dot" />
                  <button
                    onClick={() => {
                      setTab("members");
                      setMember(members[0]);
                    }}
                  >
                    숨쉬는고래
                  </button>
                  <span>{t.label}</span>
                  <time>{t.date}</time>
                </div>
              ))}
              {context.data.posts.slice(0, 3).map((p) => (
                <div key={p.id}>
                  <span className="activity-dot" />
                  <button
                    onClick={() => {
                      setTab("members");
                      setMember(members.find((m) => m.name === p.name) ?? members[0]);
                    }}
                  >
                    {p.name}
                  </button>
                  <span>새로운 이야기 작성</span>
                  <time>{formatDate(p.created)}</time>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
      {tab === "members" &&
        (member ? (
          <section className="panel">
            <button className="text-button" onClick={() => setMember(null)}>
              <ArrowLeft size={16} />
              회원 목록
            </button>
            <div className="member-header">
              <span className="profile-avatar">{member.name[0]}</span>
              <div>
                <h2>{member.name}</h2>
                <p className="muted">{member.email}</p>
              </div>
              <span className={`status-chip ${member.status !== "ACTIVE" ? "neutral" : ""}`}>
                {member.status === "ACTIVE"
                  ? "정상"
                  : member.status === "SUSPENDED"
                    ? "정지"
                    : "탈퇴"}
              </span>
            </div>
            <div className="button-row">
              <button
                className="outline-button"
                disabled={member.status === "DELETED"}
                onClick={() =>
                  context.setConfirm({
                    title:
                      member.status === "ACTIVE"
                        ? "이 회원을 정지할까요?"
                        : "이 회원의 정지를 해제할까요?",
                    body: "관리자 데모의 회원 상태를 변경합니다.",
                    action: () =>
                      setStatus(member, member.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE"),
                  })
                }
              >
                {member.status === "ACTIVE" ? "계정 정지" : "정지 해제"}
              </button>
              <button
                className="outline-button danger"
                disabled={member.status === "DELETED"}
                onClick={() =>
                  context.setConfirm({
                    title: "회원 탈퇴 처리",
                    body: "관리자 데모에서 이 회원을 탈퇴 상태로 변경합니다.",
                    action: () => setStatus(member, "DELETED"),
                  })
                }
              >
                강제 탈퇴
              </button>
            </div>
            <Tabs value={detailTab} onValueChange={setDetailTab} className="spaced">
              <TabsList>
                {[
                  { id: "records", label: "금연 기록" },
                  { id: "posts", label: "게시글 · 댓글" },
                  { id: "points", label: "포인트 내역" },
                  { id: "reports", label: "신고 내역" },
                ].map((x) => (
                  <TabsTrigger key={x.id} value={x.id}>
                    {x.label}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
            {detailTab === "records" && (
              <div className="detail-body">
                {member.id === 1 ? (
                  context.data.records.map((r) => (
                    <div className="transaction" key={r.id}>
                      <span>
                        {formatDate(r.start)} · {r.end ? "종료" : "진행 중"}
                      </span>
                      <span>
                        나무 {r.growth}% · ₩{formatAmount(calculateSavings(r, context.now))}
                      </span>
                    </div>
                  ))
                ) : (
                  <p>
                    금연 {member.days}일 · 절약 ₩{formatAmount(member.saved)} · 나무 성장 40%
                  </p>
                )}
              </div>
            )}
            {detailTab === "posts" && (
              <div className="detail-body">
                {context.data.posts
                  .filter((p) => p.name === member.name)
                  .map((p) => (
                    <div className="transaction" key={p.id}>
                      <span>{p.title}</span>
                      <span>{p.deleted ? "삭제됨" : "게시 중"}</span>
                    </div>
                  ))}
                {context.data.posts.flatMap((p) =>
                  p.comments
                    .filter((x) => x.name === member.name)
                    .map((x) => (
                      <div className="transaction" key={x.id}>
                        댓글 · {x.content}
                      </div>
                    )),
                )}
              </div>
            )}
            {detailTab === "points" && (
              <div className="detail-body">
                {member.id === 1 ? (
                  context.data.transactions.map((t, i) => (
                    <div className="transaction" key={i}>
                      <span>{t.label}</span>
                      <strong>{t.amount}P</strong>
                    </div>
                  ))
                ) : (
                  <p className="muted">이 샘플 회원의 상세 포인트 기록은 없어요.</p>
                )}
              </div>
            )}
            {detailTab === "reports" && (
              <div className="detail-body">
                <p>
                  샘플 누적 신고 {member.reports}건 ·{" "}
                  {member.status === "SUSPENDED" ? "검토 필요" : "현재 제재 없음"}
                </p>
              </div>
            )}
          </section>
        ) : (
          <section className="panel">
            <label className="search-box">
              <Search size={17} />
              <input
                aria-label="회원 검색"
                placeholder="닉네임 또는 이메일 검색"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </label>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>
                    <button className="text-button" onClick={() => setMemberSort((v) => !v)}>
                      닉네임 {memberSort ? "↑" : "↓"}
                    </button>
                  </TableHead>
                  <TableHead>가입일</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>신고 수</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {members
                  .filter((m) => (m.name + m.email).includes(query))
                  .sort((a, b) => (memberSort ? a.name.localeCompare(b.name, "ko") : a.id - b.id))
                  .map((m) => (
                    <TableRow key={m.id}>
                      <TableCell>{m.id}</TableCell>
                      <TableCell>{m.name}</TableCell>
                      <TableCell>{m.joined}</TableCell>
                      <TableCell>
                        <span className={`status-chip ${m.status !== "ACTIVE" ? "neutral" : ""}`}>
                          {m.status === "ACTIVE"
                            ? "정상"
                            : m.status === "SUSPENDED"
                              ? "정지"
                              : "탈퇴"}
                        </span>
                      </TableCell>
                      <TableCell>{m.reports}</TableCell>
                      <TableCell>
                        <button className="text-button" onClick={() => setMember(m)}>
                          상세보기
                          <ArrowRight size={15} />
                        </button>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </section>
        ))}
      {tab === "reports" && (
        <section className="panel">
          <h2 className="section-title">접수된 신고</h2>
          {context.data.reports.length === 0 ? (
            <EmptyState text="접수된 신고가 없어요." />
          ) : (
            context.data.reports.map((r, i) => {
              const [target, reason] = r.split("|"),
                [type, id] = target.split(":"),
                content =
                  type === "POST"
                    ? context.data.posts.find((p) => p.id === Number(id))?.title
                    : context.data.posts.flatMap((p) => p.comments).find((x) => x.id === Number(id))
                        ?.content;
              return (
                <div className="report-row" key={i}>
                  <div>
                    <span className="category">{type === "POST" ? "게시글" : "댓글"}</span>
                    <h3>{content ?? "삭제된 콘텐츠"}</h3>
                    <p>{reason}</p>
                  </div>
                  <button
                    className="outline-button"
                    disabled={processed.includes(r)}
                    onClick={() => {
                      setProcessed((old) => [...old, r]);
                      toast.success("신고를 검토 완료 처리했어요.");
                    }}
                  >
                    {processed.includes(r) ? "처리 완료" : "검토 완료"}
                  </button>
                  <button
                    className="text-button danger"
                    onClick={() =>
                      context.setConfirm({
                        title: "신고된 콘텐츠를 삭제할까요?",
                        body: "커뮤니티 화면에서 더 이상 표시되지 않아요.",
                        action: () => {
                          context.setData((d) => ({
                            ...d,
                            posts: d.posts.map((p) =>
                              type === "POST" && p.id === Number(id)
                                ? { ...p, deleted: true }
                                : type === "COMMENT"
                                  ? {
                                      ...p,
                                      comments: p.comments.filter(
                                        (x) => x.id !== Number(id) && x.parent !== Number(id),
                                      ),
                                    }
                                  : p,
                            ),
                          }));
                          setProcessed((old) => [...old, r]);
                        },
                      })
                    }
                  >
                    콘텐츠 삭제
                  </button>
                </div>
              );
            })
          )}
          <h2 className="section-title spaced">비속어 마스킹 모니터링</h2>
          {context.data.posts
            .filter(
              (p) =>
                !p.deleted &&
                (p.title + p.content + p.comments.map((x) => x.content).join("")).includes("████"),
            )
            .map((p) => (
              <div className="transaction" key={p.id}>
                <span>{p.title}</span>
                <button
                  className="text-button"
                  onClick={() => {
                    context.openPost(p);
                  }}
                >
                  내용 확인
                </button>
              </div>
            ))}
          {!context.data.posts.some(
            (p) =>
              !p.deleted &&
              (p.title + p.content + p.comments.map((x) => x.content).join("")).includes("████"),
          ) && <p className="muted spaced">마스킹된 콘텐츠가 없어요.</p>}
        </section>
      )}
    </div>
  );
}
