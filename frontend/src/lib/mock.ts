import type { Stay } from "./types";

// 숙소 API 가 생기기 전까지 화면을 채우는 값

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
