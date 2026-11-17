"use client";

import { useState } from "react";
import type { Assignment, Member, Role } from "@/lib/types";

const UNASSIGNED = "unassigned";

type Props = {
  members: Member[];
  roles: Role[];
  initial: Assignment[];
};

export default function RoleBoard({ members, roles, initial }: Props) {
  const [board, setBoard] = useState(initial);
  const [dragging, setDragging] = useState<number | null>(null);

  const assigned = new Set(board.map((a) => a.memberId));
  const memberById = new Map(members.map((m) => [m.id, m]));

  // 실시간 동기화가 붙으면 여기서 PATCH 를 보내고 version 이 어긋나면 되돌린다
  function moveTo(roleId: string) {
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
    ...roles.map((r) => ({ id: r.id, title: r.title, required: r.required, tasks: r.tasks })),
    { id: UNASSIGNED, title: "미배정", required: false, tasks: [] },
  ];

  return (
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
  );
}
