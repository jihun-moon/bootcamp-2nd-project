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
        <Link href="/login" className="rounded-lg bg-[#FEE500] px-5 py-3 text-center font-medium text-black">
          로그인
        </Link>
        <Link href="/trips/new" className="rounded-lg border border-line px-5 py-3 text-center">
          여행방 만들기
        </Link>
      </div>
      <p className="text-sm text-muted">친구가 보낸 초대 링크가 있으면 그 링크로 들어오세요.</p>
    </main>
  );
}
