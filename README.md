# TripMate

다인원 여행의 역할 배정과 숙소 투표를 돕는 웹 서비스.

| 폴더 | 무엇 | 여는 곳 |
|---|---|---|
| `backend/` | Spring Boot 4.1 · Java 25 · JPA · H2(로컬) / Postgres(배포) | IntelliJ에서 `backend/build.gradle` 열기 |
| `frontend/` | Next.js 16 · TypeScript · Tailwind | IntelliJ(Ultimate) 또는 VS Code |

## 로컬 실행

1. 백엔드: IntelliJ에서 `BackendApplication` 실행, 또는 `cd backend && ./gradlew bootRun` → http://localhost:8080
2. 프론트: `cd frontend && npm install && npm run dev` → http://localhost:3000
3. 예시 방: http://localhost:3000/trips/1 (백엔드가 켤 때마다 넣어 둔다)

브라우저는 `/api/*` 를 Next 로 부르고 Next 가 `BACKEND_URL`(기본 `http://localhost:8080`)로 넘긴다.

## 지금 된 것 / 목업인 것

- API 연결: 여행방 조회 `GET /api/trips/{id}`, 생성 `POST /api/trips`
- 목업(`frontend/src/lib/mock.ts`): 역할 추천, 역할 보드, 그룹 취향, 숙소 매트릭스
- 화면만: 설문 저장, 카카오 로그인, 초대 링크, 숙소 링크 추가
