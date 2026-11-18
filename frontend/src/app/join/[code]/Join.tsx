"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

// 초대 코드로 방에 들어가고, 로그인 전이면 로그인 화면으로 보냈다가 돌아온다
export default function Join() {
  const { code } = useParams<{ code: string }>();
  const router = useRouter();
  const [message, setMessage] = useState("방에 들어가는 중…");

  // POST: /api/invites/3f9a1c2b7d4e  -> { "tripId": 1 }
  useEffect(() => {
    fetch(`/api/invites/${encodeURIComponent(code)}`, { method: "POST" }).then(async (res) => {
      if (res.status === 401) {
        router.replace(`/login?next=${encodeURIComponent(`/join/${code}`)}`);
        return;
      }
      if (!res.ok) {
        setMessage("초대 링크가 맞지 않아요. 링크를 다시 받아 주세요.");
        return;
      }
      const { tripId } = await res.json();
      router.replace(`/trips/${tripId}/survey`);
    });
  }, [code, router]);

  return <p className="p-8 text-sm text-muted">{message}</p>;
}
