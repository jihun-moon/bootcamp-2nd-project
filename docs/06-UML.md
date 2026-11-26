# 6. UML

소프트웨어공학 수업 순서대로 유스케이스 → 클래스 → 시퀀스를 그렸다. 표(ERD)는 [3. DB 설계](03-DB설계.md)에 있다.
GitHub 에서 열면 그림으로 보인다(mermaid). 수정은 이 파일 글자를 고치면 된다.

## 1. 유스케이스

누가 무엇을 하는지. 방장은 참여자가 하는 일을 다 하고, 둘레 친 일을 더 한다.

```mermaid
flowchart LR
    host([방장])
    member([참여자])
    kakao[[카카오]]

    subgraph TripMate
        login((로그인))
        guest((이름만 쓰고 참여))
        create((여행방 만들기))
        invite((초대 링크 보내기))
        join((초대 링크로 참여))
        survey((설문하기))
        dates((되는 날 누르기))
        recommend((역할 추천받기))
        move((역할 카드 옮기기))
        addStay((숙소 링크 올리기))
        vote((숙소 투표))
        shop((장보기 내가 살게))
        settle((먼저 낸 돈·보냈어요))
        leave((방 나가기))
        manage((이름 고치기·방장 넘기기·빼기))
        delete((방 지우기))
    end

    member --- login
    member --- guest
    member --- join
    member --- survey
    member --- dates
    member --- move
    member --- addStay
    member --- vote
    member --- shop
    member --- settle
    member --- leave
    host --- create
    host --- invite
    host --- recommend
    host --- manage
    host --- delete
    host -. 참여자가 하는 일도 함 .-> member
    login --- kakao
```

| 유스케이스 | 할 수 있는 사람 | 요구사항 |
|---|---|---|
| 로그인 | 누구나 | REQ-01 |
| 여행방 만들기, 초대 링크 보내기 | 로그인한 사람(만들면 방장) | REQ-02, REQ-03 |
| 초대 링크로 참여 | 로그인한 사람, 또는 로그인 없이 이름만 쓴 손님 | REQ-03 |
| 설문하기 | 참여자 본인 | REQ-07, REQ-08 |
| 역할 추천받기 | 방장 | REQ-09 |
| 역할 카드 옮기기 | 참여자 | REQ-10, REQ-11 |
| 숙소 링크 올리기, 투표 | 참여자 | REQ-13~15 |
| 되는 날 누르기, 장보기 내가 살게, 먼저 낸 돈 적기·보냈어요 | 참여자 | (번호 없음, [1. 요구사항](01-주제와-요구사항.md)) |
| 방 나가기 | 참여자 본인 | REQ-06 |
| 이름·지역 고치기, 방장 넘기기, 참여자 빼기, 방 지우기 | 방장 | REQ-05, REQ-06 |

## 2. 클래스 다이어그램

기능마다 같은 모양이다. 화면 컨트롤러는 템플릿만 돌려주고, API 컨트롤러가 권한을 본 뒤 Service 에 맡긴다.
아래는 역할 기능(문지훈). 다른 기능도 `XxxPageController`, `XxxController`, `XxxService`, `XxxRepository` 이름으로 같다.

```mermaid
classDiagram
    direction LR

    class RolePageController {
        +roles(tripId, model) String
    }
    class RoleController {
        -TripAccess tripAccess
        -RoleService roleService
        +getBoard(tripId) ResponseEntity
        +move(tripId, memberId, req) ResponseEntity
        +addRole(tripId, req) ResponseEntity
        +updateRole(tripId, roleId, req) ResponseEntity
        +deleteRole(tripId, roleId) ResponseEntity
        +recommend(tripId) ResponseEntity
        +travel(tripId) ResponseEntity
    }
    class RoleService {
        -int REQUIRED_BONUS
        +board(tripId) Board
        +move(tripId, memberId, req) Board
        +addRole(tripId, req) Board
        +updateRole(tripId, roleId, req) Board
        +deleteRole(tripId, roleId)
        +recommend(tripId) Board
    }
    class RoleScorer {
        +score(survey, kind)$ Score
    }
    class HungarianAssigner {
        +assign(score)$ int[]
    }
    class TripAccess {
        +me(tripId) Member
        +self(tripId, memberId) Member
        +selfOrHost(tripId, memberId) Member
        +host(tripId) Member
    }
    class RoleRepository {
        <<interface>>
        +findByTripIdOrderById(tripId) List~Role~
    }
    class AssignmentRepository {
        <<interface>>
        +findByRoleTripIdOrderById(tripId) List~Assignment~
        +findByMemberId(memberId) Optional~Assignment~
    }
    class Role {
        -RoleKind kind
        -String title
        -boolean required
        -int capacity
    }
    class Assignment {
        -List~String~ reasons
        -Long version
        +moveTo(role)
    }
    class Member {
        -String name
        -boolean host
    }

    RoleController --> TripAccess : 권한
    RoleController --> RoleService
    RoleService --> RoleScorer : 점수
    RoleService --> HungarianAssigner : 배정
    RoleService --> RoleRepository
    RoleService --> AssignmentRepository
    RoleRepository ..> Role
    AssignmentRepository ..> Assignment
    Assignment --> Member
    Assignment --> Role
```

