import { Button } from "@/components/ui/button";
import { formatAppMessage } from "@/lib/messages/appMessages";

// Loading state while free times load. This is a status line, not an error.
export function DoctorOpenTimesLoading() {
  return (
    <p className="text-base text-muted-foreground" role="status">
      Đang tải giờ còn trống…
    </p>
  );
}

// Failure state (UC16 E2): the message is MSG-04 from the message table, with a retry button.
export function DoctorOpenTimesFailed({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      className="flex flex-col gap-3 rounded-2xl border bg-card p-5 sm:flex-row sm:items-center sm:justify-between"
      role="alert"
    >
      <p className="text-base">{formatAppMessage("MSG-04")}</p>
      <Button onClick={onRetry} type="button" variant="outline">
        Thử lại
      </Button>
    </div>
  );
}
