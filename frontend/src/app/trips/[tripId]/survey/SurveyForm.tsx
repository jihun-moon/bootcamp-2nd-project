"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Member } from "@/lib/types";

type Option = { label: string; value: unknown };
type Question = { key: string; title: string; options: Option[]; note?: string };

// key 와 value 는 Spring SurveyDto.Request 의 칸 이름·enum 값과 같아야 한다
const questions: Question[] = [
  {
    key: "activity",
    title: "여행 가서 더 끌리는 쪽은?",
    options: [
      { label: "숙소에서 쉬기", value: "INDOOR" },
      { label: "반반", value: "BOTH" },
      { label: "밖에서 놀기", value: "OUTDOOR" },
    ],
  },
  {
    key: "planning",
    title: "일정은 어떻게 짜는 편?",
    options: [
      { label: "분 단위로 계획", value: "PLANNER" },
      { label: "큰 틀만", value: "OUTLINE" },
      { label: "그때그때", value: "SPONTANEOUS" },
    ],
  },
  {
    key: "hasCar",
    title: "차를 가져갈 수 있나요?",
    options: [
      { label: "가능", value: true },
      { label: "어려움", value: false },
    ],
  },
  {
    key: "separateDeparture",
    title: "어디서 출발하나요?",
    options: [
      { label: "같이 출발", value: false },
      { label: "따로 출발", value: true },
    ],
  },
  {
    key: "mobilityLimited",
    title: "이동이 불편한 사정이 있나요?",
    note: "답은 본인과 방장만 봅니다. 다른 사람에게는 '이동 적은 역할 우선'으로만 보여요.",
    options: [
      { label: "없음", value: false },
      { label: "있음", value: true },
    ],
  },
  {
    key: "avoid",
    title: "이것만은 맡기 싫다",
    options: [
      { label: "운전", value: ["DRIVING"] },
      { label: "요리·바베큐", value: ["COOKING"] },
      { label: "돈 관리", value: ["MONEY"] },
      { label: "상관없음", value: [] },
    ],
  },
];

const amenities = [
  { key: "DETACHED", label: "독채" },
  { key: "PRIVATE_BBQ", label: "개별 바베큐" },
  { key: "HEATED_POOL", label: "온수풀" },
  { key: "KARAOKE", label: "노래방" },
  { key: "ROOMS_3_PLUS", label: "방 3개 이상" },
  { key: "BATHS_2_PLUS", label: "화장실 2개" },
  { key: "BARRIER_FREE", label: "1층·엘리베이터" },
];

const box = "space-y-4 rounded-xl border border-line bg-card p-6";
const choice = "rounded-lg border border-line px-4 py-3 text-left hover:border-accent";

export default function SurveyForm({ tripId, me }: { tripId: string; me: Member }) {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [preferences, setPreferences] = useState<Record<string, number>>(
    Object.fromEntries(amenities.map((a) => [a.key, 3])),
  );
  const [status, setStatus] = useState<"idle" | "saving" | "saved" | "error">("idle");

  // PUT: /api/trips/1/members/2/survey  body = { ...answers, preferences }
  async function submit() {
    setStatus("saving");
    const res = await fetch(`/api/trips/${tripId}/members/${me.id}/survey`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...answers, preferences }),
    });
    setStatus(res.ok ? "saved" : "error");
    if (res.ok) router.refresh();
  }

  function restart() {
    setStep(0);
    setStatus("idle");
  }

  const total = questions.length + 1;
  const progress = (
    <div className="h-1 rounded-full bg-line">
      <div className="h-1 rounded-full bg-accent transition-all" style={{ width: `${(step / total) * 100}%` }} />
    </div>
  );
  const back = step > 0 && (
    <button onClick={() => setStep(step - 1)} className="text-sm text-muted">← 이전</button>
  );

  if (status === "saved") {
    return (
      <div className={box}>
        <h2 className="text-lg font-semibold">{me.name}님 설문을 저장했어요</h2>
        <button onClick={restart} className="text-sm text-accent">다시 하기</button>
      </div>
    );
  }

  if (step < questions.length) {
    const q = questions[step];
    return (
      <div className="space-y-6">
        {progress}
        <div key={q.key} className={box}>
          <p className="text-sm text-muted">{me.name} · {step + 1} / {total}</p>
          <h2 className="text-xl font-semibold">{q.title}</h2>
          {q.note && <p className="rounded-lg bg-background p-3 text-sm text-muted">{q.note}</p>}
          <div className="grid gap-2 sm:grid-cols-2">
            {q.options.map((o) => (
              <button
                key={o.label}
                onClick={() => {
                  setAnswers({ ...answers, [q.key]: o.value });
                  setStep(step + 1);
                }}
                className={choice}
              >
                {o.label}
              </button>
            ))}
          </div>
          {back}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {progress}
      <div className={box}>
        <p className="text-sm text-muted">{me.name} · {total} / {total}</p>
        <h2 className="text-xl font-semibold">숙소에서 이건 얼마나 중요해요?</h2>
        <p className="text-sm text-muted">1 = 없어도 됨, 5 = 꼭 있어야 함</p>
        <ul className="space-y-3">
          {amenities.map((a) => (
            <li key={a.key} className="flex items-center justify-between gap-3">
              <span className="text-sm">{a.label}</span>
              <div className="flex gap-1">
                {[1, 2, 3, 4, 5].map((n) => (
                  <button
                    key={n}
                    onClick={() => setPreferences((prev) => ({ ...prev, [a.key]: n }))}
                    aria-pressed={preferences[a.key] === n}
                    aria-label={`${a.label} ${n}점`}
                    className={`h-8 w-8 rounded-md border text-sm ${
                      preferences[a.key] === n ? "border-accent bg-accent text-white" : "border-line"
                    }`}
                  >
                    {n}
                  </button>
                ))}
              </div>
            </li>
          ))}
        </ul>
        {status === "error" && <p className="text-sm text-red-600">저장하지 못했어요. 다시 눌러 주세요.</p>}
        <div className="flex items-center justify-between">
          {back}
          <button
            onClick={submit}
            disabled={status === "saving"}
            className="rounded-lg bg-accent px-5 py-2 font-medium text-white disabled:opacity-50"
          >
            {status === "saving" ? "저장 중…" : "저장하기"}
          </button>
        </div>
      </div>
    </div>
  );
}
