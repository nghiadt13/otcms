import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Skeleton } from "@/components/ui/skeleton";
import {
  appointmentWhenLabel,
  patientOfLabel,
} from "@/features/appointments/components/portal/patientAppointmentPresentation";
import type { PatientAppointment } from "@/features/appointments/types/patientAppointment";
import { ActiveVisitCard } from "@/features/clinic-visits/components/ActiveVisitCard";
import type { VisitSummary } from "@/features/clinic-visits/types/visitProgress";
import { PATIENT_NAV_ITEMS } from "@/features/patient-shell/patientNavigation";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";

// Các khối của trang chủ khu bệnh nhân. Trình bày thuần: dữ liệu đến từ `_home/*` đã ghép.

const ROW_LINK_CLASS =
  "flex min-h-12 items-center gap-3 rounded-lg border bg-card px-4 py-4 transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none";

const SHORTCUT_LINK_CLASS =
  "inline-flex min-h-11 items-center text-base font-medium text-primary underline-offset-4 hover:underline";

/** Lỗi của một khối: nói rõ chuyện gì xảy ra, có nút thử lại. Các khối khác vẫn hiện. */
export function HomeSectionError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="space-y-3">
      <p className="text-base text-destructive" role="alert">
        {message}
      </p>
      <button
        className={publicButtonClass("secondary")}
        onClick={onRetry}
        type="button"
      >
        Thử lại
      </button>
    </div>
  );
}

/** Khung chờ của trang chủ: đúng hình hai khối chính (lượt khám và lịch hẹn). */
export function PortalHomeSkeleton() {
  return (
    <div aria-busy="true" className="space-y-8" role="status">
      <span className="sr-only">Đang tải trang chủ</span>
      <Skeleton className="h-44 w-full" />
      <Skeleton className="h-20 w-full" />
    </div>
  );
}

/** Lượt đang diễn ra. Không có lượt nào thì khối không hiện, trang chủ gọn. */
export function ActiveVisitsSection({
  visits,
  loading,
  error,
  reload,
}: {
  visits: VisitSummary[];
  loading: boolean;
  error: string | null;
  reload: () => void;
}) {
  if (loading) return <Skeleton aria-hidden className="h-44 w-full" />;
  if (error)
    return (
      <HomeSectionError
        message={`Không tải được lượt khám. ${error}`}
        onRetry={reload}
      />
    );
  if (visits.length === 0) return null;

  return (
    <section aria-labelledby="home-active-visits" className="space-y-4">
      <h2 className="text-lg font-semibold" id="home-active-visits">
        Lượt khám đang diễn ra
      </h2>
      <div className="space-y-8">
        {visits.map((visit, index) => (
          <ActiveVisitCard key={visit.id} primary={index === 0} visit={visit} />
        ))}
      </div>
    </section>
  );
}

/**
 * Lịch hẹn sắp tới gần nhất. `showBooking` đặt nút "Đặt lịch khám" làm nút chính khi trang chưa có
 * lượt nào đang diễn ra.
 */
export function UpcomingAppointmentSection({
  appointments,
  loading,
  error,
  reload,
  showBooking,
}: {
  appointments: PatientAppointment[];
  loading: boolean;
  error: string | null;
  reload: () => void;
  showBooking: boolean;
}) {
  const next = appointments[0];

  return (
    <section aria-labelledby="home-upcoming" className="space-y-3">
      <h2 className="text-lg font-semibold" id="home-upcoming">
        Lịch hẹn sắp tới
      </h2>
      {loading ? (
        <Skeleton aria-hidden className="h-20 w-full" />
      ) : error ? (
        <HomeSectionError
          message={`Không tải được lịch hẹn. ${error}`}
          onRetry={reload}
        />
      ) : next ? (
        <Link
          className={ROW_LINK_CLASS}
          href={PATIENT_ROUTES.appointmentDetail(next.id)}
        >
          <span className="grid min-w-0 flex-1 gap-1 text-base">
            <span className="font-semibold tabular-nums">
              {appointmentWhenLabel(next.startAt)}
            </span>
            <span className="break-words">{next.service.name}</span>
            <span className="break-words font-medium">
              {patientOfLabel(next.patient)}
            </span>
          </span>
          <ChevronRight
            aria-hidden
            className="size-5 shrink-0 text-muted-foreground"
          />
        </Link>
      ) : (
        <p className="text-base text-muted-foreground">
          Bạn chưa có lịch hẹn sắp tới.
        </p>
      )}
      {showBooking ? (
        <Link
          className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
          href={PATIENT_ROUTES.newAppointment}
        >
          Đặt lịch khám
        </Link>
      ) : null}
    </section>
  );
}

/** Lối tắt tới các khu đã có, lấy từ menu chung (menu không còn nhóm Thanh toán). */
export function PortalShortcutList() {
  const shortcuts = PATIENT_NAV_ITEMS;
  return (
    <nav aria-label="Lối tắt" className="space-y-3">
      <p className="text-sm font-medium text-muted-foreground">Lối tắt</p>
      <ul className="flex flex-wrap gap-x-6 gap-y-1">
        {shortcuts.map((item) => (
          <li key={item.href}>
            <Link className={SHORTCUT_LINK_CLASS} href={item.href}>
              {item.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
