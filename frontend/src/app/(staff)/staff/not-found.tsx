import Link from "next/link";
import { PageHeader } from "@/components/PageHeader";
import { buttonVariants } from "@/components/ui/button";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";

// Hiện khi một màn khu Lễ tân gọi `notFound()`. URL không khớp route nào do
// `global-not-found.tsx` xử lý.
export default function StaffNotFound() {
  return (
    <>
      <PageHeader title="Không tìm thấy" />
      <div className="space-y-3">
        <p className="text-sm text-muted-foreground">
          Không tìm thấy nội dung được yêu cầu. Có thể đã bị xóa hoặc không còn
          trong phạm vi của bạn.
        </p>
        <Link href={STAFF_ROUTES.home} className={buttonVariants({ variant: "outline" })}>
          Về khu Lễ tân
        </Link>
      </div>
    </>
  );
}
