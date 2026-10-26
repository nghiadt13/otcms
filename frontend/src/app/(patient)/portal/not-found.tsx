import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";

// Hiện khi một màn khu bệnh nhân gọi `notFound()` (không có bản ghi, hoặc bản ghi không
// thuộc phạm vi tài khoản). URL không khớp route nào do `global-not-found.tsx` xử lý.
export default function PatientPortalNotFound() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">Không tìm thấy</h1>
      <p className="text-base text-muted-foreground">
        Không tìm thấy trang hoặc thông tin bạn cần.
      </p>
      <Link className={publicButtonClass("primary")} href="/portal">
        Về trang chủ
      </Link>
    </div>
  );
}
