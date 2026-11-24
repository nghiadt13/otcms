import { ScanLine } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { ImagingResultItem } from "../types/examinationResult";

/**
 * Khối "Hình ảnh": từng dịch vụ chụp, kết luận của bác sĩ (chỉ khi lượt đã hoàn tất)
 * và nút "Xem ảnh" tới màn chi tiết study khi chỉ định đã gắn study.
 */
export function ImagingBlock({ items }: { items: ImagingResultItem[] }) {
  if (items.length === 0)
    return (
      <p className="text-base text-muted-foreground">
        Lượt khám này chưa có ảnh chụp.
      </p>
    );
  return (
    <ul className="divide-y">
      {items.map((item) => (
        <li
          key={item.studyId ?? item.serviceName}
          className="grid items-center gap-2 py-2.5 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,1fr)_auto] sm:gap-4"
        >
          <div className="min-w-0 space-y-0.5 wrap-break-word">
            <p className="text-base font-semibold">{item.serviceName}</p>
            {item.finding ? (
              <p className="text-base leading-6">
                <span className="text-muted-foreground">Kết luận: </span>
                {item.finding}
              </p>
            ) : null}
          </div>
          {item.studyId ? (
            <Link
              href={PATIENT_ROUTES.imageStudy(item.studyId)}
              className={cn(
                publicButtonClass("secondary"),
                "h-11 justify-self-start px-3",
              )}
            >
              <ScanLine aria-hidden className="size-4" />
              Xem ảnh
            </Link>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
