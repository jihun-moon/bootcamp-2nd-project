import { notFound } from "next/navigation";
import type { RoleBoard, StayBoard, Trip } from "./types";

// 서버 컴포넌트는 rewrite 를 거치지 않으니 Spring 주소로 바로 부른다
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${backendUrl}${path}`, { cache: "no-store" });
  if (res.status === 404 || res.status === 400) notFound();
  if (!res.ok) throw new Error(`${path} 조회 실패: ${res.status}`);
  return res.json();
}

// GET: /api/trips/1
export const getTrip = (tripId: string) => get<Trip>(`/api/trips/${tripId}`);

// GET: /api/trips/1/roles
export const getRoleBoard = (tripId: string) => get<RoleBoard>(`/api/trips/${tripId}/roles`);

// GET: /api/trips/1/stays
export const getStayBoard = (tripId: string) => get<StayBoard>(`/api/trips/${tripId}/stays`);
