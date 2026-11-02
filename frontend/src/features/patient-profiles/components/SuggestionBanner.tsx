import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";

// Băng nhắc có hồ sơ gợi ý chưa gắn, đầu trang danh sách. Nút "Xem và gắn" dẫn tới mục gợi ý
// của trang thêm hồ sơ.
export function SuggestionBanner({
  count,
  email,
}: {
  count: number;
  email: string;
}) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-card px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-base">
        Tìm thấy {count} hồ sơ khám tại phòng khám dùng email{" "}
        <span className="font-medium break-all">{email}</span>.
      </p>
      <Link
        className={cn(
          buttonVariants({ variant: "link" }),
          "h-12 shrink-0 px-0 text-base",
        )}
        href={`${PATIENT_ROUTES.newProfile}#suggested`}
      >
        Xem và gắn
      </Link>
    </div>
  );
}
