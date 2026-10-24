"use client";

import { notFound, useRouter } from "next/navigation";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useExaminationResult } from "../hooks/useExaminationResult";
import { ExaminationResultView } from "./ExaminationResultView";

/**
 * Màn chi tiết lượt khám. 403 và 404 đi tới trang "không tìm thấy" (không lộ việc hồ sơ
 * có tồn tại hay không); 409 (lượt đã đổi trạng thái) báo bằng toast rồi về danh sách;
 * lỗi khác hiện thông báo kèm nút thử lại.
 */
export function ResultDetailScreen({ visitId }: { visitId: string }) {
  const router = useRouter();
  const { result, loading, error, reload } = useExaminationResult(visitId);
  const code = isApiError(error) ? error.code : undefined;
  const notAccessible = code === "MSG-40" || code === "MSG-41";
  const stale = code === "MSG-42";

  useEffect(() => {
    if (!stale) return;
    notifyError(error);
    router.replace(PATIENT_ROUTES.results);
  }, [stale, error, router]);

  if (notAccessible) notFound();
  if (stale) return null;
  if (error)
    return <DetailError message={messageFromError(error)} onRetry={reload} />;
  if (loading || !result) return <DetailSkeleton />;
  return <ExaminationResultView result={result} />;
}

function DetailSkeleton() {
  return (
    <div aria-hidden className="space-y-8">
      <Skeleton className="h-10 w-2/3" />
      <Skeleton className="h-5 w-1/2" />
      <Skeleton className="h-20 w-full" />
      <div className="space-y-4">
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} className="h-16 w-full" />
        ))}
      </div>
    </div>
  );
}

function DetailError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="space-y-4 rounded-lg border bg-card px-6 py-8 text-center">
      <p className="text-base text-destructive">{message}</p>
      <Button variant="outline" className="h-12 px-5 text-base" onClick={onRetry}>
        Thử lại
      </Button>
    </div>
  );
}
