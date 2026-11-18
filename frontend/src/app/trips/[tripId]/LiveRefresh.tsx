"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect } from "react";

// 서버는 15초마다 ping 을 보낸다. 이보다 오래 조용하면 중간에서 끊긴 것으로 본다
const SILENCE_LIMIT = 40_000;

// GET: /api/trips/1/events  (SSE, event: change | ping)
// 서버는 "바뀌었다"만 알리고, 화면은 서버 컴포넌트를 다시 읽어 최신 값을 받는다
export default function LiveRefresh() {
  const { tripId } = useParams<{ tripId: string }>();
  const router = useRouter();

  useEffect(() => {
    let source: EventSource | null = null;
    let lastSeen = Date.now();
    let opened = false;

    const connect = () => {
      source?.close();
      lastSeen = Date.now();
      source = new EventSource(`/api/trips/${tripId}/events`);
      source.onopen = () => {
        lastSeen = Date.now();
        // 다시 붙은 것이면 끊긴 사이 바뀐 것을 놓쳤을 수 있어 한 번 새로 읽는다
        if (opened) router.refresh();
        opened = true;
      };
      source.addEventListener("ping", () => {
        lastSeen = Date.now();
      });
      source.addEventListener("change", () => {
        lastSeen = Date.now();
        router.refresh();
      });
    };

    connect();
    // 백엔드가 재시작돼도 프록시가 브라우저 쪽 연결을 열어 두면 EventSource 는 스스로 다시 붙지 않는다
    const watchdog = setInterval(() => {
      if (Date.now() - lastSeen > SILENCE_LIMIT) connect();
    }, 10_000);

    return () => {
      clearInterval(watchdog);
      source?.close();
    };
  }, [tripId, router]);

  return null;
}
