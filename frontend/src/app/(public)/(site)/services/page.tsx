import type { Metadata } from "next";
import Link from "next/link";
import { publicServiceCategories } from "@/lib/routes/publicServices";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { listPublicServices } from "@/features/clinic-services/api/publicServiceApi";
import { PublicServiceList } from "@/features/clinic-services/components/PublicServiceList";
import { PublicServiceSearchForm } from "@/features/clinic-services/components/PublicServiceSearchForm";
import { parseServiceListQuery } from "@/features/clinic-services/schema/serviceListQuery";

export const metadata: Metadata = { title: "Dịch vụ và bảng giá | OTCMS" };

// UC71 Xem dịch vụ và giá. Tiêu chí đọc từ URL (?q=) nên lọc vẫn chạy khi chưa có
// JavaScript. Tiêu chí sai (MSG-54) hiện ngay dưới form và không gọi danh mục.
export default async function ServicesPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const category = publicServiceCategories.find(
    ({ type }) => type === params.type,
  );
  const { input, query, error } = parseServiceListQuery(params);
  const catalog = error ? null : (await listPublicServices(query)).items;
  const services = category
    ? catalog?.filter((service) => service.type === category.type)
    : catalog;

  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <PublicPageHeader
        description="Giá công khai của các dịch vụ đang cung cấp tại phòng khám."
        title={category?.label ?? "Dịch vụ và bảng giá"}
      />
      {category ? (
        <Link
          className="mt-5 inline-block font-medium text-primary underline"
          href="/services"
        >
          Xem tất cả dịch vụ
        </Link>
      ) : null}
      <PublicServiceSearchForm
        className="mt-10"
        error={error}
        input={input}
        category={category?.type}
      />
      {services ? (
        <PublicServiceList
          filtered={Boolean(query.q || category)}
          services={services}
        />
      ) : null}
    </div>
  );
}
