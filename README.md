# TripMate

다인원 여행의 역할 배정과 숙소 투표를 돕는 웹 서비스.

| 폴더 | 무엇 | 여는 곳 |
|---|---|---|
| `backend/` | Spring Boot 4.1 · Java 25 · JPA · Spring Security(카카오 OAuth2) · H2(로컬) / Postgres(배포) | IntelliJ에서 `backend/build.gradle` 열기 |
| `frontend/` | Next.js 16 · TypeScript · Tailwind | IntelliJ(Ultimate) 또는 VS Code |

## 로컬 실행

1. 백엔드: IntelliJ 실행 메뉴의 `backend`(개발용 로그인 켜짐), 또는 `cd backend && DEV_LOGIN=true ./gradlew bootRun` → http://localhost:8080
2. 프론트: `cd frontend && npm install && npm run dev` → http://localhost:3000
3. http://localhost:3000/login 에서 개발용 로그인으로 예시 방 참여자(재현=방장, 민지, …)를 고르면 예시 방(`/trips/1`)에 들어간다

브라우저는 `/api/*`, `/oauth2/*`, `/login/oauth2/*` 를 Next 로 부르고 Next 가 `BACKEND_URL`(기본 `http://localhost:8080`)로 넘긴다. 그래서 세션 쿠키가 프론트 주소 하나에만 붙는다.

## 카카오 로그인 켜기

1. [Kakao Developers](https://developers.kakao.com) → 내 애플리케이션 → 앱 만들기
2. 카카오 로그인 → 활성화, Redirect URI 에 `http://localhost:3000/login/oauth2/code/kakao` 등록
3. 동의항목 → 닉네임(profile_nickname) 사용
4. `backend/secrets.properties` 를 만들어 키를 넣는다(git 에 안 올라간다)

   ```properties
   KAKAO_CLIENT_ID=REST API 키
   # 보안 → Client Secret 을 켰다면
   KAKAO_CLIENT_SECRET=...
   ```

5. 백엔드를 다시 켜면 로그인 화면에 카카오 버튼이 나온다

배포할 때는 `FRONTEND_URL`(예: `https://tripmate.example.com`)을 바꾸고, 같은 주소의 Redirect URI 를 카카오에 하나 더 등록한다. `DEV_LOGIN` 은 켜지 않는다.

## 된 것

- 로그인: 카카오(키를 넣으면), 개발용 로그인(로컬만). 방 API 는 그 방 참여자만, 설문·표는 본인 것만, 역할 추천은 방장만
- 초대: 방 개요의 "초대 링크 복사" → `/join/{초대코드}` → 로그인 → 참여자로 들어가 설문부터
- 설문 저장·조회(이동 제약은 본인과 방장만), 역할 추천([알고리즘 설명](docs/matching.md)), 카드 이동(동시에 옮기면 409)
- 숙소: 링크 올리기(og:title·og:image 미리보기, 내부 주소 차단), 조건 체크, 한 사람 한 표, 그룹 취향(설문 평균·최솟값)
- 실시간: SSE(`GET /api/trips/{id}/events`). 설문·추천·이동·숙소·투표·참여가 커밋되면 같은 방 화면이 새로 읽는다. 15초마다 ping, 40초 조용하면 화면이 다시 붙는다

설계: [ERD](docs/erd.md)
