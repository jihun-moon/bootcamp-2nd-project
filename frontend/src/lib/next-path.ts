// 로그인 뒤 돌아갈 주소. 바깥 사이트로 튕겨 나가지 않게 이 사이트 안의 경로(/로 시작, //는 아님)만 받는다
export function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
}

// 카카오는 브라우저를 다른 사이트로 보냈다가 /login/done 으로 돌려보낸다. 그 사이 돌아갈 곳을 여기 맡겨 둔다
export const NEXT_KEY = "tripmate.next";
