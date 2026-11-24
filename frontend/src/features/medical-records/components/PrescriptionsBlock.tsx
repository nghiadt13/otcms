import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { PrescriptionRef } from "../types/examinationResult";

/** Khối "Đơn thuốc": mỗi đơn đã phát hành một dòng, bấm để mở đơn. */
export function PrescriptionsBlock({ items }: { items: PrescriptionRef[] }) {
  if (items.length === 0)
    return (
      <p className="text-base text-muted-foreground">
        Lượt khám này chưa có đơn thuốc.
      </p>
    );
  return (
    <ul className="divide-y">
      {items.map((item) => (
        <li
          key={item.id}
          className="grid items-center gap-2 py-2.5 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,1fr)_auto] sm:gap-4"
        >
          <p className="text-base tabular-nums">
            <span className="font-semibold">
              Đơn ngày{" "}
              <time dateTime={item.issuedAt}>{formatDate(item.issuedAt)}</time>
            </span>
            <span className="text-muted-foreground">
              {" "}
              · {item.itemCount} thuốc
            </span>
          </p>
          <Link
            href={PATIENT_ROUTES.prescriptionDetail(item.id)}
            className={cn(
              publicButtonClass("secondary"),
              "h-11 justify-self-start px-3",
            )}
          >
            Xem đơn thuốc
            <ChevronRight aria-hidden className="size-4" />
          </Link>
        </li>
      ))}
    </ul>
  );
}
