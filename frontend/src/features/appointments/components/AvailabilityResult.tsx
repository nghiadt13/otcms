import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { DoctorAvailability } from "../types/appointment";
import { AvailabilityGrid } from "./AvailabilityGrid";

type AvailabilityResultProps = {
  hasDoctor: boolean;
  loading: boolean;
  /** Lỗi hiển thị trong lưới (5xx); lỗi theo ô được màn xử lý riêng. */
  error: string | null;
  data: DoctorAvailability | null;
  hasFreeSlot: boolean;
  onRetry: () => void;
};

const EMPTY_BOX = "rounded-lg border bg-card px-4 py-10 text-center text-sm";

/** Vùng kết quả tab giờ trống: chưa chọn, đang tải, lỗi, không có giờ trống, hoặc lưới. */
export function AvailabilityResult({
  hasDoctor,
  loading,
  error,
  data,
  hasFreeSlot,
  onRetry,
}: AvailabilityResultProps) {
  if (loading)
    return (
      <div className="grid gap-2 rounded-lg border bg-card p-4" aria-hidden>
        {Array.from({ length: 6 }, (_, row) => (
          <Skeleton key={row} className="h-6 w-full" />
        ))}
      </div>
    );
  if (error)
    return (
      <div className={EMPTY_BOX}>
        <p role="alert" className="text-destructive">
          {error}
        </p>
        <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
          Thử lại
        </Button>
      </div>
    );
  if (!hasDoctor)
    return (
      <p className={`${EMPTY_BOX} text-muted-foreground`}>
        Chọn bác sĩ để xem giờ trống.
      </p>
    );
  if (!data) return null;
  if (!hasFreeSlot)
    return (
      <p className={`${EMPTY_BOX} text-muted-foreground`}>
        Chưa có giờ trống để hiển thị. Chọn ngày hoặc tuần khác.
      </p>
    );
  return <AvailabilityGrid data={data} />;
}
