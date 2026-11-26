import { ArrowRight } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { publicAssetPath } from "@/lib/publicAssetPath";
import { formatClinicServicePrice } from "../formatClinicServicePrice";
import type { PublicService } from "../types/clinicService";

// Chỉ chọn ảnh minh họa; tên, giá, liên kết và mô tả luôn dùng danh mục công khai.
function serviceImage(name: string): `/images/${string}` {
  const folded = name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
  if (/xet nghiem|lab|mau|uric/.test(folded))
    return "/images/home/laboratory-service.webp";
  if (/x.quang|chup|sieu am|mri|ct\b|hinh anh/.test(folded))
    return "/images/home/imaging-service.webp";
  return "/images/home/clinic-consultation.jpg";
}

export function FeaturedServices({ services }: { services: PublicService[] }) {
  if (services.length === 0) return null;
  return (
    <section
      aria-labelledby="featured-services-title"
      className="bg-card pt-4 pb-12 sm:pb-16 lg:pb-20"
    >
      <div className={publicContainerClass}>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="max-w-2xl">
            <h2
              className="text-2xl font-semibold tracking-tight sm:text-3xl lg:text-4xl"
              id="featured-services-title"
            >
              Dịch vụ tại phòng khám
            </h2>
            <p className="mt-3 text-base leading-7 text-muted-foreground">
              Tìm hiểu dịch vụ và chi phí trước khi đến khám.
            </p>
          </div>
          <Link
            className="inline-flex min-h-11 items-center gap-2 rounded-md font-semibold text-primary outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
            href="/services"
          >
            Xem tất cả dịch vụ
            <ArrowRight aria-hidden className="size-4" />
          </Link>
        </div>
        <ul className="mt-6 grid gap-4 sm:mt-8 sm:grid-cols-2 lg:grid-cols-3 lg:gap-6">
          {services.map((service) => (
            <li className="min-w-0" key={service.id}>
              <Link
                className="group flex h-full overflow-hidden rounded-xl border bg-card outline-none transition-colors hover:border-primary/50 focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none sm:flex-col"
                href={`/services/${encodeURIComponent(service.id)}`}
              >
                <div className="relative w-24 shrink-0 overflow-hidden bg-secondary sm:aspect-[16/10] sm:w-full">
                  <Image
                    alt=""
                    className="object-cover transition-transform duration-300 group-hover:scale-105 motion-reduce:transform-none motion-reduce:transition-none"
                    fill
                    sizes="(min-width: 1280px) 389px, (min-width: 1024px) 33vw, (min-width: 640px) 50vw, 96px"
                    src={publicAssetPath(serviceImage(service.name))}
                  />
                </div>
                <div className="flex min-w-0 flex-1 flex-col p-4 sm:p-5">
                  <h3 className="flex items-start justify-between gap-3 text-base font-semibold sm:text-lg">
                    <span className="min-w-0 break-words">{service.name}</span>
                    <ArrowRight
                      aria-hidden
                      className="mt-1 size-4 shrink-0 text-primary"
                    />
                  </h3>
                  <p className="mt-2 text-base font-semibold text-primary tabular-nums">
                    {formatClinicServicePrice(service.price)}
                  </p>
                  {service.description ? (
                    <p className="mt-2 hidden text-sm leading-6 text-muted-foreground sm:line-clamp-2">
                      {service.description}
                    </p>
                  ) : null}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
