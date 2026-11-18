"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { NEXT_KEY, safeNext } from "@/lib/next-path";

// 개발용 로그인에서 바로 고를 수 있는 예시 방 참여자
const DEMO_NAMES = ["재현", "민지", "도윤", "서연", "하준", "지우", "예린", "시우"];

type Options = { kakao: boolean; dev: boolean };

export default function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [options, setOptions] = useState<Options | null>(null);
  const [error, setError] = useState(params.has("error") ? "카카오 로그인에 실패했어요. 다시 시도해 주세요." : "");

  // GET: /api/auth/options  어떤 로그인 버튼을 보여 줄지
  useEffect(() => {
    fetch("/api/auth/options")
      .then((res) => res.json())
      .then(setOptions)
      .catch(() => setError("서버에 연결하지 못했어요."));
  }, []);

  // 카카오 버튼은 Spring 으로 가는 전체 페이지 이동이라 Link 가 아닌 a 로 둔다. 떠나기 전에 돌아올 곳만 맡긴다
  function rememberNext() {
    try {
      sessionStorage.setItem(NEXT_KEY, next);
    } catch {
      // 저장소를 못 쓰면 로그인 뒤 첫 화면으로 간다
    }
  }

  // POST: /api/dev/login  { "name": "민지" }
  async function devLogin(name: string) {
    const res = await fetch("/api/dev/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
    if (!res.ok) {
      setError("개발용 로그인에 실패했어요.");
      return;
    }
    router.replace(next);
    router.refresh();
  }

  if (!options) return <p className="text-sm text-muted">{error || "불러오는 중…"}</p>;

  return (
    <div className="space-y-6">
      {error && <p role="alert" className="rounded-lg border border-amber-400 px-3 py-2 text-sm">{error}</p>}

      {options.kakao ? (
        <a
          href="/oauth2/authorization/kakao"
          onClick={rememberNext}
          className="block w-full rounded-lg bg-[#FEE500] px-5 py-3 text-center font-medium text-black"
        >
          카카오로 시작하기
        </a>
      ) : (
        <p className="text-sm text-muted">카카오 키가 아직 없어 카카오 로그인을 숨겼어요. (KAKAO_CLIENT_ID)</p>
      )}

      {options.dev && (
        <section className="space-y-3 rounded-xl border border-dashed border-line p-4">
          <h2 className="text-sm font-semibold">개발용 로그인</h2>
          <p className="text-xs text-muted">예시 방 참여자로 들어가요. 배포에서는 꺼져 있어요.</p>
          <div className="grid grid-cols-4 gap-2">
            {DEMO_NAMES.map((name) => (
              <button key={name} onClick={() => devLogin(name)} className="rounded-lg border border-line px-2 py-2 text-sm hover:border-accent">
                {name}
              </button>
            ))}
          </div>
          <form
            action={(form) => devLogin(String(form.get("name")))}
            className="flex gap-2"
          >
            <input name="name" required maxLength={20} placeholder="새 이름으로" className="flex-1 rounded-lg border border-line bg-card px-3 py-2 text-sm" />
            <button className="rounded-lg border border-line px-3 py-2 text-sm">들어가기</button>
          </form>
        </section>
      )}
    </div>
  );
}
