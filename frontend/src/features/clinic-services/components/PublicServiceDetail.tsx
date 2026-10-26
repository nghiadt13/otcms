import { ArrowLeft, CalendarPlus } from "lucide-react";
import Link from "next/link";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import type { PublicService } from "../types/clinicService";

// Chi tiết một dịch vụ công khai (UC71 bước 5–6): loại, giá hiện hành, mô tả và thông tin
// đặt lịch. Chỉ dịch vụ Khám có nút "Đặt lịch khám" (đặt theo khung giờ của bác sĩ);
// chẩn đoán hình ảnh và xét nghiệm do bác sĩ chỉ định khi khám.
export function PublicServiceDetail({ service }: { service: PublicService }) {
  const isExamination = service.type === "EXAMINATION";
  const details = service.bookingDetails;

  return (
    <article className="max-w-3xl">
      <Link
        className="inline-flex items-center gap-2 rounded-sm text-base font-medium text-primary underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
        href="/services"
      >
        <ArrowLeft aria-hidden className="size-4" />
        Tất cả dịch vụ
      </Link>

      <p className="mt-10 text-base font-medium text-muted-foreground">
        {CATEGORY_LABELS[service.type]}
      </p>
      <h1 className="mt-2 text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
        {service.name}
      </h1>
      <p className="mt-6 text-3xl font-semibold text-primary tabular-nums">
        {formatCurrencyVnd(service.price)}
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
          {isExamination ? (
            details?.slotMinutes ? (
              <p>Mỗi lượt khám khoảng {details.slotMinutes} phút.</p>
            ) : null
          ) : (
            <p>Do bác sĩ chỉ định khi khám.</p>
          )}
          {details?.patientNote ? <p>{details.patientNote}</p> : null}
        </div>

        {isExamination ? (
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
