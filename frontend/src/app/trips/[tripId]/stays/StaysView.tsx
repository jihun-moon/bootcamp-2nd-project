"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Member, StayBoard } from "@/lib/types";

type Props = { tripId: string; members: Member[]; board: StayBoard };

const field = "w-full rounded-lg border border-line bg-card px-3 py-2 text-sm";

export default function StaysView({ tripId, members, board }: Props) {
  const router = useRouter();
  // shortcut: 로그인 전이라 투표·등록할 사람을 목록에서 고른다. 카카오 로그인 붙이면 뺀다
  const [meId, setMeId] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState("");

  const { amenities, preferences } = board;
  const nameOf = new Map(members.map((m) => [m.id, m.name]));
  // 그룹이 원하는 정도(평균)를 숙소가 가진 조건만큼 더한다. 갈림(최솟값 1) 표시는 점수와 따로 보여 준다
  const score = (keys: string[]) => keys.reduce((sum, k) => sum + (preferences[k]?.avg ?? 0), 0);
  const ranked = [...board.stays].sort((a, b) => score(b.amenities) - score(a.amenities) || a.id - b.id);
  const myVote = board.stays.find((s) => meId !== null && s.voterIds.includes(meId))?.id ?? null;

  // PUT: /api/trips/1/votes/2  { "stayId": 3 }  같은 숙소를 다시 누르면 취소(null)
  async function vote(stayId: number) {
    if (meId === null) return;
    setError("");
    const res = await fetch(`/api/trips/${tripId}/votes/${meId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ stayId: myVote === stayId ? null : stayId }),
    });
    if (!res.ok) setError("투표하지 못했어요. 다시 눌러 주세요.");
    router.refresh();
  }

  // POST: /api/trips/1/stays  { "url": "...", "title": "...", "amenities": ["DETACHED"], "addedBy": 2 }
  async function addStay(form: FormData) {
    if (meId === null) return;
    setAdding(true);
    setError("");
    const res = await fetch(`/api/trips/${tripId}/stays`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        url: form.get("url"),
        title: form.get("title") || null,
        amenities: form.getAll("amenities"),
        addedBy: meId,
      }),
    });
    setAdding(false);
    if (!res.ok) {
      setError("숙소를 올리지 못했어요. 링크가 https:// 로 시작하는지 확인해 주세요.");
      return;
    }
    router.refresh();
  }

  return (
    <div className="space-y-6">
      <label className="flex items-center gap-2 text-sm">
        <span className="text-muted">나는</span>
        <select
          value={meId ?? ""}
          onChange={(e) => setMeId(e.target.value ? Number(e.target.value) : null)}
          className="rounded-lg border border-line bg-card px-2 py-1"
        >
          <option value="">선택하세요</option>
          {members.map((m) => (
            <option key={m.id} value={m.id}>{m.name}</option>
          ))}
        </select>
        {meId === null && <span className="text-muted">고르면 투표하고 숙소를 올릴 수 있어요</span>}
      </label>

      <section className="rounded-xl border border-line bg-card p-4">
        <h2 className="mb-1 font-semibold">우리 그룹이 중요하게 보는 것</h2>
        {board.surveyed === 0 ? (
          <p className="text-sm text-muted">설문이 모이면 보여요.</p>
        ) : (
          <>
            <p className="mb-4 text-xs text-muted">
              설문 {board.surveyed}명 · 막대는 평균, 세로선은 가장 낮게 준 사람의 점수(5점 만점)
            </p>
            <ul className="space-y-2">
              {amenities.map(({ key, label }) => {
                const p = preferences[key];
                if (!p) return null;
                return (
                  <li key={key} className="grid grid-cols-[7rem_1fr_4rem] items-center gap-2 text-sm">
                    <span>{label}</span>
                    <div className="relative h-3 rounded-full bg-line">
                      <div className="h-3 rounded-full bg-accent" style={{ width: `${(p.avg / 5) * 100}%` }} />
                      <div className="absolute top-[-3px] h-[18px] w-0.5 bg-foreground" style={{ left: `${(p.min / 5) * 100}%` }} />
                    </div>
                    <span className={`text-right text-xs ${p.min <= 1 ? "text-red-600" : "text-muted"}`}>
                      {p.min <= 1 ? "의견 갈림" : `${p.avg.toFixed(1)}점`}
                    </span>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </section>

      {error && <p role="status" className="rounded-lg border border-amber-400 px-3 py-2 text-sm">{error}</p>}

      {ranked.length > 0 && (
        <section className="overflow-x-auto rounded-xl border border-line bg-card">
          <table className="w-full min-w-[760px] text-sm">
            <thead>
              <tr className="border-b border-line text-left">
                <th className="p-3">숙소</th>
                {/* 폰에서는 표가 옆으로 밀리니 점수·투표를 이름 바로 옆에 둔다 */}
                <th className="p-3 text-right">점수</th>
                <th className="p-3 text-center">투표</th>
                {amenities.map(({ key, label }) => (
                  <th key={key} className="p-3 text-center font-normal text-muted">{label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ranked.map((stay) => (
                <tr key={stay.id} className="border-b border-line last:border-0">
                  <td className="p-3">
                    <a href={stay.url} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 font-medium">
                      {stay.imageUrl && (
                        // 바깥 사이트 사진이라 next/image 의 도메인 허용 목록을 두지 않고 그냥 img 로 둔다
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={stay.imageUrl} alt="" referrerPolicy="no-referrer" className="h-10 w-14 rounded object-cover" />
                      )}
                      {stay.title}
                    </a>
                  </td>
                  <td className="p-3 text-right">{score(stay.amenities).toFixed(1)}</td>
                  <td className="p-3 text-center">
                    <button
                      onClick={() => vote(stay.id)}
                      disabled={meId === null}
                      aria-pressed={myVote === stay.id}
                      className={`rounded-lg border px-3 py-1 disabled:opacity-40 ${
                        myVote === stay.id ? "border-accent bg-accent text-white" : "border-line"
                      }`}
                    >
                      {stay.voterIds.length}표
                    </button>
                    {stay.voterIds.length > 0 && (
                      <div className="mt-1 text-xs text-muted">{stay.voterIds.map((id) => nameOf.get(id)).join(", ")}</div>
                    )}
                  </td>
                  {amenities.map(({ key, label }) => (
                    <td key={key} className="p-3 text-center" aria-label={stay.amenities.includes(key) ? label : undefined}>
                      {stay.amenities.includes(key) ? "✓" : ""}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <form action={addStay} className="space-y-3 rounded-xl border border-dashed border-line p-4">
        <h2 className="font-semibold">숙소 링크 올리기</h2>
        <input name="url" type="url" required placeholder="https://..." className={field} />
        <input name="title" maxLength={200} placeholder="이름 (비우면 링크 제목을 가져와요)" className={field} />
        <fieldset className="flex flex-wrap gap-x-4 gap-y-2 text-sm">
          <legend className="mb-1 text-muted">있는 조건을 체크해 주세요</legend>
          {amenities.map(({ key, label }) => (
            <label key={key} className="flex items-center gap-1">
              <input type="checkbox" name="amenities" value={key} />
              {label}
            </label>
          ))}
        </fieldset>
        <button
          disabled={meId === null || adding}
          className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white disabled:opacity-40"
        >
          {adding ? "올리는 중…" : "올리기"}
        </button>
      </form>
    </div>
  );
}
