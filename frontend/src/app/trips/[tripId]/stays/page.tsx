import { groupPreference, stayTags, stays } from "@/lib/mock";

// 그룹이 원하는 정도(평균)만큼 점수를 준다. 누군가 1점을 준 항목은 갈림 표시만 하고 점수는 그대로 둔다
function score(tags: string[]) {
  return tags.reduce((sum, tag) => sum + (groupPreference[tag]?.avg ?? 0), 0);
}

export default function StaysPage() {
  const ranked = [...stays].sort((a, b) => score(b.tags) - score(a.tags));

  return (
    <div className="space-y-6">
      <section className="rounded-xl border border-line bg-card p-4">
        <h2 className="mb-1 font-semibold">우리 그룹이 중요하게 보는 것</h2>
        <p className="mb-4 text-xs text-muted">막대는 평균, 세로선은 가장 낮게 준 사람의 점수(5점 만점)</p>
        {/* 레이더 차트는 차트 라이브러리를 고른 뒤 바꾼다. 지금은 같은 값을 막대로 본다 */}
        <ul className="space-y-2">
          {stayTags.map((tag) => {
            const { avg, min } = groupPreference[tag];
            return (
              <li key={tag} className="grid grid-cols-[7rem_1fr_4rem] items-center gap-2 text-sm">
                <span>{tag}</span>
                <div className="relative h-3 rounded-full bg-line">
                  <div className="h-3 rounded-full bg-accent" style={{ width: `${(avg / 5) * 100}%` }} />
                  <div className="absolute top-[-3px] h-[18px] w-0.5 bg-foreground" style={{ left: `${(min / 5) * 100}%` }} />
                </div>
                <span className={`text-right text-xs ${min <= 1 ? "text-red-600" : "text-muted"}`}>
                  {min <= 1 ? "의견 갈림" : `${avg.toFixed(1)}점`}
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      <section className="overflow-x-auto rounded-xl border border-line bg-card">
        <table className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="border-b border-line text-left">
              <th className="p-3">숙소</th>
              {stayTags.map((tag) => (
                <th key={tag} className="p-3 text-center font-normal text-muted">{tag}</th>
              ))}
              <th className="p-3 text-right">점수</th>
            </tr>
          </thead>
          <tbody>
            {ranked.map((stay) => (
              <tr key={stay.id} className="border-b border-line last:border-0">
                <td className="p-3 font-medium">{stay.title}</td>
                {stayTags.map((tag) => (
                  <td key={tag} className="p-3 text-center">{stay.tags.includes(tag) ? "✓" : ""}</td>
                ))}
                <td className="p-3 text-right">{score(stay.tags).toFixed(1)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {/* 링크를 넣으면 OG 태그로 제목·썸네일을 가져오고 조건은 직접 체크한다 */}
      <button className="rounded-lg border border-dashed border-line px-4 py-3 text-sm text-muted">+ 숙소 링크 추가</button>
    </div>
  );
}
