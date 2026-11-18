import { getTrip } from "@/lib/api";
import Avatar from "@/app/Avatar";
import InviteButton from "./InviteButton";

export default async function TripOverview({ params }: PageProps<"/trips/[tripId]">) {
  const { tripId } = await params;
  const trip = await getTrip(tripId);
  const done = trip.members.filter((m) => m.surveyDone).length;

  return (
    <div className="space-y-6">
      <section className="rounded-xl border border-line bg-card p-4">
        <div className="mb-2 flex justify-between text-sm">
          <span>설문 진행</span>
          <span className="text-muted">{done}/{trip.members.length}</span>
        </div>
        <div className="h-2 rounded-full bg-line">
          <div className="h-2 rounded-full bg-accent" style={{ width: `${(done / trip.members.length) * 100}%` }} />
        </div>
      </section>

      <section className="rounded-xl border border-line bg-card p-4">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold">참여자</h2>
          <InviteButton code={trip.inviteCode} />
        </div>
        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {trip.members.map((m) => (
            <li key={m.id} className="flex items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm">
              <Avatar name={m.name} imageUrl={m.imageUrl} />
              <div>
                <div className="font-medium">{m.name}{m.host && " · 방장"}</div>
                <div className="text-muted">{m.surveyDone ? "설문 완료" : "설문 전"}</div>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
