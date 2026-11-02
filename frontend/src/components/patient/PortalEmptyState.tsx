import Link from "next/link";
import type { ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Khối trống chung của khu bệnh nhân: một câu dạy cách dùng và, nếu có, một nút gợi ý
// việc tiếp theo. Không viết "Không có dữ liệu" trơ trọi.
export function PortalEmptyState({
  children,
  action,
}: {
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-lg border bg-card px-6 py-10 text-center">
      <p className="text-base">{children}</p>
      {action ? <div className="mt-6 flex justify-center">{action}</div> : null}
    </div>
  );
}

/** Tài khoản chưa có hồ sơ nào: không đặt lịch hay xem kết quả được, dẫn tới thêm hồ sơ. */
export function PortalNoProfileState() {
  return (
    <PortalEmptyState
      action={
        <Link
          className={publicButtonClass("primary")}
          href={PATIENT_ROUTES.newProfile}
        >
          Thêm hồ sơ bệnh nhân
        </Link>
      }
    >
      Tài khoản của bạn chưa có hồ sơ bệnh nhân. Thêm hồ sơ để đặt lịch và xem
      kết quả.
    </PortalEmptyState>
  );
}
