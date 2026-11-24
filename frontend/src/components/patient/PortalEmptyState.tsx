import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";

// Khối trống chung của khu bệnh nhân. Hai dạng:
// - Có `icon` + `title`: khung nét đứt, biểu tượng trong ô tròn, tiêu đề, một câu dạy cách dùng
//   (`children`) và nút gợi ý việc tiếp theo (`action`). Dùng cho màn danh sách chưa có dữ liệu.
// - Chỉ `children`: một câu ngắn (kết quả lọc rỗng, mục phụ trong màn).
// Không viết "Không có dữ liệu" trơ trọi. Biểu tượng chỉ trang trí nên ẩn với trình đọc màn hình.
export function PortalEmptyState({
  children,
  action,
  className,
  icon: Icon,
  title,
}: {
  children: ReactNode;
  action?: ReactNode;
  className?: string;
  icon?: LucideIcon;
  title?: string;
}) {
  if (!Icon || !title) {
    return (
      <div className={cn("rounded-lg border bg-card px-6 py-10 text-center", className)}>
        <p className="text-base">{children}</p>
        {action ? (
          <div className="mt-6 flex justify-center">{action}</div>
        ) : null}
      </div>
    );
  }
  return (
    <section
      className={cn(
        "flex flex-col items-center rounded-xl border border-dashed border-muted-foreground/55 bg-card px-6 py-12 text-center",
        className,
      )}
    >
      <span className="flex size-14 items-center justify-center rounded-2xl bg-secondary text-primary">
        <Icon aria-hidden className="size-7" />
      </span>
      <h3 className="mt-5 text-lg font-semibold">{title}</h3>
      <p className="mt-1.5 max-w-sm text-base text-muted-foreground">
        {children}
      </p>
      {action ? <div className="mt-6 flex justify-center">{action}</div> : null}
    </section>
  );
}

/** Tài khoản chưa có hồ sơ nào: không đặt lịch hay xem kết quả được, dẫn tới thêm hồ sơ. */
export function PortalNoProfileState() {
  return (
    <PortalEmptyState
      action={
        <Link
          className={publicButtonClass("primary")}
          href={PATIENT_ROUTES.account}
        >
          Điền thông tin cá nhân
        </Link>
      }
    >
      Tài khoản của bạn chưa có thông tin cá nhân. Hãy điền đầy đủ để đặt lịch
      và xem kết quả.
    </PortalEmptyState>
  );
}
