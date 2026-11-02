import { Skeleton } from "@/components/ui/skeleton";
import { publicContainerClass } from "./publicLayout";

export type PublicSkeletonVariant = "list" | "grid" | "detail";

const pulse = "motion-reduce:animate-none";

// Khung tải đúng hình nội dung của từng kiểu trang công khai: danh sách, lưới bác sĩ,
// trang chi tiết. Không dùng spinner giữa nội dung.
export function PublicPageSkeleton({
  variant,
}: {
  variant: PublicSkeletonVariant;
}) {
  return (
    <div
      aria-busy="true"
      aria-label="Đang tải nội dung"
      className={`${publicContainerClass} py-16 lg:py-24`}
      role="status"
    >
      <Skeleton className={`h-12 w-2/3 max-w-md ${pulse}`} />
      <Skeleton className={`mt-4 h-6 w-full max-w-2xl ${pulse}`} />

      {variant === "list" ? (
        <div className="mt-12 divide-y divide-border overflow-hidden rounded-2xl border bg-card">
          {[0, 1, 2, 3].map((row) => (
            <div className="flex flex-col gap-3 px-6 py-6" key={row}>
              <Skeleton className={`h-6 w-1/2 ${pulse}`} />
              <Skeleton className={`h-5 w-3/4 ${pulse}`} />
            </div>
          ))}
        </div>
      ) : null}

      {variant === "grid" ? (
        <div className="mt-12 grid gap-x-8 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2].map((card) => (
            <div key={card}>
              <Skeleton
                className={`aspect-[4/5] w-full rounded-2xl ${pulse}`}
              />
              <Skeleton className={`mt-5 h-6 w-2/3 ${pulse}`} />
            </div>
          ))}
        </div>
      ) : null}

      {variant === "detail" ? (
        <div className="mt-12 max-w-3xl space-y-4">
          <Skeleton className={`h-5 w-full ${pulse}`} />
          <Skeleton className={`h-5 w-11/12 ${pulse}`} />
          <Skeleton className={`h-5 w-4/5 ${pulse}`} />
          <Skeleton className={`mt-8 h-6 w-1/3 ${pulse}`} />
          <Skeleton className={`h-5 w-full ${pulse}`} />
        </div>
      ) : null}
    </div>
  );
}
