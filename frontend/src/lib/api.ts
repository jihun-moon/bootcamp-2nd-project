import { notFound } from "next/navigation";
import type { Trip } from "./types";

// 서버 컴포넌트는 rewrite 를 거치지 않으니 Spring 주소로 바로 부른다
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

// GET: /api/trips/1
export async function getTrip(tripId: string): Promise<Trip> {
  const res = await fetch(`${backendUrl}/api/trips/${tripId}`, { cache: "no-store" });
  if (res.status === 404 || res.status === 400) notFound();
  if (!res.ok) throw new Error(`trip ${tripId} 조회 실패: ${res.status}`);
  return res.json();
}
