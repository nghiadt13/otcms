import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

/**
 * Giao diện "không tìm thấy" của màn thanh toán khi BE trả 403/404 (MSG-50 không hiện cho người
 * dùng). Dựng ngay trong màn vì dữ liệu tải ở phía trình duyệt, nên không gọi `notFound()`.
 */
export function PaymentNotFoundView() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">Không tìm thấy</h1>
      <p className="text-base text-muted-foreground">
        Không tìm thấy trang hoặc thông tin bạn cần.
      </p>
      <Link className={publicButtonClass("secondary")} href={PATIENT_ROUTES.payments}>
        Về trang Thanh toán
      </Link>
    </div>
  );
}
