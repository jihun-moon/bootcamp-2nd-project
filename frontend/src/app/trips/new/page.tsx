"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

const field = "w-full rounded-lg border border-line bg-card px-3 py-2";

export default function NewTrip() {
  const router = useRouter();
  const [error, setError] = useState("");

  // POST: /api/trips  { "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17" }
  async function create(form: FormData) {
    const body = Object.fromEntries([...form].filter(([, v]) => v !== ""));
    const res = await fetch("/api/trips", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      setError("방을 만들지 못했어요. 여행 이름을 확인해 주세요.");
      return;
    }
    const trip = await res.json();
    router.push(`/trips/${trip.id}`);
  }

  return (
    <main className="mx-auto w-full max-w-xl flex-1 px-4 py-10">
      <h1 className="mb-6 text-2xl font-bold">여행방 만들기</h1>
      <form action={create} className="space-y-4">
        <label className="block space-y-1">
          <span className="text-sm text-muted">여행 이름</span>
          <input name="title" required className={field} placeholder="겨울 펜션 2박 3일" />
        </label>
        <label className="block space-y-1">
          <span className="text-sm text-muted">지역</span>
          <input name="destination" className={field} placeholder="가평" />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className="block space-y-1">
            <span className="text-sm text-muted">출발</span>
            <input name="startDate" type="date" className={field} />
          </label>
          <label className="block space-y-1">
            <span className="text-sm text-muted">도착</span>
            <input name="endDate" type="date" className={field} />
          </label>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="rounded-lg bg-accent px-5 py-2 font-medium text-white">만들기</button>
          <Link href="/" className="rounded-lg px-5 py-2 text-muted">취소</Link>
        </div>
      </form>
    </main>
  );
}
