import { getStayBoard, getTrip } from "@/lib/api";
import StaysView from "./StaysView";

export default async function StaysPage({ params }: PageProps<"/trips/[tripId]">) {
  const { tripId } = await params;
  const [trip, board] = await Promise.all([getTrip(tripId), getStayBoard(tripId)]);
  return <StaysView tripId={tripId} members={trip.members} board={board} />;
}
