import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { adminApiMessage } from "@/lib/api/adminApiMessage";

export function RemoteDataState({
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
        <p className="text-sm text-destructive">{adminApiMessage(error)}</p>
        <Button variant="outline" onClick={retry}>
          Thử lại
        </Button>
      </div>
    );
  return null;
}
