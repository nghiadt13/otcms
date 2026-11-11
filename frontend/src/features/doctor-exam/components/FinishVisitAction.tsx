"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { notifySuccess } from "@/lib/messages/notify";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { notifyDoctorError } from "../schema/doctorErrors";
import type { VisitContext } from "../types/doctorExam";

// Nút kết thúc lượt (plan 261111-0208 A9): hộp xác nhận, gọi đúng đường theo nguồn lượt, rồi về
// danh sách hôm nay. Lỗi (ví dụ thiếu bằng chứng hoàn tất) báo bằng mã MSG; `onFailed` để màn tải lại
// điều kiện và trạng thái lượt.

export function FinishVisitAction({
  context,
  ready,
  onFailed,
}: {
  context: VisitContext;
  /** Điều kiện hoàn tất phần khám đã đạt; chưa đạt thì nút bị khóa. */
  ready: boolean;
  onFailed: () => void;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [finishing, setFinishing] = useState(false);

  async function finish() {
    setFinishing(true);
    try {
      await getDoctorExamApi().finishVisit(context);
      setOpen(false);
      notifySuccess("MSG-43");
      router.push(DOCTOR_ROUTES.todayPatients);
    } catch (cause) {
      setOpen(false);
      notifyDoctorError(cause);
      onFailed();
    } finally {
      setFinishing(false);
    }
  }

  return (
    <div className="space-y-2">
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogTrigger render={<Button disabled={!ready || finishing} />}>
          Kết thúc lượt khám
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Kết thúc lượt khám này?</AlertDialogTitle>
            <AlertDialogDescription>
              Sau khi kết thúc, lượt khám chuyển sang chỉ xem.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={finishing}>Hủy</AlertDialogCancel>
            {/* Không dùng AlertDialogAction: nó tự đóng hộp thoại, còn nhãn "Đang kết thúc..." cần hộp thoại mở tới khi có kết quả. */}
            <Button disabled={finishing} onClick={() => void finish()}>
              {finishing ? "Đang kết thúc..." : "Kết thúc lượt khám"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      {ready ? null : (
        <p className="text-sm text-muted-foreground">
          Chưa kết thúc được: còn điều kiện chưa đạt ở trên.
        </p>
      )}
    </div>
  );
}
