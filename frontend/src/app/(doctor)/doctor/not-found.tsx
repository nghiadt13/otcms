import { FileQuestion } from "lucide-react";
import Link from "next/link";
import { PageHeader } from "@/components/PageHeader";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

// Hiển thị khi một màn của bác sĩ gọi `notFound()` (ví dụ không có lượt khám theo id).
// URL không khớp route nào do `global-not-found.tsx` xử lý.
export default function DoctorNotFound() {
  return (
    <>
      <PageHeader title="Không tìm thấy" />
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
          <FileQuestion className="size-10 text-muted-foreground" />
          <p className="font-medium">Không tìm thấy nội dung được yêu cầu.</p>
          <Link href={DOCTOR_ROUTES.dashboard} className={buttonVariants()}>
            Về bảng điều khiển
          </Link>
        </CardContent>
      </Card>
    </>
  );
}
