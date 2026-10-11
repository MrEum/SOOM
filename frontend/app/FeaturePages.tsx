import type { QuitRecord } from "@/app/appTypes";
import { Progress } from "@/components/ui/progress";
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
import { AccountPanel } from "@/features/auth/AccountPanel";
import { AdminGate } from "@/features/auth/AdminGate";
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Droplets,
  Heart,
  Leaf,
  MessageCircle,
  Pencil,
  Plus,
  Search,
  Trash2,
  TreeDeciduous,
  Trophy,
  Users,
} from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import type { AppContext } from "@/app/appContext";
import { calculateSavings, formatAmount, formatDate } from "@/app/formatters";

import { EmptyState, SavingsSummary } from "@/app/SharedViews";

import { AdminDemo } from "@/features/admin/AdminDemo";
import { PostDetail } from "@/features/community/PostDetail";
export function FeaturePages({
  page,
  ...context
}: AppContext & {
  page: string;
}) {
  const { data, setData, active, now, setModal, setConfirm } = context;
  const [recordTab, setRecordTab] = useState("all"),
    [wishTab, setWishTab] = useState("all"),
    [sort, setSort] = useState("latest");
  const newPost = () => {
    if (!context.dashboardStore.member) {
      context.go("login");
      return;
    }
    context.setEditing(null);
    context.setImages([]);
    setModal("post");
  };
  const removeRecord = (r: QuitRecord) =>
    setConfirm({
      title: "이 기록을 삭제할까요?",
      body: "기록과 연결된 위시리스트가 삭제되며, 누적 절약 금액에서도 제외돼요. 삭제한 기록은 복원할 수 없어요.",
      action: () => {
        void context.recordStore
          .remove(r.id)
          .then(() => toast.success("기록을 삭제했어요."))
          .catch((error) => toast.error(error.message));
      },
    });
  const resetRecord = () =>
    active &&
    setConfirm({
      title: context.dashboardStore.member
        ? "현재 금연 기록을 종료할까요?"
        : "현재 금연 기록을 리셋할까요?",
      body: context.dashboardStore.member
        ? "지금까지의 금연 기록과 절약 금액은 과거 기록으로 보관돼요. 계정은 유지되며 언제든 다시 시작할 수 있어요."
        : "기존 금연 기록과 출석·포인트·위시리스트가 삭제되고, 같은 흡연 설정으로 지금부터 다시 시작해요. 이전 기록은 남지 않아요.",
      action: () => {
        if (!context.dashboardStore.member) {
          void context.recordStore
            .reset()
            .then(() => toast.success("금연 기록을 리셋했어요. 지금부터 다시 시작해요."))
            .catch((error) => toast.error(error.message));
          return;
        }
        void context.recordStore
          .save({ ...active, end: Date.now() }, active.id)
          .then(() => {
            context.setEditRecord(null);
            setModal("start");
          })
          .catch((error) => toast.error(error.message));
      },
    });
  if (page === "records")
    return (
      <div className="view-stack">
        <p className="view-description">
          {context.dashboardStore.member
            ? "성공도, 다시 시작한 순간도. 모든 한 걸음이 소중한 기록이에요."
            : "비회원은 현재 금연 기록 하나만 보관해요. 리셋하면 이전 기록은 남지 않아요."}
        </p>
        {data.records.length > 0 && <SavingsSummary context={context} />}
        <section className="panel">
          <div className="section-toolbar">
            {context.dashboardStore.member ? (
              <Tabs value={recordTab} onValueChange={setRecordTab}>
                <TabsList>
                  <TabsTrigger value="all">전체 기록</TabsTrigger>
                  <TabsTrigger value="active">진행 중</TabsTrigger>
                  <TabsTrigger value="past">과거 기록</TabsTrigger>
                </TabsList>
              </Tabs>
            ) : (
              <h2>현재 금연 기록</h2>
            )}
            {active ? (
              <button
                className="outline-button"
                disabled={context.recordStore.busy}
                onClick={resetRecord}
              >
                {context.dashboardStore.member ? "현재 기록 종료" : "현재 기록 리셋"}
              </button>
            ) : (
              <button className="primary" onClick={() => setModal("start")}>
                <Plus size={16} />
                금연 시작하기
              </button>
            )}
          </div>
          {data.records.filter(
            (r) =>
              !context.dashboardStore.member ||
              recordTab === "all" ||
              (recordTab === "active" ? !r.end : !!r.end),
          ).length === 0 ? (
            <EmptyState text="아직 해당 금연 기록이 없어요." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>금연 기간</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>절약 금액</TableHead>
                  <TableHead>흡연량 · 가격</TableHead>
                  <TableHead>나무 성장</TableHead>
                  <TableHead>관리</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.records
                  .filter(
                    (r) =>
                      !context.dashboardStore.member ||
                      recordTab === "all" ||
                      (recordTab === "active" ? !r.end : !!r.end),
                  )
                  .map((r) => (
                    <TableRow key={r.id}>
                      <TableCell>
                        <strong>
                          {formatDate(r.start)} ~ {r.end ? formatDate(r.end) : "현재"}
                        </strong>
                        <small className="block muted">
                          {Math.floor(((r.end ?? now) - r.start) / 86400000)}
                          일의 건강한 선택
                        </small>
                      </TableCell>
                      <TableCell>
                        <span className={`status-chip ${r.end ? "neutral" : ""}`}>
                          {r.end ? "종료됨" : "진행 중"}
                        </span>
                      </TableCell>
                      <TableCell>₩ {formatAmount(calculateSavings(r, now))}</TableCell>
                      <TableCell>
                        {r.daily}개비 / ₩{formatAmount(r.price)}
                      </TableCell>
                      <TableCell>
                        {context.dashboardStore.member ? `${r.growth}%` : "회원 전용"}
                      </TableCell>
                      <TableCell>
                        <button
                          className="text-button"
                          disabled={context.recordStore.busy}
                          onClick={() => {
                            context.setEditRecord(r);
                            setModal("start");
                          }}
                        >
                          수정
                        </button>
                        <button
                          disabled={context.recordStore.busy}
                          aria-label={`${formatDate(r.start)} 기록 삭제`}
                          className="icon-button danger"
                          onClick={() => removeRecord(r)}
                        >
                          <Trash2 size={16} />
                        </button>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          )}
          <p className="footnote">
            절약 금액 = 하루 흡연량 ÷ 한 갑당 개비 수 × 한 갑 가격 × 금연 일수.
            {context.dashboardStore.member
              ? " 종료된 기록은 종료 시점의 금액으로 유지돼요."
              : " 리셋하면 절약 금액도 0원부터 다시 계산해요."}
          </p>
        </section>
      </div>
    );
  if (page === "tree" && !context.dashboardStore.member)
    return (
      <section className="panel">
        <EmptyState
          text="로그인하고 나만의 나무를 키워보세요."
          action="로그인하기"
          onClick={() => context.go("login")}
        />
      </section>
    );
  if (["tree", "journey", "wishlist"].includes(page) && context.dashboardStore.status !== "ready")
    return (
      <section className="panel">
        <EmptyState
          text={context.dashboardStore.error || "대시보드 정보를 불러오고 있어요."}
          action="다시 불러오기"
          onClick={() => void context.dashboardStore.refresh()}
        />
      </section>
    );
  if (page === "tree")
    return (
      <div className="view-stack">
        <p className="view-description">오늘의 작은 돌봄이, 내일의 푸른 나무가 됩니다.</p>
        {!active ? (
          <section className="panel">
            <EmptyState
              text="금연을 시작하면 나만의 나무가 생겨요."
              action="금연 시작하기"
              onClick={() => setModal("start")}
            />
          </section>
        ) : (
          <div className="two-column">
            <section className="panel tree-detail">
              <span className="status-chip">LEVEL {Math.floor(active.growth / 25) + 1}</span>
              <div className="tree-visual large">
                <TreeDeciduous className="tree-icon" strokeWidth={1} />
              </div>
              <h2>{active.growth >= 100 ? "온전히 자란 나의 나무" : "나와 함께 자라는 초록"}</h2>
              <p className="muted">
                {active.growth >= 100
                  ? "꾸준한 돌봄으로 나무를 완성했어요. 정말 잘했어요!"
                  : "서두르지 않아도 괜찮아요. 우리만의 속도로 자라요."}
              </p>
              <div className="growth-bar">
                <div className="row">
                  <span>성장률</span>
                  <strong>{active.growth}%</strong>
                </div>
                <Progress value={active.growth} />
              </div>
              <div className="button-row">
                <button
                  className="primary"
                  onClick={() => context.nurture(30, 5)}
                  disabled={active.growth >= 100 || context.dashboardStore.busy}
                >
                  <Droplets size={18} />물 주기 · 30P
                </button>
                <button
                  className="outline-button"
                  onClick={() => context.nurture(80, 15)}
                  disabled={active.growth >= 100 || context.dashboardStore.busy}
                >
                  <Leaf size={18} />
                  비료 주기 · 80P
                </button>
              </div>
              <p className="footnote">
                물 주기 +5% · 비료 주기 +15% · 보유 포인트 {formatAmount(data.points)}P
              </p>
            </section>
            <section className="panel">
              <h2 className="section-title">나무를 키우는 작은 습관</h2>
              <div className="tip-row">
                <CalendarDays />
                <div>
                  <h3>매일 출석하기</h3>
                  <p>하루 한 번 금연을 약속하고 50P를 받아요.</p>
                  <button
                    className="text-button"
                    onClick={context.attend}
                    disabled={context.attended || context.dashboardStore.busy}
                  >
                    {context.attended ? "오늘 출석 완료" : "오늘 출석하기"}
                    <ArrowRight size={15} />
                  </button>
                </div>
              </div>
              <div className="tip-row">
                <Trophy />
                <div>
                  <h3>금연 여정 달성하기</h3>
                  <p>금연 시간이 쌓일 때마다 보상 포인트가 지급돼요.</p>
                  <button className="text-button" onClick={() => context.go("journey")}>
                    나의 여정 보기
                    <ArrowRight size={15} />
                  </button>
                </div>
              </div>
              <h2 className="section-title spaced">최근 포인트 내역</h2>
              {data.transactions.slice(0, 6).map((t, i) => (
                <div className="transaction" key={i}>
                  <div>
                    {t.label}
                    <small>{t.date}</small>
                  </div>
                  <strong className={t.amount > 0 ? "green" : "muted"}>
                    {t.amount > 0 ? "+" : ""}
                    {t.amount}P
                  </strong>
                </div>
              ))}
            </section>
          </div>
        )}
      </div>
    );
  if (page === "journey")
    return (
      <div className="view-stack">
        <p className="view-description">
          20분부터 15년까지, 금연을 이어가며 기대할 수 있는 몸의 변화를 살펴보세요.
        </p>
        <div className="journey-banner">
          <Trophy size={42} strokeWidth={1.2} />
          <div>
            <h2>지금까지 {Math.floor(context.elapsed / 86400000)}일, 멋지게 걸어왔어요.</h2>
            <p>달성한 목표의 보상은 자동으로 지급돼요. 같은 기록에서는 한 번만 받을 수 있어요.</p>
          </div>
        </div>
        <div className="milestone-grid">
          {context.dashboardStore.catalog.milestones.map((m, i) => {
            const done = active?.rewards.includes(m.minutes);
            const progress = (context.elapsed / 60000 / m.minutes) * 100;
            return (
              <section className={`panel milestone ${done ? "achieved" : ""}`} key={m.minutes}>
                <div className="row">
                  <span className="milestone-number">{String(i + 1).padStart(2, "0")}</span>
                  {done ? <CheckCircle2 size={22} /> : <Trophy size={22} />}
                </div>
                <span className="milestone-duration">{m.label}</span>
                <span className="tag">
                  {m.minutes <= 1440
                    ? "단기 · 시간"
                    : m.minutes < 525600
                      ? "중기 · 일~개월"
                      : "장기 · 년"}
                </span>
                <h2>{m.title}</h2>
                <p className="milestone-description">{m.description}</p>
                <Progress value={Math.min(100, progress)} />
                <div className="row">
                  <span>
                    {done
                      ? "기준 시간 도달"
                      : "진행률 " + Math.floor(Math.min(100, progress)) + "%"}
                  </span>
                  <strong>{m.points > 0 ? `+${m.points}P` : "건강 이정표"}</strong>
                </div>
              </section>
            );
          })}
        </div>
        <section className="panel journey-guide">
          <h2>건강 변화는 사람마다 속도가 달라요</h2>
          <p>
            진행률은 금연 경과 시간을 표시하며, 실제 회복 정도나 질병 위험을 측정한 값은 아니에요.
            흡연 기간과 건강 상태에 따라 변화 시점이 달라질 수 있어요.
          </p>
          <p>
            8~12시간 단계는 12시간, 기간으로 표시한 단계는 시작 시점에 도달 처리해요. 계산에는
            1개월을 30일, 1년을 365일로 사용해요. 니코틴 대체제를 사용하는 경우 혈중 니코틴 감소
            시점은 달라져요.
          </p>
          <p>
            자료:{" "}
            <a
              href="https://www.who.int/news-room/questions-and-answers/item/tobacco-health-benefits-of-smoking-cessation"
              target="_blank"
              rel="noreferrer"
            >
              WHO 금연의 건강 효과
            </a>{" "}
            ·{" "}
            <a
              href="https://www.nhs.uk/better-health/quit-smoking/ready-to-quit-smoking/what-could-happen-when-you-quit-smoking/"
              target="_blank"
              rel="noreferrer"
            >
              NHS 금연 후 변화
            </a>{" "}
            ·{" "}
            <a
              href="https://www.cdc.gov/tobacco/about/benefits-of-quitting.html"
              target="_blank"
              rel="noreferrer"
            >
              CDC 금연의 이점
            </a>
          </p>
        </section>
      </div>
    );
  if (page === "wishlist")
    return (
      <div className="view-stack">
        <p className="view-description">
          담배 대신 나에게. 아낀 돈으로 일상의 작은 바람을 이뤄보세요.
        </p>
        <SavingsSummary context={context} />
        <div className="section-toolbar">
          <Tabs value={wishTab} onValueChange={setWishTab}>
            <TabsList>
              <TabsTrigger value="all">전체 목표</TabsTrigger>
              <TabsTrigger value="progress">모으는 중</TabsTrigger>
              <TabsTrigger value="done">달성한 목표</TabsTrigger>
            </TabsList>
          </Tabs>
          <button className="primary" onClick={() => setModal(active ? "wish" : "start")}>
            <Plus size={17} />
            목표 추가
          </button>
        </div>
        <div className="wish-grid">
          {data.wishes
            .filter(
              (w) =>
                wishTab === "all" ||
                (wishTab === "done" ? context.total >= w.price : context.total < w.price),
            )
            .map((w) => (
              <section className="panel full-wish" key={w.id}>
                <div className="row">
                  <span className="wish-icon">
                    <Heart size={27} />
                  </span>
                  <button
                    className="icon-button"
                    aria-label={`${w.name} 삭제`}
                    onClick={() =>
                      setConfirm({
                        title: "이 목표를 삭제할까요?",
                        body: "금연 기록과 절약 금액은 유지돼요.",
                        action: () => {
                          void context.run(`/wishes/${w.id}/delete`, {}, "목표를 삭제했어요.");
                        },
                      })
                    }
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
                <h2>{w.name}</h2>
                <p>
                  목표 금액 <strong>₩ {formatAmount(w.price)}</strong>
                </p>
                <div className="row">
                  <span className="green">
                    {context.total >= w.price ? "목표 달성!" : "차곡차곡 모으는 중"}
                  </span>
                  <strong>{Math.min(100, Math.floor((context.total / w.price) * 100))}%</strong>
                </div>
                <Progress value={Math.min(100, (context.total / w.price) * 100)} />
                <p className="footnote">
                  {context.total >= w.price
                    ? "나를 위한 선물, 이제 만나볼 시간이에요."
                    : `₩ ${formatAmount(w.price - context.total)} 더 모으면 목표에 도착해요.`}
                </p>
              </section>
            ))}
        </div>
        {data.wishes.filter(
          (w) =>
            wishTab === "all" ||
            (wishTab === "done" ? context.total >= w.price : context.total < w.price),
        ).length === 0 && (
          <EmptyState
            text="아직 등록된 목표가 없어요."
            action="새로운 목표 추가"
            onClick={() => setModal(active ? "wish" : "start")}
          />
        )}
      </div>
    );
  if (page === "community")
    return (
      <div className="view-stack">
        <p className="view-description">서로의 하루를 응원하며, 함께 조금 더 멀리 가요.</p>
        {context.post ? (
          <PostDetail key={context.post.id} context={context} />
        ) : (
          <>
            <div className="community-banner">
              <div>
                <span className="eyebrow">BETTER TOGETHER</span>
                <h2>
                  우리의 작은 변화가
                  <br />
                  누군가에게는 큰 응원이니까.
                </h2>
              </div>
              <Users size={72} strokeWidth={1} />
            </div>
            <div className="section-toolbar wrap">
              <Tabs value={context.category} onValueChange={context.setCategory}>
                <TabsList>
                  {["전체", "금연 일기", "꿀팁 공유", "응원해요"].map((x) => (
                    <TabsTrigger key={x} value={x}>
                      {x}
                    </TabsTrigger>
                  ))}
                </TabsList>
              </Tabs>
              <button className="primary" onClick={newPost}>
                <Pencil size={16} />
                이야기 쓰기
              </button>
            </div>
            <div className="section-toolbar">
              <label className="search-box">
                <Search size={17} />
                <input
                  aria-label="게시글 검색"
                  value={context.search}
                  onChange={(e) => context.setSearch(e.target.value)}
                  placeholder="궁금한 이야기 검색하기"
                />
              </label>
              <Select value={sort} onValueChange={setSort}>
                <SelectTrigger className="sort-select">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="latest">최신순</SelectItem>
                  <SelectItem value="popular">인기순</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <section className="panel post-list">
              {data.posts
                .filter(
                  (p) =>
                    !p.deleted &&
                    (context.category === "전체" || p.category === context.category) &&
                    (p.title + p.content + p.name).includes(context.search),
                )
                .sort((a, b) => (sort === "latest" ? b.created - a.created : b.likes - a.likes))
                .map((p) => (
                  <button key={p.id} onClick={() => context.openPost(p)} className="post-list-item">
                    <div>
                      <span className="category">{p.category}</span>
                      <h2>{p.title}</h2>
                      <p>{p.content}</p>
                      <div className="post-meta">
                        <span className="avatar">{p.name[0]}</span>
                        {p.name}
                        <span>· {formatDate(p.created)}</span>
                        {p.edited && <span className="small muted">수정됨</span>}
                      </div>
                    </div>
                    <div className="post-counts">
                      <span>
                        <Heart size={16} />
                        {p.likes}
                      </span>
                      <span>
                        <MessageCircle size={16} />
                        {p.comments.length}
                      </span>
                      <span>조회 {p.views}</span>
                    </div>
                  </button>
                ))}
              {!data.posts.some(
                (p) =>
                  !p.deleted &&
                  (context.category === "전체" || p.category === context.category) &&
                  (p.title + p.content + p.name).includes(context.search),
              ) && <EmptyState text="검색 결과가 없어요. 다른 단어로 찾아보세요." />}
            </section>
          </>
        )}
      </div>
    );
  if (page === "admin")
    return (
      <AdminGate>
        <AdminDemo context={context} />
      </AdminGate>
    );
  if (page === "profile") return <AccountPanel go={context.go} />;
  return null;
}
