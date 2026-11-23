import {
  ArrowUpRight,
  CalendarPlus,
  FileSearch,
  Route,
  Stethoscope,
} from "lucide-react";
import Link from "next/link";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";

const linkClass =
  "group flex h-full min-h-20 items-center gap-3 p-4 text-sm font-semibold outline-none transition-colors hover:bg-secondary focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50 motion-reduce:transition-none sm:text-base lg:px-6";
const quickLinks = [
  { icon: CalendarPlus, title: "Đặt lịch khám", href: null },
  { icon: FileSearch, title: "Kết quả khám", href: PATIENT_ROUTES.results },
  { icon: Stethoscope, title: "Đội ngũ bác sĩ", href: "/doctors" },
  { icon: Route, title: "Hướng dẫn khám", href: "/#quy-trinh-kham" },
];

export function HomeQuickAccess() {
  return (
    <section aria-label="Truy cập nhanh" className="bg-card py-6 sm:py-8">
      <div className={publicContainerClass}>
        <ul className="grid grid-cols-2 overflow-hidden rounded-xl border bg-card lg:grid-cols-4">
          {quickLinks.map(({ icon: Icon, title, href }, index) => {
            const content = (
              <>
                <Icon aria-hidden className="size-6 shrink-0 text-primary" />
                <span className="min-w-0">{title}</span>
                <ArrowUpRight
                  aria-hidden
                  className="ml-auto hidden size-4 shrink-0 text-primary sm:block"
                />
              </>
            );
            return (
              <li
                className={cn(
                  index % 2 === 0 && "border-r",
                  index < 2 && "border-b lg:border-b-0",
                  index === 1 && "lg:border-r",
                )}
                key={title}
              >
                {href ? (
                  <Link className={linkClass} href={href}>
                    {content}
                  </Link>
                ) : (
                  <PublicBookAppointmentLink className={linkClass}>
                    {content}
                  </PublicBookAppointmentLink>
                )}
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
