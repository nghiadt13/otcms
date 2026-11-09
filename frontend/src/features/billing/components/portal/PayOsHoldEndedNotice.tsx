import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Thông báo khi BE không còn nhận thanh toán: lịch giữ chỗ hết hạn/bị hủy, hoặc hóa đơn đã hủy.
// Chữ cố định chưa có mã MSG (không tự đặt mã mới).

/** Lịch giữ chỗ chờ thanh toán đã hết hạn hoặc bị hủy: dẫn người dùng đặt lịch lại. */
export function PayOsHoldEndedNotice({
  onBookAgain,
}: {
  /** Có thì quay về bước đầu của wizard; không có thì dẫn tới trang đặt lịch. */
  onBookAgain?: () => void;
}) {
  return (
    <section className="space-y-4 rounded-xl border bg-card p-5" role="status">
      <div className="space-y-2">
        <h3 className="text-lg font-semibold">Hết thời gian giữ lịch</h3>
        <p className="text-sm text-muted-foreground">
          Lịch hẹn chưa được thanh toán đúng hạn nên giờ khám đã được mở lại cho
          người khác. Vui lòng đặt lịch lại.
        </p>
      </div>
      {onBookAgain ? (
        <button
          className={publicButtonClass("primary")}
          onClick={onBookAgain}
          type="button"
        >
          Đặt lịch lại
        </button>
      ) : (
        <Link
          className={publicButtonClass("primary")}
          href={PATIENT_ROUTES.newAppointment}
        >
          Đặt lịch lại
        </Link>
      )}
    </section>
  );
}

/** Hóa đơn lẻ đã hủy/hết hạn: không cho tạo mã mới. */
export function PayOsInvoiceVoidNotice() {
  return (
    <section className="space-y-4 rounded-xl border bg-card p-5" role="status">
      <div className="space-y-2">
        <h3 className="text-lg font-semibold">
          Hóa đơn không còn thanh toán được
        </h3>
        <p className="text-sm text-muted-foreground">
          Hóa đơn này đã hết hạn hoặc đã bị hủy nên không thể tạo mã thanh toán
          mới.
        </p>
      </div>
      <Link
        className={publicButtonClass("secondary")}
        href={PATIENT_ROUTES.payments}
      >
        Về trang Thanh toán
      </Link>
    </section>
  );
}
