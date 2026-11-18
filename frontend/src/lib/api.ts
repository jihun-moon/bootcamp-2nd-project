import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import type { RoleBoard, StayBoard, Trip } from "./types";

// 서버 컴포넌트는 rewrite 를 거치지 않으니 Spring 주소로 바로 부른다
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

// back: 로그인이 풀려 있으면 로그인 뒤 돌아올 화면
async function get<T>(path: string, back: string): Promise<T> {
  // 브라우저가 Next 에 준 세션 쿠키를 Spring 에도 그대로 넘겨야 같은 사람으로 본다
  const cookie = (await cookies()).toString();
  const res = await fetch(`${backendUrl}${path}`, { cache: "no-store", headers: { cookie } });
  if (res.status === 401) redirect(`/login?next=${encodeURIComponent(back)}`);
  if (res.status === 404 || res.status === 400) notFound();
  if (!res.ok) throw new Error(`${path} 조회 실패: ${res.status}`);
  return res.json();
}

// GET: /api/trips/1
export const getTrip = (tripId: string) => get<Trip>(`/api/trips/${tripId}`, `/trips/${tripId}`);

// GET: /api/trips/1/roles
export const getRoleBoard = (tripId: string) => get<RoleBoard>(`/api/trips/${tripId}/roles`, `/trips/${tripId}/roles`);

// GET: /api/trips/1/stays
export const getStayBoard = (tripId: string) => get<StayBoard>(`/api/trips/${tripId}/stays`, `/trips/${tripId}/stays`);
