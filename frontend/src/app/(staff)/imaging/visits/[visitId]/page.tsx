import { VisitProgressScreen } from "@/features/imaging/components/VisitProgressScreen";

export default async function Page({
  params,
}: {
  params: Promise<{ visitId: string }>;
}) {
  const { visitId } = await params;
  return <VisitProgressScreen id={visitId} />;
}
