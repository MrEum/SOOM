"use client";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarProvider,
  useSidebar,
} from "@/components/ui/sidebar";
import { useAuth } from "@/features/auth/AuthProvider";
import { AuthScreen } from "@/features/auth/AuthScreen";
import { RecoveryScreen } from "@/features/auth/RecoveryScreen";
import { useDashboard } from "@/features/dashboard/useDashboard";
import { useQuitRecords, type QuitRecord } from "@/features/records/useQuitRecords";
import {
  ArrowRight,
  CalendarDays,
  Check,
  ChevronRight,
  CigaretteOff,
  CircleHelp,
  Coins,
  Droplets,
  Flame,
  Heart,
  LayoutDashboard,
  Leaf,
  Plus,
  RefreshCw,
  Settings,
  ShieldCheck,
  Sprout,
  TreeDeciduous,
  Trophy,
  Users,
  Wallet,
  Wind,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Toaster, toast } from "sonner";
import { AppDialogs } from "./AppDialogs";
import type { AppData, Post, Wish } from "./appTypes";
import { FeaturePages } from "./FeaturePages";
import { calculateSavings, formatAmount } from "./formatters";
import { SiteFooter, SiteHeader } from "./SiteLayout";
function NavAction({ onClick, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const { setOpenMobile } = useSidebar();
  return (
    <button
      {...props}
      onClick={(e) => {
        onClick?.(e);
        setOpenMobile(false);
      }}
    />
  );
}
const navigation = [
  { id: "dashboard", name: "대시보드", icon: LayoutDashboard },
  { id: "records", name: "나의 금연 기록", icon: CalendarDays },
  { id: "tree", name: "나의 금연 나무", icon: Sprout },
  { id: "journey", name: "금연 여정", icon: Trophy },
  { id: "wishlist", name: "위시리스트", icon: Heart },
  { id: "community", name: "커뮤니티", icon: Users },
];

const dayKey = () => new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });

function initialData(): AppData {
  return { records: [], points: 0, wishes: [], posts: [], reports: [], transactions: [] };
}

