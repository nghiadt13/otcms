import { PlugZap } from "lucide-react";
import type { ReactNode } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

// Khung báo "backend chưa có": dùng khi màn đã dựng theo R3 nhưng BE chưa cấp endpoint. Màn
// vẫn xem được cấu trúc, nút lưu bị khóa. Không hiện mã UC cho người dùng cuối ngoài trang chờ,
// nên chỉ nêu việc cần BE.

export function BackendGapNotice({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <Alert>
      <PlugZap aria-hidden />
      <AlertTitle>{title}</AlertTitle>
      {children ? <AlertDescription>{children}</AlertDescription> : null}
    </Alert>
  );
}
