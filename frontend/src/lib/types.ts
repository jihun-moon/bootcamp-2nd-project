// Spring TripDto.Response 와 칸 이름을 맞춘다
export type Member = {
  id: number;
  name: string;
  host: boolean;
  surveyDone: boolean;
  // 카카오 프로필 사진(선택 동의). 없으면 null
  imageUrl: string | null;
};

export type Trip = {
  id: number;
  title: string;
  destination: string | null;
  startDate: string | null;
  endDate: string | null;
  inviteCode: string;
  // 로그인한 사람의 이 방 참여자 id
  meId: number;
  members: Member[];
};

// Spring RoleDto.Board
export type Role = {
  id: number;
  title: string;
  required: boolean;
  capacity: number;
  tasks: string[];
};

export type Assignment = {
  roleId: number;
  memberId: number;
  reasons: string[];
  version: number;
};

export type RoleBoard = {
  roles: Role[];
  assignments: Assignment[];
};

// Spring StayDto.Board
export type Stay = {
  id: number;
  title: string;
  url: string;
  imageUrl: string | null;
  amenities: string[];
  voterIds: number[];
};

export type StayBoard = {
  amenities: { key: string; label: string }[];
  preferences: Record<string, { avg: number; min: number }>;
  surveyed: number;
  stays: Stay[];
};
