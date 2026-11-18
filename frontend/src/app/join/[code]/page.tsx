import { Suspense } from "react";
import Join from "./Join";

// 초대 링크 /join/3f9a1c2b7d4e  (주소 값을 읽는 화면이라 Suspense 안에 둔다)
export default function JoinPage() {
  return (
    <Suspense fallback={<p className="p-8 text-sm text-muted">방에 들어가는 중…</p>}>
      <Join />
    </Suspense>
  );
}
