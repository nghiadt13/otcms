import { notFound } from "next/navigation";
import { DoctorVisitPlaceholderPage } from "@/features/doctor-shell/components/DoctorVisitPlaceholderPage";
import {
  doctorVisitNavItems,
  findVisitNavItem,
} from "@/features/doctor-shell/doctorVisitNavigation";

// Trang chờ chung cho mọi màn của workspace khám. Màn nào dựng thật thì tạo thư mục tĩnh
// `visits/[visitId]/<section>/page.tsx`; Next ưu tiên route tĩnh nên không cần sửa file này.
export function generateStaticParams() {
  return doctorVisitNavItems
    .filter((item) => item.section)
    .map((item) => ({ section: item.section }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ section: string }>;
}) {
  const { section } = await params;
  return { title: findVisitNavItem(section)?.title };
}

export default async function DoctorVisitSectionPage({
  params,
}: {
  params: Promise<{ visitId: string; section: string }>;
}) {
  const { visitId, section } = await params;
  const item = section ? findVisitNavItem(section) : undefined;
  if (!item) notFound();
  return <DoctorVisitPlaceholderPage item={item} visitId={visitId} />;
}
