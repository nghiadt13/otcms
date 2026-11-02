"use client";
import { Button } from "@/components/ui/button";
export default function ReceptionError({
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  return (
    <div role="alert" className="space-y-4 p-6">
      <h1 className="text-2xl font-semibold">Không thể mở màn hình lễ tân</h1>
      <p className="text-muted-foreground">
        Hãy thử tải lại. Kiểm tra trạng thái giao dịch trước khi thực hiện lại
        thao tác thu phí.
      </p>
      <Button onClick={retry}>Thử lại</Button>
    </div>
  );
}
