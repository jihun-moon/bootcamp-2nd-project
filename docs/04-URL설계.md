# 4. URL 설계

규칙: 주소는 명사(복수, 소문자, 하이픈), 행위는 HTTP 메서드. 한 개는 경로(`/trips/3`), 검색·페이지는 쿼리.

- 화면 주소(Thymeleaf, `@Controller`)는 [2. 화면 설계](02-화면설계.md)에 있다
- 데이터는 모두 `/api/**`(JSON, `@RestController`)로 주고받는다. 화면의 jQuery `$.ajax` 가 부른다
- 로그인 안 했으면 401, 그 방 참여자가 아니면 404(방이 있는지도 알려 주지 않는다), 본인·방장만 되는 일은 403
- 각 Controller 메서드 위에 부르는 예 한 줄이 있다(`// PUT: /api/trips/1/members/2/survey`)

## 로그인·초대·여행방 (박재현)

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 코드 |
|---|---|---|---|---|---|---|
| REQ-01 | 카카오 로그인 시작 | GET | `/oauth2/authorization/kakao` | | 302 → 카카오 | Spring Security |
| REQ-01 | 카카오가 돌려보내는 곳 | GET | `/login/oauth2/code/kakao` | | 302 → `/login/done` | Spring Security, `KakaoUserService` |
| REQ-01 | 개발용 로그인(로컬만) | POST | `/api/dev/login` | `{"name": "민지"}` | 204 | `DevLoginController` |
| REQ-01 | 내 정보 | GET | `/api/me` | | 200 `{id, nickname}` | `AuthController` |
| REQ-01 | 로그인 버튼 고르기 | GET | `/api/auth/options` | | 200 `{kakao, dev}` | `AuthController` |
| REQ-01 | 로그아웃 | POST | `/api/logout` | | 204 | Spring Security |
| REQ-02 | 방 만들기 | POST | `/api/trips` | `{"title", "destination", "startDate", "endDate"}` | 201 방 | `TripController`, `TripService` |
| REQ-04 | 방 보기 | GET | `/api/trips/{id}` | | 200 방(참여자, `meId`, `inviteCode`) | `TripController`, `TripService` |
| REQ-03 | 초대 링크로 참여 | POST | `/api/invites/{inviteCode}` | | 200 `{tripId}` | `TripController`, `TripService` |
| REQ-11 | 바뀜 알림(SSE) | GET | `/api/trips/{id}/events` | | `event: change` / `ping` | `TripController`, `TripEvents` |
| REQ-05 | 방 고치기 *(남은 일)* | PATCH | `/api/trips/{id}` | 바꿀 칸 | 200 | |
| REQ-05 | 방 지우기 *(남은 일)* | DELETE | `/api/trips/{id}` | | 204 | |
| REQ-06 | 나가기·내보내기 *(남은 일)* | DELETE | `/api/trips/{id}/members/{memberId}` | | 204 | |

## 설문·역할 (문지훈)

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 코드 |
|---|---|---|---|---|---|---|
| REQ-07 | 설문 저장(처음·다시) | PUT | `/api/trips/{id}/members/{memberId}/survey` | `{"activity", "planning", "hasCar", "separateDeparture", "mobilityLimited", "avoid": [], "preferences": {}}` | 200 | `SurveyController`, `SurveyService` |
| REQ-08 | 설문 보기 | GET | `/api/trips/{id}/members/{memberId}/survey` | | 200 (본인·방장 외에는 `mobilityLimited` 없음) | `SurveyController`, `SurveyService` |
| REQ-09 | 역할 보드 | GET | `/api/trips/{id}/roles` | | 200 `{roles, assignments}` | `RoleController`, `RoleService` |
| REQ-09 | 역할 추천(방장) | POST | `/api/trips/{id}/roles/recommend` | | 200 보드 | `RoleController`, `RoleService.recommend`, `RoleScorer`, `HungarianAssigner` |
| REQ-10 | 카드 옮기기 | PATCH | `/api/trips/{id}/roles/assignments/{memberId}` | `{"roleId": 4, "version": 0}` | 200 보드 / 409 | `RoleController`, `RoleService.move` |
| REQ-18 | 이동·회비 근거 | GET | `/api/trips/{id}/roles/travel` | | 200 `{message, legs, perPerson, fuelPrice, fuelSource, kmPerLiter, tips}` | `RoleController`, `RoleService.travelPlan`, `TravelService`, `KakaoMapClient`, `FuelPriceClient` |
| REQ-12 | 역할 더하기·고치기·지우기 *(남은 일)* | POST / PATCH / DELETE | `/api/trips/{id}/roles`, `/api/trips/{id}/roles/{roleId}` | | 201 / 200 / 204 | |

## 숙소·투표 (김현수)

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 코드 |
|---|---|---|---|---|---|---|
| REQ-15 | 숙소 목록·그룹 취향 | GET | `/api/trips/{id}/stays` | | 200 `{amenities, preferences, surveyed, stays}` | `StayController` |
| REQ-13 | 숙소 올리기 | POST | `/api/trips/{id}/stays` | `{"url", "title", "amenities": []}` | 201 | `StayController`, `LinkPreview` |
| REQ-14 | 투표·바꾸기·취소 | PUT | `/api/trips/{id}/votes/{memberId}` | `{"stayId": 3}` (null 이면 취소) | 204 | `StayController` |
| REQ-16 | 숙소 고치기·지우기 *(남은 일)* | PATCH / DELETE | `/api/trips/{id}/stays/{stayId}` | 조건 | 200 / 204 | |

API 는 IntelliJ HTTP Client 나 Talend API Tester 로 시험할 수 있다. 로그인이 필요하니 먼저 `POST /api/dev/login` 으로 세션 쿠키를 받는다.
