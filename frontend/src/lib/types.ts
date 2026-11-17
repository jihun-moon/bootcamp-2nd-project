// Spring TripDto.Response 와 칸 이름을 맞춘다
export type Member = {
  id: number;
  name: string;
  host: boolean;
  surveyDone: boolean;
};

export type Trip = {
  id: number;
  title: string;
  destination: string | null;
  startDate: string | null;
  endDate: string | null;
  members: Member[];
};

// 아래는 아직 API 가 없어 목업으로만 쓴다
export type Role = {
  id: string;
  title: string;
  required: boolean;
  tasks: string[];
};

export type Assignment = {
  roleId: string;
  memberId: number;
  reasons: string[];
  version: number;
};

export type Stay = {
  id: string;
  title: string;
  url: string;
  tags: string[];
};
