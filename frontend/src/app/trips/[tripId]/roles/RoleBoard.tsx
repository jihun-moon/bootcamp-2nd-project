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
  const [notice, setNotice] = useState("");

  const assigned = new Set(board.map((a) => a.memberId));
  const memberById = new Map(members.map((m) => [m.id, m]));

  // POST: /api/trips/1/roles/recommend  결과는 서버 컴포넌트가 다시 읽어 새 initial 로 내려준다
  async function recommend() {
    setRecommending(true);
    await fetch(`/api/trips/${tripId}/roles/recommend`, { method: "POST" });
    router.refresh();
    setRecommending(false);
  }

  // PATCH: /api/trips/1/roles/assignments/2  { "roleId": 4, "version": 0 }
  async function moveTo(roleId: number) {
    if (dragging === null) return;
    const memberId = dragging;
    setDragging(null);
    const prev = board.find((a) => a.memberId === memberId);
    if ((prev?.roleId ?? UNASSIGNED) === roleId) return;

    // 서버 답을 기다리지 않고 먼저 옮겨 보인다. 안 되면 되돌린다
    const before = board;
    const rest = board.filter((a) => a.memberId !== memberId);
    setBoard(roleId === UNASSIGNED ? rest : [...rest, { roleId, memberId, reasons: [], version: prev?.version ?? 0 }]);
    setNotice("");

    const res = await fetch(`/api/trips/${tripId}/roles/assignments/${memberId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ roleId: roleId === UNASSIGNED ? null : roleId, version: prev?.version ?? null }),
    });
    if (res.ok) {
      setBoard((await res.json()).assignments);
      return;
    }
    setBoard(before);
    if (res.status === 409) {
      // router.refresh 를 쓰면 보드가 새로 그려지며 안내 문구까지 사라져서 보드만 다시 읽는다
      const latest = await fetch(`/api/trips/${tripId}/roles`);
      if (latest.ok) setBoard((await latest.json()).assignments);
      setNotice("다른 사람이 먼저 옮겼어요. 최신 보드로 바꿨으니 다시 옮겨 주세요.");
    } else {
      setNotice("옮기지 못했어요. 다시 해 주세요.");
    }
  }

  const columns = [
    ...roles.map((r) => ({ id: r.id, title: r.title, required: r.required, capacity: r.capacity, tasks: r.tasks })),
    { id: UNASSIGNED, title: "미배정", required: false, capacity: 0, tasks: [] },
  ];

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted">카드를 끌어 다른 역할로 옮길 수 있어요.</p>
        <button
          onClick={recommend}
          disabled={recommending}
          className="shrink-0 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {recommending ? "계산 중…" : board.length ? "다시 추천받기" : "설문으로 추천받기"}
        </button>
      </div>
      {notice && (
        <p role="status" className="rounded-lg border border-amber-400 px-3 py-2 text-sm">
          {notice}
        </p>
      )}
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
