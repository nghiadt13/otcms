import { Button } from "@/components/ui/button";
import type { DashboardFailure } from "../hooks/useDashboardOverview";

/**
 * Thay vùng số liệu khi tải lỗi. Lỗi quyền (403) không có "Thử lại" vì tải lại vô ích.
 */
export function DashboardStateNotice({
  failure,
  onRetry,
}: {
  failure: DashboardFailure;
  onRetry: () => void;
}) {
  return (
    <div
      role="alert"
      className="rounded-lg border bg-card px-4 py-10 text-center"
    >
      <p className="text-sm text-destructive">{failure.message}</p>
      {failure.kind === "error" ? (
        <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
          Thử lại
        </Button>
      ) : null}
    </div>
  );
}
