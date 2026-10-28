import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
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
    <ul className="divide-y rounded-lg border">
      {items.map((item) => (
        <li
          key={item.studyId ?? item.serviceName}
          className="flex flex-wrap items-center justify-between gap-3 px-4 py-4"
        >
          <div className="min-w-0 flex-1 space-y-1">
            <p className="text-base font-medium">{item.serviceName}</p>
            {item.finding ? (
              <p className="text-base">
                <span className="text-muted-foreground">Kết luận: </span>
                {item.finding}
              </p>
            ) : null}
          </div>
          {item.studyId ? (
            <Link
              href={PATIENT_ROUTES.imageStudy(item.studyId)}
              className={publicButtonClass("secondary")}
            >
              Xem ảnh
            </Link>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
