import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { cn } from "@/lib/utils";
import { formatClinicServicePrice } from "../formatClinicServicePrice";
import type { PublicService } from "../types/clinicService";

// Khối "Dịch vụ chính" trên trang chủ. Bố cục cột biên tập: cột trái là tiêu đề,
// mô tả và nút sang /services (dính khi cuộn từ lg); cột phải là bảng giá rút gọn
// hai cột ngăn bằng vạch mảnh. Giá công khai đứng cùng hàng với tên.
export function FeaturedServices({ services }: { services: PublicService[] }) {
  if (services.length === 0) return null;
  return (
    <section
      aria-labelledby="featured-services-title"
      className="bg-card py-20 lg:py-28"
    >
      <div
        className={cn(
          publicContainerClass,
          "grid gap-12 lg:grid-cols-[minmax(0,4fr)_minmax(0,8fr)] lg:gap-20",
        )}
      >
        <div className="lg:sticky lg:top-32 lg:self-start">
          <h2
            className="text-3xl font-semibold tracking-tight text-balance xl:text-4xl"
            id="featured-services-title"
          >
            Dịch vụ chính
          </h2>
          <p className="mt-4 text-lg leading-8 text-pretty text-muted-foreground">
            Từ khám, chụp chiếu đến phẫu thuật nội soi và phục hồi chức năng,
            thực hiện tại cùng một phòng khám.
          </p>
          <Link
            className={cn(publicButtonClass("secondary"), "mt-8")}
            href="/services"
          >
            Xem bảng giá đầy đủ
            <ArrowRight aria-hidden />
          </Link>
        </div>

        <ul className="grid gap-x-12 sm:grid-cols-2">
          {services.map((service) => (
            <li className="border-t border-border py-7" key={service.id}>
              <h3 className="text-lg font-semibold text-balance">
                {service.name}
              </h3>
              <p className="mt-1 text-base font-semibold text-primary tabular-nums">
                {formatClinicServicePrice(service.price)}
              </p>
              {service.description ? (
                <p className="mt-3 text-base leading-7 text-muted-foreground">
                  {service.description}
                </p>
              ) : null}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
