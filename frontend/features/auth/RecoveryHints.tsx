"use client";
import { Check, Plus } from "lucide-react";
import { useState } from "react";

export const recoveryQuestions = [
  "가장 좋아하는 색은 무엇인가요?",
  "가장 좋아하는 음식은 무엇인가요?",
  "기억에 남는 여행지는 어디인가요?",
  "좋아하는 책이나 영화의 제목은 무엇인가요?",
  "어릴 때 꿈꾸던 직업은 무엇인가요?",
] as const;

export function RecoveryHints({
  answers,
  onChange,
  signup = false,
  questionNumbers = [],
}: {
  answers: string[];
  onChange: (answers: string[]) => void;
  signup?: boolean;
  questionNumbers?: number[];
}) {
  const [selected, setSelected] = useState<number[]>(() =>
    answers.flatMap((a, i) => (a ? [i + 1] : [])),
  );
  const visible = signup ? selected : questionNumbers;
  return (
    <fieldset className="recovery-hints">
      <legend>
        {signup ? "나만의 복구 질문" : "나만 아는 답변"}
        <span>{signup ? `${selected.length} / 5 선택` : `${visible.length}개 질문`}</span>
      </legend>
      <p className="hint-description">
        {signup
          ? "기억하기 쉬운 질문을 1개 이상 골라주세요. 이메일을 잊었을 때 선택한 질문의 답변으로 찾을 수 있어요."
          : "가입할 때 남긴 답변을 입력해주세요. 영문 대소문자와 앞뒤 공백은 구분하지 않아요."}
      </p>
      <div className="hint-list">
        {recoveryQuestions.map((question, index) => {
          const active = visible.includes(index + 1);
          if (!signup && !active) return null;
          return (
            <div className={`hint-item ${active ? "is-selected" : ""}`} key={question}>
              {signup ? (
                <button
                  className="hint-toggle"
                  type="button"
                  aria-expanded={active}
                  aria-controls={`hint-answer-${index}`}
                  onClick={() => {
                    setSelected(
                      active ? selected.filter((n) => n !== index + 1) : [...selected, index + 1],
                    );
                    if (active) onChange(answers.map((a, i) => (i === index ? "" : a)));
                  }}
                >
                  <span className="hint-check">
                    {active ? <Check size={14} /> : <Plus size={14} />}
                  </span>
                  <span>{question}</span>
                </button>
              ) : (
                <label className="hint-question" htmlFor={`hint-answer-${index}`}>
                  <span className="hint-number">
                    {String(visible.indexOf(index + 1) + 1).padStart(2, "0")}
                  </span>
                  {question}
                </label>
              )}
              {active && (
                <div className="hint-answer">
                  <input
                    id={`hint-answer-${index}`}
                    aria-label={question}
                    name={`recovery-answer-${index + 1}`}
                    autoComplete="off"
                    value={answers[index] ?? ""}
                    onChange={(event) =>
                      onChange(
                        recoveryQuestions.map((_, i) =>
                          i === index ? event.target.value : (answers[i] ?? ""),
                        ),
                      )
                    }
                    maxLength={40}
                    pattern=".*\S.*"
                    placeholder="나만 기억할 수 있는 답변"
                    required
                  />
                </div>
              )}
            </div>
          );
        })}
      </div>
      {signup && (
        <p className="hint-footnote">
          선택한 질문은 모두 답변해주세요. 답변은 최대 40자까지 입력할 수 있어요.
        </p>
      )}
    </fieldset>
  );
}
