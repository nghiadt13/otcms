import type { Metadata } from "next";
import { cache } from "react";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { getPublicService } from "@/features/clinic-services/api/publicServiceApi";
import { PublicServiceDetail } from "@/features/clinic-services/components/PublicServiceDetail";
import { loadOrNotFound } from "@/lib/api/loadOrNotFound";

type Props = { params: Promise<{ serviceId: string }> };

// Chi tiết dịch vụ (UC71 bước 5–6). Dịch vụ đã ngừng hoặc không có → 404 MSG-50 qua notFound().
// `cache` để metadata và trang chỉ gọi API một lần trong một lượt render.
const loadService = cache((serviceId: string) =>
  loadOrNotFound(() => getPublicService(serviceId)),
);

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { serviceId } = await params;
  const service = await loadService(serviceId);
  return { title: `${service.name} | Dịch vụ và bảng giá` };
}

export default async function ServiceDetailPage({ params }: Props) {
  const { serviceId } = await params;
  const service = await loadService(serviceId);
  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <PublicServiceDetail service={service} />
    </div>
  );
}
