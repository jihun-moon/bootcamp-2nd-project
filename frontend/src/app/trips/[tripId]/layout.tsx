import Link from "next/link";
import { Suspense } from "react";
import { getTrip } from "@/lib/api";
import LiveRefresh from "./LiveRefresh";
import TripTabs from "./TripTabs";
import UserMenu from "./UserMenu";

async function TripHeader({ params }: { params: Promise<{ tripId: string }> }) {
  const { tripId } = await params;
  const trip = await getTrip(tripId);
  const period = trip.startDate ? `${trip.startDate} ~ ${trip.endDate ?? ""}` : "날짜 미정";
  const me = trip.members.find((m) => m.id === trip.meId);

  return (
    <>
      <div className="flex items-center justify-between">
        <Link href="/" className="text-sm text-muted">TripMate</Link>
        {me && <UserMenu name={me.name} imageUrl={me.imageUrl} />}
      </div>
      <h1 className="text-2xl font-bold">{trip.title}</h1>
      <p className="text-sm text-muted">
        {trip.destination || "지역 미정"} · {period} · {trip.members.length}명
      </p>
    </>
  );
}

export default function TripLayout({ children, params }: LayoutProps<"/trips/[tripId]">) {
  return (
    <div className="mx-auto w-full max-w-4xl flex-1 px-4 py-6">
      <header className="mb-4 space-y-1">
        <Suspense fallback={<div className="h-20" />}>
          <TripHeader params={params} />
        </Suspense>
      </header>
      <Suspense fallback={<div className="h-10 border-b border-line" />}>
        <TripTabs />
        <LiveRefresh />
      </Suspense>
      <main className="pt-6">{children}</main>
    </div>
  );
}
