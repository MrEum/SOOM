"use client";

import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { useAuth } from "@/features/auth/AuthProvider";
import { ArrowUp, Bell, Coins, Leaf, Wind } from "lucide-react";
import { useState } from "react";

type NavigationProps = { page: string; go: (page: string) => void };

export function SiteHeader({
  page,
  go,
  points = 0,
  unreadCount = 0,
  onNotifications,
  guest = false,
}: NavigationProps & {
  unreadCount?: number;
  points?: number;
  onNotifications?: () => void;
  guest?: boolean;
}) {
  const { user } = useAuth();
  return (
    <header className="site-header">
      <div className="site-header-inner">
        <div className="site-header-brand">
          {!guest && <SidebarTrigger className="mobile-trigger" aria-label="메뉴 열기" />}
          <a
            href="#dashboard"
            onClick={() => go("dashboard")}
            aria-label="숨 홈"
            className="site-logo"
          >
            <span>
              <Wind size={23} />
            </span>
            <strong>숨</strong>
            <small>soom</small>
          </a>
        </div>
        <nav className="site-header-nav" aria-label="주요 메뉴">
          {[
            { id: "dashboard", label: "나의 하루" },
            { id: "journey", label: "금연 여정" },
            { id: "community", label: "커뮤니티" },
          ].map((item) => (
            <a
              key={item.id}
              href={`#${item.id}`}
              onClick={() => go(item.id)}
              aria-current={page === item.id ? "page" : undefined}
            >
              {item.label}
            </a>
          ))}
        </nav>
        <div className="site-header-actions">
          {guest ? (
            <>
              <a className="header-login" href="#login" onClick={() => go("login")}>
                로그인
              </a>
              <a className="header-signup" href="#signup" onClick={() => go("signup")}>
                회원가입
              </a>
            </>
          ) : (
            <>
              <a
                title="포인트 내역 보기"
                href="#tree"
                onClick={() => go("tree")}
                className="header-points"
                aria-label={`보유 포인트 ${points.toLocaleString("ko-KR")}P, 내역 보기`}
              >
                <Coins size={17} />
                <span>{points.toLocaleString("ko-KR")} P</span>
              </a>
              <button
                className="header-notifications"
                aria-label={
                  unreadCount > 0 ? `알림 보기, 읽지 않은 알림 ${unreadCount}개` : "알림 보기"
                }
                onClick={onNotifications}
              >
                <Bell size={20} />
                {unreadCount > 0 && (
                  <span className="notification-badge" aria-hidden="true">
                    {unreadCount > 99 ? "99+" : unreadCount}
                  </span>
                )}
              </button>
              <a
                href="#profile"
                onClick={() => go("profile")}
                className="header-profile"
                aria-label="마이페이지"
              >
                <span className="profile-avatar small-avatar">숨</span>
                <span>{user?.nickname ?? "방문자"}</span>
              </a>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

export function SiteFooter({ go }: Pick<NavigationProps, "go">) {
  const [info, setInfo] = useState<"about" | "data" | null>(null);
  const link = (id: string, title: string) => (
    <a href={`#${id}`} onClick={() => go(id)}>
      {title}
    </a>
  );
  return (
    <>
      <footer className="site-footer">
        <div className="site-footer-inner">
          <div className="site-footer-top">
            <div className="footer-intro">
              <a
                href="#dashboard"
                onClick={() => go("dashboard")}
                className="site-logo"
                aria-label="숨 홈"
              >
                <span>
                  <Wind size={23} />
                </span>
                <strong>숨</strong>
                <small>soom</small>
              </a>
              <p>
                조금 더 가벼운 숨,
                <br />
                조금 더 건강한 내일.
              </p>
              <span className="footer-signature">
                <Leaf size={14} />
                당신의 작은 변화를 함께해요.
              </span>
            </div>
            <nav className="footer-link-group" aria-label="나의 금연 메뉴">
              <h2>나의 금연</h2>
              {link("records", "금연 기록")}
              {link("tree", "나의 금연 나무")}
              {link("wishlist", "위시리스트")}
            </nav>
            <nav className="footer-link-group" aria-label="함께하는 숨 메뉴">
              <h2>함께하는 숨</h2>
              {link("journey", "금연 여정")}
              {link("community", "커뮤니티")}
              <button onClick={() => setInfo("about")}>서비스 안내</button>
            </nav>
          </div>
          <div className="site-footer-bottom">
            <p>© {new Date().getFullYear()} SOOM. All rights reserved.</p>
            <div>
              <button onClick={() => setInfo("data")}>데이터 저장 안내</button>
              <span className="footer-demo">SOOM</span>
              <button
                className="back-to-top"
                onClick={() =>
                  window.scrollTo({
                    top: 0,
                    behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
                      ? "instant"
                      : "smooth",
                  })
                }
                aria-label="페이지 맨 위로"
              >
                <ArrowUp size={16} />
                <span>맨 위로</span>
              </button>
            </div>
          </div>
        </div>
      </footer>
      <Dialog open={info !== null} onOpenChange={(open) => !open && setInfo(null)}>
        <DialogContent className="soom-dialog">
          <DialogTitle>
            {info === "about" ? "나를 위한 건강한 습관, 숨" : "데이터 저장 안내"}
          </DialogTitle>
          <DialogDescription>
            {info === "about"
              ? "오늘의 작은 선택을 건강한 습관으로 이어가세요."
              : "회원과 비회원의 금연 기록 저장 방식이 다릅니다."}
          </DialogDescription>
          <div className="help-text">
            {info === "about" ? (
              <>
                <p>
                  금연 시간과 절약 금액을 확인하고, 매일 출석과 여정 보상으로 나만의 나무를
                  키워보세요.
                </p>
                <p>
                  위시리스트로 나에게 줄 선물을 준비하고 커뮤니티에서 서로의 하루를 응원할 수
                  있어요.
                </p>
              </>
            ) : (
              <>
                <p>
                  비회원은 이 브라우저에 현재 금연 기록 하나만 저장합니다. 리셋하면 이전 기록은 남지
                  않습니다. 회원 금연 기록은 로그인한 계정에 저장됩니다.
                </p>
                <p>
                  로그인한 계정에 진행 중인 금연 기록이 없으면, 안내 팝업에서 동의한 뒤 현재 비회원
                  기록을 가져올 수 있습니다. 브라우저 데이터를 삭제하면 이전하지 않은 비회원 기록은
                  사라집니다. 회원 출석·포인트·금연 나무·커뮤니티는 서버에 저장됩니다. 금연 나무는
                  회원 전용입니다.
                </p>
              </>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
