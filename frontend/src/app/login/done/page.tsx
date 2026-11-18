"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { NEXT_KEY, safeNext } from "@/lib/next-path";

// 카카오 로그인이 끝나면 Spring 이 여기로 보낸다. 로그인 전에 맡겨 둔 곳으로 돌아간다
export default function LoginDone() {
  const router = useRouter();

  useEffect(() => {
    let next: string | null = null;
    try {
      next = sessionStorage.getItem(NEXT_KEY);
      sessionStorage.removeItem(NEXT_KEY);
    } catch {
      // 저장소를 못 쓰면 첫 화면으로
    }
    router.replace(safeNext(next));
  }, [router]);

  return <p className="p-8 text-sm text-muted">로그인하는 중…</p>;
}
