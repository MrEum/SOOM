import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "숨 — 나를 위한 건강한 습관",
  description: "금연 기록, 나무 키우기, 위시리스트로 함께 만드는 건강한 하루",
  other: {
    "codex-preview": "development",
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body className="antialiased">{children}</body>
    </html>
  );
}
