import Link from "next/link";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center gap-8 px-4 py-16">
      <div className="space-y-3">
        <h1 className="text-3xl font-bold">TripMate</h1>
        <p className="text-muted">
          8명이 가는 여행, 누가 뭘 맡을지 설문으로 정하고 숙소는 모두가 싫어하지 않는 곳으로.
        </p>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row">
        {/* 카카오 로그인은 인증 붙일 때 연결한다 */}
        <button className="rounded-lg bg-[#FEE500] px-5 py-3 font-medium text-black">카카오로 시작하기</button>
        <Link href="/trips/new" className="rounded-lg border border-line px-5 py-3 text-center">
          여행방 만들기
        </Link>
        <Link href="/trips/1" className="rounded-lg px-5 py-3 text-center text-accent">
          예시 방 보기
        </Link>
      </div>
    </main>
  );
}
