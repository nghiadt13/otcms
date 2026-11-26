import type { Metadata } from "next";
import { LaboratoryPreview } from "@/features/laboratory/components/LaboratoryPreview";

export const metadata: Metadata = { title: "Xét nghiệm | OTCMS" };

export default async function LaboratoryPage({
  searchParams,
}: {
  searchParams: Promise<{ view?: string }>;
}) {
  const { view } = await searchParams;
  const screens = {
    overview: "Tổng quan",
    queue: "Danh sách chờ",
    history: "Trạng thái chỉ định",
    rooms: "Phòng dịch vụ",
    schedule: "Lịch làm việc",
    notifications: "Thông báo",
    account: "Tài khoản của tôi",
  } as const;
  return (
    <LaboratoryPreview
      initialScreen={screens[view as keyof typeof screens] ?? "Tổng quan"}
    />
  );
}
