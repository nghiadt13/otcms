"use client";

import { TriangleAlert } from "lucide-react";
import { useEffect } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

// Lỗi runtime trong một màn hình admin. Nằm trong `admin/layout.tsx` nên sidebar
// và header vẫn hiển thị, người dùng có thể chuyển sang màn khác.
export default function AdminError({
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
      <PageHeader title="Đã có lỗi xảy ra" />
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
          <TriangleAlert className="size-10 text-destructive" />
          <p className="font-medium">Không hiển thị được màn hình này.</p>
          {error.digest ? (
            <p className="text-xs text-muted-foreground">
              Mã lỗi: {error.digest}
            </p>
          ) : null}
          <Button onClick={() => retry()}>Thử lại</Button>
        </CardContent>
      </Card>
    </>
  );
}
