"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Assignment, Member, Role } from "@/lib/types";

// 역할 id 는 1 부터라 -1 은 겹치지 않는다
const UNASSIGNED = -1;

type Props = {
  tripId: string;
  members: Member[];
  roles: Role[];
  initial: Assignment[];
};

export default function RoleBoard({ tripId, members, roles, initial }: Props) {
  const router = useRouter();
  const [board, setBoard] = useState(initial);
  const [dragging, setDragging] = useState<number | null>(null);
  const [recommending, setRecommending] = useState(false);

  const assigned = new Set(board.map((a) => a.memberId));
  const memberById = new Map(members.map((m) => [m.id, m]));

  // POST: /api/trips/1/roles/recommend  결과는 서버 컴포넌트가 다시 읽어 새 initial 로 내려준다
  async function recommend() {
    setRecommending(true);
    await fetch(`/api/trips/${tripId}/roles/recommend`, { method: "POST" });
    router.refresh();
    setRecommending(false);
  }

  // 카드 이동 API(PATCH)가 생기면 여기서 보내고 version 이 어긋나면 되돌린다
  function moveTo(roleId: number) {
    if (dragging === null) return;
    const rest = board.filter((a) => a.memberId !== dragging);
    const prev = board.find((a) => a.memberId === dragging);
    setBoard(
      roleId === UNASSIGNED
        ? rest
        : [...rest, { roleId, memberId: dragging, reasons: [], version: (prev?.version ?? 0) + 1 }],
    );
    setDragging(null);
  }

  const columns = [
    ...roles.map((r) => ({ id: r.id, title: r.title, required: r.required, capacity: r.capacity, tasks: r.tasks })),
    { id: UNASSIGNED, title: "미배정", required: false, capacity: 0, tasks: [] },
  ];

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted">카드를 끌어 다른 역할로 옮길 수 있어요. (아직 이 화면에서만 바뀝니다)</p>
        <button
          onClick={recommend}
          disabled={recommending}
          className="shrink-0 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {recommending ? "계산 중…" : board.length ? "다시 추천받기" : "설문으로 추천받기"}
        </button>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {columns.map((col) => {
          const cards = col.id === UNASSIGNED
            ? members.filter((m) => !assigned.has(m.id)).map((m) => ({ member: m, reasons: [] as string[] }))
            : board.filter((a) => a.roleId === col.id).flatMap((a) => {
                const member = memberById.get(a.memberId);
                return member ? [{ member, reasons: a.reasons }] : [];
              });
          const empty = col.required && cards.length === 0;
  
          return (
            <section
              key={col.id}
              onDragOver={(e) => e.preventDefault()}
              onDrop={() => moveTo(col.id)}
              className={`min-h-32 rounded-xl border bg-card p-3 ${empty ? "border-red-400" : "border-line"}`}
            >
              <h3 className="font-semibold">
                {col.title}
                {col.required && <span className="ml-1 text-xs text-muted">필수</span>}
                {col.capacity > 0 && <span className="ml-1 text-xs text-muted">{cards.length}/{col.capacity}</span>}
              </h3>
              {col.tasks.length > 0 && <p className="mb-2 text-xs text-muted">{col.tasks.join(" · ")}</p>}
              <ul className="space-y-2">
                {cards.map(({ member, reasons }) => (
                  <li
                    key={member.id}
                    draggable
                    onDragStart={() => setDragging(member.id)}
                    className="cursor-grab rounded-lg border border-line bg-background px-3 py-2 text-sm"
                  >
                    <div className="font-medium">{member.name}</div>
                    {reasons.length > 0 && <div className="text-xs text-muted">{reasons.join(", ")}</div>}
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    </div>
  );
}
