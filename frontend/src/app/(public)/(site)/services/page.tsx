import type { Metadata } from "next";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { listPublicServices } from "@/features/clinic-services/api/publicServiceApi";
import { PublicServiceList } from "@/features/clinic-services/components/PublicServiceList";
import { PublicServiceSearchForm } from "@/features/clinic-services/components/PublicServiceSearchForm";
import { parseServiceListQuery } from "@/features/clinic-services/schema/serviceListQuery";

export const metadata: Metadata = { title: "Dịch vụ và bảng giá | OTCMS" };

// UC71 Xem dịch vụ và giá. Tiêu chí đọc từ URL (?q=&type=) nên lọc vẫn chạy khi chưa có
// JavaScript. Tiêu chí sai (MSG-54) hiện ngay dưới form và không gọi danh mục.
export default async function ServicesPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { input, query, error } = parseServiceListQuery(await searchParams);
  const services = error ? null : (await listPublicServices(query)).items;

  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <PublicPageHeader
        description="Giá công khai của các dịch vụ đang cung cấp tại phòng khám."
        title="Dịch vụ và bảng giá"
      />
      <PublicServiceSearchForm className="mt-10" error={error} input={input} />
      {services ? (
        <PublicServiceList
          filtered={Boolean(query.q || query.type)}
          services={services}
        />
      ) : null}
    </div>
  );
}
