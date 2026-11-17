import Link from "next/link";
import { Suspense } from "react";
import { getTrip } from "@/lib/api";
import TripTabs from "./TripTabs";

async function TripHeader({ params }: { params: Promise<{ tripId: string }> }) {
  const { tripId } = await params;
  const trip = await getTrip(tripId);
  const period = trip.startDate ? `${trip.startDate} ~ ${trip.endDate ?? ""}` : "날짜 미정";

  return (
    <>
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
        <Link href="/" className="text-sm text-muted">TripMate</Link>
        <Suspense fallback={<div className="h-14" />}>
          <TripHeader params={params} />
        </Suspense>
      </header>
      <Suspense fallback={<div className="h-10 border-b border-line" />}>
        <TripTabs />
      </Suspense>
      <main className="pt-6">{children}</main>
    </div>
  );
}
