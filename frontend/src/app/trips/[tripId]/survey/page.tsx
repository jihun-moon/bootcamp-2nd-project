"use client";

import { useState } from "react";

type Question = {
  key: string;
  title: string;
  options: string[];
  note?: string;
};

// 설문 칸은 나중 매칭 점수표의 입력이 된다. key 를 백엔드 칸 이름과 맞춘다
const questions: Question[] = [
  { key: "activity", title: "여행 가서 더 끌리는 쪽은?", options: ["숙소에서 쉬기", "반반", "밖에서 놀기"] },
  { key: "planning", title: "일정은 어떻게 짜는 편?", options: ["분 단위로 계획", "큰 틀만", "그때그때"] },
  { key: "car", title: "차를 가져갈 수 있나요?", options: ["가능", "어려움"] },
  { key: "departure", title: "어디서 출발하나요?", options: ["같이 출발", "따로 출발"] },
  {
    key: "mobility",
    title: "이동이 불편한 사정이 있나요?",
    options: ["없음", "있음"],
    note: "답은 본인과 방장만 봅니다. 다른 사람에게는 '이동 적은 역할 우선'으로만 보여요.",
  },
  { key: "avoid", title: "이것만은 맡기 싫다", options: ["운전", "요리·바베큐", "돈 관리", "상관없음"] },
];

export default function SurveyPage() {
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const done = step === questions.length;
  const q = questions[step];

  function choose(option: string) {
    setAnswers({ ...answers, [q.key]: option });
    setStep(step + 1);
  }

  if (done) {
    return (
      <div className="space-y-4 rounded-xl border border-line bg-card p-6">
        <h2 className="text-lg font-semibold">설문 끝!</h2>
        <ul className="space-y-1 text-sm">
          {questions.map((item) => (
            <li key={item.key}>
              <span className="text-muted">{item.title}</span> {answers[item.key]}
            </li>
          ))}
        </ul>
        {/* 저장 API(POST /api/trips/{id}/surveys)가 생기면 여기서 보낸다 */}
        <button onClick={() => setStep(0)} className="text-sm text-accent">다시 하기</button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="h-1 rounded-full bg-line">
        <div className="h-1 rounded-full bg-accent transition-all" style={{ width: `${(step / questions.length) * 100}%` }} />
      </div>
      <div key={q.key} className="space-y-4 rounded-xl border border-line bg-card p-6">
        <p className="text-sm text-muted">{step + 1} / {questions.length}</p>
        <h2 className="text-xl font-semibold">{q.title}</h2>
        {q.note && <p className="rounded-lg bg-background p-3 text-sm text-muted">{q.note}</p>}
        <div className="grid gap-2 sm:grid-cols-2">
          {q.options.map((option) => (
            <button
              key={option}
              onClick={() => choose(option)}
              className="rounded-lg border border-line px-4 py-3 text-left hover:border-accent"
            >
              {option}
            </button>
          ))}
        </div>
        {step > 0 && (
          <button onClick={() => setStep(step - 1)} className="text-sm text-muted">← 이전</button>
        )}
      </div>
    </div>
  );
}
