import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
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
    <ul className="divide-y rounded-lg border">
      {items.map((item) => (
        <li
          key={item.id}
          className="flex flex-wrap items-center justify-between gap-3 px-4 py-4"
        >
          <p className="text-base tabular-nums">
            Đơn ngày {formatDate(item.issuedAt)} · {item.itemCount} thuốc
          </p>
          <Link
            href={PATIENT_ROUTES.prescriptionDetail(item.id)}
            className={publicButtonClass("secondary")}
          >
            Xem đơn thuốc
          </Link>
        </li>
      ))}
    </ul>
  );
}
