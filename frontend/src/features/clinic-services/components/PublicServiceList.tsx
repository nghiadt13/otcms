import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatClinicServicePrice } from "../formatClinicServicePrice";
import type { PublicService } from "../types/clinicService";

// Danh sách dịch vụ công khai: một khung có vạch chia, cả dòng là liên kết tới chi tiết.
// `filtered` cho biết có tiêu chí lọc đang áp dụng, để câu trống gợi ý bỏ lọc.
export function PublicServiceList({
  services,
  filtered,
}: {
  services: PublicService[];
  filtered: boolean;
}) {
  if (services.length === 0) {
    return (
      <div className="mt-12 max-w-2xl rounded-2xl border bg-card p-8">
        <p className="text-lg font-semibold">Chưa có dịch vụ phù hợp.</p>
        <p className="mt-2 text-base leading-7 text-muted-foreground">
          Thử tìm bằng một phần khác trong tên dịch vụ.
        </p>
        {filtered ? (
          <Link
            className={publicButtonClass("secondary", "md") + " mt-6"}
            href="/services"
          >
            Xem tất cả dịch vụ
          </Link>
        ) : null}
      </div>
    );
  }

  return (
    <ul className="mt-12 divide-y divide-border overflow-hidden rounded-2xl border bg-card">
      {services.map((service) => (
        <li key={service.id}>
          <Link
            className="group flex items-center gap-4 px-5 py-6 outline-none transition-colors duration-150 hover:bg-secondary/60 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset motion-reduce:transition-none sm:gap-6 sm:px-6 lg:px-8"
            href={`/services/${encodeURIComponent(service.id)}`}
          >
            <div className="min-w-0 flex-1">
              <h2 className="text-xl font-semibold text-balance">
                {service.name}
              </h2>
              {service.description ? (
                <p className="mt-2 text-base leading-7 text-muted-foreground">
                  {service.description}
                </p>
              ) : null}
            </div>
            <span className="shrink-0 text-right text-lg font-semibold text-primary tabular-nums">
              {formatClinicServicePrice(service.price)}
            </span>
            <ChevronRight
              aria-hidden
              className="size-5 shrink-0 text-primary transition-transform duration-150 group-hover:translate-x-1 motion-reduce:transition-none"
            />
          </Link>
        </li>
      ))}
    </ul>
  );
}