function Meter({ value }: { value: number }) {
  return (
    <Progress
      value={Math.min(100, Math.max(0, value))}
      aria-label={`달성률 ${Math.round(value)}%`}
      className="meter"
    />
  );
}
function Heading({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
      {children}
    </div>
  );
}
function PanelTitle({
  title,
  sub,
  action,
  onClick,
}: {
  title: string;
  sub?: string;
  action?: string;
  onClick?: () => void;
}) {
  return (
    <div className="panel-title">
      <div>
        <h2>{title}</h2>
        {sub && <p>{sub}</p>}
      </div>
      {action && (
        <button className="text-button" onClick={onClick}>
          {action}
          <ChevronRight size={16} />
        </button>
      )}
    </div>
  );
}
export default function App() {
  const { user: authUser, error: authError } = useAuth();
  const recordStore = useQuitRecords();
  const dashboardStore = useDashboard(
    recordStore.records,
    ["ready", "empty"].includes(recordStore.status),
  );
  const quotes = dashboardStore.catalog.quotes;
  const [guestStarting, setGuestStarting] = useState(false);
  const [page, setPage] = useState("login"),
    [extras, setData] = useState<AppData>(initialData),
    [now, setNow] = useState(Date.now()),
    [quote, setQuote] = useState(0),
    [modal, setModal] = useState(""),
    [selected, setSelected] = useState<number | null>(null),
    [search, setSearch] = useState(""),
    [category, setCategory] = useState("전체"),
    [reply, setReply] = useState<number | undefined>(),
    [editing, setEditing] = useState<Post | null>(null),
    [images, setImages] = useState<string[]>([]);
  const data: AppData = {
    ...dashboardStore.data,
    ...dashboardStore.feed,
    records: recordStore.records.map((record) => ({
      ...record,
      ...dashboardStore.data.recordDetails.find((detail) => detail.id === record.id),
    })),
  };
  const run = async (
    path: string,
    input: Record<string, unknown> = {},
    message = "",
    community = false,
  ) => {
    try {
      await dashboardStore.saveChanges(path, input, community);
      if (message) toast.success(message);
      return true;
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "저장하지 못했어요.");
      return false;
    }
  };
  const [editRecord, setEditRecord] = useState<QuitRecord | null>(null);
  useEffect(() => {
    setModal("");
    setEditRecord(null);
    setData(initialData());
  }, [authUser?.userId]);
  const [confirm, setConfirm] = useState<{
    title: string;
    body: string;
    action: () => void;
  } | null>(null);
  useEffect(() => {
    const handler = () => {
      const hash =
        location.hash.slice(1) || (location.pathname === "/reset-password" ? "reset-password" : "");
      setPage(
        [
          ...navigation.map((n) => n.id),
          "admin",
          "profile",
          "login",
          "signup",
          "admin-login",
          "forgot-password",
          "find-id",
          "reset-password",
        ].includes(hash)
          ? hash
          : "login",
      );
    };
    handler();
    window.addEventListener("hashchange", handler);
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      clearInterval(timer);
      window.removeEventListener("hashchange", handler);
    };
  }, []);

  const formatDate = new Date(now).toDateString();
  useEffect(() => {
    setQuote(new Date(formatDate).getDate() % Math.max(1, quotes.length));
  }, [formatDate, quotes.length]);
  useEffect(() => {
    if (
      !guestStarting ||
      authUser ||
      recordStore.owner !== "guest" ||
      !["empty", "ready"].includes(recordStore.status)
    )
      return;
    setGuestStarting(false);
    if (recordStore.status === "empty") {
      setEditRecord(null);
      setModal("start");
    }
  }, [guestStarting, authUser, recordStore.owner, recordStore.status]);
  const go = (id: string) => {
    if (id === "guest-start") {
      setGuestStarting(true);
      id = "dashboard";
    }
    if (location.pathname === "/reset-password") window.history.replaceState(null, "", "/");
    setPage(id);
    location.hash = id;
    setSelected(null);
    setSearch("");
  };
  const active = data.records.find((r) => !r.end),
    elapsed = active ? Math.max(0, now - active.start) : 0,
    days = Math.floor(elapsed / 86400000),
    hours = Math.floor(elapsed / 3600000) % 24,
    minutes = Math.floor(elapsed / 60000) % 60,
    savings = active ? calculateSavings(active, now) : 0,
    total = data.records.reduce((s, r) => s + calculateSavings(r, now), 0),
    attended = !!active?.attendance.includes(dayKey());
  const attend = () => {
    if (!active) {
      setModal("start");
      return;
    }
    void run("/attendance", { recordId: active.id }, "오늘의 출석을 저장했어요.");
  };
  const nextMilestone = dashboardStore.catalog.milestones.find((m) => elapsed < m.minutes * 60000);
  const nurture = (cost: number, _growth: number) => {
    if (!authUser) {
      go("login");
      return;
    }
    if (!active) {
      setModal("start");
      return;
    }
    void run(
      "/tree",
      { recordId: active.id, kind: cost === 30 ? "water" : "fertilizer" },
      "나무가 자랐어요!",
    );
  };
  const openPost = (p: Post) => {
    void run("/" + p.id + "/view", {}, "", true).then((ok) => {
      if (ok) {
        setSelected(p.id);
        setPage("community");
        location.hash = "community";
      }
    });
  };
  const post = data.posts.find((p) => p.id === selected && !p.deleted),
    mask = (s: string) => s.replace(/씨발|시발|병신|개새끼/g, "████");
  const currentLabel =
    navigation.find((n) => n.id === page)?.name ??
    (
      {
        admin: "관리자",
        profile: "마이페이지",
        login: "로그인",
        signup: "회원가입",
      } as Record<string, string>
    )[page];
  const summaryRef = useRef({ days: 0, savings: 0, points: 0, active: false });
  summaryRef.current = {
    days,
    savings: Math.floor(savings),
    points: data.points,
    active: !!active,
  };
  useEffect(() => {
    const context = (
      document as Document & {
        modelContext?: {
          registerTool: (
            tool: unknown,
            options: {
              signal: AbortSignal;
            },
          ) => void | Promise<void>;
        };
      }
    ).modelContext;
    if (!context) return;
    const lifecycle = new AbortController();
    Promise.resolve(
      context.registerTool(
        {
          name: "read_quit_summary",
          title: "금연 현황 확인",
          description:
            "실제 조회된 금연 일수와 절약 금액을 조회합니다. 데이터를 변경하지 않습니다.",
          inputSchema: {
            type: "object",
            properties: {},
            additionalProperties: false,
          },
          annotations: { readOnlyHint: true },
          execute: (input: unknown) => {
            if (
              !input ||
              typeof input !== "object" ||
              Array.isArray(input) ||
              Object.keys(input).length
            )
              throw new Error("빈 객체를 입력해주세요.");
            return { ...summaryRef.current };
          },
        },
        { signal: lifecycle.signal },
      ),
    ).catch(() => {});
    return () => lifecycle.abort();
  }, []);
  const context = {
    recordStore,
    editRecord,
    setEditRecord,
    dashboardStore,
    run,
    data,
    setData,
    now,
    active,
    elapsed,
    savings,
    total,
    go,
    setModal,
    setConfirm,
    selected,
    setSelected,
    post,
    openPost,
    search,
    setSearch,
    category,
    setCategory,
    reply,
    setReply,
    editing,
    setEditing,
    images,
    setImages,
    mask,
    nurture,
    attend,
    attended,
  };
  const TreeView = ({ large = false }: { large?: boolean }) => (
    <div className={`tree-visual ${large ? "large" : ""}`}>
      <span className="tree-level">LEVEL {Math.floor((active?.growth ?? 0) / 25) + 1}</span>
      <TreeDeciduous className="tree-icon" strokeWidth={1.15} />
      <span className="tree-ground" />
      <span className="tree-spark spark-one">✦</span>
      <span className="tree-spark spark-two">✧</span>
      <span className="tree-caption">
        {(active?.growth ?? 0) >= 100 ? "무럭무럭, 완성된 나무" : "당신과 함께 자라는 중"}
      </span>
    </div>
  );
  const WishCard = ({ wish }: { wish: Wish }) => (
    <div className="wish-item">
      <span className="wish-icon">
        <Heart size={24} />
      </span>
      <div className="wish-info">
        <div className="row">
          <strong>{wish.name}</strong>
          <span className="green">{Math.min(100, Math.floor((total / wish.price) * 100))}%</span>
        </div>
        <Meter value={(total / wish.price) * 100} />
        <div className="row muted small">
          <span>
            ₩ {formatAmount(total)} <span className="light">/ {formatAmount(wish.price)}</span>
          </span>
          <span>{total >= wish.price ? "목표 달성!" : "차곡차곡 모으는 중"}</span>
        </div>
      </div>
    </div>
  );
  const dashboard = (
    <>
      <Heading
        eyebrow="MY SMOKE-FREE LIFE"
        title={`좋은 하루예요, ${authUser?.nickname ?? "방문자"}님 👋`}
      >
        <span className="date-label">
          <CalendarDays size={16} />
          {new Date(now).toLocaleDateString("ko-KR", {
            year: "numeric",
            month: "long",
            day: "numeric",
            weekday: "long",
          })}
        </span>
      </Heading>
      <p className="intro">오늘도 나를 위한 선택, 숨과 함께 이어가요.</p>

      <div className="dashboard-top">
        <section className="quit-hero">
          <div className="row">
            <span className="pill">
              <span className="status-dot" />
              {active ? "지금, 건강해지는 중" : "새로운 시작을 기다려요"}
            </span>
            <Wind size={26} />
          </div>
          <p className="hero-label">담배 없이, 더 자유롭게</p>
          <div className="quit-time">
            <strong>
              {days}
              <small>일</small>
            </strong>
            <span>
              {hours}
              <small>시간</small>
            </span>
            <span>
              {minutes}
              <small>분</small>
            </span>
          </div>
          <p className="hero-start">
            {active
              ? `${new Date(active.start).toLocaleDateString("ko-KR")}부터 이어온 소중한 변화`
              : "오늘부터 나를 위한 한 걸음을 시작해요."}
          </p>
          <div className="hero-bottom">
            <div>
              <span>
                {nextMilestone
                  ? `다음 목표 · ${nextMilestone.label}`
                  : dashboardStore.catalog.milestones.length
                    ? "모든 여정의 기준 시간에 도달했어요"
                    : "금연 여정 불러오는 중"}
              </span>
              <Meter
                value={
                  nextMilestone
                    ? (elapsed / (nextMilestone.minutes * 60000)) * 100
                    : dashboardStore.catalog.milestones.length
                      ? 100
                      : 0
                }
              />
            </div>
            <button onClick={() => (active ? go("journey") : setModal("start"))}>
              {active ? "여정 보기" : "금연 시작"}
              <ArrowRight size={17} />
            </button>
          </div>
        </section>
        <section className="panel attendance">
          <div className="row">
            <span className="icon-box orange">
              <Flame size={23} />
            </span>
            <span className="tag">매일 +50P</span>
          </div>
          <h2>오늘도 금연, 체크!</h2>
          <p>작은 약속이 쌓여 큰 변화가 돼요.</p>
          <div className="week">
            {["월", "화", "수", "목", "금", "토", "일"].map((d, i) => {
              const dt = new Date(
                new Date(now).toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" }) +
                  "T00:00:00Z",
              );
              dt.setUTCDate(dt.getUTCDate() - ((dt.getUTCDay() + 6) % 7) + i);
              const attendanceDate = dt.toISOString().slice(0, 10);
              const today = attendanceDate === dayKey(),
                done = active?.attendance.includes(attendanceDate);
              return (
                <div key={d}>
                  <span>{d}</span>
                  <span className={`day-circle ${done ? "past" : ""} ${today ? "today" : ""}`}>
                    {done ? <Check size={15} /> : today ? <Sprout size={17} /> : <span>·</span>}
                  </span>
                </div>
              );
            })}
          </div>
          <button
            className="primary wide"
            onClick={attend}
            disabled={attended || dashboardStore.busy || dashboardStore.status !== "ready"}
          >
            {attended ? (
              <>
                <Check size={18} />
                오늘 출석 완료
              </>
            ) : (
              <>
                오늘의 출석 체크
                <ArrowRight size={17} />
              </>
            )}
          </button>
        </section>
      </div>
      <div className="stats-grid">
        <section className="panel stat">
          <div className="row">
            <span>현재 절약 금액</span>
            <span className="icon-box mint">
              <Wallet size={20} />
            </span>
          </div>
          <strong>₩ {formatAmount(savings)}</strong>
          <p>
            <span className="green">차곡차곡</span> 나를 위해 모인 돈
          </p>
        </section>
        <section className="panel stat">
          <div className="row">
            <span>피우지 않은 담배</span>
            <span className="icon-box blue">
              <CigaretteOff size={20} />
            </span>
          </div>
          <strong>
            {active ? Math.floor((elapsed / 86400000) * active.daily) : 0}
            <small>개비</small>
          </strong>
          <p>담배 대신 건강을 선택했어요</p>
        </section>
        <section className="panel stat">
          <div className="row">
            <span>나의 보유 포인트</span>
            <span className="icon-box yellow">
              <Coins size={20} />
            </span>
          </div>
          <strong>
            {formatAmount(data.points)}
            <small>P</small>
          </strong>
          <p>출석과 금연 여정으로 모은 포인트</p>
        </section>
      </div>
      <section className="encouragement">
        <span className="quote-mark">“</span>
        <div>
          <span>오늘의 응원</span>
          <p>{quotes[quote] ?? "등록된 응원 문구가 없습니다."}</p>
        </div>
        <button
          disabled={quotes.length < 2}
          aria-label="다른 응원글 보기"
          className="icon-button"
          onClick={() =>
            setQuote(
              (q) => (q + 1 + Math.floor(Math.random() * (quotes.length - 1))) % quotes.length,
            )
          }
        >
          <RefreshCw size={18} />
        </button>
      </section>
      <div className="dashboard-middle">
        <section className="panel tree-panel">
          <PanelTitle title="나의 금연 나무" action="내 나무 보러가기" onClick={() => go("tree")} />
          {!authUser ? (
            <div className="empty-state">
              <Sprout size={40} />
              <p>로그인하고 나만의 나무를 키워보세요.</p>
              <button className="primary" onClick={() => go("login")}>
                로그인하기
              </button>
            </div>
          ) : (
            <div className="tree-content">
              <TreeView />
              <div className="tree-copy">
                <span className="tag green-tag">
                  {(active?.growth ?? 0) >= 100 ? "다 자란 나무" : "작은 나무"}
                </span>
                <h3>
                  함께 자라는
                  <br />
                  건강한 하루
                </h3>
                <p>
                  꾸준한 마음에 물을 주세요.
                  <br />
                  당신의 나무가 자라고 있어요.
                </p>
                <div className="row small">
                  <span>성장률</span>
                  <strong className="green">{active?.growth ?? 0}%</strong>
                </div>
                <Meter value={active?.growth ?? 0} />
                <button
                  className="soft-button"
                  onClick={() => nurture(30, 5)}
                  disabled={
                    active?.growth === 100 ||
                    dashboardStore.busy ||
                    dashboardStore.status !== "ready"
                  }
                >
                  <Droplets size={16} />물 주기 <span>30P</span>
                </button>
              </div>
            </div>
          )}
        </section>
        <section className="panel wishlist-panel">
          <PanelTitle
            title="금연으로 이루는 위시리스트"
            action="전체보기"
            onClick={() => go("wishlist")}
          />
          <p className="muted small">아낀 담뱃값이 나에게 주는 선물이 돼요.</p>
          {data.wishes.slice(0, 2).map((w) => (
            <WishCard key={w.id} wish={w} />
          ))}
          <button className="add-wish" onClick={() => setModal(active ? "wish" : "start")}>
            <Plus size={17} />
            새로운 목표 추가하기
          </button>
        </section>
      </div>
      <section className="panel community-preview">
        <PanelTitle
          title="혼자가 아닌, 함께하는 금연"
          action="커뮤니티 전체보기"
          onClick={() => go("community")}
        />
        <div className="post-preview-grid">
          {data.posts
            .filter((p) => !p.deleted)
            .slice(0, 3)
            .map((p, i) => (
              <button className="post-preview" key={p.id} onClick={() => openPost(p)}>
                <span className={`category category-${i}`}>{p.category}</span>
                <h3>{p.title}</h3>
                <p>{p.content}</p>
                <div className="post-meta">
                  <span className={`avatar avatar-${i}`}>{p.name.slice(0, 1)}</span>
                  <span>{p.name}</span>
                  <span className="post-likes">
                    <Heart size={13} />
                    {p.likes}
                  </span>
                </div>
              </button>
            ))}
        </div>
      </section>
    </>
  );
  const recordNotice = (
    <section className="panel empty-state" aria-live="polite">
      <Sprout size={40} />
      {recordStore.status === "initial" || recordStore.status === "loading" ? (
        <p>금연 기록을 확인하고 있어요.</p>
      ) : recordStore.status === "error" ? (
        <>
          <p role="alert">{recordStore.error}</p>
          <button className="primary" onClick={() => void recordStore.refresh()}>
            다시 시도
          </button>
        </>
      ) : (
        <>
          <h2>
            {recordStore.records.length
              ? "이전 금연 기록이 보관되어 있어요"
              : "나만의 금연 여정을 시작해 보세요"}
          </h2>
          <p>
            {recordStore.records.length
              ? "새 기록을 시작하거나 과거 기록을 확인할 수 있어요."
              : "금연 기록을 설정하면 경과 시간과 절약 금액을 확인할 수 있어요."}
          </p>
          <div className="row">
            {!authUser && (
              <button className="outline-button" onClick={() => go("login")}>
                로그인하러 가기
              </button>
            )}
            <button
              className="primary"
              onClick={() => {
                setEditRecord(null);
                setModal("start");
              }}
            >
              {authUser ? "금연 기록 설정하기" : "비회원으로 시작하기"}
            </button>
            {!!recordStore.records.length && (
              <button className="outline-button" onClick={() => go("records")}>
                과거 기록 보기
              </button>
            )}
          </div>
        </>
      )}
    </section>
  );
  if (
    ["login", "signup", "admin-login", "forgot-password", "find-id", "reset-password"].includes(
      page,
    )
  )
    return (
      <div className="guest-shell">
        <SiteHeader page={page} go={go} guest />
        {["find-id", "forgot-password", "reset-password"].includes(page) ? (
          <RecoveryScreen key={page} page={page} go={go} />
        ) : (
          <AuthScreen page={page} go={go} />
        )}
        <SiteFooter go={go} />
        <Toaster richColors />
      </div>
    );
  return (
    <SidebarProvider>
      <Sidebar className="app-sidebar">
        <SidebarHeader>
          <a href="#dashboard" onClick={() => go("dashboard")} className="brand">
            <span className="brand-mark">
              <Wind size={27} />
            </span>
            <strong>숨</strong>
            <span>soom</span>
          </a>
          <p className="brand-caption">나를 위한, 가장 건강한 습관</p>
        </SidebarHeader>
        <SidebarContent>
          <p className="nav-section-label">MY JOURNEY</p>
          <nav>
            {navigation.map((n) => (
              <NavAction
                key={n.id}
                className={`nav-item ${page === n.id ? "active" : ""}`}
                onClick={() => go(n.id)}
              >
                <n.icon size={20} />
                <span>{n.name}</span>
                {page === n.id && <span className="nav-active-dot" />}
              </NavAction>
            ))}
          </nav>
          <div className="sidebar-note">
            <span className="icon-box mint">
              <Leaf size={21} />
            </span>
            <strong>작은 시작, 큰 변화</strong>
            <p>
              오늘의 한 걸음이
              <br />
              내일의 나를 바꿔요.
            </p>
            <NavAction onClick={() => go("journey")}>
              나의 여정 살펴보기
              <ArrowRight size={15} />
            </NavAction>
          </div>
        </SidebarContent>
        <SidebarFooter>
          {authUser?.role === "ADMIN" && (
            <NavAction className="nav-item" onClick={() => go("admin")}>
              <ShieldCheck size={19} />
              관리자 데모
            </NavAction>
          )}
          <NavAction className="nav-item" onClick={() => setModal("help")}>
            <CircleHelp size={19} />
            도움말
          </NavAction>
          <NavAction className="sidebar-profile" onClick={() => go("profile")}>
            <span className="profile-avatar">숨</span>
            <span>
              <strong>{authUser?.nickname ?? "방문자"}</strong>
              <small>오늘도 한 걸음씩 🌱</small>
            </span>
            <Settings size={17} />
          </NavAction>
        </SidebarFooter>
      </Sidebar>
      <div className="app-main">
        <SiteHeader
          page={page}
          go={go}
          guest={!authUser}
          points={data.points}
          unreadCount={dashboardStore.data.unreadCount}
          onNotifications={() => {
            setModal("notifications");
            if (!dashboardStore.busy) void dashboardStore.refresh();
          }}
        />
        <main className="content">
          {authUser && <p className="muted small">회원 · 금연 기록은 계정에 저장됩니다.</p>}
          {authError && <p role="alert">{authError}</p>}

          <Dialog
            open={recordStore.pending.length > 0 && !modal && !confirm}
            onOpenChange={(open) => {
              if (!open && !recordStore.busy) recordStore.dismiss();
            }}
          >
            <DialogContent className="soom-dialog" showCloseButton={!recordStore.busy}>
              <DialogTitle>현재 비회원 금연 기록을 가져올까요?</DialogTitle>
              <DialogDescription>
                {authUser?.nickname}님의 계정에 진행 중인 금연 기록이 없어요. 이 브라우저의 현재
                금연 기록을 계정에 저장할 수 있어요.
              </DialogDescription>
              {recordStore.pending[0] && (
                <p>금연 시작: {new Date(recordStore.pending[0].start).toLocaleString("ko-KR")}</p>
              )}
              {recordStore.transferError && <p role="alert">{recordStore.transferError}</p>}
              <button
                className="primary"
                disabled={recordStore.busy}
                onClick={() => void recordStore.transfer()}
              >
                {recordStore.busy ? "가져오는 중…" : "동의하고 기록 가져오기"}
              </button>
              <button
                className="outline-button"
                disabled={recordStore.busy}
                onClick={recordStore.dismiss}
              >
                나중에
              </button>
            </DialogContent>
          </Dialog>
          {recordStore.transferError && <p role="alert">{recordStore.transferError}</p>}
          {dashboardStore.error && (
            <p role="alert">
              {dashboardStore.error}{" "}
              <button className="text-button" onClick={() => void dashboardStore.refresh()}>
                다시 불러오기
              </button>
            </p>
          )}
          {dashboardStore.publicError && (
            <p role="alert">
              커뮤니티·응원 정보를 불러오지 못했어요.{" "}
              <button className="text-button" onClick={() => void dashboardStore.refreshPublic()}>
                다시 불러오기
              </button>
            </p>
          )}
          {page === "dashboard" ? (
            recordStore.status === "ready" && active ? (
              dashboardStore.status === "ready" ? (
                dashboard
              ) : (
                <section className="panel empty-state">
                  <p>
                    {dashboardStore.status === "error"
                      ? "대시보드 정보를 불러오지 못했어요."
                      : "대시보드 정보를 불러오고 있어요."}
                  </p>
                  <button className="primary" onClick={() => void dashboardStore.refresh()}>
                    다시 불러오기
                  </button>
                </section>
              )
            ) : !authUser && ["empty", "ready"].includes(recordStore.status) ? (
              <>
                <Heading eyebrow="MY SMOKE-FREE LIFE" title="나의 금연 기록" />
                <FeaturePages page="records" {...context} />
              </>
            ) : (
              recordNotice
            )
          ) : (
            <>
              <Heading
                eyebrow={page === "admin" ? "ADMIN WORKSPACE" : "MY SMOKE-FREE LIFE"}
                title={currentLabel || "대시보드"}
              />
              {["records", "tree", "journey", "wishlist"].includes(page) &&
              !["ready", "empty"].includes(recordStore.status) ? (
                recordNotice
              ) : (
                <FeaturePages page={page} {...context} />
              )}
            </>
          )}
        </main>
        <SiteFooter go={go} />
      </div>
      <Toaster richColors position="bottom-right" />
      <AppDialogs modal={modal} confirm={confirm} {...context} />
    </SidebarProvider>
  );
}
