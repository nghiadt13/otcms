"use client";

import { useEffect } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";

// Lỗi runtime trong một màn khu Lễ tân. Nằm trong staff/layout nên sidebar và header
// vẫn hiển thị, người dùng có thể chuyển sang màn khác.
export default function StaffError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  useEffect(() => {
    // Chỗ gắn dịch vụ ghi log lỗi khi có; tạm thời ghi ra console.
    console.error(error);
  }, [error]);

  return (
    <>
      <PageHeader title="Không mở được màn hình này" />
      <div className="space-y-3">
        <p className="text-sm text-destructive" role="alert">
          Đã có lỗi khi tải màn hình. Bấm Thử lại; nếu vẫn lỗi, tải lại trang.
        </p>
        {error.digest ? (
          <p className="text-xs text-muted-foreground">
            Mã lỗi: {error.digest}
          </p>
        ) : null}
        <Button onClick={() => retry()}>Thử lại</Button>
      </div>
    </>
  );
}
