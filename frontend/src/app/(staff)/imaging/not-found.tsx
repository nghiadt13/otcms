import { FileQuestion } from "lucide-react";
import Link from "next/link";
import { PageHeader } from "@/components/PageHeader";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

// Hiển thị khi một màn kỹ thuật viên gọi `notFound()` (ví dụ không có bản ghi theo id).
// URL không khớp route nào do `global-not-found.tsx` xử lý.
export default function ImagingNotFound() {
  return (
    <>
      <PageHeader title="Không tìm thấy" />
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
          <FileQuestion className="size-10 text-muted-foreground" />
          <p className="font-medium">Không tìm thấy nội dung được yêu cầu.</p>
          <Link href="/imaging" className={buttonVariants()}>
            Về tổng quan công việc
          </Link>
        </CardContent>
      </Card>
    </>
  );
}
