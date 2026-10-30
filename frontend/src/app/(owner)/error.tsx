"use client";
import { Button } from "@/components/ui/button";
export default function OwnerError({
  retry,
}: {
  error: Error;
  retry: () => void;
}) {
  return (
    <div className="space-y-4 p-6">
      <p role="alert">Không thể mở khu Chủ phòng khám.</p>
      <Button onClick={retry}>Thử lại</Button>
    </div>
  );
}
