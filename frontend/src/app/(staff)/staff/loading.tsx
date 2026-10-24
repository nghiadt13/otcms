import { Skeleton } from "@/components/ui/skeleton";

// Khung chờ khi chuyển giữa các màn khu Lễ tân: tiêu đề và các dòng danh sách cao 44px
// như bảng thật, không dùng spinner giữa nội dung.
export default function StaffLoading() {
  return (
    <>
      <div className="mb-6 space-y-2">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
      <div className="divide-y rounded-lg border" aria-hidden="true">
        {Array.from({ length: 5 }, (_, index) => (
          <div key={index} className="flex h-11 items-center px-3">
            <Skeleton className="h-4 w-full" />
          </div>
        ))}
      </div>
    </>
  );
}
