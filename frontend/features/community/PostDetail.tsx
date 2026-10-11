import { visibleComments } from "@/features/dashboard/commentTree";
import { ArrowLeft, Heart } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import type { AppContext } from "@/app/appContext";
import { formatDate } from "@/app/formatters";

export function PostDetail({ context }: { context: AppContext }) {
  const p = context.post!;
  const [text, setText] = useState("");
  const [expandedReplies, setExpandedReplies] = useState<Set<number>>(() => new Set());
  const commentInput = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    context.setReply(undefined);
  }, [p.id, context.setReply]);
  const [editingComment, setEditingComment] = useState<number | null>(null);
  useEffect(() => {
    if (editingComment !== null && !p.comments.some((x) => x.id === editingComment)) {
      setEditingComment(null);
      setText("");
    }
    if (context.reply !== undefined && !p.comments.some((x) => x.id === context.reply)) {
      context.setReply(undefined);
      setText("");
    }
  }, [p.comments, editingComment, context.reply, context.setReply]);
  const report = (type: "POST" | "COMMENT", id: number) => {
    context.setModal(`report:${type}:${id}`);
  };
  return (
    <>
      <button className="text-button" onClick={() => context.setSelected(null)}>
        <ArrowLeft size={16} />
        목록으로 돌아가기
      </button>
      <article className="panel post-detail">
        <div className="row">
          <span className="category">{p.category}</span>
          <div className="button-row">
            {p.mine && (
              <>
                <button
                  className="text-button"
                  onClick={() => {
                    context.setEditing(p);
                    context.setImages(p.images);
                    context.setModal("post");
                  }}
                >
                  수정
                </button>
                <button
                  className="text-button danger"
                  onClick={() =>
                    context.setConfirm({
                      title: "이 글을 삭제할까요?",
                      body: "삭제한 글은 커뮤니티 목록에 표시되지 않아요.",
                      action: () => {
                        void context
                          .run(`/${p.id}/delete`, {}, "게시글을 삭제했어요.", true)
                          .then((ok) => {
                            if (ok) context.setSelected(null);
                          });
                      },
                    })
                  }
                >
                  삭제
                </button>
              </>
            )}
            <button className="text-button" onClick={() => report("POST", p.id)}>
              신고
            </button>
          </div>
        </div>
        <h2>{p.title}</h2>
        <div className="post-meta">
          <span className="avatar">{p.name[0]}</span>
          {p.name}
          <span>· {formatDate(p.created)}</span>
          {p.edited && <span className="small muted">수정됨</span>}
          <span>· 조회 {p.views}</span>
        </div>
        <p className="post-body">{p.content}</p>
        {p.images.length > 0 && (
          <div className="post-images">
            {p.images.map((src, i) => (
              <img src={src} alt={`첨부 사진 ${i + 1}`} key={i} />
            ))}
          </div>
        )}
        <button
          className={`like-button ${p.liked ? "liked" : ""}`}
          aria-pressed={p.liked}
          disabled={context.dashboardStore.busy}
          onClick={() => void context.run(`/${p.id}/like`, { liked: !p.liked }, "", true)}
        >
          <Heart size={18} fill={p.liked ? "currentColor" : "none"} />
          응원해요 {p.likes}
        </button>
        <div className="comment-section">
          <h3>
            함께 나누는 이야기 <span className="green">{p.comments.length}</span>
          </h3>
          {visibleComments(p.comments, expandedReplies).map(
            ({ comment: x, depth, replyCount, expanded }) => (
              <div
                className="comment nested-comment"
                style={{ marginLeft: Math.min(depth, 4) * 16 }}
                key={x.id}
              >
                <div className="row comment-header">
                  <div className="comment-author">
                    <strong>{x.name}</strong>
                    {x.edited && <span className="small muted">수정됨</span>}
                  </div>
                  <div className="comment-actions">
                    {x.mine && (
                      <>
                        <button
                          className="text-button"
                          onClick={() => {
                            setEditingComment(x.id);
                            setText(x.content);
                            context.setReply(undefined);
                            commentInput.current?.focus();
                          }}
                        >
                          수정
                        </button>
                        <button
                          className="text-button danger"
                          disabled={context.dashboardStore.busy}
                          onClick={() =>
                            context.setConfirm({
                              title: "댓글을 삭제할까요?",
                              body: "이 댓글과 하위 답글이 모두 삭제됩니다. 삭제 후에는 되돌릴 수 없어요.",
                              action: () => {
                                void context.run(
                                  "/comments/" + x.id + "/delete",
                                  {},
                                  "댓글을 삭제했어요.",
                                  true,
                                );
                              },
                            })
                          }
                        >
                          삭제
                        </button>
                      </>
                    )}
                    {!x.deleted && (
                      <button className="text-button" onClick={() => report("COMMENT", x.id)}>
                        신고
                      </button>
                    )}
                  </div>
                </div>
                {x.parent && (
                  <span className="small green">
                    ↳{" "}
                    {p.comments.find((parent) => parent.id === (x.replyTo ?? x.parent))?.name ??
                      "댓글"}
                    님에게 답글
                  </span>
                )}
                <p>{x.content}</p>
                <div className="button-row">
                  {!x.deleted && (
                    <button
                      className="text-button"
                      onClick={() => {
                        setEditingComment(null);
                        setText("");
                        context.setReply(x.id);
                        commentInput.current?.focus();
                      }}
                    >
                      답글 달기
                    </button>
                  )}
                  {replyCount >= 1 && (
                    <button
                      className="text-button"
                      aria-expanded={expanded}
                      aria-label={x.name + "님의 댓글 답글 " + (expanded ? "접기" : "더보기")}
                      onClick={() =>
                        setExpandedReplies((old) => {
                          const next = new Set(old);
                          if (next.has(x.id)) next.delete(x.id);
                          else next.add(x.id);
                          return next;
                        })
                      }
                    >
                      {expanded ? "답글 접기" : "답글 " + replyCount + "개"}
                    </button>
                  )}
                </div>
              </div>
            ),
          )}
          <form
            className="comment-form"
            onSubmit={async (e) => {
              e.preventDefault();
              if (!text.trim()) return;
              const ok = await context.run(
                editingComment
                  ? "/comments/" + editingComment + "/update"
                  : "/" + p.id + "/comments",
                { content: context.mask(text.trim()), parent: context.reply ?? null },
                "댓글을 저장했어요.",
                true,
              );
              if (ok) {
                if (context.reply) setExpandedReplies((old) => new Set([...old, context.reply!]));
                setText("");
                context.setReply(undefined);
                setEditingComment(null);
              }
            }}
          >
            {context.reply && (
              <div className="row">
                <span className="small green">
                  {p.comments.find((x) => x.id === context.reply)?.name}님에게 답글을 작성하고
                  있어요
                </span>
                <button
                  type="button"
                  className="text-button"
                  onClick={() => context.setReply(undefined)}
                >
                  취소
                </button>
              </div>
            )}
            <textarea
              ref={commentInput}
              aria-label="댓글 내용"
              placeholder="따뜻한 응원 한마디를 남겨주세요."
              value={text}
              onChange={(e) => setText(e.target.value)}
              maxLength={1000}
              required
            />
            <button className="primary" disabled={context.dashboardStore.busy} type="submit">
              {editingComment ? "댓글 수정 저장" : context.reply ? "답글 등록" : "댓글 등록"}
            </button>
          </form>
        </div>
      </article>
    </>
  );
}
