import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

/** Hóa đơn đã hủy (lịch bị hủy): BE không nhận thanh toán nữa, không cho tạo mã mới. */
export function PayOsInvoiceVoidNotice() {
  return (
    <section className="space-y-4 rounded-xl border bg-card p-5" role="status">
      <div className="space-y-2">
        <h3 className="text-lg font-semibold">
          Hóa đơn không còn thanh toán được
        </h3>
        <p className="text-sm text-muted-foreground">
          Hóa đơn này đã bị hủy nên không thể tạo mã thanh toán mới.
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
