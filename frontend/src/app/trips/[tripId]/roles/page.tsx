import { getRoleBoard, getTrip } from "@/lib/api";
import RoleBoard from "./RoleBoard";

export default async function RolesPage({ params }: PageProps<"/trips/[tripId]">) {
  const { tripId } = await params;
  const [trip, board] = await Promise.all([getTrip(tripId), getRoleBoard(tripId)]);

  // 추천을 다시 받으면 배정이 바뀌므로 key 로 보드 상태를 새로 시작한다
  return (
    <RoleBoard
      key={board.assignments.map((a) => `${a.memberId}:${a.roleId}`).join()}
      tripId={tripId}
      members={trip.members}
      roles={board.roles}
      initial={board.assignments}
    />
  );
}
