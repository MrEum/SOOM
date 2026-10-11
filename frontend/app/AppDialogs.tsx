import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ImagePlus, X } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";

import type { AppContext, ConfirmDialogState } from "@/app/appContext";

import { QuitRecordForm } from "@/features/records/QuitRecordForm";
export function AppDialogs({
  modal,
  confirm,
  ...context
}: AppContext & {
  modal: string;
  confirm: ConfirmDialogState | null;
}) {
  const [busy, setBusy] = useState(false);
  const uploadRef = useRef<HTMLInputElement>(null);
  const close = () => {
    context.setModal("");
    context.setEditRecord(null);
  };
  const titles: Record<string, string> = {
    start: "나를 위한 금연, 시작해볼까요?",
    wish: "나에게 선물할 새로운 목표",
    post: context.editing ? "이야기 수정하기" : "우리에게 들려주고 싶은 이야기",
    help: "숨과 함께하는 금연",
    notifications: "나의 알림",
  };
  return (
    <>
      <Dialog open={!!modal} onOpenChange={(v) => !v && close()}>
        <DialogContent className="soom-dialog">
          <DialogTitle>
            {modal.startsWith("report:") ? "신고하기" : (titles[modal] ?? "안내")}
          </DialogTitle>
          <DialogDescription>
            {modal === "start"
              ? "작은 결심을 오래 이어갈 수 있도록 숨이 함께할게요."
              : modal === "post"
                ? "서로를 배려하는 따뜻한 이야기를 나눠주세요."
                : "작은 변화부터 차근차근 시작해요."}
          </DialogDescription>
          {modal === "start" && (
            <QuitRecordForm key={context.editRecord?.id ?? "new"} context={context} close={close} />
          )}
          {modal === "wish" && (
            <form
              className="form-stack"
              onSubmit={async (e) => {
                e.preventDefault();
                if (!context.active) {
                  context.setModal("start");
                  return;
                }
                const f = new FormData(e.currentTarget);
                if (
                  await context.run(
                    "/wishes",
                    {
                      recordId: context.active.id,
                      name: String(f.get("name")).trim(),
                      price: Number(f.get("price")),
                    },
                    "새로운 목표를 저장했어요.",
                  )
                )
                  close();
              }}
            >
              <label>
                어떤 선물을 하고 싶나요?
                <input
                  name="name"
                  placeholder="예: 가벼운 발걸음을 위한 운동화"
                  maxLength={60}
                  required
                />
              </label>
              <label>
                목표 금액 (원)
                <input
                  name="price"
                  type="number"
                  min="1"
                  max="1000000000"
                  placeholder="159000"
                  required
                />
              </label>
              <p className="footnote">모든 금연 기록의 누적 절약 금액으로 달성률을 계산해요.</p>
              <button className="primary wide" disabled={context.dashboardStore.busy} type="submit">
                목표 추가하기
              </button>
            </form>
          )}
          {modal === "post" && (
            <form
              className="form-stack"
              onSubmit={async (e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                if (
                  await context.run(
                    "/posts",
                    {
                      id: context.editing?.id ?? null,
                      title: context.mask(String(f.get("title")).trim()),
                      content: context.mask(String(f.get("content")).trim()),
                      category: String(f.get("category")),
                      images: context.images,
                    },
                    context.editing ? "이야기를 수정했어요." : "이야기를 등록했어요.",
                    true,
                  )
                ) {
                  context.setSelected(null);
                  close();
                  context.go("community");
                }
              }}
            >
              <label>
                이야기 종류
                <Select name="category" defaultValue={context.editing?.category ?? "금연 일기"}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {["금연 일기", "꿀팁 공유", "응원해요"].map((x) => (
                      <SelectItem key={x} value={x}>
                        {x}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
              <label>
                제목
                <input
                  name="title"
                  defaultValue={context.editing?.title}
                  placeholder="어떤 하루를 보내셨나요?"
                  maxLength={120}
                  required
                />
              </label>
              <label>
                내용
                <textarea
                  name="content"
                  defaultValue={context.editing?.content}
                  placeholder="나의 이야기와 응원을 나눠주세요."
                  rows={6}
                  maxLength={5000}
                  required
                />
              </label>
              <input
                ref={uploadRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                hidden
                onChange={async (e) => {
                  const files = Array.from(e.target.files ?? []);
                  if (context.images.length + files.length > 4) {
                    toast.error("사진은 최대 4장까지 올릴 수 있어요.");
                    return;
                  }
                  if (
                    files.some(
                      (f) =>
                        f.size > 500000 ||
                        !["image/jpeg", "image/png", "image/webp"].includes(f.type),
                    )
                  ) {
                    toast.error("JPG, PNG, WebP 사진을 장당 500KB 이하로 올려주세요.");
                    return;
                  }
                  setBusy(true);
                  try {
                    const result = await Promise.all(
                      files.map(
                        (f) =>
                          new Promise<string>((resolve, reject) => {
                            const r = new FileReader();
                            r.onload = () => resolve(String(r.result));
                            r.onerror = reject;
                            r.readAsDataURL(f);
                          }),
                      ),
                    );
                    context.setImages((old) => [...old, ...result]);
                  } catch {
                    toast.error("사진을 읽지 못했어요.");
                  } finally {
                    setBusy(false);
                  }
                }}
              />
              <div className="image-previews">
                {context.images.map((src, i) => (
                  <div key={i}>
                    <img src={src} alt={`첨부 ${i + 1}`} />
                    <button
                      type="button"
                      aria-label={`사진 ${i + 1} 삭제`}
                      onClick={() => context.setImages((old) => old.filter((_, j) => j !== i))}
                    >
                      <X size={14} />
                    </button>
                    <span>{i + 1}</span>
                  </div>
                ))}
              </div>
              <button
                type="button"
                className="outline-button"
                onClick={() => uploadRef.current?.click()}
                disabled={busy}
              >
                <ImagePlus size={17} />
                사진 첨부 ({context.images.length}/4)
              </button>
              <p className="footnote">사진은 선택 순서대로 표시돼요. 장당 최대 500KB.</p>
              <button
                className="primary wide"
                disabled={busy || context.dashboardStore.busy}
                type="submit"
              >
                {context.editing ? "수정 완료" : "이야기 등록"}
              </button>
            </form>
          )}
          {modal.startsWith("report:") && (
            <form
              className="form-stack"
              onSubmit={async (e) => {
                e.preventDefault();
                const [type, id] = modal.slice(7).split(":");
                const reason = String(new FormData(e.currentTarget).get("reason")).trim();
                if (
                  await context.run(
                    "/reports",
                    { type, id: Number(id), reason },
                    "신고가 접수됐어요.",
                    true,
                  )
                )
                  close();
              }}
            >
              <label>
                신고 사유
                <textarea
                  name="reason"
                  placeholder="확인이 필요한 내용을 알려주세요."
                  maxLength={255}
                  required
                />
              </label>
              <p className="footnote">
                신고 내역은 서버에 저장되며 같은 콘텐츠는 한 번만 신고할 수 있어요.
              </p>
              <button className="primary" type="submit">
                신고 접수
              </button>
            </form>
          )}
          {modal === "help" && (
            <div className="help-text">
              <p>
                매일 금연을 기록하고, 출석과 여정 보상으로 나만의 나무를 키워보세요. 절약한 돈은
                위시리스트의 목표를 채워줘요.
              </p>
              <p>
                회원의 기록·출석·포인트·나무·목표는 계정에 저장됩니다. 비회원의 개인 기록은 이
                브라우저에 저장되며, 나무와 커뮤니티 작성은 로그인 후 이용할 수 있어요.
              </p>
              <button
                className="outline-button"
                onClick={() => {
                  close();
                  context.go("login");
                }}
              >
                로그인 · 회원가입 화면 보기
              </button>
            </div>
          )}
          {modal === "notifications" && (
            <div className="help-text">
              {!context.dashboardStore.member ? (
                <p>로그인 후 알림을 확인할 수 있어요.</p>
              ) : context.dashboardStore.status !== "ready" ? (
                <>
                  <p>{context.dashboardStore.error || "알림을 불러오고 있어요."}</p>
                  <button
                    className="text-button"
                    onClick={() => void context.dashboardStore.refresh()}
                  >
                    다시 불러오기
                  </button>
                </>
              ) : (
                <>
                  <div className="row">
                    <span role="status">
                      읽지 않은 알림 {context.dashboardStore.data.unreadCount}개
                    </span>
                    <button
                      className="text-button danger"
                      disabled={
                        context.dashboardStore.busy ||
                        context.dashboardStore.data.notifications.length === 0
                      }
                      onClick={() =>
                        context.setConfirm({
                          title: "알림을 모두 삭제할까요?",
                          body: "읽은 알림과 읽지 않은 알림이 모두 삭제돼요. 삭제한 알림은 복원할 수 없어요.",
                          action: () => {
                            void context.run(
                              "/notifications/delete-all",
                              {},
                              "알림을 모두 삭제했어요.",
                            );
                          },
                        })
                      }
                    >
                      전체 삭제
                    </button>
                  </div>
                  {context.dashboardStore.data.notifications.length === 0 ? (
                    <p>알림이 없어요.</p>
                  ) : (
                    <div className="notification-list">
                      {context.dashboardStore.data.notifications.map((n) => (
                        <div
                          className={"transaction notification-item" + (n.read ? "" : " unread")}
                          key={n.id}
                        >
                          <div>
                            {!n.read && <span className="notification-unread">안 읽음</span>}
                            <p>{n.content}</p>
                            <small>{n.date.replace("T", " ")}</small>
                          </div>
                          <div className="notification-actions">
                            <button
                              className="text-button"
                              disabled={n.read || context.dashboardStore.busy}
                              onClick={() => void context.run("/notifications/" + n.id + "/read")}
                            >
                              {n.read ? "읽음" : "읽음 표시"}
                            </button>
                            <button
                              className="text-button danger"
                              disabled={context.dashboardStore.busy}
                              aria-label={n.content + " 알림 삭제"}
                              onClick={() =>
                                void context.run(
                                  "/notifications/" + n.id + "/delete",
                                  {},
                                  "알림을 삭제했어요.",
                                )
                              }
                            >
                              삭제
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
      <AlertDialog open={!!confirm} onOpenChange={(v) => !v && context.setConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogTitle>{confirm?.title}</AlertDialogTitle>
          <AlertDialogDescription>{confirm?.body}</AlertDialogDescription>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                confirm?.action();
                context.setConfirm(null);
              }}
            >
              확인
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
