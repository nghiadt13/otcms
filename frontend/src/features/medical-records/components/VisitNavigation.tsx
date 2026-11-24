import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";

const visitLinkClass = cn(publicButtonClass("secondary"), "h-11 px-3");

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
      className="flex flex-wrap items-center gap-2"
    >
      {previousVisitId ? (
        <Link
          href={PATIENT_ROUTES.resultDetail(previousVisitId)}
          className={visitLinkClass}
        >
          <ChevronLeft aria-hidden />
          Lượt trước
        </Link>
      ) : null}
      {nextVisitId ? (
        <Link
          href={PATIENT_ROUTES.resultDetail(nextVisitId)}
          className={visitLinkClass}
        >
          Lượt sau
          <ChevronRight aria-hidden />
        </Link>
      ) : null}
    </nav>
  );
}
