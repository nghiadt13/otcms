import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { PrescriptionListItem } from "../types/prescription";

/**
 * Danh sách đơn thuốc: ngày, người bệnh (kèm quan hệ), số thuốc, bác sĩ. Cả dòng là link.
 */
export function PrescriptionList({ items }: { items: PrescriptionListItem[] }) {
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {items.map((item) => (
        <li key={item.id}>
          <Link
            href={PATIENT_ROUTES.prescriptionDetail(item.id)}
            className="flex min-h-14 items-center gap-4 px-4 py-4 transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset motion-reduce:transition-none"
          >
            <span className="min-w-0 flex-1 space-y-1">
              <span className="block text-base font-semibold tabular-nums">
                {formatDate(item.issuedAt)} · {item.itemCount} thuốc
              </span>
              <span className="block text-base text-muted-foreground">
                {item.patient.fullName} (
                {CARE_RELATIONSHIP_LABELS[item.patient.relationship]}) ·{" "}
                {item.doctorName}
              </span>
            </span>
            <ChevronRight aria-hidden className="size-5 shrink-0 text-muted-foreground" />
          </Link>
        </li>
      ))}
    </ul>
  );
}
