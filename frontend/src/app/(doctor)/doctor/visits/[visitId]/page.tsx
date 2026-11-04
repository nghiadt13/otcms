import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DoctorVisitPlaceholderPage } from "@/features/doctor-shell/components/DoctorVisitPlaceholderPage";
import { findVisitNavItem } from "@/features/doctor-shell/doctorVisitNavigation";

export const metadata: Metadata = { title: "Tổng quan lượt khám" };

export default async function DoctorVisitWorkspacePage({
  params,
}: {
  params: Promise<{ visitId: string }>;
}) {
  const { visitId } = await params;
  const item = findVisitNavItem("");
  if (!item) notFound();
  return <DoctorVisitPlaceholderPage item={item} visitId={visitId} />;
}
