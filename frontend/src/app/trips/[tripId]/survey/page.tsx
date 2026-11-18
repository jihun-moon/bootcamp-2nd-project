import { getTrip } from "@/lib/api";
import SurveyForm from "./SurveyForm";

export default async function SurveyPage({ params }: PageProps<"/trips/[tripId]">) {
  const { tripId } = await params;
  const trip = await getTrip(tripId);
  return <SurveyForm tripId={tripId} members={trip.members} />;
}