기능 폴더는 층마다 같은 이름이다(예: `controller/role`, `service/role`, `entity/role`).

| 기능 폴더 | 화면 컨트롤러 | API 컨트롤러 | 서비스 | 엔티티 |
|---|---|---|---|---|
| `user` | `LoginPageController` | `AuthController` | `KakaoUserService`, `GuestService` | `AppUser`, `PersistentLogin` |
| `trip` | `TripPageController` | `TripController` | `TripService` | `Trip`, `Member` |
| `survey` | `SurveyPageController` | `SurveyController` | `SurveyService` | `SurveyAnswer` |
| `role` | `RolePageController` | `RoleController` | `RoleService` | `Role`, `Assignment` |
| `stay` | `StayPageController` | `StayController` | (김현수 1주 차) | `Stay`, `Vote` |
| `shopping` | `ShoppingPageController` | `ShoppingController` | `ShoppingService` | `ShoppingPlan`, `ShoppingClaim` |
| `settle` | (영수증·정산 화면은 `TripPageController.receipt`) | `SettleController` | `SettleService` | `Expense`, `Payment` |

## 3. 시퀀스 다이어그램

### 역할 카드 옮기기 (동시에 옮기면 409, 다른 화면은 SSE 로 갱신)

```mermaid
sequenceDiagram
    actor A as 민지 화면
    actor B as 도윤 화면
    participant C as RoleController
    participant S as RoleService
    participant DB as DB
    participant E as TripEvents(SSE)

    A->>C: PATCH /api/trips/1/roles/assignments/3 {roleId: 4, version: 0}
    C->>C: tripAccess.me(1) 참여자인지
    C->>S: move(1, 3, req)
    S->>DB: 지금 version 읽기 (0)
    S->>DB: role 바꾸고 저장 (version 1)
    S-->>E: TripChanged(1, "roles")
    C-->>A: 200 보드
    E-->>B: event: change / data: roles
    B->>C: GET /api/trips/1/roles (다시 그림)

    Note over B: 도윤이 옛 화면(version 0)으로 같은 카드를 옮기면
    B->>C: PATCH ... {roleId: 2, version: 0}
    C->>S: move
    S-->>C: 409 CONFLICT (version 0 ≠ 1)
    C-->>B: 409 → "다른 사람이 먼저 옮겼어요"
```

### 역할 추천 (방장)

```mermaid
sequenceDiagram
    actor H as 재현(방장) 화면
    participant C as RoleController
    participant S as RoleService
    participant R as RoleScorer
    participant G as HungarianAssigner
    participant DB as DB

    H->>C: POST /api/trips/1/roles/recommend
    C->>C: tripAccess.host(1) 방장인지 (아니면 403)
    C->>S: recommend(1)
    S->>DB: 참여자, 역할, 설문 한 번에 읽기
    loop 사람 × 역할
        S->>R: score(설문, 역할)
        R-->>S: 점수 + 근거("차 있음 +4")
    end
    S->>G: assign(점수표)
    G-->>S: 사람마다 칸 번호
    S->>DB: 옛 배정 지우고 새로 저장
    C-->>H: 200 보드(카드마다 근거)
```

점수 규칙과 헝가리안을 쓴 까닭은 [역할 추천 알고리즘](matching.md)에 있다.
