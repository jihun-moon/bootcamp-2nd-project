import type { Assignment, Role, Stay } from "./types";

// 역할·숙소 API 가 생기기 전까지 화면을 채우는 값. memberId 는 백엔드 예시 방(id 1)의 참여자 id

export const roles: Role[] = [
  { id: "r1", title: "총무·타임라인", required: true, tasks: ["회비 걷기", "일정표 공유"] },
  { id: "r2", title: "운전", required: true, tasks: ["차량 픽업", "주유"] },
  { id: "r3", title: "장보기", required: true, tasks: ["고기 사기", "술·음료"] },
  { id: "r4", title: "바베큐 세팅", required: false, tasks: ["그릴 준비", "뒷정리"] },
  { id: "r5", title: "여행 DJ", required: false, tasks: ["플레이리스트"] },
  { id: "r6", title: "기록 담당", required: false, tasks: ["사진·영상", "후기 정리"] },
];

export const assignments: Assignment[] = [
  { roleId: "r1", memberId: 1, reasons: ["계획형 +3"], version: 1 },
  { roleId: "r2", memberId: 3, reasons: ["자차 보유 +4"], version: 1 },
  { roleId: "r2", memberId: 5, reasons: ["자차 보유 +4"], version: 1 },
  { roleId: "r3", memberId: 2, reasons: ["요리 선호 +2"], version: 1 },
  { roleId: "r4", memberId: 7, reasons: ["이동 적은 역할 우선 +2"], version: 1 },
  { roleId: "r6", memberId: 8, reasons: ["촬영 선호 +3"], version: 1 },
];

// 숙소 매트릭스의 열이자 그룹 취향 차트의 축
export const stayTags = ["독채", "개별 바베큐", "온수풀", "노래방", "방 3개 이상", "화장실 2개", "1층·엘리베이터"];

// 축마다 1~5점. 평균만 보면 4:4 로 갈린 항목이 무난해 보여서 최솟값을 같이 든다
export const groupPreference: Record<string, { avg: number; min: number }> = {
  "독채": { avg: 4.4, min: 4 },
  "개별 바베큐": { avg: 4.6, min: 4 },
  "온수풀": { avg: 3.1, min: 1 },
  "노래방": { avg: 2.9, min: 1 },
  "방 3개 이상": { avg: 4.0, min: 3 },
  "화장실 2개": { avg: 3.8, min: 3 },
  "1층·엘리베이터": { avg: 3.0, min: 3 },
};

export const stays: Stay[] = [
  { id: "s1", title: "숲속 독채 펜션", url: "https://example.com/a", tags: ["독채", "개별 바베큐", "방 3개 이상"] },
  { id: "s2", title: "온수풀 풀빌라", url: "https://example.com/b", tags: ["온수풀", "노래방", "방 3개 이상", "화장실 2개"] },
  { id: "s3", title: "강변 단층 펜션", url: "https://example.com/c", tags: ["독채", "1층·엘리베이터", "개별 바베큐", "화장실 2개"] },
];
