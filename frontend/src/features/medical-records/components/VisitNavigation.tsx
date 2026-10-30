import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

/** Lượt trước / lượt sau của cùng người. Id null thì mục đó ẩn. */
export function VisitNavigation({
  previousVisitId,
  nextVisitId,
}: {
  previousVisitId: string | null;
  nextVisitId: string | null;
}) {
  if (!previousVisitId && !nextVisitId) return null;
  return (
    <nav
      aria-label="Lượt khám khác của người này"
      className="flex flex-wrap items-center gap-3"
    >
      {previousVisitId ? (
        <Link
          href={PATIENT_ROUTES.resultDetail(previousVisitId)}
          className={publicButtonClass("secondary")}
        >
          <ChevronLeft aria-hidden />
          Lượt trước
        </Link>
      ) : null}
      {nextVisitId ? (
        <Link
          href={PATIENT_ROUTES.resultDetail(nextVisitId)}
          className={`${publicButtonClass("secondary")} ml-auto`}
        >
          Lượt sau
          <ChevronRight aria-hidden />
        </Link>
      ) : null}
    </nav>
  );
}
