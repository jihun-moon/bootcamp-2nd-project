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

// 아래는 아직 API 가 없어 목업으로만 쓴다
export type Stay = {
  id: string;
  title: string;
  url: string;
  tags: string[];
};
