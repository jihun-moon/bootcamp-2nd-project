import { getTrip } from "@/lib/api";
import { assignments, roles } from "@/lib/mock";
import RoleBoard from "./RoleBoard";

export default async function RolesPage({ params }: PageProps<"/trips/[tripId]">) {
  const { tripId } = await params;
  const trip = await getTrip(tripId);

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted">
        추천 결과입니다. 카드를 끌어 다른 역할로 옮길 수 있어요. (지금은 이 화면에서만 바뀝니다)
      </p>
      <RoleBoard members={trip.members} roles={roles} initial={assignments} />
    </div>
  );
}
