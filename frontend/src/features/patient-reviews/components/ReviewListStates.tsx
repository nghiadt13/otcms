"use client";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

/** Khung chờ đúng hình một dòng đánh giá hoặc lượt khám. */
export function ReviewListSkeleton({ label }: { label: string }) {
  return (
    <div
      aria-busy="true"
      className="divide-y overflow-hidden rounded-lg border bg-card"
      role="status"
    >
      <span className="sr-only">{label}</span>
      {[0, 1].map((row) => (
        <div className="space-y-2 px-4 py-4" key={row}>
          <Skeleton className="h-5 w-2/3" />
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-4 w-1/3" />
        </div>
      ))}
    </div>
  );
}

/** Lỗi tải: nói rõ chuyện gì xảy ra, có nút Thử lại. */
export function ReviewLoadError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="grid gap-4 rounded-lg border bg-card p-4" role="alert">
      <p className="text-base text-destructive">{message}</p>
      <Button
        className="h-12 w-full text-base sm:w-auto"
        onClick={onRetry}
        variant="outline"
      >
        Thử lại
      </Button>
    </div>
  );
}
