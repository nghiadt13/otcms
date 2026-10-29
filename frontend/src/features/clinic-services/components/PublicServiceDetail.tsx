import { ArrowLeft, CalendarPlus } from "lucide-react";
import Link from "next/link";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import type { PublicService } from "../types/clinicService";
import { formatClinicServicePrice } from "../formatClinicServicePrice";

// Chi tiết dùng đúng projection public từ backend; API hiện chưa cung cấp loại dịch vụ
// hay ghi chú đặt lịch nên không suy diễn các trường đó từ tên.
export function PublicServiceDetail({ service }: { service: PublicService }) {
  return (
    <article className="max-w-3xl">
      <Link
        className="inline-flex items-center gap-2 rounded-sm text-base font-medium text-primary underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
        href="/services"
      >
        <ArrowLeft aria-hidden className="size-4" />
        Tất cả dịch vụ
      </Link>

      <h1 className="mt-10 text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
        {service.name}
      </h1>
      <p className="mt-6 text-3xl font-semibold text-primary tabular-nums">
        {formatClinicServicePrice(service.price)}
      </p>
      {service.description ? (
        <p className="mt-6 text-lg leading-8 text-pretty">
          {service.description}
        </p>
      ) : null}

      <section
        aria-labelledby="service-booking-title"
        className="mt-12 border-t pt-10"
      >
        <h2 className="text-2xl font-semibold" id="service-booking-title">
          Thông tin đặt lịch
        </h2>
        <div className="mt-4 space-y-3 text-lg leading-8">
          <p>Thời lượng dự kiến: {service.durationMinutes} phút.</p>
        </div>

        {service.onlineBookable ? (
          <PublicBookAppointmentLink
            className={publicButtonClass("primary", "lg") + " mt-8"}
          >
            <CalendarPlus aria-hidden />
            Đặt lịch khám
          </PublicBookAppointmentLink>
        ) : null}
      </section>
    </article>
  );
}
