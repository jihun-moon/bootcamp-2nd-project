# TripMate — 다인원 여행 역할 배정·숙소 투표

8명쯤 가는 여행에서 누가 무엇을 맡을지 **설문으로 추천**하고, 숙소는 **모두가 싫어하지 않는 곳**으로 고르게 돕는 웹 서비스입니다. 부트캠프 2기 팀 프로젝트.

## 기술

| 폴더 | 무엇 | 여는 곳 |
|---|---|---|
| `backend/` | Spring Boot 4.1 · Java 17 · Gradle · Spring Data JPA · Spring Security(카카오 OAuth2) · H2(로컬) / Postgres(배포) | IntelliJ에서 저장소 폴더 열기 → Gradle 불러오기 |
| `frontend/` | Next.js 16 · TypeScript · Tailwind CSS | IntelliJ(Ultimate) 또는 VS Code |

왜 이 기술인지, 코드는 어떻게 쓰는지는 [팀 코드 규칙](docs/코드-규칙.md)에 있습니다.

## 팀

| GitHub | 이름 | 맡은 기능 |
|---|---|---|
| [Parkjaehyun1204](https://github.com/Parkjaehyun1204) (팀장) | 박재현 | 로그인·초대·여행방, 배포 |
| [jihun-moon](https://github.com/jihun-moon) | 문지훈 | 설문·역할 추천 알고리즘·실시간 역할 보드 |
| [khs219012](https://github.com/khs219012) | 김현수 | 숙소 링크·투표·그룹 취향 |

누가 무엇을 남겨 두고 있는지는 [역할 분담과 일정](docs/05-역할분담.md)에 있습니다.

## 문서

1. [주제와 요구사항](docs/01-주제와-요구사항.md)
2. [화면 설계](docs/02-화면설계.md)
3. [DB 설계 (ERD)](docs/03-DB설계.md)
4. [URL 설계 (API)](docs/04-URL설계.md)
5. [역할 분담과 일정](docs/05-역할분담.md)
6. [역할 추천 알고리즘](docs/matching.md)
7. 각자 [내 파트 정리](docs/parts/_틀.md) → 발표

같이 작업하는 법은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있습니다.

## 로컬 실행

필요한 것: JDK 17 이상, Node.js 20 이상.

1. **백엔드**: IntelliJ 실행 메뉴의 `backend`(개발용 로그인 켜짐) ▶
   또는 터미널에서 `cd backend && DEV_LOGIN=true ./gradlew bootRun` → http://localhost:8080
2. **프론트**: `cd frontend && npm install && npm run dev` → http://localhost:3000
   IntelliJ 에서는 실행 메뉴 `backend + frontend` 로 둘 다 켤 수 있다
3. http://localhost:3000/login 에서 **개발용 로그인**으로 예시 방 참여자(재현=방장, 민지, …)를 고르면 예시 방(`/trips/1`)에 들어간다

DB 는 메모리 H2 라 설치할 것이 없고, 백엔드를 다시 켜면 데이터가 처음으로 돌아간다.

브라우저는 `/api/*`, `/oauth2/*`, `/login/oauth2/*` 를 Next 로 부르고 Next 가 Spring(`BACKEND_URL`, 기본 `http://localhost:8080`)으로 넘긴다. 그래서 세션 쿠키가 프론트 주소 하나에만 붙고 CORS 설정이 없다.

## 카카오 로그인 켜기 (키가 있는 사람만)

카카오 키가 없어도 개발용 로그인으로 모든 기능을 쓸 수 있다.

1. `backend/secrets.properties` 를 만들고 키를 넣는다(저장소에 안 올라간다. 팀장에게 값을 따로 받는다)

   ```properties
   KAKAO_CLIENT_ID=REST API 키
   KAKAO_CLIENT_SECRET=클라이언트 시크릿
   ```

2. 백엔드를 다시 켜면 로그인 화면에 카카오 버튼이 나온다

카카오 콘솔 설정(이미 되어 있음): 카카오 로그인 ON, 리다이렉트 URI `http://localhost:3000/login/oauth2/code/kakao`, 동의항목 닉네임(필수)·프로필 사진(선택).
배포할 때는 `FRONTEND_URL` 을 바꾸고 같은 주소의 리다이렉트 URI 를 하나 더 등록한다. `DEV_LOGIN` 은 켜지 않는다.

## 테스트

```bash
cd backend && ./gradlew test        # 53개
cd frontend && npx tsc --noEmit && npm run lint && npm run build
```
