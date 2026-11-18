import { getTrip } from "@/lib/api";
import SurveyForm from "./SurveyForm";

export default async function SurveyPage({ params }: PageProps<"/trips/[tripId]">) {
  const { tripId } = await params;
  const trip = await getTrip(tripId);
  const me = trip.members.find((m) => m.id === trip.meId)!;
  return <SurveyForm tripId={tripId} me={me} />;
}
