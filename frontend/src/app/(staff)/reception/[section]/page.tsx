import { Suspense } from "react";
import { notFound } from "next/navigation";
import { ReceptionScreen } from "@/features/patient-reception/components/ReceptionScreen";
import { DataLoading } from "@/features/patient-reception/components/ReceptionUi";
import { receptionNavigation } from "@/features/patient-reception/navigation";

export function generateStaticParams() {
  return receptionNavigation
    .filter((item) => item.section)
    .map((item) => ({ section: item.section }));
}
export default async function Page({
  params,
}: {
  params: Promise<{ section: string }>;
}) {
  const { section } = await params;
  const item = receptionNavigation.find((item) => item.section === section);
  if (!item) notFound();
  return (
    <Suspense fallback={<DataLoading />}>
      <ReceptionScreen key={item.section} section={item.section} />
    </Suspense>
  );
}
