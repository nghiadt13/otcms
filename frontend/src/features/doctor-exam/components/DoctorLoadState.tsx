import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { doctorErrorMessage } from "../schema/doctorErrors";

/**
 * Trạng thái tải/lỗi của màn bác sĩ. Khác `RemoteDataState` của khu admin ở chỗ lời lỗi theo mã MSG
 * của R3 (không nhắc "tài khoản Quản trị viên") và đọc được mã lỗi kỹ thuật của diagnostic.
 */
export function DoctorLoadState({
  loading,
  error,
  retry,
}: {
  loading: boolean;
  error?: unknown;
  retry: () => void;
}) {
  if (loading)
    return (
      <div
        aria-label="Đang tải dữ liệu"
        className="space-y-3 rounded-lg border p-4"
      >
        {[0, 1, 2].map((n) => (
          <Skeleton key={n} className="h-12 w-full" />
        ))}
      </div>
    );
  if (error)
    return (
      <div role="alert" className="space-y-3 rounded-lg border p-4">
        <p className="text-sm text-destructive">{doctorErrorMessage(error)}</p>
        <Button variant="outline" onClick={retry}>
          Thử lại
        </Button>
      </div>
    );
  return null;
}
