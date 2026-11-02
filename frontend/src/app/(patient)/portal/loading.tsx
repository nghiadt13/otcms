import { Skeleton } from "@/components/ui/skeleton";

// Khung chờ khi chuyển giữa các màn khu bệnh nhân: tiêu đề và vài dòng, cùng một cột.
export default function PatientPortalLoading() {
  return (
    <div aria-busy="true" className="space-y-6" role="status">
      <span className="sr-only">Đang tải trang</span>
      <div className="space-y-3">
        <Skeleton className="h-8 w-56 max-w-full" />
        <Skeleton className="h-5 w-80 max-w-full" />
      </div>
      <div className="space-y-3">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    </div>
  );
}
