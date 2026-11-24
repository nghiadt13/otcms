import { BookOpen, CalendarDays, FileSearch, Phone, Stethoscope } from "lucide-react";
import Link from "next/link";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { clinicContact, hotlineTelHref } from "../siteNavigation";

const shortcuts = [
  { icon: CalendarDays, label: "Đặt lịch", href: PATIENT_ROUTES.newAppointment },
  { icon: FileSearch, label: "Kết quả khám", href: PATIENT_ROUTES.results },
  { icon: Stethoscope, label: "Bác sĩ", href: "/doctors" },
  { icon: BookOpen, label: "Hướng dẫn", href: "#quy-trinh-kham" },
];

export function HomeMobileShortcuts() {
  return (
    <nav
      aria-label="Lối tắt trang chủ"
      className={cn(publicContainerClass, "pt-3 md:hidden")}
    >
      <ul className="grid grid-cols-2 gap-2">
        {shortcuts.map(({ icon: Icon, label, href }) => (
          <li className="min-w-0" key={href}>
            <Link
              className="flex min-h-12 items-center gap-2 rounded-xl bg-secondary px-3 py-2 text-sm leading-tight font-semibold hover:bg-secondary/75 focus-visible:outline-2 focus-visible:outline-ring sm:text-base"
              href={href}
            >
              <Icon aria-hidden className="size-5 shrink-0 text-primary" />
              {label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export function HomeMobileBookingBar() {
  return (
    <nav
      aria-label="Thanh đặt lịch"
      className="fixed inset-x-0 bottom-0 z-30 border-t bg-card/95 pt-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] backdrop-blur md:hidden"
    >
      <div className={cn(publicContainerClass, "flex items-center gap-2")}>
        <a
          aria-label="Gọi phòng khám"
          className={cn(
            publicButtonClass("secondary"),
            "w-14 shrink-0 border-primary px-0 text-primary",
          )}
          href={hotlineTelHref(clinicContact.hotline)}
        >
          <Phone aria-hidden />
        </a>
        <PublicBookAppointmentLink
          className={cn(publicButtonClass("primary"), "min-w-0 flex-1")}
        >
          <CalendarDays aria-hidden />
          Đặt lịch khám
        </PublicBookAppointmentLink>
      </div>
    </nav>
  );
}
