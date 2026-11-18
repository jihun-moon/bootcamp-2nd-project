# TripMate

7~8명이 같이 가는 여행에서 **누가 무엇을 맡을지 설문으로 추천**하고, 숙소는 **모두가 싫어하지 않는 곳**으로 고르게 돕는 웹 서비스입니다.
부트캠프 2기 팀 프로젝트입니다.

| 기능 | 하는 일 |
|---|---|
| 설문 → 역할 추천 | 차 유무·계획 성향·맡기 싫은 일로 운전·총무·장보기 등을 추천하고, 카드마다 근거를 보여 줍니다 |
| 실시간 역할 보드 | 카드를 끌어 역할을 바꾸면 같은 방 사람들 화면에 바로 반영됩니다 |
| 숙소 투표 | 링크를 올리면 제목·사진을 가져오고, 조건별 **평균과 최솟값**으로 "의견이 갈린 조건"을 보여 줍니다 |
| 로그인·초대 | 카카오 로그인, 초대 링크로 방에 참여합니다 |

## 바로 실행

필요한 것: **JDK 17 이상** 하나. DB 는 메모리 H2 라 설치할 것이 없습니다.

1. IntelliJ 에서 저장소 폴더를 열고 Gradle 을 불러온다
2. 오른쪽 위 실행 메뉴에서 **`tripmate`** ▶ (개발용 로그인이 켜져 있다)
3. http://localhost:8080/login → 예시 방 참여자(**재현** = 방장, 민지, 도윤 …)를 누르면 예시 방에 들어간다

터미널에서는 이렇게 띄웁니다.

```bash
DEV_LOGIN=true ./gradlew bootRun
```

서버를 다시 켜면 데이터는 처음으로 돌아갑니다. 실시간 반영은 브라우저 창 두 개(하나는 시크릿 창)로 다른 사람으로 들어가 보면 확인할 수 있습니다.

## 구조

수업(Spring 노션)에서 배운 짜임 그대로입니다.

```
브라우저 ─GET /trips/1/roles─▶ RolePageController ─▶ templates/role/roles.html (Thymeleaf)
   │
   └─ roles.js 의 $.ajax ─GET /api/trips/1/roles─▶ RoleController ─▶ RoleService ─▶ RoleRepository ─▶ DB
                                                     (JSON, ResponseEntity)
```

| 무엇 | 쓰는 것 |
|---|---|
| 서버 | Spring Boot 4 · Java 17 · Gradle |
| 화면 | Thymeleaf · HTML · CSS · jQuery |
| DB | Spring Data JPA · H2(로컬) / PostgreSQL(배포) |
| 로그인 | Spring Security + 카카오 OAuth2 (세션) |
| 실시간 | SSE(`SseEmitter`) |

```
src/main/java/com/tripmate/
  user/    로그인            박재현
  trip/    여행방·초대       박재현
  survey/  설문              문지훈
  role/    역할 추천·보드    문지훈
  stay/    숙소·투표         김현수
src/main/resources/
  templates/   화면(기능별 폴더) + fragments/common.html(공통 머리)
  static/      js/(화면별 jQuery), css/style.css
docs/          설계 문서, 각자 발표 정리(parts/)
```

## 팀

| GitHub | 이름 | 맡은 기능 |
|---|---|---|
| [Parkjaehyun1204](https://github.com/Parkjaehyun1204) (팀장) | 박재현 | 로그인·초대·여행방, 배포 |
| [jihun-moon](https://github.com/jihun-moon) | 문지훈 | 설문·역할 추천 알고리즘·실시간 역할 보드 |
| [khs219012](https://github.com/khs219012) | 김현수 | 숙소 링크·투표·그룹 취향 |

## 문서

1. [주제와 요구사항](docs/01-주제와-요구사항.md)
2. [화면 설계](docs/02-화면설계.md)
3. [DB 설계 (ERD)](docs/03-DB설계.md)
4. [URL 설계 (API)](docs/04-URL설계.md)
5. [역할 분담과 일정](docs/05-역할분담.md)
6. [역할 추천 알고리즘](docs/matching.md)
7. [팀 코드 규칙](docs/코드-규칙.md)
8. 각자 [내 파트 정리](docs/parts/_틀.md) → 발표

같이 작업하는 법(브랜치·PR)은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있습니다.

## 테스트

```bash
./gradlew test
```

API 와 화면 테스트 56개가 돕니다. PR 을 올리기 전에 통과해야 합니다.

## 카카오 로그인 켜기 (키가 있는 사람만)

키가 없어도 개발용 로그인으로 모든 기능을 쓸 수 있습니다.

1. 저장소 맨 위 폴더에 `secrets.properties` 를 만든다(저장소에 안 올라감. 값은 팀장에게 따로 받는다)

   ```properties
   KAKAO_CLIENT_ID=REST API 키
   KAKAO_CLIENT_SECRET=클라이언트 시크릿
   ```

2. 서버를 다시 켜면 로그인 화면에 카카오 버튼이 나온다

카카오 개발자 콘솔의 Redirect URI 는 `http://localhost:8080/login/oauth2/code/kakao` 여야 합니다.
배포할 때는 `{배포 주소}/login/oauth2/code/kakao` 를 하나 더 등록하고, `DEV_LOGIN` 은 켜지 않습니다.
