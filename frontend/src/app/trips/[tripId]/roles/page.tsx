import { getRoleBoard, getTrip } from "@/lib/api";
import RoleBoard from "./RoleBoard";

export default async function RolesPage({ params }: PageProps<"/trips/[tripId]">) {
  const { tripId } = await params;
  const [trip, board] = await Promise.all([getTrip(tripId), getRoleBoard(tripId)]);

  // 추천이나 다른 사람의 이동(SSE 로 새로 읽음)으로 배정이 바뀌면 key 가 바뀌어 보드 상태를 새로 시작한다.
  // version 까지 넣어야 누가 옮겼다 되돌려 놓은 경우에도 낡은 version 을 들고 있지 않는다
  return (
    <RoleBoard
      key={board.assignments.map((a) => `${a.memberId}:${a.roleId}:${a.version}`).join()}
      tripId={tripId}
      members={trip.members}
      roles={board.roles}
      initial={board.assignments}
    />
  );
}
